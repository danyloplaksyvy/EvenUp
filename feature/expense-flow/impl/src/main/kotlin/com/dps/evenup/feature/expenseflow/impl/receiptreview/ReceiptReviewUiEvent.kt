package com.dps.evenup.feature.expenseflow.impl.receiptreview

sealed interface ReceiptReviewUiEvent {
    data class EditTargetSelected(val target: ReceiptReviewEditTarget) : ReceiptReviewUiEvent

    data object EditDismissed : ReceiptReviewUiEvent

    data class MerchantNameChanged(val value: String) : ReceiptReviewUiEvent

    data class DateChanged(val value: String) : ReceiptReviewUiEvent

    data class CurrencyChanged(val value: String) : ReceiptReviewUiEvent

    data class ItemNameChanged(val itemId: String, val value: String) : ReceiptReviewUiEvent

    data class ItemQuantityChanged(val itemId: String, val value: String) : ReceiptReviewUiEvent

    data class ItemAmountChanged(val itemId: String, val value: String) : ReceiptReviewUiEvent

    data object AddItemClick : ReceiptReviewUiEvent

    data class RemoveItemClick(val itemId: String) : ReceiptReviewUiEvent

    data class FeeLabelChanged(val feeId: String, val value: String) : ReceiptReviewUiEvent

    data class FeeAmountChanged(val feeId: String, val value: String) : ReceiptReviewUiEvent

    data object AddFeeClick : ReceiptReviewUiEvent

    data class RemoveFeeClick(val feeId: String) : ReceiptReviewUiEvent

    data class SubtotalChanged(val value: String) : ReceiptReviewUiEvent

    data class TotalChanged(val value: String) : ReceiptReviewUiEvent

    data object ContinueClick : ReceiptReviewUiEvent

    data object BackClick : ReceiptReviewUiEvent
}
