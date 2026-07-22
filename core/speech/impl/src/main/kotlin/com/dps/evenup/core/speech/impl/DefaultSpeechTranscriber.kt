package com.dps.evenup.core.speech.impl

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.core.content.ContextCompat
import com.dps.evenup.core.speech.api.SpeechErrorReason
import com.dps.evenup.core.speech.api.SpeechEvent
import com.dps.evenup.core.speech.api.SpeechTranscriber
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

class DefaultSpeechTranscriber(
    private val context: Context,
) : SpeechTranscriber, RecognitionListener {
    private val mutableEvents = MutableSharedFlow<SpeechEvent>(extraBufferCapacity = 16)
    override val events: Flow<SpeechEvent> = mutableEvents.asSharedFlow()
    private var recognizer: SpeechRecognizer? = null

    override val isAvailable: Boolean
        get() = SpeechRecognizer.isRecognitionAvailable(context)

    override fun start(localeTag: String) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            mutableEvents.tryEmit(SpeechEvent.Error(SpeechErrorReason.PermissionDenied))
            return
        }
        if (!isAvailable) {
            mutableEvents.tryEmit(SpeechEvent.Error(SpeechErrorReason.ServiceUnavailable))
            return
        }
        val speechRecognizer = recognizer ?: SpeechRecognizer.createSpeechRecognizer(context).also { created ->
            created.setRecognitionListener(this)
            recognizer = created
        }
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, localeTag)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }
        runCatching { speechRecognizer.startListening(intent) }
            .onFailure { mutableEvents.tryEmit(SpeechEvent.Error(SpeechErrorReason.ServiceUnavailable)) }
    }

    override fun stop() {
        recognizer?.stopListening()
    }

    override fun cancel() {
        recognizer?.cancel()
        mutableEvents.tryEmit(SpeechEvent.Ended)
    }

    override fun release() {
        recognizer?.destroy()
        recognizer = null
    }

    override fun onReadyForSpeech(params: Bundle?) {
        mutableEvents.tryEmit(SpeechEvent.Listening)
    }

    override fun onPartialResults(partialResults: Bundle?) {
        partialResults.bestTranscript()?.let { transcript ->
            mutableEvents.tryEmit(SpeechEvent.PartialTranscript(transcript))
        }
    }

    override fun onResults(results: Bundle?) {
        val transcript = results.bestTranscript()
        if (transcript.isNullOrBlank()) mutableEvents.tryEmit(SpeechEvent.Error(SpeechErrorReason.NoMatch))
        else mutableEvents.tryEmit(SpeechEvent.FinalTranscript(transcript))
        mutableEvents.tryEmit(SpeechEvent.Ended)
    }

    override fun onError(error: Int) {
        val reason = when (error) {
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> SpeechErrorReason.PermissionDenied
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> SpeechErrorReason.Busy
            SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> SpeechErrorReason.Network
            SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> SpeechErrorReason.NoMatch
            SpeechRecognizer.ERROR_SERVER, SpeechRecognizer.ERROR_SERVER_DISCONNECTED,
            SpeechRecognizer.ERROR_CLIENT -> SpeechErrorReason.ServiceUnavailable
            else -> SpeechErrorReason.Unknown
        }
        mutableEvents.tryEmit(SpeechEvent.Error(reason))
        mutableEvents.tryEmit(SpeechEvent.Ended)
    }

    override fun onBeginningOfSpeech() = Unit
    override fun onRmsChanged(rmsdB: Float) = Unit
    override fun onBufferReceived(buffer: ByteArray?) = Unit
    override fun onEndOfSpeech() = Unit
    override fun onEvent(eventType: Int, params: Bundle?) = Unit

    private fun Bundle?.bestTranscript(): String? = this
        ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
        ?.firstOrNull()
        ?.trim()
        ?.takeIf(String::isNotBlank)
}
