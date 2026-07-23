package com.dps.evenup.domain.account.api

@JvmInline
value class AccountId(val value: String) {
    init {
        require(value.isNotBlank()) { "Account ID must not be blank." }
    }
}

@JvmInline
value class ProfileVersion(val value: Long) {
    init {
        require(value > 0) { "Profile version must be positive." }
    }
}

@JvmInline
value class Username private constructor(val value: String) {
    companion object {
        private val Pattern = Regex("^[a-z][a-z0-9_]{1,22}[a-z0-9]$")

        fun parse(value: String): Username? {
            val normalized = value.trim().lowercase()
            return normalized
                .takeIf { it.length in 3..24 }
                ?.takeIf(Pattern::matches)
                ?.takeIf { "__" !in it }
                ?.let(::Username)
        }
    }
}

enum class AccountState {
    Active,
    DeletionPending,
    Deleted,
}

enum class AccountAuthProvider {
    Google,
    EmailLink,
}

data class Account(
    val id: AccountId,
    val state: AccountState,
)

data class Profile(
    val ownerAccountId: AccountId,
    val username: Username,
    val displayName: String,
    val avatarUrl: String?,
    val defaultCurrency: String,
    val locale: String,
    val version: ProfileVersion,
)

enum class BootstrapStatus {
    Ready,
    UsernameRequired,
}

data class BootstrappedAccount(
    val account: Account,
    val profile: Profile?,
    val providers: Set<AccountAuthProvider>,
    val status: BootstrapStatus,
)

sealed interface AccountSessionState {
    data object SignedOut : AccountSessionState
    data object Authenticating : AccountSessionState
    data object AuthenticatedBootstrapping : AccountSessionState
    data class AuthenticatedBootstrapFailed(val retryable: Boolean) : AccountSessionState
    data class AuthenticatedActive(val account: BootstrappedAccount) : AccountSessionState
    data class AuthenticatedOffline(val account: BootstrappedAccount) : AccountSessionState
    data object ReauthenticationRequired : AccountSessionState
    data class DeletionPending(val recoveryEndsAt: String?) : AccountSessionState
    data object Deleted : AccountSessionState
}

enum class PendingAuthActionType {
    SubmitAiDescription,
    SubmitAiClarification,
    OpenReceiptScan,
    ConfirmExpenseSave,
    OpenProtectedDestination,
}

enum class PendingAuthOrigin {
    NewExpense,
    ReviewExpense,
    Profile,
}

enum class PendingActionState {
    Pending,
    Executing,
    RetryRequired,
    Consumed,
    Cancelled,
}

data class PendingAuthAction(
    val id: String,
    val type: PendingAuthActionType,
    val origin: PendingAuthOrigin,
    val reference: String?,
    val createdAtEpochMillis: Long,
    val state: PendingActionState,
)

sealed interface ProtectedActionDecision {
    data object Allowed : ProtectedActionDecision

    data class AuthenticationRequired(val action: PendingAuthAction) : ProtectedActionDecision

    data object BootstrapRequired : ProtectedActionDecision
}
