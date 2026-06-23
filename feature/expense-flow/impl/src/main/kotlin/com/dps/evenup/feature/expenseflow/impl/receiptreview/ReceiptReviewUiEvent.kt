package com.dps.evenup.feature.expenseflow.impl.receiptreview

import com.dps.evenup.domain.receipt.api.FeeType

sealed interface ReceiptReviewUiEvent {
    data class EditTargetSelected(val target: ReceiptReviewEditTarget) : ReceiptReviewUiEvent

    data object EditDismissed : ReceiptReviewUiEvent

    data object EditCommitClick : ReceiptReviewUiEvent

    data object StatusClick : ReceiptReviewUiEvent

    data class IssueSelected(val issueId: String) : ReceiptReviewUiEvent

    data object IssueNavigatorDismissed : ReceiptReviewUiEvent

    data object ReviewHighlightedItemsClick : ReceiptReviewUiEvent

    data object UseReceiptTotalClick : ReceiptReviewUiEvent

    data object KeepCalculatedTotalClick : ReceiptReviewUiEvent

    data object EditReceiptTotalClick : ReceiptReviewUiEvent

    data class MerchantNameChanged(val value: String) : ReceiptReviewUiEvent

    data class DateChanged(val value: String) : ReceiptReviewUiEvent

    data class CurrencyChanged(val value: String) : ReceiptReviewUiEvent

    data class ReceiptTotalChanged(val value: String) : ReceiptReviewUiEvent

    data class ItemNameChanged(val value: String) : ReceiptReviewUiEvent

    data class ItemQuantityChanged(val value: String) : ReceiptReviewUiEvent

    data class ItemQuantityStepped(val delta: Int) : ReceiptReviewUiEvent

    data class ItemUnitPriceChanged(val value: String) : ReceiptReviewUiEvent

    data class ItemLineTotalChanged(val value: String) : ReceiptReviewUiEvent

    data object UseSuggestedItemCorrectionClick : ReceiptReviewUiEvent

    data object AddItemClick : ReceiptReviewUiEvent

    data class RemoveItemClick(val itemId: String) : ReceiptReviewUiEvent

    data class FeeTypeChanged(val value: FeeType) : ReceiptReviewUiEvent

    data class FeeLabelChanged(val value: String) : ReceiptReviewUiEvent

    data class FeeAmountChanged(val value: String) : ReceiptReviewUiEvent

    data object AddFeeClick : ReceiptReviewUiEvent

    data class RemoveFeeClick(val feeId: String) : ReceiptReviewUiEvent

    data object ContinueClick : ReceiptReviewUiEvent

    data object BackClick : ReceiptReviewUiEvent
}
