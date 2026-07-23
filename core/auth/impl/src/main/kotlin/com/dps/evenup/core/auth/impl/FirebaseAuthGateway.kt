package com.dps.evenup.core.auth.impl

import android.app.Activity
import android.content.Context
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.NoCredentialException
import com.dps.evenup.core.auth.api.AppAttestationTokenProvider
import com.dps.evenup.core.auth.api.AuthConfiguration
import com.dps.evenup.core.auth.api.AuthFailureReason
import com.dps.evenup.core.auth.api.AuthIdentity
import com.dps.evenup.core.auth.api.AuthProviderType
import com.dps.evenup.core.auth.api.AuthResult
import com.dps.evenup.core.auth.api.AuthSessionManager
import com.dps.evenup.core.auth.api.AuthSessionState
import com.dps.evenup.core.auth.api.AuthStateObserver
import com.dps.evenup.core.auth.api.AuthTokenProvider
import com.dps.evenup.core.auth.api.AuthenticationProvider
import com.dps.evenup.core.auth.api.EmailLinkRequestResult
import com.dps.evenup.core.auth.api.ReauthenticationManager
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory
import com.google.firebase.auth.ActionCodeSettings
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthRecentLoginRequiredException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.tasks.await

class FirebaseAuthGateway(
    context: Context,
    private val configuration: AuthConfiguration,
) : AuthenticationProvider,
    ReauthenticationManager,
    AuthSessionManager,
    AuthTokenProvider,
    AuthStateObserver,
    AppAttestationTokenProvider {
    private val appContext = context.applicationContext
    private val credentialManager = CredentialManager.create(appContext)
    private val mutableAuthSessionState = MutableStateFlow<AuthSessionState>(AuthSessionState.Resolving)
    override val authSessionState: StateFlow<AuthSessionState> = mutableAuthSessionState

    private val firebaseAuth: FirebaseAuth?
        get() = if (configuration.configured && FirebaseApp.getApps(appContext).isNotEmpty()) {
            FirebaseAuth.getInstance()
        } else {
            null
        }

    init {
        val auth = firebaseAuth
        if (auth == null) {
            mutableAuthSessionState.value = AuthSessionState.SignedOut
        } else {
            installAppCheck()
            auth.addAuthStateListener { source ->
                mutableAuthSessionState.value = source.currentUser
                    ?.toIdentity()
                    ?.let(AuthSessionState::Authenticated)
                    ?: AuthSessionState.SignedOut
            }
        }
    }

    override suspend fun authenticateWithGoogle(activity: Activity): AuthResult {
        val auth = firebaseAuth ?: return AuthResult.Failure(AuthFailureReason.ConfigurationMissing)
        if (configuration.webClientId.isBlank()) {
            return AuthResult.Failure(AuthFailureReason.ConfigurationMissing)
        }
        val credential = try {
            getGoogleCredential(activity)
        } catch (error: Throwable) {
            return AuthResult.Failure(error.toFailureReason())
        } ?: return AuthResult.Cancelled
        return mapFirebaseResult {
            auth.signInWithCredential(credential).await().user.requireIdentity()
        }
    }

    override suspend fun requestEmailLink(email: String): EmailLinkRequestResult {
        val auth = firebaseAuth ?: return EmailLinkRequestResult.Failure(AuthFailureReason.ConfigurationMissing)
        val normalizedEmail = email.trim()
        if (!EMAIL_PATTERN.matches(normalizedEmail)) {
            return EmailLinkRequestResult.Failure(AuthFailureReason.InvalidEmail)
        }
        return try {
            auth.sendSignInLinkToEmail(normalizedEmail, actionCodeSettings()).await()
            EmailLinkRequestResult.Accepted
        } catch (error: Throwable) {
            EmailLinkRequestResult.Failure(error.toFailureReason())
        }
    }

    override fun isEmailSignInLink(link: String): Boolean =
        firebaseAuth?.isSignInWithEmailLink(link) == true

    override suspend fun completeEmailLink(
        email: String,
        link: String,
    ): AuthResult {
        val auth = firebaseAuth ?: return AuthResult.Failure(AuthFailureReason.ConfigurationMissing)
        if (!auth.isSignInWithEmailLink(link)) {
            return AuthResult.Failure(AuthFailureReason.InvalidOrExpiredLink)
        }
        return mapFirebaseResult {
            auth.signInWithEmailLink(email.trim(), link).await().user.requireIdentity()
        }
    }

    override suspend fun linkGoogle(activity: Activity): AuthResult {
        val user = firebaseAuth?.currentUser ?: return AuthResult.Failure(AuthFailureReason.InvalidCredential)
        if (configuration.webClientId.isBlank()) {
            return AuthResult.Failure(AuthFailureReason.ConfigurationMissing)
        }
        val credential = try {
            getGoogleCredential(activity)
        } catch (error: Throwable) {
            return AuthResult.Failure(error.toFailureReason())
        } ?: return AuthResult.Cancelled
        return mapFirebaseResult {
            user.linkWithCredential(credential).await().user.requireIdentity()
        }
    }

    override suspend fun linkEmail(
        email: String,
        link: String,
    ): AuthResult {
        val user = firebaseAuth?.currentUser ?: return AuthResult.Failure(AuthFailureReason.InvalidCredential)
        return mapFirebaseResult {
            user.linkWithCredential(EmailAuthProvider.getCredentialWithLink(email.trim(), link))
                .await()
                .user
                .requireIdentity()
        }
    }

    override suspend fun reauthenticateWithGoogle(activity: Activity): AuthResult {
        val user = firebaseAuth?.currentUser ?: return AuthResult.Failure(AuthFailureReason.InvalidCredential)
        if (configuration.webClientId.isBlank()) {
            return AuthResult.Failure(AuthFailureReason.ConfigurationMissing)
        }
        val credential = try {
            getGoogleCredential(activity)
        } catch (error: Throwable) {
            return AuthResult.Failure(error.toFailureReason())
        } ?: return AuthResult.Cancelled
        return mapFirebaseResult {
            user.reauthenticate(credential).await()
            user.reload().await()
            user.requireIdentity()
        }
    }

    override suspend fun reauthenticateWithEmail(
        email: String,
        link: String,
    ): AuthResult {
        val user = firebaseAuth?.currentUser ?: return AuthResult.Failure(AuthFailureReason.InvalidCredential)
        return mapFirebaseResult {
            user.reauthenticate(EmailAuthProvider.getCredentialWithLink(email.trim(), link)).await()
            user.requireIdentity()
        }
    }

    override fun currentIdentity(): AuthIdentity? = firebaseAuth?.currentUser?.toIdentity()

    override suspend fun signOut() {
        firebaseAuth?.signOut()
        runCatching { credentialManager.clearCredentialState(ClearCredentialStateRequest()) }
        mutableAuthSessionState.value = AuthSessionState.SignedOut
    }

    override suspend fun unlink(provider: AuthProviderType): AuthResult {
        val user = firebaseAuth?.currentUser ?: return AuthResult.Failure(AuthFailureReason.InvalidCredential)
        val linkedProviders = user.providerData.map { it.providerId }.filter { it != "firebase" }.toSet()
        if (linkedProviders.size <= 1) {
            return AuthResult.Failure(AuthFailureReason.ProviderWouldBeOrphaned)
        }
        val providerId = when (provider) {
            AuthProviderType.Google -> GoogleAuthProvider.PROVIDER_ID
            AuthProviderType.EmailLink -> EmailAuthProvider.PROVIDER_ID
        }
        return mapFirebaseResult {
            user.unlink(providerId).await().user.requireIdentity()
        }
    }

    override suspend fun getIdToken(forceRefresh: Boolean): String? =
        firebaseAuth?.currentUser?.getIdToken(forceRefresh)?.await()?.token

    override suspend fun getToken(forceRefresh: Boolean): String? {
        if (firebaseAuth == null) return null
        return runCatching {
            FirebaseAppCheck.getInstance().getAppCheckToken(forceRefresh).await().token
        }.getOrNull()
    }

    private suspend fun getGoogleCredential(activity: Activity): com.google.firebase.auth.AuthCredential? {
        if (configuration.webClientId.isBlank()) return null
        return try {
            val googleIdOption = GetGoogleIdOption.Builder()
                .setServerClientId(configuration.webClientId)
                .setFilterByAuthorizedAccounts(false)
                .setAutoSelectEnabled(false)
                .build()
            val response = credentialManager.getCredential(
                context = activity,
                request = GetCredentialRequest.Builder()
                    .addCredentialOption(googleIdOption)
                    .build(),
            )
            val customCredential = response.credential as? CustomCredential ?: return null
            if (customCredential.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) return null
            val googleCredential = GoogleIdTokenCredential.createFrom(customCredential.data)
            GoogleAuthProvider.getCredential(googleCredential.idToken, null)
        } catch (_: GetCredentialCancellationException) {
            null
        } catch (_: GoogleIdTokenParsingException) {
            null
        }
    }

    private fun actionCodeSettings(): ActionCodeSettings = ActionCodeSettings.newBuilder()
        .setUrl(configuration.emailLink.continueUrl)
        .setHandleCodeInApp(true)
        .setAndroidPackageName(
            configuration.emailLink.androidPackageName,
            true,
            configuration.emailLink.minimumVersion,
        )
        .build()

    private fun installAppCheck() {
        val appCheck = FirebaseAppCheck.getInstance()
        if (configuration.appCheckDebug) {
            appCheck.installAppCheckProviderFactory(DebugAppCheckProviderFactory.getInstance())
        } else {
            appCheck.installAppCheckProviderFactory(PlayIntegrityAppCheckProviderFactory.getInstance())
        }
    }

    private suspend fun mapFirebaseResult(block: suspend () -> AuthIdentity): AuthResult = try {
        AuthResult.Success(block())
    } catch (error: Throwable) {
        AuthResult.Failure(error.toFailureReason())
    }

    private companion object {
        val EMAIL_PATTERN = Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")
    }
}

