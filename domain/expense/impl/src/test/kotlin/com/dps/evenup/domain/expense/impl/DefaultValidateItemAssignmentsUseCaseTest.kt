package com.dps.evenup.domain.expense.impl

import com.dps.evenup.domain.expense.api.ItemAssignment
import com.dps.evenup.domain.expense.api.ItemAssignmentMode
import com.dps.evenup.domain.expense.api.ItemAssignmentValidationError
import com.dps.evenup.domain.expense.api.ItemParticipantShare
import com.dps.evenup.domain.expense.api.PercentageBasisPoints
import com.dps.evenup.domain.participant.api.Participant
import com.dps.evenup.domain.participant.api.ParticipantId
import com.dps.evenup.domain.receipt.api.MoneyMinor
import com.dps.evenup.domain.receipt.api.Quantity
import com.dps.evenup.domain.receipt.api.ReceiptItem
import com.dps.evenup.domain.receipt.api.ReceiptItemId
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DefaultValidateItemAssignmentsUseCaseTest {
    private val useCase = DefaultValidateItemAssignmentsUseCase()

    @Test
    fun `valid shared assignment passes`() {
        val result = useCase.validate(
            receiptItems = listOf(item()),
            participants = participants(),
            assignments = listOf(
                ItemAssignment(
                    receiptItemId = ReceiptItemId("item-1"),
                    mode = ItemAssignmentMode.SharedEqual,
                    shares = listOf(
                        share("p1", 500),
                        share("p2", 500),
                    ),
                ),
            ),
        )

        assertTrue(result.isValid)
    }

    @Test
    fun `missing item assignment is invalid`() {
        val result = useCase.validate(listOf(item()), participants(), emptyList())

        assertFalse(result.isValid)
        assertTrue(ItemAssignmentValidationError.MissingItemAssignment in result.errors)
    }

    @Test
    fun `quantity assignment must sum to item quantity`() {
        val result = useCase.validate(
            receiptItems = listOf(item(quantity = 2)),
            participants = participants(),
            assignments = listOf(
                ItemAssignment(
                    receiptItemId = ReceiptItemId("item-1"),
                    mode = ItemAssignmentMode.ByUnits,
                    shares = listOf(share("p1", 1_000, quantity = 1)),
                ),
            ),
        )

        assertTrue(ItemAssignmentValidationError.QuantityTotalMismatch in result.errors)
    }

    @Test
    fun `percentage assignment must sum to one hundred percent`() {
        val result = useCase.validate(
            receiptItems = listOf(item()),
            participants = participants(),
            assignments = listOf(
                ItemAssignment(
                    receiptItemId = ReceiptItemId("item-1"),
                    mode = ItemAssignmentMode.Percentage,
                    shares = listOf(
                        share("p1", 500, percentage = 2_500),
                        share("p2", 500, percentage = 2_500),
                    ),
                ),
            ),
        )

        assertTrue(ItemAssignmentValidationError.PercentageTotalMismatch in result.errors)
    }

    @Test
    fun `unknown participant is invalid`() {
        val result = useCase.validate(
            receiptItems = listOf(item()),
            participants = participants(),
            assignments = listOf(
                ItemAssignment(
                    receiptItemId = ReceiptItemId("item-1"),
                    mode = ItemAssignmentMode.Full,
                    shares = listOf(share("missing", 1_000)),
                ),
            ),
        )

        assertTrue(ItemAssignmentValidationError.UnknownParticipant in result.errors)
    }

    private fun item(quantity: Int = 1): ReceiptItem = ReceiptItem(
        id = ReceiptItemId("item-1"),
        name = "Pasta",
        quantity = Quantity(quantity),
        unitPrice = MoneyMinor(1_000),
        totalPrice = MoneyMinor(1_000),
    )

    private fun participants(): List<Participant> = listOf(
        Participant(ParticipantId("p1"), "Anna", 0),
        Participant(ParticipantId("p2"), "Ben", 1),
    )

    private fun share(
        participantId: String,
        amount: Long,
        quantity: Int? = null,
        percentage: Int? = null,
    ): ItemParticipantShare = ItemParticipantShare(
        participantId = ParticipantId(participantId),
        amount = MoneyMinor(amount),
        quantity = quantity?.let(::Quantity),
        percentage = percentage?.let(::PercentageBasisPoints),
    )
}
