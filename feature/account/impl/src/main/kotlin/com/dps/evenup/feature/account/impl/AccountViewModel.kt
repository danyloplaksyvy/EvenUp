package com.dps.evenup.feature.account.impl

import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.dps.evenup.core.auth.api.AuthFailureReason
import com.dps.evenup.core.auth.api.AuthResult
import com.dps.evenup.core.auth.api.AuthProviderType
import com.dps.evenup.core.auth.api.AuthSessionManager
import com.dps.evenup.core.auth.api.AuthenticationProvider
import com.dps.evenup.core.auth.api.EmailLinkRequestResult
import com.dps.evenup.core.auth.api.ReauthenticationManager
import com.dps.evenup.data.account.api.ProfileRepository
import com.dps.evenup.domain.account.api.AccountSessionState
import com.dps.evenup.domain.account.api.CancelAccountDeletionUseCase
import com.dps.evenup.domain.account.api.Profile
import com.dps.evenup.domain.account.api.RequestAccountDeletionUseCase
import com.dps.evenup.domain.account.api.ResolveAccountSessionUseCase
import com.dps.evenup.domain.account.api.SignOutUseCase
import com.dps.evenup.domain.account.api.UpdateProfileCommand
import com.dps.evenup.domain.account.api.UpdateProfileUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

data class AccountUiState(
    val session: AccountSessionState = AccountSessionState.AuthenticatedBootstrapping,
    val isBusy: Boolean = false,
    val error: String? = null,
    val email: String = "",
    val emailSent: Boolean = false,
    val emailResendCooldownSeconds: Int = 0,
    val profile: Profile? = null,
    val displayName: String = "",
    val username: String = "",
    val currency: String = "",
    val locale: String = "",
    val deletionConfirmation: String = "",
    val linkedProviders: Set<AuthProviderType> = emptySet(),
)

enum class EmailLinkCompletionOutcome {
    Authenticated,
    Linked,
    Reauthenticated,
}

