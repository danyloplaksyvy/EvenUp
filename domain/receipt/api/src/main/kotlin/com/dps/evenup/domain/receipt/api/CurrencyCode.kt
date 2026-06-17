package com.dps.evenup.domain.receipt.api

@JvmInline
value class CurrencyCode(val value: String) {
    init {
        require(value.length == ISO_4217_LENGTH) { "Currency code must be three letters." }
        require(value.all { it in 'A'..'Z' }) { "Currency code must use uppercase ISO 4217 letters." }
    }

    override fun toString(): String = value

    private companion object {
        const val ISO_4217_LENGTH = 3
    }
}
