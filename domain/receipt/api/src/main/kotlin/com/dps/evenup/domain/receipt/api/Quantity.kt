package com.dps.evenup.domain.receipt.api

@JvmInline
value class Quantity(val value: Int) : Comparable<Quantity> {
    init {
        require(value > 0) { "Quantity must be greater than zero." }
    }

    operator fun plus(other: Quantity): Quantity = Quantity(value + other.value)

    override fun compareTo(other: Quantity): Int = value.compareTo(other.value)

    override fun toString(): String = value.toString()
}