class AccountViewModel(
    private val authenticationProvider: AuthenticationProvider,
    private val reauthenticationManager: ReauthenticationManager,
    private val authSessionManager: AuthSessionManager,
    private val resolveSession: ResolveAccountSessionUseCase,
    private val profileRepository: ProfileRepository,
    private val updateProfile: UpdateProfileUseCase,
    private val signOut: SignOutUseCase,
    private val requestDeletion: RequestAccountDeletionUseCase,
    private val cancelDeletion: CancelAccountDeletionUseCase,
) : ViewModel() {
    private val mutableState = MutableStateFlow(AccountUiState())
    val state: StateFlow<AccountUiState> = mutableState.asStateFlow()

    init {
        resolve()
        viewModelScope.launch {
            profileRepository.observeProfile().collect { profile ->
                if (profile != null) applyProfile(profile)
            }
        }
    }

    fun resolve() {
        launchBusy {
            val session = resolveSession.resolve()
            mutableState.value = mutableState.value.copy(session = session)
            refreshProviders()
            session.profileOrNull()?.let(::applyProfile)
        }
    }

    fun setEmail(value: String) {
        mutableState.value = mutableState.value.copy(email = value, error = null)
    }

    fun setDisplayName(value: String) = updateState { copy(displayName = value, error = null) }
    fun setUsername(value: String) = updateState { copy(username = value.lowercase(), error = null) }
    fun setCurrency(value: String) = updateState { copy(currency = value.uppercase().take(3), error = null) }
    fun setLocale(value: String) = updateState { copy(locale = value, error = null) }
    fun setDeletionConfirmation(value: String) = updateState { copy(deletionConfirmation = value, error = null) }

    fun signInWithGoogle(activity: Activity, onReady: suspend () -> Unit) {
        launchBusy {
            when (val result = authenticationProvider.authenticateWithGoogle(activity)) {
                is AuthResult.Success -> bootstrapAfterAuthentication(onReady)
                AuthResult.Cancelled -> Unit
                is AuthResult.Failure -> showFailure(result.reason)
            }
        }
    }

    fun requestEmailLink() {
        launchBusy {
            when (val result = authenticationProvider.requestEmailLink(state.value.email)) {
                EmailLinkRequestResult.Accepted -> startEmailResendCooldown()
                is EmailLinkRequestResult.Failure -> showFailure(result.reason)
            }
        }
    }

    fun resendEmailLink() {
        if (state.value.emailResendCooldownSeconds == 0) requestEmailLink()
    }

    fun linkGoogle(activity: Activity) {
        launchBusy {
            when (val result = authenticationProvider.linkGoogle(activity)) {
                is AuthResult.Success -> {
                    refreshProviders()
                    resolve()
                }
                AuthResult.Cancelled -> Unit
                is AuthResult.Failure -> showFailure(result.reason)
            }
        }
    }

    fun reauthenticateWithGoogle(activity: Activity, onComplete: () -> Unit) {
        launchBusy {
            when (val result = reauthenticationManager.reauthenticateWithGoogle(activity)) {
                is AuthResult.Success -> onComplete()
                AuthResult.Cancelled -> Unit
                is AuthResult.Failure -> showFailure(result.reason)
            }
        }
    }

    fun completeEmailLink(
        link: String,
        onReady: suspend (EmailLinkCompletionOutcome) -> Unit,
    ) {
        launchBusy {
            val identity = authSessionManager.currentIdentity()
            val outcome = when {
                identity == null -> EmailLinkCompletionOutcome.Authenticated
                AuthProviderType.EmailLink in identity.providers -> EmailLinkCompletionOutcome.Reauthenticated
                else -> EmailLinkCompletionOutcome.Linked
            }
            val result = when (outcome) {
                EmailLinkCompletionOutcome.Authenticated ->
                    authenticationProvider.completeEmailLink(state.value.email, link)
                EmailLinkCompletionOutcome.Linked ->
                    authenticationProvider.linkEmail(state.value.email, link)
                EmailLinkCompletionOutcome.Reauthenticated ->
                    reauthenticationManager.reauthenticateWithEmail(state.value.email, link)
            }
            when (result) {
                is AuthResult.Success -> {
                    refreshProviders()
                    if (outcome == EmailLinkCompletionOutcome.Authenticated) {
                        bootstrapAfterAuthentication { onReady(outcome) }
                    } else {
                        onReady(outcome)
                    }
                }
                AuthResult.Cancelled -> Unit
                is AuthResult.Failure -> showFailure(result.reason)
            }
        }
    }

    fun unlink(provider: AuthProviderType) {
        launchBusy {
            when (val result = authSessionManager.unlink(provider)) {
                is AuthResult.Success -> refreshProviders()
                AuthResult.Cancelled -> Unit
                is AuthResult.Failure -> showFailure(result.reason)
            }
        }
    }

    fun saveProfile(onSaved: () -> Unit) {
        val current = state.value.profile ?: return
        launchBusy {
            val updated = updateProfile.update(
                UpdateProfileCommand(
                    displayName = state.value.displayName,
                    username = state.value.username,
                    defaultCurrency = state.value.currency,
                    locale = state.value.locale,
                    expectedVersion = current.version,
                ),
            )
            applyProfile(updated)
            onSaved()
        }
    }

    fun updateAvatar(contentType: String, bytes: ByteArray) {
        launchBusy {
            applyProfile(profileRepository.updateAvatar(contentType, bytes))
        }
    }

    fun signOut(onComplete: () -> Unit) {
        launchBusy {
            signOut.signOut()
            updateState { copy(session = AccountSessionState.SignedOut, profile = null) }
            onComplete()
        }
    }

    fun delete(onComplete: () -> Unit) {
        launchBusy {
            val session = requestDeletion.request(state.value.deletionConfirmation)
            updateState { copy(session = session) }
            signOut.signOut()
            onComplete()
        }
    }

    fun cancelDeletion(onRestored: () -> Unit = {}) {
        launchBusy {
            val session = cancelDeletion.cancel()
            updateState { copy(session = session) }
            session.profileOrNull()?.let(::applyProfile)
            onRestored()
        }
    }

    private suspend fun bootstrapAfterAuthentication(onReady: suspend () -> Unit) {
        val session = resolveSession.resolve()
        updateState { copy(session = session) }
        session.profileOrNull()?.let(::applyProfile)
        if (session is AccountSessionState.AuthenticatedActive ||
            session is AccountSessionState.AuthenticatedOffline
        ) {
            onReady()
        }
    }

    private fun startEmailResendCooldown() {
        updateState { copy(emailSent = true, emailResendCooldownSeconds = EMAIL_RESEND_COOLDOWN_SECONDS) }
        viewModelScope.launch {
            while (state.value.emailResendCooldownSeconds > 0) {
                delay(1_000)
                updateState {
                    copy(emailResendCooldownSeconds = (emailResendCooldownSeconds - 1).coerceAtLeast(0))
                }
            }
        }
    }

    private fun launchBusy(block: suspend () -> Unit) {
        viewModelScope.launch {
            updateState { copy(isBusy = true, error = null) }
            runCatching { block() }
                .onFailure { error -> updateState { copy(error = error.message ?: "Something went wrong.") } }
            updateState { copy(isBusy = false) }
        }
    }

    private fun showFailure(reason: AuthFailureReason) {
        val message = when (reason) {
            AuthFailureReason.ConfigurationMissing -> "Development authentication is not configured on this build."
            AuthFailureReason.InvalidEmail -> "Enter a valid email address."
            AuthFailureReason.InvalidOrExpiredLink -> "This sign-in link is invalid or expired."
            AuthFailureReason.Network -> "Check your connection and try again."
            AuthFailureReason.CredentialConflict -> "This sign-in method belongs to another account."
            AuthFailureReason.RecentAuthenticationRequired -> "Sign in again to continue."
            AuthFailureReason.ProviderWouldBeOrphaned -> "Add another sign-in method before removing this one."
            else -> "Authentication could not be completed."
        }
        updateState { copy(error = message) }
    }

    private fun applyProfile(profile: Profile) {
        updateState {
            copy(
                profile = profile,
                displayName = profile.displayName,
                username = profile.username.value,
                currency = profile.defaultCurrency,
                locale = profile.locale,
            )
        }
    }

    private fun refreshProviders() {
        updateState { copy(linkedProviders = authSessionManager.currentIdentity()?.providers.orEmpty()) }
    }

    private fun updateState(block: AccountUiState.() -> AccountUiState) {
        mutableState.value = mutableState.value.block()
    }

    companion object {
        private const val EMAIL_RESEND_COOLDOWN_SECONDS = 60

        fun factory(
            authenticationProvider: AuthenticationProvider,
            reauthenticationManager: ReauthenticationManager,
            authSessionManager: AuthSessionManager,
            resolveSession: ResolveAccountSessionUseCase,
            profileRepository: ProfileRepository,
            updateProfile: UpdateProfileUseCase,
            signOut: SignOutUseCase,
            requestDeletion: RequestAccountDeletionUseCase,
            cancelDeletion: CancelAccountDeletionUseCase,
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = AccountViewModel(
                authenticationProvider,
                reauthenticationManager,
                authSessionManager,
                resolveSession,
                profileRepository,
                updateProfile,
                signOut,
                requestDeletion,
                cancelDeletion,
            ) as T
        }
    }
}

private fun AccountSessionState.profileOrNull(): Profile? = when (this) {
    is AccountSessionState.AuthenticatedActive -> account.profile
    is AccountSessionState.AuthenticatedOffline -> account.profile
    else -> null
}
