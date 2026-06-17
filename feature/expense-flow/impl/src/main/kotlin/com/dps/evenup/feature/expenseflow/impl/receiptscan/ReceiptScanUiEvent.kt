package com.dps.evenup.feature.expenseflow.impl.receiptscan

sealed interface ReceiptScanUiEvent {
    data object BackClick : ReceiptScanUiEvent

    data object CaptureClick : ReceiptScanUiEvent

    data object GalleryClick : ReceiptScanUiEvent

    data object TryAgainClick : ReceiptScanUiEvent

    data object ManualFallbackClick : ReceiptScanUiEvent
}
