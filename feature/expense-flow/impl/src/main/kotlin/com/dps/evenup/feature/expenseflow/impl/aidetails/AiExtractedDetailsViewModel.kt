package com.dps.evenup.feature.expenseflow.impl.aidetails

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.dps.evenup.data.expense.api.ExpenseDraftRepository
import com.dps.evenup.data.expenseinput.api.AiExpensePreferencesRepository
import com.dps.evenup.data.expenseinput.api.AiExpenseSessionRepository
import com.dps.evenup.data.participant.api.SavedParticipantRepository
import com.dps.evenup.domain.expense.api.ExpenseDraft
import com.dps.evenup.domain.expense.api.ExpenseDraftId
import com.dps.evenup.domain.expenseinput.api.AiAssignmentMode
import com.dps.evenup.domain.expenseinput.api.AiExpensePhase
import com.dps.evenup.domain.expenseinput.api.AiExpenseSession
import com.dps.evenup.domain.expenseinput.api.AiExtractedAssignment
import com.dps.evenup.domain.expenseinput.api.AiExtractedFee
import com.dps.evenup.domain.expenseinput.api.AiExtractedItem
import com.dps.evenup.domain.expenseinput.api.AiExtractedParticipant
import com.dps.evenup.domain.expenseinput.api.AiExtractedShare
import com.dps.evenup.domain.expenseinput.api.AiExtraction
import com.dps.evenup.domain.expenseinput.api.AiFactProvenance
import com.dps.evenup.domain.expenseinput.api.AiFactSource
import com.dps.evenup.domain.expenseinput.api.AiFeeAllocationMode
import com.dps.evenup.domain.expenseinput.api.AiPricingMode
import com.dps.evenup.domain.expenseinput.api.ClarificationKind
import com.dps.evenup.domain.expenseinput.api.PrepareAiExpenseCommand
import com.dps.evenup.domain.expenseinput.api.PrepareAiExpenseResult
import com.dps.evenup.domain.expenseinput.api.PrepareAiExpenseUseCase
import com.dps.evenup.domain.receipt.api.FeeType
import java.math.BigDecimal
import java.time.LocalDate
import java.util.Currency
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class AiExtractedDetailsUiState(
    val isLoading: Boolean = true,
    val extraction: AiExtraction = AiExtraction(),
    val savedParticipantNames: List<String> = emptyList(),
    val editDraft: AiDetailsEditDraft? = null,
    val blockingKind: ClarificationKind? = null,
    val blockingMessage: String? = null,
    val sheetError: String? = null,
    val pageError: String? = null,
    val isSaving: Boolean = false,
    val hasManualEdits: Boolean = false,
    val fromReview: Boolean = false,
) {
    val isComplete: Boolean get() = !isLoading && blockingKind == null && pageError == null
    val primaryLabel: String
        get() = when {
            isComplete && fromReview -> "Save changes"
            isComplete -> "Continue to review"
            blockingKind == ClarificationKind.PersonalName -> "Set your name"
            blockingKind == ClarificationKind.Payer -> "Choose payer"
            blockingKind == ClarificationKind.Currency -> "Choose currency"
            blockingKind == ClarificationKind.TotalOrPrices -> "Add missing amounts"
            blockingKind == ClarificationKind.Participants -> "Add another person"
            blockingKind == ClarificationKind.AmbiguousParticipant -> "Review people"
            blockingKind == ClarificationKind.SplitIntent -> "Set the split"
            else -> "Review expense details"
        }
}

sealed interface AiDetailsEditDraft {
    data class Facts(
        val title: String,
        val date: String,
        val currency: String,
        val total: String,
        val pricingMode: AiPricingMode,
    ) : AiDetailsEditDraft

    data class People(
        val participants: List<AiDetailsParticipantDraft>,
        val payerRef: String?,
        val newName: String = "",
    ) : AiDetailsEditDraft

