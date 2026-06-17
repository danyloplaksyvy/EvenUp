package com.dps.evenup.domain.receipt.api

@JvmInline
value class MoneyMinor(val value: Long) : Comparable<MoneyMinor> {
    operator fun plus(other: MoneyMinor): MoneyMinor = MoneyMinor(value + other.value)

    operator fun minus(other: MoneyMinor): MoneyMinor = MoneyMinor(value - other.value)

    operator fun unaryMinus(): MoneyMinor = MoneyMinor(-value)

    override fun compareTo(other: MoneyMinor): Int = value.compareTo(other.value)

    override fun toString(): String = value.toString()

    companion object {
        val Zero = MoneyMinor(0)
    }
}
