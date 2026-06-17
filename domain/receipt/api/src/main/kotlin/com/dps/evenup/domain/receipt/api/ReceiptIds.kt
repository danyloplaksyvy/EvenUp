package com.dps.evenup.domain.receipt.api

@JvmInline
value class ReceiptItemId(val value: String) {
    init {
        require(value.isNotBlank()) { "Receipt item id must not be blank." }
    }
}

@JvmInline
value class FeeId(val value: String) {
    init {
        require(value.isNotBlank()) { "Fee id must not be blank." }
    }
}
