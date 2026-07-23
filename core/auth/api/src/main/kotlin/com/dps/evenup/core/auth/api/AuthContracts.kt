package com.dps.evenup.core.auth.api

import android.app.Activity
import kotlinx.coroutines.flow.StateFlow

interface AuthStateObserver {
    val authSessionState: StateFlow<AuthSessionState>
}

interface AuthenticationProvider {
    suspend fun authenticateWithGoogle(activity: Activity): AuthResult

    suspend fun requestEmailLink(email: String): EmailLinkRequestResult

    fun isEmailSignInLink(link: String): Boolean

    suspend fun completeEmailLink(
        email: String,
        link: String,
    ): AuthResult

    suspend fun linkGoogle(activity: Activity): AuthResult

    suspend fun linkEmail(
        email: String,
        link: String,
    ): AuthResult
}

interface ReauthenticationManager {
    suspend fun reauthenticateWithGoogle(activity: Activity): AuthResult

    suspend fun reauthenticateWithEmail(
        email: String,
        link: String,
    ): AuthResult
}

interface AuthSessionManager {
    fun currentIdentity(): AuthIdentity?

    suspend fun signOut()

    suspend fun unlink(provider: AuthProviderType): AuthResult
}

interface AuthTokenProvider {
    suspend fun getIdToken(forceRefresh: Boolean = false): String?
}

interface AppAttestationTokenProvider {
    suspend fun getToken(forceRefresh: Boolean = false): String?
}
