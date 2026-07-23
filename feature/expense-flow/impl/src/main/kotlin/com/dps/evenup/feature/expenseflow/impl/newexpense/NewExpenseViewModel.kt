package com.dps.evenup.feature.expenseflow.impl.newexpense

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.dps.evenup.core.network.api.NetworkStatus
import com.dps.evenup.core.speech.api.SpeechErrorReason
import com.dps.evenup.core.speech.api.SpeechEvent
import com.dps.evenup.core.speech.api.SpeechTranscriber
import com.dps.evenup.data.expense.api.ExpenseDraftRepository
import com.dps.evenup.data.expenseinput.api.AiExpenseInterpreter
import com.dps.evenup.data.expenseinput.api.AiExpensePreferencesRepository
import com.dps.evenup.data.expenseinput.api.AiExpenseSessionRepository
import com.dps.evenup.data.expenseinput.api.AiInterpretationFailureReason
import com.dps.evenup.data.expenseinput.api.InterpretAiExpenseCommand
import com.dps.evenup.data.expenseinput.api.InterpretAiExpenseResult
import com.dps.evenup.data.participant.api.SavedParticipantRepository
import com.dps.evenup.domain.expense.api.ExpenseDraftId
import com.dps.evenup.domain.expenseinput.api.AiClarificationTurn
import com.dps.evenup.domain.expenseinput.api.AiExpensePhase
import com.dps.evenup.domain.expenseinput.api.AiExpenseSession
import com.dps.evenup.domain.expenseinput.api.AiExtraction
import com.dps.evenup.domain.expenseinput.api.ClarificationKind
import com.dps.evenup.domain.expenseinput.api.PrepareAiExpenseCommand
import com.dps.evenup.domain.expenseinput.api.PrepareAiExpenseResult
import com.dps.evenup.domain.expenseinput.api.PrepareAiExpenseUseCase
import java.time.LocalDate
import java.util.Currency
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class NewExpenseViewModel(
    private val interpreter: AiExpenseInterpreter,
    private val sessionRepository: AiExpenseSessionRepository,
    private val preferencesRepository: AiExpensePreferencesRepository,
    private val savedParticipantRepository: SavedParticipantRepository,
    private val draftRepository: ExpenseDraftRepository,
    private val prepareAiExpense: PrepareAiExpenseUseCase,
    private val speechTranscriber: SpeechTranscriber,
    private val networkStatus: NetworkStatus,
    private val localeTag: String,
    private val localeCurrency: String,
) : ViewModel() {
    private val mutableUiState = MutableStateFlow(NewExpenseUiState(defaultCurrency = localeCurrency))
    val uiState: StateFlow<NewExpenseUiState> = mutableUiState.asStateFlow()
    private val mutableEffects = MutableSharedFlow<NewExpenseEffect>(extraBufferCapacity = 4)
    val effects: SharedFlow<NewExpenseEffect> = mutableEffects.asSharedFlow()

    private var session = AiExpenseSession(sessionId = UUID.randomUUID().toString())
    private var requestJob: Job? = null
    private var activeRequestId: String? = null
    private var personalName: String? = null
    private var defaultCurrency: String = localeCurrency
    private var dictationTarget: DictationTarget? = null
    private var dictationBase: String = ""
    private var pendingInputMode: NewExpenseInputMode? = null
    private var restoreComplete: Boolean = false

    init {
        viewModelScope.launch {
            networkStatus.isOnline.collectLatest { online ->
                mutableUiState.value = mutableUiState.value.copy(isOnline = online)
            }
        }
        viewModelScope.launch {
            speechTranscriber.events.collect(::handleSpeechEvent)
        }
        viewModelScope.launch { restore() }
        viewModelScope.launch {
            sessionRepository.session.collectLatest { storedSession ->
                if (restoreComplete) {
                    when {
                        storedSession == null && session.hasExpenseState() -> resetExpenseState()
                        storedSession != null &&
                            storedSession.sessionId == session.sessionId &&
                            storedSession.hasManualEdits &&
                            storedSession.extraction != session.extraction &&
                            requestJob?.isActive != true -> {
                            session = storedSession
                            syncUi()
                        }
                    }
                }
            }
        }
    }

    fun onEvent(event: NewExpenseUiEvent) {
        when (event) {
            is NewExpenseUiEvent.DescriptionChanged -> updateDescription(event.value)
            is NewExpenseUiEvent.AnswerChanged -> updateAnswer(event.value)
            NewExpenseUiEvent.SubmitDescription -> submitDescription()
            NewExpenseUiEvent.SubmitAnswer -> submitAnswer()
            NewExpenseUiEvent.CancelProcessing -> cancelProcessing()
            NewExpenseUiEvent.StopRecording -> stopDictation()
            NewExpenseUiEvent.ReviewAllDetailsClick -> mutableEffects.tryEmit(NewExpenseEffect.OpenExtractedDetails)
            NewExpenseUiEvent.DefaultsClick -> mutableUiState.value = mutableUiState.value.copy(
                defaultsDialogVisible = true,
                defaultsNameDraft = personalName.orEmpty(),
                defaultsCurrencyDraft = defaultCurrency,
            )
            is NewExpenseUiEvent.DefaultsNameChanged -> mutableUiState.value = mutableUiState.value.copy(defaultsNameDraft = event.value)
            is NewExpenseUiEvent.DefaultsCurrencyChanged -> mutableUiState.value = mutableUiState.value.copy(defaultsCurrencyDraft = event.value.uppercase(Locale.ROOT).take(3))
            NewExpenseUiEvent.DefaultsSave -> saveDefaults()
            NewExpenseUiEvent.DefaultsDismiss -> mutableUiState.value = mutableUiState.value.copy(defaultsDialogVisible = false)
            NewExpenseUiEvent.ScanReceiptClick -> requestInputMode(NewExpenseInputMode.Scan)
            NewExpenseUiEvent.EnterManuallyClick -> requestInputMode(NewExpenseInputMode.Manual)
            NewExpenseUiEvent.DiscardConfirmed -> discardAndContinue()
            NewExpenseUiEvent.DialogDismissed -> {
                pendingInputMode = null
                mutableUiState.value = mutableUiState.value.copy(discardDialogVisible = false, replaceDialogVisible = false)
            }
            NewExpenseUiEvent.ReplaceConfirmed -> {
                mutableUiState.value = mutableUiState.value.copy(replaceDialogVisible = false)
                interpretDescription(fresh = true)
            }
            NewExpenseUiEvent.DescriptionMicClick,
            NewExpenseUiEvent.AnswerMicClick,
            NewExpenseUiEvent.ProfileClick,
            NewExpenseUiEvent.CloseClick,
            -> Unit
        }
    }

    fun startDescriptionDictation() = startDictation(DictationTarget.Description)

    fun startAnswerDictation() = startDictation(DictationTarget.Answer)

    fun cancelDictation() {
        when (dictationTarget) {
            DictationTarget.Description -> updateDescription(dictationBase)
            DictationTarget.Answer -> updateAnswer(dictationBase)
            null -> Unit
        }
        speechTranscriber.cancel()
        dictationTarget = null
        syncUi()
    }

    private suspend fun restore() {
        personalName = preferencesRepository.getPersonalName()
        defaultCurrency = preferencesRepository.getDefaultCurrency()?.takeIf(::isCurrency) ?: localeCurrency.also {
            preferencesRepository.setDefaultCurrency(it)
        }
        session = sessionRepository.getSession() ?: session
        restoreComplete = true
        syncUi(isLoading = false)
    }

    private fun resetExpenseState() {
        activeRequestId = null
        requestJob?.cancel()
        requestJob = null
        speechTranscriber.cancel()
        dictationTarget = null
        dictationBase = ""
        pendingInputMode = null
        session = AiExpenseSession(sessionId = UUID.randomUUID().toString())
        mutableUiState.value = NewExpenseUiState(
            isLoading = false,
            isOnline = networkStatus.isOnline.value,
            personalName = personalName,
            defaultCurrency = defaultCurrency,
        )
    }

    private fun updateDescription(value: String) {
        if (value.length > MAX_DESCRIPTION_LENGTH) return
        session = session.copy(description = value, failureCode = null)
        syncUi()
        persistSession()
    }

    private fun updateAnswer(value: String) {
        if (value.length > MAX_ANSWER_LENGTH) return
        session = session.copy(answerDraft = value)
        syncUi()
        persistSession()
    }

    private fun submitDescription() {
        if (!mutableUiState.value.canSubmitDescription) return
        val fresh = session.interpretedDescription != null && session.interpretedDescription != session.description
        if (fresh && session.hasManualEdits) {
            mutableUiState.value = mutableUiState.value.copy(replaceDialogVisible = true)
            return
        }
        interpretDescription(fresh)
    }

    private fun interpretDescription(fresh: Boolean) {
        runInterpretation(
            activeClarification = null,
            answer = null,
            priorExtraction = if (fresh) null else session.extraction,
            history = if (fresh) emptyList() else session.clarificationHistory,
        )
    }

    private fun submitAnswer() {
        val kind = session.activeClarification ?: return
        val answer = session.answerDraft.trim()
        if (answer.isBlank() || !mutableUiState.value.canSubmitAnswer) return
        if (kind == ClarificationKind.PersonalName) {
            viewModelScope.launch {
                preferencesRepository.setPersonalName(answer)
                personalName = answer
                val history = session.clarificationHistory + AiClarificationTurn(kind, answer)
                session = session.copy(clarificationHistory = history, answerDraft = "")
                evaluate(requireNotNull(session.extraction))
            }
            return
        }
        if (kind == ClarificationKind.Currency && isCurrency(answer.uppercase(Locale.ROOT))) {
            defaultCurrency = answer.uppercase(Locale.ROOT)
            viewModelScope.launch { preferencesRepository.setDefaultCurrency(defaultCurrency) }
        }
        runInterpretation(kind, answer, session.extraction, session.clarificationHistory)
    }

    private fun runInterpretation(
        activeClarification: ClarificationKind?,
        answer: String?,
        priorExtraction: AiExtraction?,
        history: List<AiClarificationTurn>,
    ) {
        if (!networkStatus.isOnline.value || requestJob?.isActive == true) return
        val requestId = UUID.randomUUID().toString()
        activeRequestId = requestId
        requestJob = viewModelScope.launch {
            session = session.copy(phase = AiExpensePhase.Processing, failureCode = null)
            sessionRepository.saveSession(session)
            syncUi()
            val result = interpreter.interpret(
                InterpretAiExpenseCommand(
                    sessionId = session.sessionId,
                    requestId = requestId,
                    description = session.description,
                    locale = localeTag,
                    defaultCurrency = defaultCurrency,
                    personalName = personalName.takeIf { priorExtraction?.participants?.any { it.isSelf } == true },
                    activeClarification = activeClarification,
                    clarificationAnswer = answer,
                    clarificationHistory = history,
                    priorExtraction = priorExtraction,
                ),
            )
            if (activeRequestId != requestId) return@launch
            when (result) {
                is InterpretAiExpenseResult.Success -> {
                    val updatedHistory = if (activeClarification != null && answer != null) {
                        history + AiClarificationTurn(activeClarification, answer)
                    } else {
                        history
                    }
                    session = session.copy(
                        interpretedDescription = session.description,
                        extraction = result.extraction,
                        clarificationHistory = updatedHistory,
                        answerDraft = "",
                    )
                    evaluate(result.extraction)
                }
                is InterpretAiExpenseResult.Failure -> {
                    session = session.copy(phase = AiExpensePhase.Failure, failureCode = result.code ?: result.reason.name)
                    sessionRepository.saveSession(session)
                    syncUi(error = result.reason.userMessage())
                }
            }
        }
    }

    private suspend fun evaluate(extraction: AiExtraction) {
        val savedNames = savedParticipantRepository.getSavedParticipantNames().map { it.value }
        when (val result = prepareAiExpense.prepare(
            PrepareAiExpenseCommand(
                draftId = ExpenseDraftId("ai_${session.sessionId}"),
                extraction = extraction,
                originalDescription = session.description,
                personalName = personalName,
                defaultCurrency = defaultCurrency,
                savedParticipantNames = savedNames,
                todayIsoDate = LocalDate.now().toString(),
                skipPossibleParticipantMatches = session.clarificationHistory.any { it.kind == ClarificationKind.AmbiguousParticipant },
            ),
        )) {
            is PrepareAiExpenseResult.Ready -> {
                draftRepository.saveDraft(result.draft)
                session = session.copy(
                    extraction = result.extraction,
                    phase = AiExpensePhase.Ready,
                    activeClarification = null,
                    answerDraft = "",
                )
                sessionRepository.saveSession(session)
                syncUi()
                mutableEffects.emit(NewExpenseEffect.OpenReview)
            }
            is PrepareAiExpenseResult.NeedsClarification -> {
                session = session.copy(
                    extraction = result.extraction,
                    phase = AiExpensePhase.NeedsClarification,
                    activeClarification = result.kind,
                    answerDraft = "",
                )
                sessionRepository.saveSession(session)
                syncUi(question = result.question, candidates = result.candidateNames)
            }
            is PrepareAiExpenseResult.Invalid -> {
                session = session.copy(
                    extraction = result.extraction,
                    phase = AiExpensePhase.Failure,
                    failureCode = "INVALID_INTERPRETATION",
                )
                sessionRepository.saveSession(session)
                syncUi(error = result.message)
            }
        }
    }

    private fun cancelProcessing() {
        activeRequestId = null
        requestJob?.cancel()
        requestJob = null
        session = session.copy(
            phase = if (session.activeClarification == null) AiExpensePhase.Idle else AiExpensePhase.NeedsClarification,
            failureCode = null,
        )
        syncUi()
        persistSession()
    }

    private fun requestInputMode(mode: NewExpenseInputMode) {
        if (mutableUiState.value.hasUnsavedInput) {
            pendingInputMode = mode
            mutableUiState.value = mutableUiState.value.copy(discardDialogVisible = true)
        } else {
            mutableEffects.tryEmit(NewExpenseEffect.OpenInputMode(mode))
        }
    }

    private fun discardAndContinue() {
        val mode = pendingInputMode ?: return
        viewModelScope.launch {
            sessionRepository.clearSession()
            draftRepository.clearDraft()
            session = AiExpenseSession(sessionId = UUID.randomUUID().toString())
            pendingInputMode = null
            syncUi()
            mutableEffects.emit(NewExpenseEffect.OpenInputMode(mode))
        }
    }

    private fun saveDefaults() {
        val currency = mutableUiState.value.defaultsCurrencyDraft.trim().uppercase(Locale.ROOT)
        if (!isCurrency(currency)) {
            mutableUiState.value = mutableUiState.value.copy(errorMessage = "Enter a three-letter currency code.")
            return
        }
        val name = mutableUiState.value.defaultsNameDraft.trim().takeIf(String::isNotBlank)
        viewModelScope.launch {
            preferencesRepository.setPersonalName(name)
            preferencesRepository.setDefaultCurrency(currency)
            personalName = name
            defaultCurrency = currency
            syncUi()
        }
    }

    private fun startDictation(target: DictationTarget) {
        if (dictationTarget != null) return
        dictationTarget = target
        dictationBase = if (target == DictationTarget.Description) session.description else session.answerDraft
        speechTranscriber.start(localeTag)
        syncUi()
    }

    private fun stopDictation() = speechTranscriber.stop()

    private fun handleSpeechEvent(event: SpeechEvent) {
        when (event) {
            SpeechEvent.Listening -> syncUi()
            is SpeechEvent.PartialTranscript -> applyTranscript(event.text)
            is SpeechEvent.FinalTranscript -> applyTranscript(event.text)
            is SpeechEvent.Error -> {
                mutableUiState.value = mutableUiState.value.copy(errorMessage = event.reason.userMessage())
                dictationTarget = null
                syncUi(error = event.reason.userMessage())
            }
            SpeechEvent.Ended -> {
                dictationTarget = null
                syncUi()
            }
        }
    }

    private fun applyTranscript(transcript: String) {
        val combined = mergeDictationText(dictationBase, transcript)
        when (dictationTarget) {
            DictationTarget.Description -> updateDescription(combined)
            DictationTarget.Answer -> updateAnswer(combined)
            null -> Unit
        }
    }

    private fun syncUi(
        isLoading: Boolean = false,
        question: String? = null,
        candidates: List<String> = emptyList(),
        error: String? = null,
    ) {
        val extraction = session.extraction
        mutableUiState.value = mutableUiState.value.copy(
            isLoading = isLoading,
            description = session.description,
            answer = session.answerDraft,
            phase = session.phase,
            isRecordingDescription = dictationTarget == DictationTarget.Description,
            isRecordingAnswer = dictationTarget == DictationTarget.Answer,
            clarificationQuestion = question ?: session.activeClarification?.questionCopy(),
            clarificationCandidates = candidates,
            extractedSummary = extraction.summaryLines(),
            errorMessage = error ?: session.failureCode?.failureMessage(),
            personalName = personalName,
            defaultCurrency = defaultCurrency,
            defaultsDialogVisible = false,
            discardDialogVisible = false,
        )
    }

    private fun persistSession() {
        viewModelScope.launch { sessionRepository.saveSession(session) }
    }

    override fun onCleared() {
        speechTranscriber.release()
        super.onCleared()
    }

    private fun AiExtraction?.summaryLines(): List<String> {
        this ?: return emptyList()
        return buildList {
            title?.let { add(it) }
            totalMinor?.let { amount -> add("${currency ?: defaultCurrency} ${formatMinor(amount)}") }
            if (participants.isNotEmpty()) add(participants.joinToString(prefix = "People: ") { it.name })
            payerParticipantRef?.let { payerRef ->
                participants.firstOrNull { it.ref == payerRef }?.let { add("Paid by ${it.name}") }
            }
            if (items.isNotEmpty()) add("${items.size} ${if (items.size == 1) "item" else "items"}")
        }
    }

    private fun AiExpenseSession.hasExpenseState(): Boolean = description.isNotBlank() ||
        extraction != null ||
        clarificationHistory.isNotEmpty() ||
        phase != AiExpensePhase.Idle ||
        hasManualEdits

    private fun formatMinor(value: Long): String {
        val absolute = kotlin.math.abs(value)
        val sign = if (value < 0L) "-" else ""
        return "$sign${absolute / 100}.${(absolute % 100).toString().padStart(2, '0')}"
    }

    private fun ClarificationKind.questionCopy(): String = when (this) {
        ClarificationKind.PersonalName -> "What should we call you?"
        ClarificationKind.Payer -> "Who paid for this expense?"
        ClarificationKind.Currency -> "What currency should we use?"
        ClarificationKind.TotalOrPrices -> "What total or item prices are missing?"
        ClarificationKind.Participants -> "Who else should be included?"
        ClarificationKind.AmbiguousParticipant -> "Is this person one of your saved participants?"
        ClarificationKind.SplitIntent -> "How should this expense be split?"
    }

    private fun AiInterpretationFailureReason.userMessage(): String = when (this) {
        AiInterpretationFailureReason.Connection -> "No internet connection. Your description is still here."
        AiInterpretationFailureReason.Timeout -> "That took too long. Try again."
        AiInterpretationFailureReason.InvalidInput -> "Review the description and try again."
        AiInterpretationFailureReason.UnsupportedLanguage -> "AI expense input currently supports English only."
        AiInterpretationFailureReason.RateLimited -> "AI input is busy right now. Try again shortly."
        AiInterpretationFailureReason.Unavailable, AiInterpretationFailureReason.InvalidResponse -> "We couldn't organize that expense. Try again."
    }

    private fun SpeechErrorReason.userMessage(): String = when (this) {
        SpeechErrorReason.PermissionDenied -> "Microphone permission is needed for dictation. You can keep typing."
        SpeechErrorReason.ServiceUnavailable -> "Speech recognition isn't available. You can keep typing."
        SpeechErrorReason.NoMatch -> "No speech was recognized. Try again or keep typing."
        SpeechErrorReason.Network -> "Speech recognition needs a connection. You can keep typing."
        SpeechErrorReason.Busy -> "Speech recognition is busy. Try again."
        SpeechErrorReason.Unknown -> "Dictation stopped. You can keep typing."
    }

    private fun String.failureMessage(): String? = when (this) {
        "INTERRUPTED" -> "Processing was interrupted. Your description is ready to retry."
        "INVALID_INTERPRETATION" -> "Review the extracted details and try again."
        else -> null
    }

    private fun isCurrency(value: String): Boolean = value != "XXX" && runCatching {
        Currency.getInstance(value).currencyCode == value
    }.getOrDefault(false)

    private enum class DictationTarget { Description, Answer }

    companion object {
        const val MAX_DESCRIPTION_LENGTH = 4_000
        const val MAX_ANSWER_LENGTH = 1_000

        fun factory(
            interpreter: AiExpenseInterpreter,
            sessionRepository: AiExpenseSessionRepository,
            preferencesRepository: AiExpensePreferencesRepository,
            savedParticipantRepository: SavedParticipantRepository,
            draftRepository: ExpenseDraftRepository,
            prepareAiExpense: PrepareAiExpenseUseCase,
            speechTranscriber: SpeechTranscriber,
            networkStatus: NetworkStatus,
            localeTag: String,
            localeCurrency: String,
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = NewExpenseViewModel(
                interpreter,
                sessionRepository,
                preferencesRepository,
                savedParticipantRepository,
                draftRepository,
                prepareAiExpense,
                speechTranscriber,
                networkStatus,
                localeTag,
                localeCurrency,
            ) as T
        }
    }
}

internal fun mergeDictationText(typedPrefix: String, currentTranscript: String): String =
    listOf(typedPrefix.trimEnd(), currentTranscript.trim())
        .filter { it.isNotBlank() }
        .joinToString(" ")
