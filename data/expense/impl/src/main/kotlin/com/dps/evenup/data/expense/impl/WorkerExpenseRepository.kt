package com.dps.evenup.data.expense.impl

import com.dps.evenup.core.network.api.WorkerApiClient
import com.dps.evenup.core.network.api.WorkerApiResult
import com.dps.evenup.core.network.api.WorkerNetworkError
import com.dps.evenup.data.expense.api.ExpenseDraftRepository
import com.dps.evenup.data.expense.api.ExpenseDataException
import com.dps.evenup.data.expense.api.ExpenseDataFailureReason
import com.dps.evenup.data.expense.api.ExpenseRepository
import com.dps.evenup.data.sharing.api.SavedShareLink
import com.dps.evenup.data.sharing.api.ShareLinkResponseMapper
import com.dps.evenup.domain.expense.api.ExpenseSummary
import com.dps.evenup.domain.expense.api.FeeAllocation
import com.dps.evenup.domain.expense.api.FeeParticipantShare
import com.dps.evenup.domain.expense.api.FinalizedExpensePayload
import com.dps.evenup.domain.expense.api.ItemAssignment
import com.dps.evenup.domain.expense.api.ItemParticipantShare
import com.dps.evenup.domain.expense.api.ParticipantExpenseSummary
import com.dps.evenup.domain.expense.api.SettlementRow
import com.dps.evenup.domain.participant.api.Participant
import com.dps.evenup.domain.receipt.api.Receipt
import com.dps.evenup.domain.receipt.api.ReceiptFee
import com.dps.evenup.domain.receipt.api.ReceiptItem
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

class WorkerExpenseRepository(
    private val workerApiClient: WorkerApiClient,
    private val shareLinkResponseMapper: ShareLinkResponseMapper,
    private val draftRepository: ExpenseDraftRepository? = null,
    private val json: Json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        encodeDefaults = true
    },
) : ExpenseRepository {
    override suspend fun saveFinalizedExpense(payload: FinalizedExpensePayload): SavedShareLink {
        val requestBody = json.encodeToString(payload.toDto())
        val response = workerApiClient.postJson("/v1/expenses", requestBody)
        return when (response) {
            is WorkerApiResult.Failure -> throw ExpenseDataException(
                message = "Expense save request failed.",
                reason = response.error.toExpenseFailureReason(),
            )
            is WorkerApiResult.Success -> mapSaveResponse(response.response.body).also {
                draftRepository?.clearDraft()
            }
        }
    }

    private fun mapSaveResponse(body: String): SavedShareLink {
        val dto = try {
            json.decodeFromString<SaveExpenseResponseDto>(body)
        } catch (error: SerializationException) {
            throw ExpenseDataException("Expense save response was invalid.", cause = error)
        }

        return try {
            shareLinkResponseMapper.map(
                expenseId = dto.expenseId,
                shareId = dto.shareId,
                shareUrl = dto.shareUrl,
            )
        } catch (error: IllegalArgumentException) {
            throw ExpenseDataException("Expense save response contained invalid values.", cause = error)
        }
    }
}

private fun WorkerNetworkError.toExpenseFailureReason(): ExpenseDataFailureReason = when (this) {
    WorkerNetworkError.ConnectionFailed,
    WorkerNetworkError.InvalidBaseUrl,
    WorkerNetworkError.InvalidPath,
    -> ExpenseDataFailureReason.Connection
    WorkerNetworkError.Timeout -> ExpenseDataFailureReason.Timeout
    is WorkerNetworkError.HttpFailure -> ExpenseDataFailureReason.Rejected
    WorkerNetworkError.Unknown -> ExpenseDataFailureReason.Unknown
}

private fun FinalizedExpensePayload.toDto(): SaveExpenseRequestDto = SaveExpenseRequestDto(
    title = receipt.merchantName,
    receipt = receipt.toDto(),
    participants = participants.map { participant -> participant.toDto() },
    payerParticipantId = payerId.value,
    itemAssignments = itemAssignments.map { assignment -> assignment.toDto() },
    feeAllocations = feeAllocations.map { allocation -> allocation.toDto() },
    summary = summary.toDto(),
)

private fun Receipt.toDto(): ReceiptDto = ReceiptDto(
    merchantName = merchantName,
    transactionDate = transactionDateLabel,
    currency = currencyCode.value,
    items = items.map { item -> item.toDto() },
    fees = fees.map { fee -> fee.toDto() },
    subtotalMinor = subtotal?.value,
    totalMinor = total.value,
)

private fun ReceiptItem.toDto(): ReceiptItemDto = ReceiptItemDto(
    id = id.value,
    name = name,
    quantity = quantity.value,
    unitPriceMinor = unitPrice.value,
    totalPriceMinor = totalPrice.value,
)

