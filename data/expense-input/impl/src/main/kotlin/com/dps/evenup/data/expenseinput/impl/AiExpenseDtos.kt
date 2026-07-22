package com.dps.evenup.data.expenseinput.impl

import com.dps.evenup.domain.expenseinput.api.AiAssignmentMode
import com.dps.evenup.domain.expenseinput.api.AiClarificationTurn
import com.dps.evenup.domain.expenseinput.api.AiExpensePhase
import com.dps.evenup.domain.expenseinput.api.AiExpenseSession
import com.dps.evenup.domain.expenseinput.api.AiExtractedAssignment
import com.dps.evenup.domain.expenseinput.api.AiExtractedFee
import com.dps.evenup.domain.expenseinput.api.AiExtractedItem
import com.dps.evenup.domain.expenseinput.api.AiExtractedParticipant
import com.dps.evenup.domain.expenseinput.api.AiExtractedShare
import com.dps.evenup.domain.expenseinput.api.AiExtraction
import com.dps.evenup.domain.expenseinput.api.AiExtractionWarning
import com.dps.evenup.domain.expenseinput.api.AiFactProvenance
import com.dps.evenup.domain.expenseinput.api.AiFactSource
import com.dps.evenup.domain.expenseinput.api.AiFeeAllocationMode
import com.dps.evenup.domain.expenseinput.api.AiPricingMode
import com.dps.evenup.domain.expenseinput.api.ClarificationKind
import com.dps.evenup.domain.receipt.api.FeeType
import kotlinx.serialization.Serializable

@Serializable
internal data class InterpretRequestDto(
    val schemaVersion: Int = 1,
    val sessionId: String,
    val requestId: String,
    val language: String = "en",
    val locale: String,
    val defaultCurrency: String,
    val personalName: String? = null,
    val description: String,
    val activeClarification: ActiveClarificationDto? = null,
    val clarificationHistory: List<ClarificationTurnDto> = emptyList(),
    val priorExtraction: ExtractionDto? = null,
)

@Serializable
internal data class ActiveClarificationDto(
    val kind: String,
    val answer: String,
)

@Serializable
internal data class InterpretResponseDto(
    val schemaVersion: Int,
    val requestId: String,
    val extraction: ExtractionDto,
)

@Serializable
internal data class AiExpenseSessionDto(
    val sessionId: String,
    val description: String,
    val interpretedDescription: String? = null,
    val extraction: ExtractionDto? = null,
    val clarificationHistory: List<ClarificationTurnDto> = emptyList(),
    val phase: String,
    val activeClarification: String? = null,
    val answerDraft: String = "",
    val hasManualEdits: Boolean = false,
    val failureCode: String? = null,
)

@Serializable
internal data class ClarificationTurnDto(
    val kind: String,
    val answer: String,
)

@Serializable
internal data class ExtractionDto(
    val title: String? = null,
    val transactionDate: String? = null,
    val currency: String? = null,
    val totalMinor: Long? = null,
    val pricingMode: String? = null,
    val participants: List<ParticipantDto> = emptyList(),
    val payerParticipantRef: String? = null,
    val items: List<ItemDto> = emptyList(),
    val fees: List<FeeDto> = emptyList(),
    val splitEverythingEqually: Boolean = false,
    val provenance: List<ProvenanceDto> = emptyList(),
    val warnings: List<WarningDto> = emptyList(),
)

@Serializable
internal data class ParticipantDto(
    val ref: String,
    val name: String,
    val isSelf: Boolean = false,
)

@Serializable
internal data class ItemDto(
    val ref: String,
    val name: String,
    val quantity: Int? = null,
    val unitPriceMinor: Long? = null,
    val totalPriceMinor: Long? = null,
    val assignment: AssignmentDto? = null,
)

@Serializable
internal data class AssignmentDto(
    val mode: String,
    val shares: List<ShareDto> = emptyList(),
)

@Serializable
internal data class ShareDto(
    val participantRef: String,
    val amountMinor: Long? = null,
    val quantity: Int? = null,
    val percentageBasisPoints: Int? = null,
    val ratioWeight: Int? = null,
)

@Serializable
internal data class FeeDto(
    val ref: String,
    val type: String,
    val label: String,
    val amountMinor: Long,
    val allocationMode: String? = null,
    val participantRefs: List<String> = emptyList(),
    val shares: List<ShareDto> = emptyList(),
)

@Serializable
internal data class ProvenanceDto(
    val path: String,
    val source: String,
    val needsReview: Boolean = false,
    val reason: String? = null,
)

@Serializable
internal data class WarningDto(
    val code: String,
    val path: String? = null,
)

@Serializable
internal data class ErrorEnvelopeDto(val error: ErrorDto)

@Serializable
internal data class ErrorDto(
    val code: String,
    val message: String,
    val requestId: String? = null,
)

internal fun AiExpenseSession.toDto(): AiExpenseSessionDto = AiExpenseSessionDto(
    sessionId = sessionId,
    description = description,
    interpretedDescription = interpretedDescription,
    extraction = extraction?.toDto(),
    clarificationHistory = clarificationHistory.map { it.toDto() },
    phase = phase.name,
    activeClarification = activeClarification?.name,
    answerDraft = answerDraft,
    hasManualEdits = hasManualEdits,
    failureCode = failureCode,
)

