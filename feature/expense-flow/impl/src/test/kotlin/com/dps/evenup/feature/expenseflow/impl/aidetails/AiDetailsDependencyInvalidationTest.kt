package com.dps.evenup.feature.expenseflow.impl.aidetails

import com.dps.evenup.domain.expenseinput.api.AiAssignmentMode
import com.dps.evenup.domain.expenseinput.api.AiExtractedAssignment
import com.dps.evenup.domain.expenseinput.api.AiExtractedFee
import com.dps.evenup.domain.expenseinput.api.AiExtractedItem
import com.dps.evenup.domain.expenseinput.api.AiExtractedParticipant
import com.dps.evenup.domain.expenseinput.api.AiExtractedShare
import com.dps.evenup.domain.expenseinput.api.AiExtraction
import com.dps.evenup.domain.expenseinput.api.AiFeeAllocationMode
import com.dps.evenup.domain.receipt.api.FeeType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AiDetailsDependencyInvalidationTest {
    @Test
    fun `removing referenced participant clears payer item assignment and custom fee allocation`() {
        val extraction = AiExtraction(
            participants = listOf(
                AiExtractedParticipant("dana", "Dana"),
                AiExtractedParticipant("lee", "Lee"),
            ),
            payerParticipantRef = "lee",
            items = listOf(
                AiExtractedItem(
                    ref = "pizza",
                    name = "Pizza",
                    quantity = 1,
                    totalPriceMinor = 1_000,
                    assignment = AiExtractedAssignment(
                        AiAssignmentMode.SharedEqual,
                        listOf(AiExtractedShare("dana"), AiExtractedShare("lee")),
                    ),
                ),
            ),
            fees = listOf(
                AiExtractedFee(
                    ref = "tip",
                    type = FeeType.Tip,
                    label = "Tip",
                    amountMinor = 200,
                    allocationMode = AiFeeAllocationMode.Custom,
                    participantRefs = listOf("dana", "lee"),
                    shares = listOf(
                        AiExtractedShare("dana", amountMinor = 100),
                        AiExtractedShare("lee", amountMinor = 100),
                    ),
                ),
            ),
        )

        val result = extraction.invalidateReferencesForParticipants(setOf("dana"))

        assertNull(result.payerParticipantRef)
        assertNull(result.items.single().assignment)
        assertNull(result.fees.single().allocationMode)
        assertEquals(listOf("dana"), result.fees.single().participantRefs)
        assertEquals(listOf("dana"), result.fees.single().shares.map { it.participantRef })
    }

    @Test
    fun `changing item price invalidates assignment while renaming alone preserves it`() {
        val assignment = AiExtractedAssignment(
            AiAssignmentMode.Full,
            listOf(AiExtractedShare("dana")),
        )
        val item = AiExtractedItem(
            ref = "pizza",
            name = "Pizza",
            quantity = 1,
            unitPriceMinor = 1_000,
            totalPriceMinor = 1_000,
            assignment = assignment,
        )

        val renamed = item.withEditedPricing("Large pizza", 1, 1_000)
        val repriced = item.withEditedPricing("Pizza", 1, 1_200)

        assertTrue(renamed.assignment === assignment)
        assertNull(repriced.assignment)
        assertEquals(1_200L, repriced.totalPriceMinor)
    }
}
