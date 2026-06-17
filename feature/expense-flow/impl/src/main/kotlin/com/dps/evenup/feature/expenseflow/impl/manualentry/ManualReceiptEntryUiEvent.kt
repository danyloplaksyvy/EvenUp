package com.dps.evenup.feature.expenseflow.impl.manualentry

sealed interface ManualReceiptEntryUiEvent {
    data class MerchantNameChanged(val value: String) : ManualReceiptEntryUiEvent

    data class DateChanged(val value: String) : ManualReceiptEntryUiEvent

    data class CurrencyChanged(val value: String) : ManualReceiptEntryUiEvent

    data class ItemNameChanged(val itemId: String, val value: String) : ManualReceiptEntryUiEvent

    data class ItemQuantityChanged(val itemId: String, val value: String) : ManualReceiptEntryUiEvent

    data class ItemAmountChanged(val itemId: String, val value: String) : ManualReceiptEntryUiEvent

    data object AddItemClick : ManualReceiptEntryUiEvent

    data class RemoveItemClick(val itemId: String) : ManualReceiptEntryUiEvent

    data class TaxChanged(val value: String) : ManualReceiptEntryUiEvent

    data class TipChanged(val value: String) : ManualReceiptEntryUiEvent

    data class TotalChanged(val value: String) : ManualReceiptEntryUiEvent

    data object ContinueClick : ManualReceiptEntryUiEvent

    data object BackClick : ManualReceiptEntryUiEvent
}
