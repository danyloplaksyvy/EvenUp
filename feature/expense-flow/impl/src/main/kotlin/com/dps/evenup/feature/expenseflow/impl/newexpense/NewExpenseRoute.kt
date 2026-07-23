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
import androidx.compose.runtime.rememberCoroutineScope
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
import com.dps.evenup.data.account.api.PendingAuthActionRepository
import com.dps.evenup.domain.account.api.PendingActionState
import com.dps.evenup.domain.account.api.PendingAuthAction
import com.dps.evenup.domain.account.api.PendingAuthActionType
import com.dps.evenup.domain.account.api.PendingAuthOrigin
import com.dps.evenup.domain.account.api.ProtectedActionDecision
import com.dps.evenup.domain.account.api.RequireAuthenticatedAccountUseCase
import com.dps.evenup.domain.account.api.ResumePendingAuthActionUseCase
import java.util.Currency
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.launch

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
    requireAuthenticatedAccount: RequireAuthenticatedAccountUseCase,
    pendingActions: PendingAuthActionRepository,
    resumePendingAction: ResumePendingAuthActionUseCase,
    onAuthenticationRequired: (String) -> Unit,
    onProfile: () -> Unit,
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
    val coroutineScope = rememberCoroutineScope()
    var executingPendingActionId by remember { mutableStateOf<String?>(null) }
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

    fun runProtectedAction(
        type: PendingAuthActionType,
        reason: String,
        action: () -> Unit,
    ) {
        coroutineScope.launch {
            val pending = PendingAuthAction(
                id = UUID.randomUUID().toString(),
                type = type,
                origin = PendingAuthOrigin.NewExpense,
                reference = null,
                createdAtEpochMillis = System.currentTimeMillis(),
                state = PendingActionState.Pending,
            )
            when (requireAuthenticatedAccount.require(pending)) {
                ProtectedActionDecision.Allowed -> action()
                is ProtectedActionDecision.AuthenticationRequired,
                ProtectedActionDecision.BootstrapRequired,
                -> onAuthenticationRequired(reason)
            }
        }
    }

    LaunchedEffect(viewModel, pendingActions) {
        val pending = pendingActions.get() ?: return@LaunchedEffect
        val event = when (pending.type) {
            PendingAuthActionType.SubmitAiDescription -> NewExpenseUiEvent.SubmitDescription
            PendingAuthActionType.SubmitAiClarification -> NewExpenseUiEvent.SubmitAnswer
            else -> null
        } ?: return@LaunchedEffect
        val claimed = resumePendingAction.claim(pending.id) ?: return@LaunchedEffect
        executingPendingActionId = claimed.id
        viewModel.onEvent(event)
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
            executingPendingActionId?.let {
                resumePendingAction.complete(it)
                executingPendingActionId = null
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
                NewExpenseUiEvent.ProfileClick -> onProfile()
                NewExpenseUiEvent.SubmitDescription -> runProtectedAction(
                    PendingAuthActionType.SubmitAiDescription,
                    "Sign in to use AI expense extraction.",
                ) { viewModel.onEvent(event) }
                NewExpenseUiEvent.SubmitAnswer -> runProtectedAction(
                    PendingAuthActionType.SubmitAiClarification,
                    "Sign in to continue AI expense extraction.",
                ) { viewModel.onEvent(event) }
                NewExpenseUiEvent.ScanReceiptClick -> runProtectedAction(
                    PendingAuthActionType.OpenReceiptScan,
                    "Sign in before scanning or uploading a receipt.",
                    onScanReceipt,
                )
                else -> viewModel.onEvent(event)
            }
        },
        modifier = modifier,
        closeEnabled = onClose != null,
    )
}

private enum class MicTarget { Description, Answer }