internal fun AiExpenseSessionDto.toDomain(): AiExpenseSession = AiExpenseSession(
    sessionId = sessionId,
    description = description,
    interpretedDescription = interpretedDescription,
    extraction = extraction?.toDomain(),
    clarificationHistory = clarificationHistory.map { it.toDomain() },
    phase = AiExpensePhase.valueOf(phase),
    activeClarification = activeClarification?.let(ClarificationKind::valueOf),
    answerDraft = answerDraft,
    hasManualEdits = hasManualEdits,
    failureCode = failureCode,
)

internal fun AiClarificationTurn.toDto(): ClarificationTurnDto = ClarificationTurnDto(kind.name, answer)

internal fun ClarificationTurnDto.toDomain(): AiClarificationTurn = AiClarificationTurn(
    kind = ClarificationKind.valueOf(kind),
    answer = answer,
)

internal fun AiExtraction.toDto(): ExtractionDto = ExtractionDto(
    title = title,
    transactionDate = transactionDate,
    currency = currency,
    totalMinor = totalMinor,
    pricingMode = pricingMode?.name,
    participants = participants.map { ParticipantDto(it.ref, it.name, it.isSelf) },
    payerParticipantRef = payerParticipantRef,
    items = items.map { item ->
        ItemDto(
            ref = item.ref,
            name = item.name,
            quantity = item.quantity,
            unitPriceMinor = item.unitPriceMinor,
            totalPriceMinor = item.totalPriceMinor,
            assignment = item.assignment?.let { assignment ->
                AssignmentDto(assignment.mode.name, assignment.shares.map { it.toDto() })
            },
        )
    },
    fees = fees.map { fee ->
        FeeDto(
            ref = fee.ref,
            type = fee.type.toWire(),
            label = fee.label,
            amountMinor = fee.amountMinor,
            allocationMode = fee.allocationMode?.name,
            participantRefs = fee.participantRefs,
            shares = fee.shares.map { it.toDto() },
        )
    },
    splitEverythingEqually = splitEverythingEqually,
    provenance = provenance.map { ProvenanceDto(it.path, it.source.name, it.needsReview, it.reason) },
    warnings = warnings.map { WarningDto(it.code, it.path) },
)

internal fun ExtractionDto.toDomain(): AiExtraction = AiExtraction(
    title = title,
    transactionDate = transactionDate,
    currency = currency,
    totalMinor = totalMinor,
    pricingMode = pricingMode?.let(AiPricingMode::valueOf),
    participants = participants.map { AiExtractedParticipant(it.ref, it.name, it.isSelf) },
    payerParticipantRef = payerParticipantRef,
    items = items.map { item ->
        AiExtractedItem(
            ref = item.ref,
            name = item.name,
            quantity = item.quantity,
            unitPriceMinor = item.unitPriceMinor,
            totalPriceMinor = item.totalPriceMinor,
            assignment = item.assignment?.let { assignment ->
                AiExtractedAssignment(
                    mode = AiAssignmentMode.valueOf(assignment.mode),
                    shares = assignment.shares.map { it.toDomain() },
                )
            },
        )
    },
    fees = fees.map { fee ->
        AiExtractedFee(
            ref = fee.ref,
            type = fee.type.toFeeType(),
            label = fee.label,
            amountMinor = fee.amountMinor,
            allocationMode = fee.allocationMode?.let(AiFeeAllocationMode::valueOf),
            participantRefs = fee.participantRefs,
            shares = fee.shares.map { it.toDomain() },
        )
    },
    splitEverythingEqually = splitEverythingEqually,
    provenance = provenance.map { AiFactProvenance(it.path, AiFactSource.valueOf(it.source), it.needsReview, it.reason) },
    warnings = warnings.map { AiExtractionWarning(it.code, it.path) },
)

private fun AiExtractedShare.toDto(): ShareDto = ShareDto(
    participantRef = participantRef,
    amountMinor = amountMinor,
    quantity = quantity,
    percentageBasisPoints = percentageBasisPoints,
    ratioWeight = ratioWeight,
)

private fun ShareDto.toDomain(): AiExtractedShare = AiExtractedShare(
    participantRef = participantRef,
    amountMinor = amountMinor,
    quantity = quantity,
    percentageBasisPoints = percentageBasisPoints,
    ratioWeight = ratioWeight,
)

private fun FeeType.toWire(): String = when (this) {
    FeeType.Tax -> "TAX"
    FeeType.Tip -> "TIP"
    FeeType.ServiceFee -> "SERVICE_FEE"
    FeeType.Discount -> "DISCOUNT"
    FeeType.Other -> "OTHER"
}

private fun String.toFeeType(): FeeType = when (this) {
    "TAX" -> FeeType.Tax
    "TIP" -> FeeType.Tip
    "SERVICE_FEE" -> FeeType.ServiceFee
    "DISCOUNT" -> FeeType.Discount
    else -> FeeType.Other
}
