package com.dps.evenup.feature.account.impl

import android.app.Activity
import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dps.evenup.core.auth.api.AuthenticationProvider
import com.dps.evenup.core.auth.api.AuthSessionManager
import com.dps.evenup.core.auth.api.ReauthenticationManager
import com.dps.evenup.core.navigation.api.EvenUpEntryProviderInstaller
import com.dps.evenup.core.navigation.api.EvenUpNavigator
import com.dps.evenup.data.account.api.ProfileRepository
import com.dps.evenup.data.account.api.PendingAuthActionRepository
import com.dps.evenup.domain.account.api.CancelAccountDeletionUseCase
import com.dps.evenup.domain.account.api.RequestAccountDeletionUseCase
import com.dps.evenup.domain.account.api.ResolveAccountSessionUseCase
import com.dps.evenup.domain.account.api.SignOutUseCase
import com.dps.evenup.domain.account.api.UpdateProfileUseCase
import com.dps.evenup.domain.account.api.PendingAuthActionType
import com.dps.evenup.domain.account.api.ResumePendingAuthActionUseCase
import com.dps.evenup.feature.account.api.AccountRootDestination
import com.dps.evenup.feature.account.api.AuthenticationDestination
import com.dps.evenup.feature.account.api.DeleteAccountDestination
import com.dps.evenup.feature.account.api.EditProfileDestination
import com.dps.evenup.feature.account.api.EmailLinkCompletionDestination
import com.dps.evenup.feature.account.api.ProfileDestination
import com.dps.evenup.feature.account.api.ReauthenticationDestination
import com.dps.evenup.feature.account.api.SignInMethodsDestination
import com.dps.evenup.feature.expenseflow.api.NewExpenseDestination
import com.dps.evenup.feature.expenseflow.api.ReceiptScanDestination
import com.dps.evenup.feature.expenseflow.api.ReviewExpenseDestination
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ActivityRetainedComponent
import dagger.multibindings.IntoSet
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

