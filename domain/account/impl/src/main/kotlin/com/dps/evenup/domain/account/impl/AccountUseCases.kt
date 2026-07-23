package com.dps.evenup.domain.account.impl

import com.dps.evenup.data.account.api.AccountBootstrapCommand
import com.dps.evenup.data.account.api.AccountDataException
import com.dps.evenup.data.account.api.AccountDataFailureReason
import com.dps.evenup.data.account.api.AccountRepository
import com.dps.evenup.data.account.api.PendingAuthActionRepository
import com.dps.evenup.data.account.api.ProfileRepository
import com.dps.evenup.domain.account.api.AccountSessionState
import com.dps.evenup.domain.account.api.AccountState
import com.dps.evenup.domain.account.api.BootstrapAccountUseCase
import com.dps.evenup.domain.account.api.BootstrapStatus
import com.dps.evenup.domain.account.api.CancelAccountDeletionUseCase
import com.dps.evenup.domain.account.api.IdentitySession
import com.dps.evenup.domain.account.api.PendingActionState
import com.dps.evenup.domain.account.api.PendingAuthAction
import com.dps.evenup.domain.account.api.Profile
import com.dps.evenup.domain.account.api.ProtectedActionDecision
import com.dps.evenup.domain.account.api.RequestAccountDeletionUseCase
import com.dps.evenup.domain.account.api.RequireAuthenticatedAccountUseCase
import com.dps.evenup.domain.account.api.ResolveAccountSessionUseCase
import com.dps.evenup.domain.account.api.ResumePendingAuthActionUseCase
import com.dps.evenup.domain.account.api.SignOutUseCase
import com.dps.evenup.domain.account.api.UpdateProfileCommand
import com.dps.evenup.domain.account.api.UpdateProfileUseCase

class DefaultResolveAccountSessionUseCase(
    private val identitySession: IdentitySession,
    private val accountRepository: AccountRepository,
    private val bootstrap: BootstrapAccountUseCase,
) : ResolveAccountSessionUseCase {
    override suspend fun resolve(): AccountSessionState {
        if (!identitySession.hasIdentity()) return AccountSessionState.SignedOut
        return try {
            bootstrap.bootstrap()
        } catch (error: AccountDataException) {
            val cached = accountRepository.cachedAccount()
            if (error.reason == AccountDataFailureReason.Connection && cached != null) {
                AccountSessionState.AuthenticatedOffline(cached)
            } else {
                AccountSessionState.AuthenticatedBootstrapFailed(
                    retryable = error.reason != AccountDataFailureReason.Forbidden,
                )
            }
        }
    }
}

class DefaultBootstrapAccountUseCase(
    private val accountRepository: AccountRepository,
    private val commandProvider: () -> AccountBootstrapCommand,
) : BootstrapAccountUseCase {
    override suspend fun bootstrap(): AccountSessionState {
        val account = accountRepository.bootstrap(commandProvider())
        return when {
            account.account.state == AccountState.DeletionPending -> AccountSessionState.DeletionPending(null)
            account.account.state == AccountState.Deleted -> AccountSessionState.Deleted
            account.status == BootstrapStatus.UsernameRequired -> AccountSessionState.AuthenticatedActive(account)
            else -> AccountSessionState.AuthenticatedActive(account)
        }
    }
}

class DefaultRequireAuthenticatedAccountUseCase(
    private val identitySession: IdentitySession,
    private val accountRepository: AccountRepository,
    private val pendingActions: PendingAuthActionRepository,
) : RequireAuthenticatedAccountUseCase {
    override suspend fun require(action: PendingAuthAction): ProtectedActionDecision {
        if (!identitySession.hasIdentity()) {
            pendingActions.replace(action)
            return ProtectedActionDecision.AuthenticationRequired(action)
        }
        val account = accountRepository.cachedAccount()
        return if (account?.account?.state == AccountState.Active) {
            ProtectedActionDecision.Allowed
        } else {
            pendingActions.replace(action)
            ProtectedActionDecision.BootstrapRequired
        }
    }
}

class DefaultResumePendingAuthActionUseCase(
    private val repository: PendingAuthActionRepository,
) : ResumePendingAuthActionUseCase {
    override suspend fun claim(actionId: String): PendingAuthAction? = repository.claim(actionId)

    override suspend fun complete(actionId: String) {
        val current = repository.get() ?: return
        if (current.id == actionId) {
            repository.update(current.copy(state = PendingActionState.Consumed))
            repository.clear()
        }
    }

    override suspend fun markRetryRequired(actionId: String) {
        val current = repository.get() ?: return
        if (current.id == actionId) repository.update(current.copy(state = PendingActionState.RetryRequired))
    }
}

class DefaultSignOutUseCase(
    private val identitySession: IdentitySession,
    private val accountRepository: AccountRepository,
    private val pendingActions: PendingAuthActionRepository,
) : SignOutUseCase {
    override suspend fun signOut() {
        pendingActions.clear()
        accountRepository.signOut()
        identitySession.signOut()
    }
}

class DefaultUpdateProfileUseCase(
    private val repository: ProfileRepository,
) : UpdateProfileUseCase {
    override suspend fun update(command: UpdateProfileCommand): Profile {
        val displayName = command.displayName.trim()
        require(displayName.length in 1..80) { "Display name must contain 1 to 80 characters." }
        val username = requireNotNull(com.dps.evenup.domain.account.api.Username.parse(command.username)) {
            "Username is invalid."
        }
        val currency = command.defaultCurrency.trim().uppercase()
        require(currency.matches(Regex("^[A-Z]{3}$"))) { "Currency must be an ISO code." }
        require(command.locale.isNotBlank() && command.locale.length <= 35) { "Locale is invalid." }
        return repository.updateProfile(
            displayName = displayName,
            username = username.value,
            defaultCurrency = currency,
            locale = command.locale.trim(),
            expectedVersion = command.expectedVersion,
        )
    }
}

class DefaultRequestAccountDeletionUseCase(
    private val repository: AccountRepository,
) : RequestAccountDeletionUseCase {
    override suspend fun request(confirmation: String): AccountSessionState {
        require(confirmation == "DELETE") { "Confirmation must be DELETE." }
        return AccountSessionState.DeletionPending(repository.requestDeletion(confirmation))
    }
}

class DefaultCancelAccountDeletionUseCase(
    private val repository: AccountRepository,
) : CancelAccountDeletionUseCase {
    override suspend fun cancel(): AccountSessionState =
        AccountSessionState.AuthenticatedActive(repository.cancelDeletion())
}
