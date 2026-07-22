package com.dps.evenup.core.speech.api

import kotlinx.coroutines.flow.Flow

interface SpeechTranscriber {
    val events: Flow<SpeechEvent>

    val isAvailable: Boolean

    fun start(localeTag: String = "en-US")

    fun stop()

    fun cancel()

    fun release()
}

sealed interface SpeechEvent {
    data object Listening : SpeechEvent
    data class PartialTranscript(val text: String) : SpeechEvent
    data class FinalTranscript(val text: String) : SpeechEvent
    data class Error(val reason: SpeechErrorReason) : SpeechEvent
    data object Ended : SpeechEvent
}

enum class SpeechErrorReason {
    PermissionDenied,
    ServiceUnavailable,
    NoMatch,
    Network,
    Busy,
    Unknown,
}