@Module
@InstallIn(ActivityRetainedComponent::class)
object AccountNavigationModule {
    @Provides
    @IntoSet
    fun provideAccountEntryProviderInstaller(
        navigator: EvenUpNavigator,
        authenticationProvider: AuthenticationProvider,
        reauthenticationManager: ReauthenticationManager,
        authSessionManager: AuthSessionManager,
        resolveSession: ResolveAccountSessionUseCase,
        profileRepository: ProfileRepository,
        updateProfile: UpdateProfileUseCase,
        signOut: SignOutUseCase,
        requestDeletion: RequestAccountDeletionUseCase,
        cancelDeletion: CancelAccountDeletionUseCase,
        pendingActions: PendingAuthActionRepository,
        resumePendingAction: ResumePendingAuthActionUseCase,
    ): EvenUpEntryProviderInstaller = EvenUpEntryProviderInstaller { scope ->
        with(scope) {
            entry<AccountRootDestination> {
                val model = accountViewModel(
                    authenticationProvider,
                    reauthenticationManager,
                    authSessionManager,
                    resolveSession,
                    profileRepository,
                    updateProfile,
                    signOut,
                    requestDeletion,
                    cancelDeletion,
                )
                val state by model.state.collectAsState()
                AccountRootScreen(
                    state = state,
                    onContinueLocal = { navigator.replaceAll(NewExpenseDestination.fresh()) },
                    onSignIn = { navigator.navigate(AuthenticationDestination()) },
                    onProfile = { navigator.navigate(ProfileDestination) },
                    onRetry = model::resolve,
                    onCancelDeletion = { model.cancelDeletion() },
                )
            }
            entry<AuthenticationDestination> { destination ->
                val model = accountViewModel(
                    authenticationProvider,
                    reauthenticationManager,
                    authSessionManager,
                    resolveSession,
                    profileRepository,
                    updateProfile,
                    signOut,
                    requestDeletion,
                    cancelDeletion,
                )
                val state by model.state.collectAsState()
                val activity = LocalContext.current as Activity
                val coroutineScope = rememberCoroutineScope()
                LaunchedEffect(state.session) {
                    if (state.session is com.dps.evenup.domain.account.api.AccountSessionState.DeletionPending) {
                        navigator.replaceAll(AccountRootDestination)
                    }
                }
                AuthenticationScreen(
                    state = state,
                    reason = destination.reason,
                    onEmailChanged = model::setEmail,
                    onGoogle = {
                        model.signInWithGoogle(activity) {
                            navigateAfterAuthentication(navigator, pendingActions, resumePendingAction)
                        }
                    },
                    onSendEmail = model::requestEmailLink,
                    onCancel = {
                        coroutineScope.launch {
                            pendingActions.clear()
                            navigator.navigateBack()
                        }
                    },
                )
            }
            entry<EmailLinkCompletionDestination> { destination ->
                val model = accountViewModel(
                    authenticationProvider,
                    reauthenticationManager,
                    authSessionManager,
                    resolveSession,
                    profileRepository,
                    updateProfile,
                    signOut,
                    requestDeletion,
                    cancelDeletion,
                )
                val state by model.state.collectAsState()
                val coroutineScope = rememberCoroutineScope()
                LaunchedEffect(state.session) {
                    if (state.session is com.dps.evenup.domain.account.api.AccountSessionState.DeletionPending) {
                        navigator.replaceAll(AccountRootDestination)
                    }
                }
                EmailLinkCompletionScreen(
                    state = state,
                    onEmailChanged = model::setEmail,
                    onComplete = {
                        model.completeEmailLink(destination.link) { outcome ->
                            when (outcome) {
                                EmailLinkCompletionOutcome.Authenticated ->
                                    navigateAfterAuthentication(navigator, pendingActions, resumePendingAction)
                                EmailLinkCompletionOutcome.Linked ->
                                    navigator.replaceAll(ProfileDestination)
                                EmailLinkCompletionOutcome.Reauthenticated ->
                                    navigator.replaceAll(DeleteAccountDestination)
                            }
                        }
                    },
                    onCancel = {
                        coroutineScope.launch {
                            pendingActions.clear()
                            navigator.navigateBack()
                        }
                    },
                )
            }
            entry<ProfileDestination> {
                val model = accountViewModel(
                    authenticationProvider,
                    reauthenticationManager,
                    authSessionManager,
                    resolveSession,
                    profileRepository,
                    updateProfile,
                    signOut,
                    requestDeletion,
                    cancelDeletion,
                )
                val state by model.state.collectAsState()
                ProfileScreen(
                    state = state,
                    onEdit = { navigator.navigate(EditProfileDestination) },
                    onMethods = { navigator.navigate(SignInMethodsDestination) },
                    onDelete = { navigator.navigate(DeleteAccountDestination) },
                    onSignOut = { model.signOut { navigator.replaceAll(AccountRootDestination) } },
                    onBack = { navigator.navigateBack() },
                )
            }
            entry<EditProfileDestination> {
                val model = accountViewModel(
                    authenticationProvider,
                    reauthenticationManager,
                    authSessionManager,
                    resolveSession,
                    profileRepository,
                    updateProfile,
                    signOut,
                    requestDeletion,
                    cancelDeletion,
                )
                val state by model.state.collectAsState()
                val context = LocalContext.current
                val coroutineScope = rememberCoroutineScope()
                val avatarPicker = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.PickVisualMedia(),
                ) { uri ->
                    if (uri != null) {
                        coroutineScope.launch {
                            val contentType = context.contentResolver.getType(uri).orEmpty()
                            val bytes = withContext(Dispatchers.IO) { context.readAvatar(uri) }
                            if (bytes != null) model.updateAvatar(contentType, bytes)
                        }
                    }
                }
                EditProfileScreen(
                    state,
                    model::setDisplayName,
                    model::setUsername,
                    model::setCurrency,
                    model::setLocale,
                    onAvatar = {
                        avatarPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    },
                    onSave = { model.saveProfile { navigator.navigateBack() } },
                    onCancel = { navigator.navigateBack() },
                )
            }
            entry<SignInMethodsDestination> {
                val model = accountViewModel(
                    authenticationProvider,
                    reauthenticationManager,
                    authSessionManager,
                    resolveSession,
                    profileRepository,
                    updateProfile,
                    signOut,
                    requestDeletion,
                    cancelDeletion,
                )
                val state by model.state.collectAsState()
                val activity = LocalContext.current as Activity
                SignInMethodsScreen(
                    state = state,
                    onLinkGoogle = { model.linkGoogle(activity) },
                    onEmailChanged = model::setEmail,
                    onLinkEmail = model::requestEmailLink,
                    onUnlink = model::unlink,
                    onBack = { navigator.navigateBack() },
                )
            }
            entry<ReauthenticationDestination> {
                val model = accountViewModel(
                    authenticationProvider,
                    reauthenticationManager,
                    authSessionManager,
                    resolveSession,
                    profileRepository,
                    updateProfile,
                    signOut,
                    requestDeletion,
                    cancelDeletion,
                )
                val state by model.state.collectAsState()
                val activity = LocalContext.current as Activity
                AuthenticationScreen(
                    state = state,
                    reason = "Sign in again to continue this sensitive action.",
                    onEmailChanged = model::setEmail,
                    onGoogle = {
                        model.reauthenticateWithGoogle(activity) {
                            navigator.navigateBack()
                        }
                    },
                    onSendEmail = model::requestEmailLink,
                    onCancel = { navigator.navigateBack() },
                )
            }
            entry<DeleteAccountDestination> {
                val model = accountViewModel(
                    authenticationProvider,
                    reauthenticationManager,
                    authSessionManager,
                    resolveSession,
                    profileRepository,
                    updateProfile,
                    signOut,
                    requestDeletion,
                    cancelDeletion,
                )
                val state by model.state.collectAsState()
                DeleteAccountScreen(
                    state,
                    model::setDeletionConfirmation,
                    onReauthenticate = { navigator.navigate(ReauthenticationDestination) },
                    onDelete = { model.delete { navigator.replaceAll(AccountRootDestination) } },
                    onCancel = { navigator.navigateBack() },
                )
            }
        }
    }
}

