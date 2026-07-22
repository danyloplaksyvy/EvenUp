package com.dps.evenup.domain.expenseinput.impl

import com.dps.evenup.domain.expense.api.ExpenseBaseAllocation
import com.dps.evenup.domain.expense.api.ExpenseBaseAllocationMode
import com.dps.evenup.domain.expense.api.ExpenseBaseParticipantShare
import com.dps.evenup.domain.expense.api.ExpenseDraft
import com.dps.evenup.domain.expense.api.FeeAllocation
import com.dps.evenup.domain.expense.api.FeeAllocationMode
import com.dps.evenup.domain.expense.api.FeeParticipantShare
import com.dps.evenup.domain.expense.api.ItemAssignment
import com.dps.evenup.domain.expense.api.ItemAssignmentMode
import com.dps.evenup.domain.expense.api.ItemParticipantShare
import com.dps.evenup.domain.expense.api.PercentageBasisPoints
import com.dps.evenup.domain.expenseinput.api.AiAssignmentMode
import com.dps.evenup.domain.expenseinput.api.AiExtraction
import com.dps.evenup.domain.expenseinput.api.AiFactProvenance
import com.dps.evenup.domain.expenseinput.api.AiFactSource
import com.dps.evenup.domain.expenseinput.api.AiFeeAllocationMode
import com.dps.evenup.domain.expenseinput.api.AiPricingMode
import com.dps.evenup.domain.expenseinput.api.ClarificationKind
import com.dps.evenup.domain.expenseinput.api.ParticipantNameMatch
import com.dps.evenup.domain.expenseinput.api.ParticipantNameMatcher
import com.dps.evenup.domain.expenseinput.api.PrepareAiExpenseCommand
import com.dps.evenup.domain.expenseinput.api.PrepareAiExpenseResult
import com.dps.evenup.domain.expenseinput.api.PrepareAiExpenseUseCase
import com.dps.evenup.domain.participant.api.Participant
import com.dps.evenup.domain.participant.api.ParticipantId
import com.dps.evenup.domain.receipt.api.CurrencyCode
import com.dps.evenup.domain.receipt.api.DescriptiveExpenseItem
import com.dps.evenup.domain.receipt.api.ExpensePricingMode
import com.dps.evenup.domain.receipt.api.FeeId
import com.dps.evenup.domain.receipt.api.FeeType
import com.dps.evenup.domain.receipt.api.MoneyMinor
import com.dps.evenup.domain.receipt.api.Quantity
import com.dps.evenup.domain.receipt.api.Receipt
import com.dps.evenup.domain.receipt.api.ReceiptFee
import com.dps.evenup.domain.receipt.api.ReceiptItem
import com.dps.evenup.domain.receipt.api.ReceiptItemId
import java.util.Currency
import java.util.Locale

