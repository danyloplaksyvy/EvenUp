package com.dps.evenup.feature.expenseflow.impl.newexpense

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dps.evenup.core.network.api.NetworkStatus
import com.dps.evenup.core.speech.api.SpeechTranscriber
import com.dps.evenup.data.expense.api.ExpenseDraftRepository
import com.dps.evenup.data.expenseinput.api.AiExpenseInterpreter
import com.dps.evenup.data.expenseinput.api.AiExpensePreferencesRepository
import com.dps.evenup.data.expenseinput.api.AiExpenseSessionRepository
import com.dps.evenup.data.participant.api.SavedParticipantRepository
import com.dps.evenup.domain.expenseinput.api.PrepareAiExpenseUseCase
import java.util.Currency
import java.util.Locale

@Composable
fun NewExpenseRoute(
    interpreter: AiExpenseInterpreter,
    sessionRepository: AiExpenseSessionRepository,
    preferencesRepository: AiExpensePreferencesRepository,
    savedParticipantRepository: SavedParticipantRepository,
    draftRepository: ExpenseDraftRepository,
    prepareAiExpense: PrepareAiExpenseUseCase,
    speechTranscriber: SpeechTranscriber,
    networkStatus: NetworkStatus,
    onScanReceipt: () -> Unit,
    onEnterManually: () -> Unit,
    onReviewExpense: () -> Unit,
    onReviewAllDetails: () -> Unit,
    modifier: Modifier = Modifier,
    onClose: (() -> Unit)? = null,
) {
    val locale = Locale.getDefault()
    val localeCurrency = remember(locale) {
        runCatching { Currency.getInstance(locale).currencyCode }
            .getOrNull()
            ?.takeUnless { it == "XXX" }
            ?: "USD"
    }
    val factory = remember(
        interpreter,
        sessionRepository,
        preferencesRepository,
        savedParticipantRepository,
        draftRepository,
        prepareAiExpense,
        speechTranscriber,
        networkStatus,
        locale,
        localeCurrency,
    ) {
        NewExpenseViewModel.factory(
            interpreter = interpreter,
            sessionRepository = sessionRepository,
            preferencesRepository = preferencesRepository,
            savedParticipantRepository = savedParticipantRepository,
            draftRepository = draftRepository,
            prepareAiExpense = prepareAiExpense,
            speechTranscriber = speechTranscriber,
            networkStatus = networkStatus,
            localeTag = locale.toLanguageTag(),
            localeCurrency = localeCurrency,
        )
    }
    val viewModel: NewExpenseViewModel = viewModel(factory = factory)
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var pendingMicTarget by remember { mutableStateOf<MicTarget?>(null) }
    val microphonePermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        when (pendingMicTarget) {
            MicTarget.Description -> viewModel.startDescriptionDictation()
            MicTarget.Answer -> viewModel.startAnswerDictation()
            null -> Unit
        }
        pendingMicTarget = null
    }

    fun startMic(target: MicTarget) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            if (target == MicTarget.Description) viewModel.startDescriptionDictation() else viewModel.startAnswerDictation()
        } else {
            pendingMicTarget = target
            microphonePermission.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                NewExpenseEffect.OpenReview -> onReviewExpense()
                NewExpenseEffect.OpenExtractedDetails -> onReviewAllDetails()
                is NewExpenseEffect.OpenInputMode -> when (effect.mode) {
                    NewExpenseInputMode.Scan -> onScanReceipt()
                    NewExpenseInputMode.Manual -> onEnterManually()
                }
            }
        }
    }

    NewExpenseScreen(
        uiState = uiState,
        onEvent = { event ->
            when (event) {
                NewExpenseUiEvent.DescriptionMicClick -> startMic(MicTarget.Description)
                NewExpenseUiEvent.AnswerMicClick -> startMic(MicTarget.Answer)
                NewExpenseUiEvent.CloseClick -> onClose?.invoke()
                else -> viewModel.onEvent(event)
            }
        },
        modifier = modifier,
        closeEnabled = onClose != null,
    )
}

private enum class MicTarget { Description, Answer }
