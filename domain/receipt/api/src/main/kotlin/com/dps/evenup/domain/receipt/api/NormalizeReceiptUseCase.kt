package com.dps.evenup.domain.receipt.api

interface NormalizeReceiptUseCase {
    fun normalize(receipt: Receipt): Receipt
}
