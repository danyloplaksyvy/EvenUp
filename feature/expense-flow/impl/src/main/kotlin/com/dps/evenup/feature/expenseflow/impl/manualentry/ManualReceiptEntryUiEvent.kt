package com.dps.evenup.feature.expenseflow.impl.manualentry

import com.dps.evenup.domain.receipt.api.FeeType

sealed interface ManualReceiptEntryUiEvent {
    data class EditTargetSelected(val target: ManualReceiptEditTarget) : ManualReceiptEntryUiEvent

    data object EditDismissed : ManualReceiptEntryUiEvent

    data object EditCommitClick : ManualReceiptEntryUiEvent

    data class MerchantNameChanged(val value: String) : ManualReceiptEntryUiEvent

    data class DateChanged(val value: String) : ManualReceiptEntryUiEvent

    data class CurrencyChanged(val value: String) : ManualReceiptEntryUiEvent

    data class ItemNameChanged(val value: String) : ManualReceiptEntryUiEvent

    data class ItemQuantityChanged(val value: String) : ManualReceiptEntryUiEvent

    data class ItemQuantityStepped(val delta: Int) : ManualReceiptEntryUiEvent

    data class ItemUnitPriceChanged(val value: String) : ManualReceiptEntryUiEvent

    data class ItemLineTotalChanged(val value: String) : ManualReceiptEntryUiEvent

    data object AddItemClick : ManualReceiptEntryUiEvent

    data class RemoveItemClick(val itemId: String) : ManualReceiptEntryUiEvent

    data class FeeTypeChanged(val value: FeeType) : ManualReceiptEntryUiEvent

    data class FeeLabelChanged(val value: String) : ManualReceiptEntryUiEvent

    data class FeeAmountChanged(val value: String) : ManualReceiptEntryUiEvent

    data object AddFeeClick : ManualReceiptEntryUiEvent

    data class RemoveFeeClick(val feeId: String) : ManualReceiptEntryUiEvent

    data object ContinueClick : ManualReceiptEntryUiEvent

    data object BackClick : ManualReceiptEntryUiEvent
}
