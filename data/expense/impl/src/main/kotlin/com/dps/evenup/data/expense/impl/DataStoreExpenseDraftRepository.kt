package com.dps.evenup.data.expense.impl

import com.dps.evenup.core.datastore.api.StringDataStore
import com.dps.evenup.data.expense.api.ExpenseDataException
import com.dps.evenup.data.expense.api.ExpenseDraftRepository
import com.dps.evenup.domain.expense.api.ExpenseDraft
import com.dps.evenup.domain.expense.api.ExpenseDraftId
import com.dps.evenup.domain.expense.api.FeeAllocation
import com.dps.evenup.domain.expense.api.FeeAllocationMode
import com.dps.evenup.domain.expense.api.FeeParticipantShare
import com.dps.evenup.domain.expense.api.ItemAssignment
import com.dps.evenup.domain.expense.api.ItemAssignmentMode
import com.dps.evenup.domain.expense.api.ItemParticipantShare
import com.dps.evenup.domain.expense.api.PercentageBasisPoints
import com.dps.evenup.domain.participant.api.Participant
import com.dps.evenup.domain.participant.api.ParticipantId
import com.dps.evenup.domain.receipt.api.CurrencyCode
import com.dps.evenup.domain.receipt.api.FeeId
import com.dps.evenup.domain.receipt.api.FeeType
import com.dps.evenup.domain.receipt.api.MoneyMinor
import com.dps.evenup.domain.receipt.api.Quantity
import com.dps.evenup.domain.receipt.api.Receipt
import com.dps.evenup.domain.receipt.api.ReceiptFee
import com.dps.evenup.domain.receipt.api.ReceiptItem
import com.dps.evenup.domain.receipt.api.ReceiptItemId
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

class DataStoreExpenseDraftRepository(
    private val stringDataStore: StringDataStore,
    private val json: Json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        encodeDefaults = true
    },
) : ExpenseDraftRepository {
    override suspend fun getDraft(): ExpenseDraft? {
        val storedValue = stringDataStore.read(DRAFT_KEY) ?: return null
        val dto = try {
            json.decodeFromString<ExpenseDraftDto>(storedValue)
        } catch (error: SerializationException) {
            throw ExpenseDataException("Stored expense draft was invalid.", cause = error)
        }

        return try {
            dto.toDomain()
        } catch (error: IllegalArgumentException) {
            throw ExpenseDataException("Stored expense draft contained invalid values.", cause = error)
        }
    }

    override suspend fun saveDraft(draft: ExpenseDraft) {
        stringDataStore.write(DRAFT_KEY, json.encodeToString(draft.toDto()))
    }

    override suspend fun clearDraft() {
        stringDataStore.remove(DRAFT_KEY)
    }

    private companion object {
        const val DRAFT_KEY = "expense_draft_json"
    }
}

private fun ExpenseDraft.toDto(): ExpenseDraftDto = ExpenseDraftDto(
    id = id.value,
    receipt = receipt.toDraftDto(),
    participants = participants.map { participant -> participant.toDraftDto() },
    payerId = payerId.value,
    itemAssignments = itemAssignments.map { assignment -> assignment.toDraftDto() },
    feeAllocations = feeAllocations.map { allocation -> allocation.toDraftDto() },
)

private fun ExpenseDraftDto.toDomain(): ExpenseDraft = ExpenseDraft(
    id = ExpenseDraftId(id),
    receipt = receipt.toDomain(),
    participants = participants.map { participant -> participant.toDomain() },
    payerId = ParticipantId(payerId),
    itemAssignments = itemAssignments.map { assignment -> assignment.toDomain() },
    feeAllocations = feeAllocations.map { allocation -> allocation.toDomain() },
)

private fun Receipt.toDraftDto(): DraftReceiptDto = DraftReceiptDto(
    merchantName = merchantName,
    currency = currencyCode.value,
    transactionDateLabel = transactionDateLabel,
    items = items.map { item -> item.toDraftDto() },
    fees = fees.map { fee -> fee.toDraftDto() },
    totalMinor = total.value,
)

private fun DraftReceiptDto.toDomain(): Receipt = Receipt(
    merchantName = merchantName,
    currencyCode = CurrencyCode(currency),
    transactionDateLabel = transactionDateLabel,
    items = items.map { item -> item.toDomain() },
    fees = fees.map { fee -> fee.toDomain() },
    total = MoneyMinor(totalMinor),
)

private fun ReceiptItem.toDraftDto(): DraftReceiptItemDto = DraftReceiptItemDto(
    id = id.value,
    name = name,
    quantity = quantity.value,
    unitPriceMinor = unitPrice.value,
    totalPriceMinor = totalPrice.value,
)