class DefaultPrepareAiExpenseUseCase(
    private val participantNameMatcher: ParticipantNameMatcher = DefaultParticipantNameMatcher(),
) : PrepareAiExpenseUseCase {
    override fun prepare(command: PrepareAiExpenseCommand): PrepareAiExpenseResult {
        val original = command.extraction
        if (original.participants.any { participant -> participant.isSelf } && command.personalName.isNullOrBlank()) {
            return needs(ClarificationKind.PersonalName, "What should we call you?", original)
        }

        val selfResolved = original.copy(
            participants = original.participants.map { participant ->
                if (participant.isSelf) participant.copy(name = command.personalName.orEmpty().trim()) else participant
            },
        )
        if (selfResolved.payerParticipantRef.isNullOrBlank() ||
            selfResolved.participants.none { participant -> participant.ref == selfResolved.payerParticipantRef }
        ) {
            return needs(ClarificationKind.Payer, "Who paid for this expense?", selfResolved)
        }

        val currency = (selfResolved.currency ?: command.defaultCurrency)
            ?.trim()
            ?.uppercase(Locale.ROOT)
            ?.takeIf(::isCurrencyCode)
            ?: return needs(ClarificationKind.Currency, "What currency should we use?", selfResolved)

        val total = selfResolved.totalMinor?.takeIf { it > 0L }
            ?: return needs(ClarificationKind.TotalOrPrices, "What was the total amount?", selfResolved)
        val pricingMode = selfResolved.resolvePricingMode()
        val normalized = selfResolved.withDefaults(command, currency, pricingMode)

        if (pricingMode == AiPricingMode.TotalOnly && normalized.items.any { item ->
                item.unitPriceMinor != null || item.totalPriceMinor != null
            }
        ) {
            return PrepareAiExpenseResult.Invalid(
                "Overall-total expenses can only contain unpriced descriptive items.",
                normalized,
            )
        }

        if (pricingMode == AiPricingMode.Itemized && normalized.items.any { item -> item.resolvedPrices() == null }) {
            return needs(
                ClarificationKind.TotalOrPrices,
                "What were the missing item prices?",
                normalized,
            )
        }

        if (!command.skipPossibleParticipantMatches) {
            normalized.participants.forEach { participant ->
                when (val match = participantNameMatcher.match(participant.name, command.savedParticipantNames)) {
                    is ParticipantNameMatch.Possible -> return needs(
                        kind = ClarificationKind.AmbiguousParticipant,
                        question = "Is ${participant.name} one of your saved participants?",
                        extraction = normalized,
                        candidates = match.candidates,
                    )
                    else -> Unit
                }
            }
        }
        if (normalized.participants.map { it.name.trim().lowercase(Locale.ROOT) }.distinct().size < MIN_PARTICIPANTS) {
            return needs(ClarificationKind.Participants, "Who else should be included?", normalized)
        }

        if (pricingMode == AiPricingMode.TotalOnly && !normalized.splitEverythingEqually) {
            return needs(ClarificationKind.SplitIntent, "How should the overall total be split?", normalized)
        }
        if (pricingMode == AiPricingMode.Itemized && !normalized.splitEverythingEqually &&
            normalized.items.any { item -> item.assignment == null }
        ) {
            return needs(ClarificationKind.SplitIntent, "How should the items be split?", normalized)
        }
        if (!normalized.splitEverythingEqually && normalized.fees.any { fee -> fee.allocationMode == null }
        ) {
            return needs(ClarificationKind.SplitIntent, "How should the fees be split?", normalized)
        }

        return runCatching { buildDraft(command, normalized, total, currency, pricingMode) }
            .fold(
                onSuccess = { draft -> PrepareAiExpenseResult.Ready(draft, normalized) },
                onFailure = { error -> PrepareAiExpenseResult.Invalid(error.message ?: "Review the expense details.", normalized) },
            )
    }

    private fun buildDraft(
        command: PrepareAiExpenseCommand,
        extraction: AiExtraction,
        total: Long,
        currency: String,
        pricingMode: AiPricingMode,
    ): ExpenseDraft {
        val participants = extraction.participants.mapIndexed { index, extracted ->
            val match = participantNameMatcher.match(extracted.name, command.savedParticipantNames)
            Participant(
                id = ParticipantId(extracted.ref.nonBlankId("participant_$index")),
                name = when (match) {
                    is ParticipantNameMatch.Exact -> match.savedName
                    else -> extracted.name.trim()
                },
                creationOrder = index,
                isSavedLocalName = match is ParticipantNameMatch.Exact,
            )
        }
        require(participants.size >= MIN_PARTICIPANTS) { "At least two participants are required." }
        val participantIdsByRef = extraction.participants.mapIndexed { index, participant ->
            participant.ref to participants[index].id
        }.toMap()
        val payerId = participantIdsByRef.getValue(requireNotNull(extraction.payerParticipantRef))
        val stableParticipantIds = participants.sortedWith(
            compareBy<Participant> { participant -> if (participant.id == payerId) 0 else 1 }
                .thenBy { participant -> participant.creationOrder },
        ).map { participant -> participant.id }

        val fees = extraction.fees.mapIndexed { index, fee ->
            ReceiptFee(
                id = FeeId(fee.ref.nonBlankId("fee_$index")),
                type = fee.type,
                label = fee.label.ifBlank { fee.type.name },
                amount = MoneyMinor(if (fee.type == FeeType.Discount) -kotlin.math.abs(fee.amountMinor) else kotlin.math.abs(fee.amountMinor)),
            )
        }
        val feeTotal = fees.sumOf { fee -> fee.amount.value }
        val baseTotal = total - feeTotal
        require(baseTotal >= 0L) { "The total is smaller than the fees and discounts." }

        val itemPairs = if (pricingMode == AiPricingMode.Itemized) {
            extraction.items.mapIndexed { index, item ->
                val prices = requireNotNull(item.resolvedPrices()) { "Every item needs a price." }
                val receiptItem = ReceiptItem(
                    id = ReceiptItemId(item.ref.nonBlankId("item_$index")),
                    name = item.name.trim(),
                    quantity = Quantity(item.quantity ?: 1),
                    unitPrice = MoneyMinor(prices.first),
                    totalPrice = MoneyMinor(prices.second),
                )
                receiptItem to buildItemAssignment(
                    item = item,
                    receiptItem = receiptItem,
                    participantIdsByRef = participantIdsByRef,
                    stableParticipantIds = stableParticipantIds,
                    splitEverythingEqually = extraction.splitEverythingEqually,
                )
            }
        } else {
            emptyList()
        }
        val receiptItems = itemPairs.map { pair -> pair.first }
        if (pricingMode == AiPricingMode.Itemized) {
            require(receiptItems.sumOf { item -> item.totalPrice.value } + feeTotal == total) {
                "The item prices and total do not add up."
            }
        }
        val baseAllocation = if (pricingMode == AiPricingMode.TotalOnly) {
            ExpenseBaseAllocation(
                mode = ExpenseBaseAllocationMode.Equal,
                shares = splitWeighted(baseTotal, stableParticipantIds.map { id -> id to 1L }).map { (id, amount) ->
                    ExpenseBaseParticipantShare(id, MoneyMinor(amount))
                },
            )
        } else {
            null
        }
        val feeAllocations = fees.mapIndexed { feeIndex, fee ->
            val extractedFee = extraction.fees[feeIndex]
            val refs = extractedFee.participantRefs.ifEmpty { extraction.participants.map { it.ref } }
            val ids = refs.map { ref -> participantIdsByRef.getValue(ref) }
                .also { values -> require(values.distinct().size == values.size) { "Fee participants must be unique." } }
                .sortedBy { id -> stableParticipantIds.indexOf(id) }
            val unsignedShares = when (extractedFee.allocationMode ?: AiFeeAllocationMode.Equal) {
                AiFeeAllocationMode.Custom -> extractedFee.shares.map { share ->
                    val amount = requireNotNull(share.amountMinor) { "Custom fee shares need amounts." }
                    participantIdsByRef.getValue(share.participantRef) to kotlin.math.abs(amount)
                }.also(::requireUniqueRecipients).sortedByStableOrder(stableParticipantIds)
                AiFeeAllocationMode.Proportional -> {
                    val itemTotals = itemPairs.flatMap { pair -> pair.second.shares }
                        .groupBy { share -> share.participantId }
                        .mapValues { (_, shares) -> shares.sumOf { it.amount.value } }
                    splitWeighted(kotlin.math.abs(fee.amount.value), ids.map { id -> id to itemTotals.getOrDefault(id, 0L).coerceAtLeast(1L) })
                }
                AiFeeAllocationMode.Equal -> splitWeighted(kotlin.math.abs(fee.amount.value), ids.map { id -> id to 1L })
            }
            val shares = unsignedShares.map { (id, amount) ->
                id to if (fee.type == FeeType.Discount) -kotlin.math.abs(amount) else kotlin.math.abs(amount)
            }
            require(shares.sumOf { it.second } == fee.amount.value) { "Fee shares do not add up." }
            FeeAllocation(
                feeId = fee.id,
                mode = when (extractedFee.allocationMode ?: AiFeeAllocationMode.Equal) {
                    AiFeeAllocationMode.Equal -> FeeAllocationMode.Equal
                    AiFeeAllocationMode.Proportional -> FeeAllocationMode.Proportional
                    AiFeeAllocationMode.Custom -> FeeAllocationMode.Custom
                },
                shares = shares.map { (id, amount) -> FeeParticipantShare(id, MoneyMinor(amount)) },
            )
        }

        val receipt = Receipt(
            merchantName = extraction.title.orEmpty(),
            currencyCode = CurrencyCode(currency),
            transactionDateLabel = extraction.transactionDate,
            items = receiptItems,
            descriptiveItems = if (pricingMode == AiPricingMode.TotalOnly) extraction.items.mapIndexed { index, item ->
                DescriptiveExpenseItem(
                    id = ReceiptItemId(item.ref.nonBlankId("description_$index")),
                    name = item.name.trim(),
                    quantity = item.quantity?.let(::Quantity),
                )
            } else emptyList(),
            fees = fees,
            subtotal = MoneyMinor(if (pricingMode == AiPricingMode.TotalOnly) baseTotal else receiptItems.sumOf { it.totalPrice.value }),
            total = MoneyMinor(total),
            pricingMode = if (pricingMode == AiPricingMode.TotalOnly) ExpensePricingMode.TotalOnly else ExpensePricingMode.Itemized,
        )
        return ExpenseDraft(
            id = command.draftId,
            receipt = receipt,
            participants = participants,
            payerId = payerId,
            itemAssignments = itemPairs.map { pair -> pair.second },
            feeAllocations = feeAllocations,
            baseAllocation = baseAllocation,
        )
    }

    private fun buildItemAssignment(
        item: com.dps.evenup.domain.expenseinput.api.AiExtractedItem,
        receiptItem: ReceiptItem,
        participantIdsByRef: Map<String, ParticipantId>,
        stableParticipantIds: List<ParticipantId>,
        splitEverythingEqually: Boolean,
    ): ItemAssignment {
        val assignment = item.assignment
        val mode = if (splitEverythingEqually) AiAssignmentMode.SharedEqual else requireNotNull(assignment).mode
        val extractedShares = assignment?.shares.orEmpty()
        val shares: List<ItemParticipantShare> = when (mode) {
            AiAssignmentMode.Full -> {
                val ref = requireNotNull(extractedShares.singleOrNull()?.participantRef) { "A full assignment needs one person." }
                listOf(ItemParticipantShare(participantIdsByRef.getValue(ref), receiptItem.totalPrice))
            }
            AiAssignmentMode.SharedEqual -> {
                val ids = extractedShares.map { share -> participantIdsByRef.getValue(share.participantRef) }
                    .ifEmpty { stableParticipantIds }
                    .also { values -> require(values.distinct().size == values.size) { "Item participants must be unique." } }
                    .sortedBy { id -> stableParticipantIds.indexOf(id) }
                splitWeighted(receiptItem.totalPrice.value, ids.map { id -> id to 1L }).map { (id, amount) ->
                    ItemParticipantShare(id, MoneyMinor(amount))
                }
            }
            AiAssignmentMode.ByUnits -> {
                val weighted = extractedShares.map { share ->
                    participantIdsByRef.getValue(share.participantRef) to requireNotNull(share.quantity).toLong()
                }.also(::requireUniqueRecipients).sortedByStableOrder(stableParticipantIds)
                require(weighted.sumOf { it.second } == receiptItem.quantity.value.toLong()) { "Assigned quantities do not match." }
                splitWeighted(receiptItem.totalPrice.value, weighted).mapIndexed { index, (id, amount) ->
                    ItemParticipantShare(id, MoneyMinor(amount), quantity = Quantity(weighted[index].second.toInt()))
                }
            }
            AiAssignmentMode.CustomAmount -> extractedShares.map { share ->
                ItemParticipantShare(
                    participantId = participantIdsByRef.getValue(share.participantRef),
                    amount = MoneyMinor(requireNotNull(share.amountMinor)),
                )
            }.also { values ->
                require(values.map { it.participantId }.distinct().size == values.size) { "Item participants must be unique." }
            }.sortedBy { share -> stableParticipantIds.indexOf(share.participantId) }
            AiAssignmentMode.Percentage -> {
                require(extractedShares.sumOf { it.percentageBasisPoints ?: 0 } == PercentageBasisPoints.MAX_BASIS_POINTS)
                val weighted = extractedShares.map { share ->
                    participantIdsByRef.getValue(share.participantRef) to requireNotNull(share.percentageBasisPoints).toLong()
                }.also(::requireUniqueRecipients).sortedByStableOrder(stableParticipantIds)
                splitWeighted(receiptItem.totalPrice.value, weighted).mapIndexed { index, (id, amount) ->
                    ItemParticipantShare(
                        id,
                        MoneyMinor(amount),
                        percentage = PercentageBasisPoints(weighted[index].second.toInt()),
                    )
                }
            }
            AiAssignmentMode.Ratio -> {
                val ratioWeights = extractedShares.map { share ->
                    participantIdsByRef.getValue(share.participantRef) to requireNotNull(share.ratioWeight).toLong()
                }.also(::requireUniqueRecipients).sortedByStableOrder(stableParticipantIds)
                val basisPoints = splitWeighted(PercentageBasisPoints.MAX_BASIS_POINTS.toLong(), ratioWeights)
                splitWeighted(receiptItem.totalPrice.value, basisPoints).mapIndexed { index, (id, amount) ->
                    ItemParticipantShare(
                        participantId = id,
                        amount = MoneyMinor(amount),
                        percentage = PercentageBasisPoints(basisPoints[index].second.toInt()),
                    )
                }
            }
        }
        require(shares.sumOf { it.amount.value } == receiptItem.totalPrice.value) { "Item shares do not add up." }
        return ItemAssignment(
            receiptItemId = receiptItem.id,
            mode = when (mode) {
                AiAssignmentMode.Full -> ItemAssignmentMode.Full
                AiAssignmentMode.ByUnits -> ItemAssignmentMode.ByUnits
                AiAssignmentMode.SharedEqual -> ItemAssignmentMode.SharedEqual
                AiAssignmentMode.CustomAmount -> ItemAssignmentMode.CustomAmount
                AiAssignmentMode.Percentage, AiAssignmentMode.Ratio -> ItemAssignmentMode.Percentage
            },
            shares = shares,
        )
    }

    private fun requireUniqueRecipients(weightedRecipients: List<Pair<ParticipantId, Long>>) {
        require(weightedRecipients.map { it.first }.distinct().size == weightedRecipients.size) {
            "Allocation participants must be unique."
        }
    }

    private fun List<Pair<ParticipantId, Long>>.sortedByStableOrder(
        stableParticipantIds: List<ParticipantId>,
    ): List<Pair<ParticipantId, Long>> = sortedBy { (id, _) -> stableParticipantIds.indexOf(id) }

    private fun splitWeighted(total: Long, weightedRecipients: List<Pair<ParticipantId, Long>>): List<Pair<ParticipantId, Long>> {
        require(total >= 0L && weightedRecipients.isNotEmpty())
        require(weightedRecipients.all { it.second > 0L })
        val totalWeight = weightedRecipients.sumOf { it.second }
        val raw = weightedRecipients.map { (id, weight) -> id to (total * weight / totalWeight) }
        var remainder = total - raw.sumOf { it.second }
        return raw.map { (id, amount) ->
            val extra = if (remainder > 0L) 1L.also { remainder -= 1L } else 0L
            id to amount + extra
        }
    }

    private fun AiExtraction.resolvePricingMode(): AiPricingMode = pricingMode
        ?: if (splitEverythingEqually && items.all { item -> item.unitPriceMinor == null && item.totalPriceMinor == null }) {
            AiPricingMode.TotalOnly
        } else {
            AiPricingMode.Itemized
        }

    private fun AiExtraction.withDefaults(
        command: PrepareAiExpenseCommand,
        currency: String,
        pricingMode: AiPricingMode,
    ): AiExtraction {
        val derivedTitle = deriveTitle(command.originalDescription)
        val additions = buildList {
            if (title.isNullOrBlank()) add(
                AiFactProvenance(
                    path = "title",
                    source = if (derivedTitle == null) AiFactSource.Defaulted else AiFactSource.Derived,
                    needsReview = false,
                    reason = if (derivedTitle == null) "No title was provided." else "Derived from the expense description.",
                ),
            )
            if (transactionDate.isNullOrBlank()) add(AiFactProvenance("transactionDate", AiFactSource.Defaulted, false))
            if (this@withDefaults.currency.isNullOrBlank()) add(AiFactProvenance("currency", AiFactSource.Defaulted, false))
        }
        return copy(
            title = title?.takeIf { it.isNotBlank() } ?: derivedTitle ?: "Shared expense",
            transactionDate = transactionDate?.takeIf { it.isNotBlank() } ?: command.todayIsoDate,
            currency = currency,
            pricingMode = pricingMode,
            provenance = provenance + additions,
        )
    }

    private fun deriveTitle(description: String): String? {
        val normalized = description.lowercase(Locale.ROOT)
        return listOf(
            "breakfast" to "Breakfast",
            "brunch" to "Brunch",
            "lunch" to "Lunch",
            "dinner" to "Dinner",
            "groceries" to "Groceries",
            "coffee" to "Coffee",
            "drinks" to "Drinks",
            "taxi" to "Taxi",
            "hotel" to "Hotel",
            "movie" to "Movie",
        ).firstOrNull { (keyword, _) ->
            Regex("\\b${Regex.escape(keyword)}\\b").containsMatchIn(normalized)
        }?.second
    }

    private fun com.dps.evenup.domain.expenseinput.api.AiExtractedItem.resolvedPrices(): Pair<Long, Long>? {
        val quantityValue = quantity ?: 1
        if (quantityValue <= 0) return null
        val total = totalPriceMinor ?: unitPriceMinor?.times(quantityValue.toLong()) ?: return null
        val unit = unitPriceMinor ?: (total / quantityValue).takeIf { it > 0L } ?: return null
        return if (unit > 0L && total > 0L) unit to total else null
    }

    private fun needs(
        kind: ClarificationKind,
        question: String,
        extraction: AiExtraction,
        candidates: List<String> = emptyList(),
    ): PrepareAiExpenseResult.NeedsClarification = PrepareAiExpenseResult.NeedsClarification(
        kind = kind,
        question = question,
        extraction = extraction,
        candidateNames = candidates,
    )

    private fun String.nonBlankId(fallback: String): String = trim().ifBlank { fallback }

    private fun isCurrencyCode(value: String): Boolean = value != "XXX" && runCatching {
        Currency.getInstance(value).currencyCode == value
    }.getOrDefault(false)

    private companion object {
        const val MIN_PARTICIPANTS = 2
    }
}