    data class Item(
        val ref: String,
        val isNew: Boolean,
        val name: String,
        val quantity: String,
        val total: String,
        val pricingMode: AiPricingMode,
        val deleteRequested: Boolean = false,
    ) : AiDetailsEditDraft

    data class Fee(
        val ref: String,
        val isNew: Boolean,
        val label: String,
        val type: FeeType,
        val amount: String,
        val allocationMode: AiFeeAllocationMode,
        val participantRefs: Set<String>,
        val customAmounts: Map<String, String>,
        val deleteRequested: Boolean = false,
    ) : AiDetailsEditDraft

    data class Split(
        val splitEverythingEqually: Boolean,
    ) : AiDetailsEditDraft

    data class Assignment(
        val itemRef: String,
        val itemName: String,
        val mode: AiAssignmentMode,
        val shares: List<AiDetailsShareDraft>,
    ) : AiDetailsEditDraft
}

data class AiDetailsParticipantDraft(
    val ref: String,
    val name: String,
    val isSelf: Boolean,
)

data class AiDetailsShareDraft(
    val participantRef: String,
    val participantName: String,
    val selected: Boolean,
    val value: String = "",
)

sealed interface AiExtractedDetailsEffect {
    data object Ready : AiExtractedDetailsEffect
}

class AiExtractedDetailsViewModel(
    private val sessionRepository: AiExpenseSessionRepository,
    private val preferencesRepository: AiExpensePreferencesRepository,
    private val savedParticipantRepository: SavedParticipantRepository,
    private val draftRepository: ExpenseDraftRepository,
    private val prepareAiExpense: PrepareAiExpenseUseCase,
    private val fromReview: Boolean,
) : ViewModel() {
    private val mutableUiState = MutableStateFlow(AiExtractedDetailsUiState(fromReview = fromReview))
    val uiState: StateFlow<AiExtractedDetailsUiState> = mutableUiState.asStateFlow()
    private val mutableEffects = MutableSharedFlow<AiExtractedDetailsEffect>(extraBufferCapacity = 1)
    val effects: SharedFlow<AiExtractedDetailsEffect> = mutableEffects.asSharedFlow()

    private var session: AiExpenseSession? = null
    private var personalName: String? = null
    private var defaultCurrency: String? = null
    private var preparedDraft: ExpenseDraft? = null

    init {
        viewModelScope.launch {
            session = sessionRepository.getSession()
            personalName = preferencesRepository.getPersonalName()
            defaultCurrency = preferencesRepository.getDefaultCurrency()
            val savedNames = savedParticipantRepository.getSavedParticipantNames().map { it.value }
            val extraction = session?.extraction ?: AiExtraction()
            mutableUiState.value = mutableUiState.value.copy(savedParticipantNames = savedNames)
            evaluate(extraction, persist = false, manualEdit = session?.hasManualEdits == true)
        }
    }

    fun openFacts() {
        val extraction = uiState.value.extraction
        mutableUiState.value = uiState.value.copy(
            editDraft = AiDetailsEditDraft.Facts(
                title = extraction.title.orEmpty(),
                date = extraction.transactionDate.orEmpty(),
                currency = extraction.currency ?: defaultCurrency ?: "USD",
                total = extraction.totalMinor?.let(::formatMinor).orEmpty(),
                pricingMode = extraction.pricingMode ?: AiPricingMode.Itemized,
            ),
            sheetError = null,
        )
    }

    fun openPeople() {
        val extraction = uiState.value.extraction
        mutableUiState.value = uiState.value.copy(
            editDraft = AiDetailsEditDraft.People(
                participants = extraction.participants.map { participant ->
                    AiDetailsParticipantDraft(
                        ref = participant.ref,
                        name = if (participant.isSelf) {
                            personalName ?: participant.name.takeUnless { it.isSelfReference() }.orEmpty()
                        } else {
                            participant.name
                        },
                        isSelf = participant.isSelf,
                    )
                },
                payerRef = extraction.payerParticipantRef,
            ),
            sheetError = null,
        )
    }

    fun openItem(ref: String?) {
        val extraction = uiState.value.extraction
        val item = extraction.items.firstOrNull { it.ref == ref }
        mutableUiState.value = uiState.value.copy(
            editDraft = AiDetailsEditDraft.Item(
                ref = item?.ref ?: "item_${UUID.randomUUID()}",
                isNew = item == null,
                name = item?.name.orEmpty(),
                quantity = (item?.quantity ?: 1).toString(),
                total = item?.totalPriceMinor?.let(::formatMinor).orEmpty(),
                pricingMode = extraction.pricingMode ?: AiPricingMode.Itemized,
            ),
            sheetError = null,
        )
    }

    fun openFee(ref: String?) {
        val extraction = uiState.value.extraction
        val fee = extraction.fees.firstOrNull { it.ref == ref }
        val participantRefs = fee?.participantRefs?.toSet().orEmpty().ifEmpty {
            extraction.participants.map { it.ref }.toSet()
        }
        mutableUiState.value = uiState.value.copy(
            editDraft = AiDetailsEditDraft.Fee(
                ref = fee?.ref ?: "fee_${UUID.randomUUID()}",
                isNew = fee == null,
                label = fee?.label.orEmpty(),
                type = fee?.type ?: FeeType.Other,
                amount = fee?.amountMinor?.let { formatMinor(kotlin.math.abs(it)) }.orEmpty(),
                allocationMode = fee?.allocationMode ?: AiFeeAllocationMode.Equal,
                participantRefs = participantRefs,
                customAmounts = fee?.shares.orEmpty().associate { share ->
                    share.participantRef to share.amountMinor?.let { formatMinor(kotlin.math.abs(it)) }.orEmpty()
                },
            ),
            sheetError = null,
        )
    }

    fun openSplit() {
        mutableUiState.value = uiState.value.copy(
            editDraft = AiDetailsEditDraft.Split(uiState.value.extraction.splitEverythingEqually),
            sheetError = null,
        )
    }

    fun openAssignment(itemRef: String) {
        val extraction = uiState.value.extraction
        val item = extraction.items.firstOrNull { it.ref == itemRef } ?: return
        val assignment = item.assignment
        val mode = assignment?.mode ?: AiAssignmentMode.SharedEqual
        val values = assignment?.shares.orEmpty().associateBy { it.participantRef }
        mutableUiState.value = uiState.value.copy(
            editDraft = AiDetailsEditDraft.Assignment(
                itemRef = item.ref,
                itemName = item.name,
                mode = mode,
                shares = extraction.participants.map { participant ->
                    val share = values[participant.ref]
                    AiDetailsShareDraft(
                        participantRef = participant.ref,
                        participantName = participant.name,
                        selected = share != null || (assignment == null && mode == AiAssignmentMode.SharedEqual),
                        value = share.valueFor(mode),
                    )
                },
            ),
            sheetError = null,
        )
    }

    fun updateEditDraft(transform: (AiDetailsEditDraft) -> AiDetailsEditDraft) {
        val draft = uiState.value.editDraft ?: return
        mutableUiState.value = uiState.value.copy(editDraft = transform(draft), sheetError = null)
    }

    fun addDraftParticipant(name: String) {
        val draft = uiState.value.editDraft as? AiDetailsEditDraft.People ?: return
        val trimmed = name.trim().replace(Regex("\\s+"), " ")
        if (trimmed.isBlank()) {
            mutableUiState.value = uiState.value.copy(sheetError = "Enter a name first.")
            return
        }
        if (draft.participants.any { it.name.equals(trimmed, ignoreCase = true) }) {
            mutableUiState.value = uiState.value.copy(sheetError = "$trimmed is already included.")
            return
        }
        val updated = draft.copy(
            participants = draft.participants + AiDetailsParticipantDraft(
                ref = "participant_${UUID.randomUUID()}",
                name = trimmed,
                isSelf = false,
            ),
            newName = "",
        )
        mutableUiState.value = uiState.value.copy(editDraft = updated, sheetError = null)
    }

    fun removeDraftParticipant(ref: String) {
        val draft = uiState.value.editDraft as? AiDetailsEditDraft.People ?: return
        mutableUiState.value = uiState.value.copy(
            editDraft = draft.copy(
                participants = draft.participants.filterNot { it.ref == ref },
                payerRef = draft.payerRef.takeUnless { it == ref },
            ),
            sheetError = null,
        )
    }

    fun dismissSheet() {
        mutableUiState.value = uiState.value.copy(editDraft = null, sheetError = null)
    }

    fun deleteActiveRow() {
        val active = uiState.value.editDraft ?: return
        when (active) {
            is AiDetailsEditDraft.Item -> if (!active.isNew) {
                mutableUiState.value = uiState.value.copy(
                    editDraft = active.copy(deleteRequested = true),
                    sheetError = null,
                )
            }
            is AiDetailsEditDraft.Fee -> if (!active.isNew) {
                mutableUiState.value = uiState.value.copy(
                    editDraft = active.copy(deleteRequested = true),
                    sheetError = null,
                )
            }
            else -> return
        }
    }

    fun applySheet() {
        val active = uiState.value.editDraft ?: return
        val current = uiState.value.extraction
        val updated = runCatching {
            when (active) {
                is AiDetailsEditDraft.Facts -> applyFacts(current, active)
                is AiDetailsEditDraft.People -> applyPeople(current, active)
                is AiDetailsEditDraft.Item -> if (active.deleteRequested) {
                    current.copy(items = current.items.filterNot { it.ref == active.ref })
                } else {
                    applyItem(current, active)
                }
                is AiDetailsEditDraft.Fee -> if (active.deleteRequested) {
                    current.copy(fees = current.fees.filterNot { it.ref == active.ref })
                } else {
                    applyFee(current, active)
                }
                is AiDetailsEditDraft.Split -> current.copy(
                    splitEverythingEqually = active.splitEverythingEqually,
                    items = if (active.splitEverythingEqually) {
                        current.items.map { it.copy(assignment = null) }
                    } else {
                        current.items
                    },
                )
                is AiDetailsEditDraft.Assignment -> applyAssignment(current, active)
            }
        }.getOrElse { error ->
            mutableUiState.value = uiState.value.copy(sheetError = error.message ?: "Review this section.")
            return
        }
        commit(updated)
    }

    fun primaryAction() {
        if (uiState.value.isComplete) {
            viewModelScope.launch {
                val draft = preparedDraft ?: return@launch
                mutableUiState.value = uiState.value.copy(isSaving = true, pageError = null)
                runCatching {
                    draftRepository.saveDraft(draft)
                    persistSession(uiState.value.extraction, AiExpensePhase.Ready, null, true)
                }.onSuccess {
                    mutableUiState.value = uiState.value.copy(isSaving = false)
                    mutableEffects.emit(AiExtractedDetailsEffect.Ready)
                }.onFailure { error ->
                    mutableUiState.value = uiState.value.copy(
                        isSaving = false,
                        pageError = error.message ?: "We couldn't save these changes.",
                    )
                }
            }
            return
        }
        when (uiState.value.blockingKind) {
            ClarificationKind.PersonalName,
            ClarificationKind.Payer,
            ClarificationKind.Participants,
            ClarificationKind.AmbiguousParticipant,
            -> openPeople()
            ClarificationKind.SplitIntent -> {
                val extraction = uiState.value.extraction
                val firstUnassigned = extraction.items.firstOrNull { it.assignment == null }
                if (extraction.pricingMode == AiPricingMode.Itemized && !extraction.splitEverythingEqually && firstUnassigned != null) {
                    openAssignment(firstUnassigned.ref)
                } else {
                    openSplit()
                }
            }
            ClarificationKind.Currency,
            ClarificationKind.TotalOrPrices,
            null,
            -> openFacts()
        }
    }

    private fun commit(extraction: AiExtraction) {
        mutableUiState.value = uiState.value.copy(editDraft = null, sheetError = null, pageError = null)
        viewModelScope.launch { evaluate(extraction, persist = true, manualEdit = true) }
    }

    private suspend fun evaluate(extraction: AiExtraction, persist: Boolean, manualEdit: Boolean) {
        val currentSession = session
        if (currentSession == null) {
            mutableUiState.value = uiState.value.copy(
                isLoading = false,
                pageError = "This expense session is no longer available.",
            )
            return
        }
        val result = prepareAiExpense.prepare(
            PrepareAiExpenseCommand(
                draftId = ExpenseDraftId("ai_${currentSession.sessionId}"),
                extraction = extraction,
                originalDescription = currentSession.description,
                personalName = personalName,
                defaultCurrency = defaultCurrency,
                savedParticipantNames = uiState.value.savedParticipantNames,
                todayIsoDate = LocalDate.now().toString(),
                skipPossibleParticipantMatches = manualEdit,
            ),
        )
        val (normalized, kind, message, draft) = when (result) {
            is PrepareAiExpenseResult.Ready -> Validation(result.extraction, null, null, result.draft)
            is PrepareAiExpenseResult.NeedsClarification -> Validation(
                result.extraction,
                result.kind,
                userFacingBlockingMessage(result.kind, result.question),
                null,
            )
            is PrepareAiExpenseResult.Invalid -> Validation(
                result.extraction,
                null,
                null,
                null,
                result.message,
            )
        }
        preparedDraft = draft
        mutableUiState.value = uiState.value.copy(
            isLoading = false,
            extraction = normalized,
            blockingKind = kind,
            blockingMessage = message,
            pageError = (result as? PrepareAiExpenseResult.Invalid)?.message,
            hasManualEdits = manualEdit,
        )
        if (persist) {
            val phase = if (draft != null) AiExpensePhase.Ready else AiExpensePhase.NeedsClarification
            persistSession(normalized, phase, kind, manualEdit)
            if (draft != null) draftRepository.saveDraft(draft)
        }
    }

    private suspend fun persistSession(
        extraction: AiExtraction,
        phase: AiExpensePhase,
        clarification: ClarificationKind?,
        hasManualEdits: Boolean,
    ) {
        val current = session ?: return
        val updated = current.copy(
            extraction = extraction,
            phase = phase,
            activeClarification = clarification,
            answerDraft = "",
            hasManualEdits = hasManualEdits,
            failureCode = null,
        )
        sessionRepository.saveSession(updated)
        session = updated
    }

    private fun applyFacts(current: AiExtraction, draft: AiDetailsEditDraft.Facts): AiExtraction {
        val title = draft.title.trim()
        require(title.isNotBlank()) { "Enter an expense title." }
        require(title.length <= 120) { "Use 120 characters or fewer for the title." }
        val date = runCatching { LocalDate.parse(draft.date.trim()) }.getOrNull()
        require(date != null && !date.isAfter(LocalDate.now())) { "Choose today or an earlier date." }
        val currency = draft.currency.trim().uppercase(Locale.ROOT)
        require(currency.length == 3 && runCatching { Currency.getInstance(currency) }.isSuccess) {
            "Choose a valid currency."
        }
        val total = parseMinor(draft.total)
        require(total > 0L) { "Enter a total greater than zero." }
        val modeChanged = current.pricingMode != null && current.pricingMode != draft.pricingMode
        val items = when (draft.pricingMode) {
            AiPricingMode.TotalOnly -> current.items.map { item ->
                item.copy(unitPriceMinor = null, totalPriceMinor = null, assignment = null)
            }
            AiPricingMode.Itemized -> if (modeChanged) current.items.map { it.copy(assignment = null) } else current.items
        }
        return current.copy(
            title = title,
            transactionDate = date.toString(),
            currency = currency,
            totalMinor = total,
            pricingMode = draft.pricingMode,
            items = items,
            splitEverythingEqually = draft.pricingMode == AiPricingMode.TotalOnly ||
                (!modeChanged && current.splitEverythingEqually),
            provenance = current.provenance.withManualValues("title", "transactionDate", "currency", "totalMinor", "pricingMode"),
        )
    }

    private fun applyPeople(current: AiExtraction, draft: AiDetailsEditDraft.People): AiExtraction {
        val participants = draft.participants.map { participant ->
            participant.copy(name = participant.name.trim().replace(Regex("\\s+"), " "))
        }
        require(participants.all { it.name.isNotBlank() }) { "Every person needs a name." }
        require(participants.map { it.name.lowercase(Locale.ROOT) }.distinct().size == participants.size) {
            "Each person must have a unique name."
        }
        val keptRefs = participants.map { it.ref }.toSet()
        require(draft.payerRef == null || draft.payerRef in keptRefs) { "Choose a payer from the people included." }
        val referencesInvalidated = current.invalidateReferencesForParticipants(keptRefs)
        val self = participants.firstOrNull { it.isSelf }
        if (self != null && self.name.isNotBlank()) {
            personalName = self.name
            viewModelScope.launch { preferencesRepository.setPersonalName(self.name) }
        }
        return referencesInvalidated.copy(
            participants = participants.map { AiExtractedParticipant(it.ref, it.name, it.isSelf) },
            payerParticipantRef = draft.payerRef,
            provenance = current.provenance.withManualValues("participants", "payerParticipantRef"),
        )
    }

    private fun applyItem(current: AiExtraction, draft: AiDetailsEditDraft.Item): AiExtraction {
        val name = draft.name.trim()
        require(name.isNotBlank()) { "Enter an item name." }
        require(name.length <= 120) { "Use 120 characters or fewer for the item name." }
        val quantity = draft.quantity.trim().toIntOrNull()
        require(quantity != null && quantity in 1..999) { "Use a quantity from 1 to 999." }
        val total = if (draft.pricingMode == AiPricingMode.Itemized) parseMinor(draft.total) else null
        if (draft.pricingMode == AiPricingMode.Itemized) require(requireNotNull(total) > 0L) { "Enter a positive item total." }
        val old = current.items.firstOrNull { it.ref == draft.ref }
        val item = old?.withEditedPricing(name, quantity, total) ?: AiExtractedItem(
            ref = draft.ref,
            name = name,
            quantity = quantity,
            unitPriceMinor = total?.div(quantity.toLong()),
            totalPriceMinor = total,
        )
        val items = if (old == null) current.items + item else current.items.map { if (it.ref == item.ref) item else it }
        return current.copy(items = items, provenance = current.provenance.withManualValues("items"))
    }

    private fun applyFee(current: AiExtraction, draft: AiDetailsEditDraft.Fee): AiExtraction {
        val label = draft.label.trim()
        require(label.isNotBlank()) { "Enter a fee or discount name." }
        val amount = parseMinor(draft.amount)
        require(amount > 0L) { "Enter an amount greater than zero." }
        require(draft.participantRefs.isNotEmpty()) { "Choose at least one person for this fee or discount." }
        val shares = if (draft.allocationMode == AiFeeAllocationMode.Custom) {
            val parsed = draft.participantRefs.map { ref ->
                AiExtractedShare(ref, amountMinor = parseMinor(draft.customAmounts[ref].orEmpty()))
            }
            require(parsed.all { (it.amountMinor ?: 0L) >= 0L } && parsed.sumOf { it.amountMinor ?: 0L } == amount) {
                "Custom shares must add up to ${formatMinor(amount)}."
            }
            parsed
        } else {
            emptyList()
        }
        val fee = AiExtractedFee(
            ref = draft.ref,
            type = draft.type,
            label = label,
            amountMinor = if (draft.type == FeeType.Discount) -amount else amount,
            allocationMode = draft.allocationMode,
            participantRefs = draft.participantRefs.toList(),
            shares = shares,
        )
        val old = current.fees.firstOrNull { it.ref == draft.ref }
        val fees = if (old == null) current.fees + fee else current.fees.map { if (it.ref == fee.ref) fee else it }
        return current.copy(fees = fees, provenance = current.provenance.withManualValues("fees"))
    }

    private fun applyAssignment(current: AiExtraction, draft: AiDetailsEditDraft.Assignment): AiExtraction {
        val item = current.items.firstOrNull { it.ref == draft.itemRef } ?: error("This item no longer exists.")
        val selected = draft.shares.filter { it.selected }
        val shares = when (draft.mode) {
            AiAssignmentMode.Full -> {
                require(selected.size == 1) { "Choose exactly one person." }
                listOf(AiExtractedShare(selected.single().participantRef))
            }
            AiAssignmentMode.SharedEqual -> {
                require(selected.size >= 2) { "Choose at least two people for an equal split." }
                selected.map { AiExtractedShare(it.participantRef) }
            }
            AiAssignmentMode.ByUnits -> {
                val values = selected.map { share ->
                    val quantity = share.value.trim().toIntOrNull()
                    require(quantity != null && quantity > 0) { "Enter a positive quantity for each selected person." }
                    AiExtractedShare(share.participantRef, quantity = quantity)
                }
                require(values.sumOf { it.quantity ?: 0 } == (item.quantity ?: 1)) { "Assigned quantities must match the item quantity." }
                values
            }
            AiAssignmentMode.CustomAmount -> {
                val values = selected.map { share -> AiExtractedShare(share.participantRef, amountMinor = parseMinor(share.value)) }
                require(values.sumOf { it.amountMinor ?: 0L } == item.totalPriceMinor) { "Custom amounts must add up to the item total." }
                values
            }
            AiAssignmentMode.Percentage -> {
                val values = selected.map { share ->
                    val basisPoints = BigDecimal(share.value.trim()).movePointRight(2).intValueExact()
                    require(basisPoints > 0) { "Enter a positive percentage for each selected person." }
                    AiExtractedShare(share.participantRef, percentageBasisPoints = basisPoints)
                }
                require(values.sumOf { it.percentageBasisPoints ?: 0 } == 10_000) { "Percentages must add up to 100%." }
                values
            }
            AiAssignmentMode.Ratio -> selected.map { share ->
                val weight = share.value.trim().toIntOrNull()
                require(weight != null && weight > 0) { "Enter a positive ratio for each selected person." }
                AiExtractedShare(share.participantRef, ratioWeight = weight)
            }.also { require(it.isNotEmpty()) { "Choose at least one person." } }
        }
        val updated = item.copy(assignment = AiExtractedAssignment(draft.mode, shares))
        return current.copy(
            splitEverythingEqually = false,
            items = current.items.map { if (it.ref == updated.ref) updated else it },
            provenance = current.provenance.withManualValues("items.${item.ref}.assignment"),
        )
    }

    private fun List<AiFactProvenance>.withManualValues(vararg paths: String): List<AiFactProvenance> {
        val changed = paths.toSet()
        return filterNot { provenance -> provenance.path in changed || changed.any { prefix -> provenance.path.startsWith("$prefix.") } } +
            changed.map { path -> AiFactProvenance(path, AiFactSource.Clarified, needsReview = false) }
    }

    private fun userFacingBlockingMessage(kind: ClarificationKind, fallback: String): String = when (kind) {
        ClarificationKind.PersonalName -> "Set your name so we can resolve ‘me’ in the description."
        ClarificationKind.Payer -> "Choose who paid for this expense."
        ClarificationKind.Currency -> "Choose the currency for this expense."
        ClarificationKind.TotalOrPrices -> "Add the missing total or item prices."
        ClarificationKind.Participants -> "At least two people are required."
        ClarificationKind.AmbiguousParticipant -> "Confirm the people included in this expense."
        ClarificationKind.SplitIntent -> "Choose how the expense should be split."
    }.ifBlank { fallback }

    private fun parseMinor(value: String): Long {
        val normalized = value.trim().replace(Regex("[^0-9.\\-]"), "")
        require(normalized.isNotBlank()) { "Enter a valid amount." }
        return BigDecimal(normalized).movePointRight(2).longValueExact()
    }

    private fun formatMinor(value: Long): String {
        val absolute = kotlin.math.abs(value)
        val sign = if (value < 0) "-" else ""
        return "$sign${absolute / 100}.${(absolute % 100).toString().padStart(2, '0')}"
    }

    private fun String.isSelfReference(): Boolean = trim().lowercase(Locale.ROOT) in setOf("i", "me", "myself", "you")

    private fun AiExtractedShare?.valueFor(mode: AiAssignmentMode): String = when (mode) {
        AiAssignmentMode.Full, AiAssignmentMode.SharedEqual -> ""
        AiAssignmentMode.ByUnits -> this?.quantity?.toString().orEmpty()
        AiAssignmentMode.CustomAmount -> this?.amountMinor?.let(::formatMinor).orEmpty()
        AiAssignmentMode.Percentage -> this?.percentageBasisPoints?.let { BigDecimal(it).movePointLeft(2).stripTrailingZeros().toPlainString() }.orEmpty()
        AiAssignmentMode.Ratio -> this?.ratioWeight?.toString().orEmpty()
    }

    private data class Validation(
        val extraction: AiExtraction,
        val kind: ClarificationKind?,
        val message: String?,
        val draft: ExpenseDraft?,
        val error: String? = null,
    )

    companion object {
        fun factory(
            sessionRepository: AiExpenseSessionRepository,
            preferencesRepository: AiExpensePreferencesRepository,
            savedParticipantRepository: SavedParticipantRepository,
            draftRepository: ExpenseDraftRepository,
            prepareAiExpense: PrepareAiExpenseUseCase,
            fromReview: Boolean,
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = AiExtractedDetailsViewModel(
                sessionRepository,
                preferencesRepository,
                savedParticipantRepository,
                draftRepository,
                prepareAiExpense,
                fromReview,
            ) as T
        }
    }
}

