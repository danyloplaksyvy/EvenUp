package com.dps.evenup.feature.expenseflow.api

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable
data object NewExpenseDestination : NavKey

@Serializable
data object ReceiptScanDestination : NavKey

@Serializable
data object ManualEntryDestination : NavKey

@Serializable
data object ChoosePeopleDestination : NavKey
