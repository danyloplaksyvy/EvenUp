package com.dps.evenup.domain.expense.impl

import com.dps.evenup.domain.expense.api.ItemAssignment
import com.dps.evenup.domain.expense.api.ItemAssignmentMode
import com.dps.evenup.domain.expense.api.ItemAssignmentValidationError
import com.dps.evenup.domain.expense.api.ItemAssignmentValidationResult
import com.dps.evenup.domain.expense.api.PercentageBasisPoints
import com.dps.evenup.domain.expense.api.ValidateItemAssignmentsUseCase
import com.dps.evenup.domain.participant.api.Participant
import com.dps.evenup.domain.receipt.api.ReceiptItem

class DefaultValidateItemAssignmentsUseCase : ValidateItemAssignmentsUseCase {
    override fun validate(
        receiptItems: List<ReceiptItem>,
        participants: List<Participant>,
        assignments: List<ItemAssignment>,
    ): ItemAssignmentValidationResult {
        val receiptItemsById = receiptItems.associateBy { it.id }
        val participantIds = participants.map { it.id }.toSet()
        val assignedItemIds = assignments.map { it.receiptItemId }.toSet()

        val errors = buildSet {
            receiptItems.forEach { item ->
                if (item.id !in assignedItemIds) add(ItemAssignmentValidationError.MissingItemAssignment)
            }

            assignments.forEach { assignment ->
                val item = receiptItemsById[assignment.receiptItemId]
                if (item == null) {
                    add(ItemAssignmentValidationError.UnknownReceiptItem)
                    return@forEach
                }
                if (assignment.shares.isEmpty()) add(ItemAssignmentValidationError.EmptyShares)
                if (assignment.shares.any { it.participantId !in participantIds }) {
                    add(ItemAssignmentValidationError.UnknownParticipant)
                }
                if (assignment.shares.any { it.amount.value < 0 }) {
                    add(ItemAssignmentValidationError.NegativeShareAmount)
                }
                if (assignment.shares.sumOf { it.amount.value } != item.totalPrice.value) {
                    add(ItemAssignmentValidationError.AmountTotalMismatch)
                }

                when (assignment.mode) {
                    ItemAssignmentMode.Full -> {
                        if (assignment.shares.size != 1) add(ItemAssignmentValidationError.InvalidModeShape)
                    }
                    ItemAssignmentMode.ByUnits -> {
                        if (assignment.shares.any { it.quantity == null }) {
                            add(ItemAssignmentValidationError.InvalidModeShape)
                        }
                        val quantityTotal = assignment.shares.sumOf { it.quantity?.value ?: 0 }
                        if (quantityTotal != item.quantity.value) {
                            add(ItemAssignmentValidationError.QuantityTotalMismatch)
                        }
                    }
                    ItemAssignmentMode.SharedEqual -> {
                        if (assignment.shares.size < MIN_SHARED_PARTICIPANTS) {
                            add(ItemAssignmentValidationError.InvalidModeShape)
                        }
                    }
                    ItemAssignmentMode.CustomAmount -> Unit
                    ItemAssignmentMode.Percentage -> {
                        if (assignment.shares.any { it.percentage == null }) {
                            add(ItemAssignmentValidationError.InvalidModeShape)
                        }
                        val percentageTotal = assignment.shares.sumOf { it.percentage?.value ?: 0 }
                        if (percentageTotal != PercentageBasisPoints.MAX_BASIS_POINTS) {
                            add(ItemAssignmentValidationError.PercentageTotalMismatch)
                        }
                    }
                }
            }
        }

        return if (errors.isEmpty()) ItemAssignmentValidationResult.Valid else ItemAssignmentValidationResult(errors)
    }

    private companion object {
        const val MIN_SHARED_PARTICIPANTS = 2
    }
}