internal fun AiExtraction.invalidateReferencesForParticipants(keptRefs: Set<String>): AiExtraction {
    val removedRefs = participants.map { it.ref }.toSet() - keptRefs
    if (removedRefs.isEmpty()) return this
    return copy(
        payerParticipantRef = payerParticipantRef?.takeIf { it in keptRefs },
        items = items.map { item ->
            if (item.assignment?.shares.orEmpty().any { it.participantRef in removedRefs }) {
                item.copy(assignment = null)
            } else {
                item
            }
        },
        fees = fees.map { fee ->
            val affected = fee.participantRefs.any { it in removedRefs } || fee.shares.any { it.participantRef in removedRefs }
            fee.copy(
                allocationMode = fee.allocationMode.takeUnless { affected && it == AiFeeAllocationMode.Custom },
                participantRefs = fee.participantRefs.filter { it in keptRefs },
                shares = fee.shares.filter { it.participantRef in keptRefs },
            )
        },
    )
}

internal fun AiExtractedItem.withEditedPricing(
    editedName: String,
    editedQuantity: Int,
    editedTotalMinor: Long?,
): AiExtractedItem {
    val pricingChanged = quantity != editedQuantity || totalPriceMinor != editedTotalMinor
    return copy(
        name = editedName,
        quantity = editedQuantity,
        unitPriceMinor = editedTotalMinor?.div(editedQuantity.toLong()),
        totalPriceMinor = editedTotalMinor,
        assignment = assignment.takeUnless { pricingChanged },
    )
}
