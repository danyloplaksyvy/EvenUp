package com.dps.evenup.domain.expenseinput.impl

import com.dps.evenup.domain.expense.api.ExpenseDraftId
import com.dps.evenup.domain.expenseinput.api.AiExtractedFee
import com.dps.evenup.domain.expenseinput.api.AiExtractedAssignment
import com.dps.evenup.domain.expenseinput.api.AiExtractedShare
import com.dps.evenup.domain.expenseinput.api.AiAssignmentMode
import com.dps.evenup.domain.expenseinput.api.AiExtractedItem
import com.dps.evenup.domain.expenseinput.api.AiExtractedParticipant
import com.dps.evenup.domain.expenseinput.api.AiExtraction
import com.dps.evenup.domain.expenseinput.api.AiFactSource
import com.dps.evenup.domain.expenseinput.api.AiFeeAllocationMode
import com.dps.evenup.domain.expenseinput.api.AiPricingMode
import com.dps.evenup.domain.expenseinput.api.ClarificationKind
import com.dps.evenup.domain.expenseinput.api.PrepareAiExpenseCommand
import com.dps.evenup.domain.expenseinput.api.PrepareAiExpenseResult
import com.dps.evenup.domain.receipt.api.ExpensePricingMode
import com.dps.evenup.domain.receipt.api.FeeType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DefaultPrepareAiExpenseUseCaseTest {
    private val useCase = DefaultPrepareAiExpenseUseCase()

    @Test
    fun `clarifications follow identity payer currency total participant and split order`() {
        val selfOnly = AiExtraction(participants = listOf(participant("self", "me", isSelf = true)))
        assertNeeds(ClarificationKind.PersonalName, prepare(selfOnly))

        val noPayer = selfOnly.copy(totalMinor = 1000)
        assertNeeds(ClarificationKind.Payer, prepare(noPayer, personalName = "Dana"))

        val noCurrency = noPayer.copy(payerParticipantRef = "self")
        assertNeeds(ClarificationKind.Currency, prepare(noCurrency, personalName = "Dana", defaultCurrency = null))

        val noTotal = noCurrency.copy(totalMinor = null)
        assertNeeds(
            ClarificationKind.TotalOrPrices,
            prepare(noTotal, personalName = "Dana", defaultCurrency = "USD"),
        )

        val oneParticipant = noCurrency.copy(totalMinor = 1000, pricingMode = AiPricingMode.TotalOnly)
        assertNeeds(
            ClarificationKind.Participants,
            prepare(oneParticipant, personalName = "Dana", defaultCurrency = "USD"),
        )

        val noSplit = oneParticipant.copy(participants = participants(), payerParticipantRef = "dana")
        assertNeeds(
            ClarificationKind.SplitIntent,
            prepare(noSplit, personalName = "Dana", defaultCurrency = "USD"),
        )
    }

    @Test
    fun `missing payer uses required product copy`() {
        val result = prepare(completeTotalOnly().copy(payerParticipantRef = null)) as PrepareAiExpenseResult.NeedsClarification

        assertEquals(ClarificationKind.Payer, result.kind)
        assertEquals("Who paid for this expense?", result.question)
    }

    @Test
    fun `itemized expenses ask for missing item prices before split intent`() {
        val extraction = completeTotalOnly().copy(
            pricingMode = AiPricingMode.Itemized,
            items = listOf(AiExtractedItem("pizza", "Pizza")),
            splitEverythingEqually = false,
        )

        assertNeeds(ClarificationKind.TotalOrPrices, prepare(extraction))
    }

    @Test
    fun `total-only equal split uses payer-first deterministic remainder and allocates fees and discounts`() {
        val result = prepare(
            completeTotalOnly().copy(
                totalMinor = 1002,
                payerParticipantRef = "lee",
                fees = listOf(
                    AiExtractedFee("tip", FeeType.Tip, "Tip", 101, AiFeeAllocationMode.Equal),
                    AiExtractedFee("discount", FeeType.Discount, "Coupon", -100, AiFeeAllocationMode.Equal),
                ),
                items = listOf(AiExtractedItem("coffee", "Coffee and pastries")),
            ),
            description = "Coffee with Dana and Lee",
        ) as PrepareAiExpenseResult.Ready

        assertEquals(ExpensePricingMode.TotalOnly, result.draft.receipt.pricingMode)
        assertEquals("Coffee", result.draft.receipt.merchantName)
        assertEquals("2026-07-16", result.draft.receipt.transactionDateLabel)
        assertEquals(listOf(501L, 500L), result.draft.baseAllocation!!.shares.map { it.amount.value })
        assertEquals(listOf("lee", "dana"), result.draft.baseAllocation!!.shares.map { it.participantId.value })
        assertEquals(2, result.draft.feeAllocations.size)
        assertEquals(101L, result.draft.feeAllocations[0].shares.sumOf { it.amount.value })
        assertEquals(-100L, result.draft.feeAllocations[1].shares.sumOf { it.amount.value })
        assertEquals("Coffee and pastries", result.draft.receipt.descriptiveItems.single().name)
        assertTrue(result.extraction.provenance.any { it.path == "title" && it.source == AiFactSource.Derived })
        assertTrue(result.extraction.provenance.any { it.path == "transactionDate" && it.source == AiFactSource.Defaulted })
        assertFalse(result.extraction.provenance.first { it.path == "title" }.needsReview)
        assertFalse(result.extraction.provenance.first { it.path == "transactionDate" }.needsReview)
    }

    @Test
    fun `unclear occasion defaults title without warning`() {
        val result = prepare(completeTotalOnly(), description = "Dana paid Lee 10 dollars") as PrepareAiExpenseResult.Ready

        assertEquals("Shared expense", result.draft.receipt.merchantName)
        assertTrue(result.extraction.provenance.any { it.path == "title" && it.source == AiFactSource.Defaulted })
        assertFalse(result.extraction.provenance.first { it.path == "title" }.needsReview)
    }

    @Test
    fun `saved currency default is recorded without warning`() {
        val result = prepare(completeTotalOnly().copy(currency = null), defaultCurrency = "EUR") as PrepareAiExpenseResult.Ready

        assertEquals("EUR", result.draft.receipt.currencyCode.value)
        val provenance = result.extraction.provenance.first { it.path == "currency" }
        assertEquals(AiFactSource.Defaulted, provenance.source)
        assertFalse(provenance.needsReview)
    }

    @Test
    fun `possible saved participant match requires confirmation but exact match is reused`() {
        val possible = prepare(
            completeTotalOnly().copy(
                participants = listOf(participant("john", "John"), participant("lee", "Lee")),
                payerParticipantRef = "john",
            ),
            savedNames = listOf("Johnny", "Lee"),
        )
        assertNeeds(ClarificationKind.AmbiguousParticipant, possible)

        val exact = prepare(completeTotalOnly(), savedNames = listOf("  DANA ", "Lee")) as PrepareAiExpenseResult.Ready
        assertEquals("DANA", exact.draft.participants.first().name)
        assertTrue(exact.draft.participants.first().isSavedLocalName)
    }

    @Test
    fun `negative total-only base is rejected`() {
        val extraction = completeTotalOnly().copy(
            totalMinor = 100,
            fees = listOf(AiExtractedFee("tip", FeeType.Tip, "Tip", 200, AiFeeAllocationMode.Equal)),
        )

        val result = prepare(extraction)
        assertTrue(result is PrepareAiExpenseResult.Invalid)
    }

    @Test
    fun `itemized ratio split converts to deterministic basis points and payer-first remainder`() {
        val extraction = completeTotalOnly().copy(
            pricingMode = AiPricingMode.Itemized,
            splitEverythingEqually = false,
            payerParticipantRef = "lee",
            items = listOf(
                AiExtractedItem(
                    ref = "pizza",
                    name = "Pizza",
                    quantity = 1,
                    unitPriceMinor = 1000,
                    totalPriceMinor = 1000,
                    assignment = AiExtractedAssignment(
                        AiAssignmentMode.Ratio,
                        listOf(
                            AiExtractedShare("dana", ratioWeight = 1),
                            AiExtractedShare("lee", ratioWeight = 2),
                        ),
                    ),
                ),
            ),
        )

        val result = prepare(extraction) as PrepareAiExpenseResult.Ready

        val shares = result.draft.itemAssignments.single().shares
        assertEquals(listOf("lee", "dana"), shares.map { it.participantId.value })
        assertEquals(listOf(667L, 333L), shares.map { it.amount.value })
        assertEquals(listOf(6667, 3333), shares.map { it.percentage?.value })
    }

    @Test
    fun `total-only mode rejects priced descriptive items`() {
        val result = prepare(
            completeTotalOnly().copy(
                items = listOf(AiExtractedItem("coffee", "Coffee", totalPriceMinor = 1000)),
            ),
        )

        assertTrue(result is PrepareAiExpenseResult.Invalid)
    }

    private fun prepare(
        extraction: AiExtraction,
        personalName: String? = null,
        defaultCurrency: String? = "USD",
        description: String = "Dinner with Dana and Lee",
        savedNames: List<String> = emptyList(),
    ): PrepareAiExpenseResult = useCase.prepare(
        PrepareAiExpenseCommand(
            draftId = ExpenseDraftId("draft-ai"),
            extraction = extraction,
            originalDescription = description,
            personalName = personalName,
            defaultCurrency = defaultCurrency,
            savedParticipantNames = savedNames,
            todayIsoDate = "2026-07-16",
        ),
    )

    private fun completeTotalOnly(): AiExtraction = AiExtraction(
        currency = "USD",
        totalMinor = 1000,
        pricingMode = AiPricingMode.TotalOnly,
        participants = participants(),
        payerParticipantRef = "dana",
        splitEverythingEqually = true,
    )

    private fun participants(): List<AiExtractedParticipant> = listOf(
        participant("dana", "Dana"),
        participant("lee", "Lee"),
    )

    private fun participant(ref: String, name: String, isSelf: Boolean = false) =
        AiExtractedParticipant(ref, name, isSelf)

    private fun assertNeeds(kind: ClarificationKind, result: PrepareAiExpenseResult) {
        assertTrue(result is PrepareAiExpenseResult.NeedsClarification)
        assertEquals(kind, (result as PrepareAiExpenseResult.NeedsClarification).kind)
    }
}
