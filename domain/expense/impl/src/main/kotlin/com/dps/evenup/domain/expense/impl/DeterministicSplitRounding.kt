package com.dps.evenup.domain.expense.impl

import com.dps.evenup.domain.receipt.api.MoneyMinor

internal object DeterministicSplitRounding {
    fun <T> splitEvenly(
        total: MoneyMinor,
        orderedRecipients: List<T>,
    ): Map<T, MoneyMinor> {
        require(total.value >= 0) { "Total must not be negative." }
        require(orderedRecipients.isNotEmpty()) { "At least one recipient is required." }

        val baseShare = total.value / orderedRecipients.size
        var remainder = (total.value % orderedRecipients.size).toInt()

        return orderedRecipients.associateWith {
            val extra = if (remainder > 0) {
                remainder -= 1
                1L
            } else {
                0L
            }
            MoneyMinor(baseShare + extra)
        }
    }
}