private fun ReceiptFee.toDto(): ReceiptFeeDto = ReceiptFeeDto(
    id = id.value,
    type = type.name,
    label = label,
    amountMinor = amount.value,
)

private fun Participant.toDto(): ParticipantDto = ParticipantDto(
    id = id.value,
    name = name,
    creationOrder = creationOrder,
    isSavedLocalName = isSavedLocalName,
)

private fun ItemAssignment.toDto(): ItemAssignmentDto = ItemAssignmentDto(
    receiptItemId = receiptItemId.value,
    mode = mode.name,
    shares = shares.map { share -> share.toDto() },
)

private fun ItemParticipantShare.toDto(): ItemParticipantShareDto = ItemParticipantShareDto(
    participantId = participantId.value,
    amountMinor = amount.value,
    quantity = quantity?.value,
    percentageBasisPoints = percentage?.value,
)

private fun FeeAllocation.toDto(): FeeAllocationDto = FeeAllocationDto(
    feeId = feeId.value,
    mode = mode.name,
    shares = shares.map { share -> share.toDto() },
)

private fun FeeParticipantShare.toDto(): FeeParticipantShareDto = FeeParticipantShareDto(
    participantId = participantId.value,
    amountMinor = amount.value,
)

private fun ExpenseSummary.toDto(): ExpenseSummaryDto = ExpenseSummaryDto(
    receiptTotalMinor = receiptTotal.value,
    participantSummaries = participantSummaries.map { summary -> summary.toDto() },
    settlementRows = settlementRows.map { row -> row.toDto() },
)

private fun ParticipantExpenseSummary.toDto(): ParticipantExpenseSummaryDto = ParticipantExpenseSummaryDto(
    participantId = participantId.value,
    assignedItemTotalMinor = assignedItemTotal.value,
    allocatedFeeTotalMinor = allocatedFeeTotal.value,
    shareMinor = personShare.value,
    paidMinor = amountPaid.value,
    netBalanceMinor = netBalance.value,
)

private fun SettlementRow.toDto(): SettlementRowDto = SettlementRowDto(
    fromParticipantId = fromParticipantId.value,
    toParticipantId = toParticipantId.value,
    amountMinor = amount.value,
)

@Serializable
private data class SaveExpenseRequestDto(
    val schemaVersion: Int = 1,
    val title: String,
    val receipt: ReceiptDto,
    val participants: List<ParticipantDto>,
    val payerParticipantId: String,
    val itemAssignments: List<ItemAssignmentDto>,
    val feeAllocations: List<FeeAllocationDto>,
    val summary: ExpenseSummaryDto,
)

@Serializable
private data class ReceiptDto(
    val merchantName: String,
    val transactionDate: String?,
    val currency: String,
    val items: List<ReceiptItemDto>,
    val fees: List<ReceiptFeeDto>,
    val subtotalMinor: Long?,
    val totalMinor: Long,
)

@Serializable
private data class ReceiptItemDto(
    val id: String,
    val name: String,
    val quantity: Int,
    val unitPriceMinor: Long,
    val totalPriceMinor: Long,
)

@Serializable
private data class ReceiptFeeDto(
    val id: String,
    val type: String,
    val label: String,
    val amountMinor: Long,
)

@Serializable
private data class ParticipantDto(
    val id: String,
    val name: String,
    val creationOrder: Int,
    val isSavedLocalName: Boolean,
)

@Serializable
private data class ItemAssignmentDto(
    val receiptItemId: String,
    val mode: String,
    val shares: List<ItemParticipantShareDto>,
)

@Serializable
private data class ItemParticipantShareDto(
    val participantId: String,
    val amountMinor: Long,
    val quantity: Int?,
    val percentageBasisPoints: Int?,
)

@Serializable
private data class FeeAllocationDto(
    val feeId: String,
    val mode: String,
    val shares: List<FeeParticipantShareDto>,
)

@Serializable
private data class FeeParticipantShareDto(
    val participantId: String,
    val amountMinor: Long,
)

@Serializable
private data class ExpenseSummaryDto(
    val receiptTotalMinor: Long,
    val participantSummaries: List<ParticipantExpenseSummaryDto>,
    val settlementRows: List<SettlementRowDto>,
)

@Serializable
private data class ParticipantExpenseSummaryDto(
    val participantId: String,
    val assignedItemTotalMinor: Long,
    val allocatedFeeTotalMinor: Long,
    val shareMinor: Long,
    val paidMinor: Long,
    val netBalanceMinor: Long,
)

@Serializable
private data class SettlementRowDto(
    val fromParticipantId: String,
    val toParticipantId: String,
    val amountMinor: Long,
)

@Serializable
private data class SaveExpenseResponseDto(
    val expenseId: String,
    val shareId: String,
    val shareUrl: String,
)