private suspend fun navigateAfterAuthentication(
    navigator: EvenUpNavigator,
    pendingActions: PendingAuthActionRepository,
    resumePendingAction: ResumePendingAuthActionUseCase,
) {
    when (val pending = pendingActions.get()) {
        null -> navigator.replaceAll(NewExpenseDestination.fresh())
        else -> when (pending.type) {
            PendingAuthActionType.OpenReceiptScan -> {
                resumePendingAction.claim(pending.id)
                resumePendingAction.complete(pending.id)
                navigator.replaceAll(ReceiptScanDestination)
            }
            PendingAuthActionType.ConfirmExpenseSave -> navigator.replaceAll(ReviewExpenseDestination)
            PendingAuthActionType.SubmitAiDescription,
            PendingAuthActionType.SubmitAiClarification,
            -> navigator.replaceAll(NewExpenseDestination.fresh())
            PendingAuthActionType.OpenProtectedDestination -> navigator.replaceAll(ProfileDestination)
        }
    }
}

@Composable
private fun accountViewModel(
    authenticationProvider: AuthenticationProvider,
    reauthenticationManager: ReauthenticationManager,
    authSessionManager: AuthSessionManager,
    resolveSession: ResolveAccountSessionUseCase,
    profileRepository: ProfileRepository,
    updateProfile: UpdateProfileUseCase,
    signOut: SignOutUseCase,
    requestDeletion: RequestAccountDeletionUseCase,
    cancelDeletion: CancelAccountDeletionUseCase,
): AccountViewModel = viewModel(
    factory = AccountViewModel.factory(
        authenticationProvider,
        reauthenticationManager,
        authSessionManager,
        resolveSession,
        profileRepository,
        updateProfile,
        signOut,
        requestDeletion,
        cancelDeletion,
    ),
)

private fun Context.readAvatar(uri: Uri): ByteArray? {
    val output = ByteArrayOutputStream()
    val buffer = ByteArray(8 * 1024)
    contentResolver.openInputStream(uri)?.use { input ->
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            output.write(buffer, 0, count)
            if (output.size() > MAX_AVATAR_BYTES) return null
        }
    } ?: return null
    return output.toByteArray()
}

private const val MAX_AVATAR_BYTES = 5 * 1024 * 1024
