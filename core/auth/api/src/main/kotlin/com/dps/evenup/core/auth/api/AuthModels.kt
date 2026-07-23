package com.dps.evenup.core.auth.api

enum class AuthProviderType {
    Google,
    EmailLink,
}

data class AuthIdentity(
    val providerUserId: String,
    val verifiedEmail: String?,
    val displayName: String?,
    val photoUrl: String?,
    val providers: Set<AuthProviderType>,
)

sealed interface AuthSessionState {
    data object SignedOut : AuthSessionState

    data object Resolving : AuthSessionState

    data class Authenticated(val identity: AuthIdentity) : AuthSessionState

    data class Failed(val reason: AuthFailureReason) : AuthSessionState
}

sealed interface AuthResult {
    data class Success(val identity: AuthIdentity) : AuthResult

    data object Cancelled : AuthResult

    data class Failure(val reason: AuthFailureReason) : AuthResult
}

enum class AuthFailureReason {
    ConfigurationMissing,
    NoCredential,
    InvalidCredential,
    InvalidEmail,
    InvalidOrExpiredLink,
    Network,
    CredentialConflict,
    RecentAuthenticationRequired,
    ProviderWouldBeOrphaned,
    Unknown,
}

sealed interface EmailLinkRequestResult {
    data object Accepted : EmailLinkRequestResult

    data class Failure(val reason: AuthFailureReason) : EmailLinkRequestResult
}

data class EmailLinkConfiguration(
    val continueUrl: String,
    val androidPackageName: String,
    val minimumVersion: String = "1",
)

data class AuthConfiguration(
    val configured: Boolean,
    val webClientId: String,
    val emailLink: EmailLinkConfiguration,
    val appCheckDebug: Boolean,
)