private fun DraftReceiptItemDto.toDomain(): ReceiptItem = ReceiptItem(
    id = ReceiptItemId(id),
    name = name,
    quantity = Quantity(quantity),
    unitPrice = MoneyMinor(unitPriceMinor),
    totalPrice = MoneyMinor(totalPriceMinor),
)

private fun ReceiptFee.toDraftDto(): DraftReceiptFeeDto = DraftReceiptFeeDto(
    id = id.value,
    type = type.name,
    label = label,
    amountMinor = amount.value,
)

private fun DraftReceiptFeeDto.toDomain(): ReceiptFee = ReceiptFee(
    id = FeeId(id),
    type = FeeType.valueOf(type),
    label = label,
    amount = MoneyMinor(amountMinor),
)

private fun Participant.toDraftDto(): DraftParticipantDto = DraftParticipantDto(
    id = id.value,
    name = name,
    creationOrder = creationOrder,
    isSavedLocalName = isSavedLocalName,
)

private fun DraftParticipantDto.toDomain(): Participant = Participant(
    id = ParticipantId(id),
    name = name,
    creationOrder = creationOrder,
    isSavedLocalName = isSavedLocalName,
)

private fun ItemAssignment.toDraftDto(): DraftItemAssignmentDto = DraftItemAssignmentDto(
    receiptItemId = receiptItemId.value,
    mode = mode.name,
    shares = shares.map { share -> share.toDraftDto() },
)

private fun DraftItemAssignmentDto.toDomain(): ItemAssignment = ItemAssignment(
    receiptItemId = ReceiptItemId(receiptItemId),
    mode = ItemAssignmentMode.valueOf(mode),
    shares = shares.map { share -> share.toDomain() },
)

private fun ItemParticipantShare.toDraftDto(): DraftItemParticipantShareDto = DraftItemParticipantShareDto(
    participantId = participantId.value,
    amountMinor = amount.value,
    quantity = quantity?.value,
    percentageBasisPoints = percentage?.value,
)

private fun DraftItemParticipantShareDto.toDomain(): ItemParticipantShare = ItemParticipantShare(
    participantId = ParticipantId(participantId),
    amount = MoneyMinor(amountMinor),
    quantity = quantity?.let(::Quantity),
    percentage = percentageBasisPoints?.let(::PercentageBasisPoints),
)

private fun FeeAllocation.toDraftDto(): DraftFeeAllocationDto = DraftFeeAllocationDto(
    feeId = feeId.value,
    mode = mode.name,
    shares = shares.map { share -> share.toDraftDto() },
)

private fun DraftFeeAllocationDto.toDomain(): FeeAllocation = FeeAllocation(
    feeId = FeeId(feeId),
    mode = FeeAllocationMode.valueOf(mode),
    shares = shares.map { share -> share.toDomain() },
)

private fun FeeParticipantShare.toDraftDto(): DraftFeeParticipantShareDto = DraftFeeParticipantShareDto(
    participantId = participantId.value,
    amountMinor = amount.value,
)

private fun DraftFeeParticipantShareDto.toDomain(): FeeParticipantShare = FeeParticipantShare(
    participantId = ParticipantId(participantId),
    amount = MoneyMinor(amountMinor),
)

@Serializable
private data class ExpenseDraftDto(
    val id: String,
    val receipt: DraftReceiptDto,
    val participants: List<DraftParticipantDto>,
    val payerId: String,
    val itemAssignments: List<DraftItemAssignmentDto>,
    val feeAllocations: List<DraftFeeAllocationDto>,
)

@Serializable
private data class DraftReceiptDto(
    val merchantName: String,
    val currency: String,
    val transactionDateLabel: String?,
    val items: List<DraftReceiptItemDto>,
    val fees: List<DraftReceiptFeeDto>,
    val totalMinor: Long,
)

@Serializable
private data class DraftReceiptItemDto(
    val id: String,
    val name: String,
    val quantity: Int,
    val unitPriceMinor: Long,
    val totalPriceMinor: Long,
)

@Serializable
private data class DraftReceiptFeeDto(
    val id: String,
    val type: String,
    val label: String,
    val amountMinor: Long,
)

@Serializable
private data class DraftParticipantDto(
    val id: String,
    val name: String,
    val creationOrder: Int,
    val isSavedLocalName: Boolean,
)

@Serializable
private data class DraftItemAssignmentDto(
    val receiptItemId: String,
    val mode: String,
    val shares: List<DraftItemParticipantShareDto>,
)

@Serializable
private data class DraftItemParticipantShareDto(
    val participantId: String,
    val amountMinor: Long,
    val quantity: Int?,
    val percentageBasisPoints: Int?,
)

@Serializable
private data class DraftFeeAllocationDto(
    val feeId: String,
    val mode: String,
    val shares: List<DraftFeeParticipantShareDto>,
)

@Serializable
private data class DraftFeeParticipantShareDto(
    val participantId: String,
    val amountMinor: Long,
)
