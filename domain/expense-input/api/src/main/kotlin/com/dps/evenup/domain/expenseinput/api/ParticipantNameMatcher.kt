package com.dps.evenup.domain.expenseinput.api

interface ParticipantNameMatcher {
    fun match(
        extractedName: String,
        savedNames: List<String>,
    ): ParticipantNameMatch
}

sealed interface ParticipantNameMatch {
    data class Exact(val savedName: String) : ParticipantNameMatch
    data class Possible(val candidates: List<String>) : ParticipantNameMatch
    data object NewName : ParticipantNameMatch
}
