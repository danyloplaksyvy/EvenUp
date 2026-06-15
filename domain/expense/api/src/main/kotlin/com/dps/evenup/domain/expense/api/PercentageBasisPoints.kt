package com.dps.evenup.domain.expense.api

@JvmInline
value class PercentageBasisPoints(val value: Int) {
    init {
        require(value in MIN_BASIS_POINTS..MAX_BASIS_POINTS) {
            "Percentage basis points must be between 0 and 10000."
        }
    }

    override fun toString(): String = value.toString()

    companion object {
        const val MIN_BASIS_POINTS = 0
        const val MAX_BASIS_POINTS = 10_000

        val Zero = PercentageBasisPoints(MIN_BASIS_POINTS)
        val OneHundredPercent = PercentageBasisPoints(MAX_BASIS_POINTS)
    }
}
