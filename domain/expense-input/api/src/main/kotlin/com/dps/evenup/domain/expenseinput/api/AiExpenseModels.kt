package com.dps.evenup.domain.expenseinput.api

import com.dps.evenup.domain.expense.api.ExpenseDraft
import com.dps.evenup.domain.expense.api.ExpenseDraftId
import com.dps.evenup.domain.receipt.api.FeeType

data class AiExpenseSession(
    val sessionId: String,
    val description: String = "",
    val interpretedDescription: String? = null,
    val extraction: AiExtraction? = null,
    val clarificationHistory: List<AiClarificationTurn> = emptyList(),
    val phase: AiExpensePhase = AiExpensePhase.Idle,
    val activeClarification: ClarificationKind? = null,
    val answerDraft: String = "",
    val hasManualEdits: Boolean = false,
    val failureCode: String? = null,
)

enum class AiExpensePhase {
    Idle,
    Processing,
    NeedsClarification,
    Ready,
    Failure,
}

data class AiClarificationTurn(
    val kind: ClarificationKind,
    val answer: String,
)

enum class ClarificationKind {
    PersonalName,
    Payer,
    Currency,
    TotalOrPrices,
    Participants,
    AmbiguousParticipant,
    SplitIntent,
}

data class AiExtraction(
    val title: String? = null,
    val transactionDate: String? = null,
    val currency: String? = null,
    val totalMinor: Long? = null,
    val pricingMode: AiPricingMode? = null,
    val participants: List<AiExtractedParticipant> = emptyList(),
    val payerParticipantRef: String? = null,
    val items: List<AiExtractedItem> = emptyList(),
    val fees: List<AiExtractedFee> = emptyList(),
    val splitEverythingEqually: Boolean = false,
    val provenance: List<AiFactProvenance> = emptyList(),
    val warnings: List<AiExtractionWarning> = emptyList(),
)

enum class AiPricingMode {
    Itemized,
    TotalOnly,
}

data class AiExtractedParticipant(
    val ref: String,
    val name: String,
    val isSelf: Boolean = false,
)

data class AiExtractedItem(
    val ref: String,
    val name: String,
    val quantity: Int? = null,
    val unitPriceMinor: Long? = null,
    val totalPriceMinor: Long? = null,
    val assignment: AiExtractedAssignment? = null,
)

data class AiExtractedAssignment(
    val mode: AiAssignmentMode,
    val shares: List<AiExtractedShare>,
)

enum class AiAssignmentMode {
    Full,
    ByUnits,
    SharedEqual,
    CustomAmount,
    Percentage,
    Ratio,
}

data class AiExtractedShare(
    val participantRef: String,
    val amountMinor: Long? = null,
    val quantity: Int? = null,
    val percentageBasisPoints: Int? = null,
    val ratioWeight: Int? = null,
)

data class AiExtractedFee(
    val ref: String,
    val type: FeeType,
    val label: String,
    val amountMinor: Long,
    val allocationMode: AiFeeAllocationMode? = null,
    val participantRefs: List<String> = emptyList(),
    val shares: List<AiExtractedShare> = emptyList(),
)

enum class AiFeeAllocationMode {
    Equal,
    Proportional,
    Custom,
}

data class AiFactProvenance(
    val path: String,
    val source: AiFactSource,
    val needsReview: Boolean = false,
    val reason: String? = null,
)

enum class AiFactSource {
    Explicit,
    Clarified,
    Derived,
    Defaulted,
}

data class AiExtractionWarning(
    val code: String,
    val path: String? = null,
)

data class PrepareAiExpenseCommand(
    val draftId: ExpenseDraftId,
    val extraction: AiExtraction,
    val originalDescription: String = "",
    val personalName: String?,
    val defaultCurrency: String?,
    val savedParticipantNames: List<String>,
    val todayIsoDate: String,
    val skipPossibleParticipantMatches: Boolean = false,
)

sealed interface PrepareAiExpenseResult {
    data class Ready(
        val draft: ExpenseDraft,
        val extraction: AiExtraction,
    ) : PrepareAiExpenseResult

    data class NeedsClarification(
        val kind: ClarificationKind,
        val question: String,
        val extraction: AiExtraction,
        val candidateNames: List<String> = emptyList(),
    ) : PrepareAiExpenseResult

    data class Invalid(
        val message: String,
        val extraction: AiExtraction,
    ) : PrepareAiExpenseResult
}
