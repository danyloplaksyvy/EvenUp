package com.dps.evenup.feature.expenseflow.api

import androidx.navigation3.runtime.NavKey
import java.util.UUID
import kotlinx.serialization.Serializable

@Serializable
data class NewExpenseDestination(
    val instanceId: String,
) : NavKey {
    companion object {
        fun fresh(): NewExpenseDestination = NewExpenseDestination(UUID.randomUUID().toString())
    }
}

@Serializable
data object ReceiptScanDestination : NavKey

@Serializable
data object ManualEntryDestination : NavKey

@Serializable
data object ReceiptReviewDestination : NavKey

@Serializable
data object ChoosePeopleDestination : NavKey

@Serializable
data object AssignItemsDestination : NavKey

@Serializable
data object FeesAllocationDestination : NavKey

@Serializable
data object ReviewExpenseDestination : NavKey

@Serializable
data object EditAiDescriptionDestination : NavKey

@Serializable
data class AiExtractedDetailsDestination(
    val fromReview: Boolean = false,
) : NavKey

@Serializable
data object EditPeopleDestination : NavKey

@Serializable
data object EditAssignmentsDestination : NavKey

@Serializable
data object EditFeesDestination : NavKey

@Serializable
data class ExpenseSavedDestination(
    val shareUrl: String,
    val guestPasscode: String,
) : NavKey