private fun FirebaseUser?.requireIdentity(): AuthIdentity =
    requireNotNull(this) { "Firebase returned no authenticated user." }.toIdentity()

private fun FirebaseUser.toIdentity(): AuthIdentity = AuthIdentity(
    providerUserId = uid,
    verifiedEmail = email?.takeIf { isEmailVerified },
    displayName = displayName,
    photoUrl = photoUrl?.toString(),
    providers = providerData.mapNotNullTo(linkedSetOf()) { provider ->
        when (provider.providerId) {
            GoogleAuthProvider.PROVIDER_ID -> AuthProviderType.Google
            EmailAuthProvider.PROVIDER_ID -> AuthProviderType.EmailLink
            else -> null
        }
    },
)

private fun Throwable.toFailureReason(): AuthFailureReason = when (this) {
    is NoCredentialException -> AuthFailureReason.NoCredential
    is FirebaseNetworkException -> AuthFailureReason.Network
    is FirebaseAuthUserCollisionException -> AuthFailureReason.CredentialConflict
    is FirebaseAuthRecentLoginRequiredException -> AuthFailureReason.RecentAuthenticationRequired
    is FirebaseAuthInvalidCredentialsException -> AuthFailureReason.InvalidCredential
    else -> AuthFailureReason.Unknown
}
