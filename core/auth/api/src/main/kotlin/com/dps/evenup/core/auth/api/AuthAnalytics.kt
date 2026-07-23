package com.dps.evenup.core.auth.api

/**
 * Provider-neutral, privacy-safe authentication telemetry.
 *
 * Events intentionally carry no email, token, provider UID, display name, or free-form text.
 */
interface AuthAnalytics {
    fun record(event: AuthAnalyticsEvent)
}

enum class AuthAnalyticsEvent {
    AuthenticationStarted,
    AuthenticationSucceeded,
    AuthenticationCancelled,
    AuthenticationFailed,
    AccountBootstrapped,
    ProviderLinked,
    ProviderUnlinked,
    SignedOut,
    DeletionRequested,
    DeletionCancelled,
}
