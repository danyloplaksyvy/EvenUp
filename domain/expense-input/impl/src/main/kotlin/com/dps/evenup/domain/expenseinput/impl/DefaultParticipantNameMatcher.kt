package com.dps.evenup.domain.expenseinput.impl

import com.dps.evenup.domain.expenseinput.api.ParticipantNameMatch
import com.dps.evenup.domain.expenseinput.api.ParticipantNameMatcher
import java.util.Locale

class DefaultParticipantNameMatcher : ParticipantNameMatcher {
    override fun match(
        extractedName: String,
        savedNames: List<String>,
    ): ParticipantNameMatch {
        val normalized = extractedName.normalizedName()
        val exact = savedNames.firstOrNull { saved -> saved.normalizedName() == normalized }
        if (exact != null) return ParticipantNameMatch.Exact(exact.trim())

        val possible = savedNames.filter { saved ->
            val candidate = saved.normalizedName()
            val prefixMatch = normalized.isNotEmpty() && candidate.isNotEmpty() &&
                (normalized.startsWith(candidate) || candidate.startsWith(normalized))
            val editMatch = normalized.isNotEmpty() && candidate.isNotEmpty() &&
                levenshteinDistance(normalized, candidate) <= MAX_EDIT_DISTANCE
            prefixMatch || editMatch
        }.map(String::trim).distinct()

        return if (possible.isEmpty()) ParticipantNameMatch.NewName else ParticipantNameMatch.Possible(possible)
    }

    private fun String.normalizedName(): String = trim()
        .replace(WHITESPACE, " ")
        .lowercase(Locale.ROOT)

    private fun levenshteinDistance(left: String, right: String): Int {
        if (left == right) return 0
        if (left.isEmpty()) return right.length
        if (right.isEmpty()) return left.length
        var previous = IntArray(right.length + 1) { it }
        left.forEachIndexed { leftIndex, leftChar ->
            val current = IntArray(right.length + 1)
            current[0] = leftIndex + 1
            right.forEachIndexed { rightIndex, rightChar ->
                current[rightIndex + 1] = minOf(
                    current[rightIndex] + 1,
                    previous[rightIndex + 1] + 1,
                    previous[rightIndex] + if (leftChar == rightChar) 0 else 1,
                )
            }
            previous = current
        }
        return previous.last()
    }

    private companion object {
        val WHITESPACE = Regex("\\s+")
        const val MAX_EDIT_DISTANCE = 2
    }
}
