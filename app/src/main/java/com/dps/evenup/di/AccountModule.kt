package com.dps.evenup.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import com.dps.evenup.BuildConfig
import com.dps.evenup.core.auth.api.AppAttestationTokenProvider
import com.dps.evenup.core.auth.api.AuthAnalytics
import com.dps.evenup.core.auth.api.AuthConfiguration
import com.dps.evenup.core.auth.api.AuthSessionManager
import com.dps.evenup.core.auth.api.AuthStateObserver
import com.dps.evenup.core.auth.api.AuthTokenProvider
import com.dps.evenup.core.auth.api.AuthenticationProvider
import com.dps.evenup.core.auth.api.EmailLinkConfiguration
import com.dps.evenup.core.auth.api.ReauthenticationManager
import com.dps.evenup.core.auth.impl.FirebaseAuthGateway
import com.dps.evenup.core.auth.impl.NoOpAuthAnalytics
import com.dps.evenup.core.database.api.AccountProfileCache
import com.dps.evenup.core.database.api.AccountScopedCacheManager
import com.dps.evenup.core.database.impl.DatabaseFactory
import com.dps.evenup.core.database.impl.RoomAccountCache
import com.dps.evenup.core.datastore.api.StringDataStore
import com.dps.evenup.core.datastore.impl.PreferencesStringDataStore
import com.dps.evenup.core.network.api.AuthenticatedWorkerApiClient
import com.dps.evenup.core.network.api.WorkerApiClient
import com.dps.evenup.core.network.impl.DefaultAuthenticatedWorkerApiClient
import com.dps.evenup.core.network.impl.AuthenticatedV2WorkerApiClient
import com.dps.evenup.data.account.api.AccountBootstrapCommand
import com.dps.evenup.data.account.api.AccountRepository
import com.dps.evenup.data.account.api.LegalAcceptanceRepository
import com.dps.evenup.data.account.api.PendingAuthActionRepository
import com.dps.evenup.data.account.api.ProfileRepository
import com.dps.evenup.data.account.impl.DataStorePendingAuthActionRepository
import com.dps.evenup.data.account.impl.WorkerAccountRepository
import com.dps.evenup.domain.account.api.BootstrapAccountUseCase
import com.dps.evenup.domain.account.api.CancelAccountDeletionUseCase
import com.dps.evenup.domain.account.api.IdentitySession
import com.dps.evenup.domain.account.api.RequestAccountDeletionUseCase
import com.dps.evenup.domain.account.api.RequireAuthenticatedAccountUseCase
import com.dps.evenup.domain.account.api.ResolveAccountSessionUseCase
import com.dps.evenup.domain.account.api.ResumePendingAuthActionUseCase
import com.dps.evenup.domain.account.api.SignOutUseCase
import com.dps.evenup.domain.account.api.UpdateProfileUseCase
import com.dps.evenup.domain.account.impl.DefaultBootstrapAccountUseCase
import com.dps.evenup.domain.account.impl.DefaultCancelAccountDeletionUseCase
import com.dps.evenup.domain.account.impl.DefaultRequestAccountDeletionUseCase
import com.dps.evenup.domain.account.impl.DefaultRequireAuthenticatedAccountUseCase
import com.dps.evenup.domain.account.impl.DefaultResolveAccountSessionUseCase
import com.dps.evenup.domain.account.impl.DefaultResumePendingAuthActionUseCase
import com.dps.evenup.domain.account.impl.DefaultSignOutUseCase
import com.dps.evenup.domain.account.impl.DefaultUpdateProfileUseCase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.util.Currency
import java.util.Locale
import javax.inject.Qualifier
import javax.inject.Singleton

private val Context.authPreferencesDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "evenup_auth_state",
)

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class AuthStateStore

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ProtectedWorkerClient

@Module
@InstallIn(SingletonComponent::class)
object AccountModule {
    @Provides
    @Singleton
    fun provideAuthConfiguration(): AuthConfiguration = AuthConfiguration(
        configured = BuildConfig.AUTH_CONFIGURED,
        webClientId = BuildConfig.AUTH_WEB_CLIENT_ID,
        emailLink = EmailLinkConfiguration(
            continueUrl = "https://${BuildConfig.AUTH_LINK_DOMAIN}/__/auth/links",
            androidPackageName = BuildConfig.APPLICATION_ID,
        ),
        appCheckDebug = BuildConfig.APP_CHECK_DEBUG,
    )

    @Provides
    @Singleton
    fun provideFirebaseAuthGateway(
        @ApplicationContext context: Context,
        configuration: AuthConfiguration,
    ): FirebaseAuthGateway = FirebaseAuthGateway(context, configuration)

    @Provides fun provideAuthenticationProvider(gateway: FirebaseAuthGateway): AuthenticationProvider = gateway
    @Provides fun provideReauthenticationManager(gateway: FirebaseAuthGateway): ReauthenticationManager = gateway
    @Provides fun provideAuthSessionManager(gateway: FirebaseAuthGateway): AuthSessionManager = gateway
    @Provides fun provideAuthStateObserver(gateway: FirebaseAuthGateway): AuthStateObserver = gateway
    @Provides fun provideAuthTokenProvider(gateway: FirebaseAuthGateway): AuthTokenProvider = gateway
    @Provides fun provideAppAttestation(gateway: FirebaseAuthGateway): AppAttestationTokenProvider = gateway
    @Provides @Singleton fun provideAuthAnalytics(): AuthAnalytics = NoOpAuthAnalytics()

    @Provides
    @Singleton
    @AuthStateStore
    fun provideAuthStringStore(@ApplicationContext context: Context): StringDataStore =
        PreferencesStringDataStore(context.authPreferencesDataStore)

    @Provides
    @Singleton
    fun providePendingActions(@AuthStateStore store: StringDataStore): PendingAuthActionRepository =
        DataStorePendingAuthActionRepository(store)

    @Provides
    @Singleton
    fun provideRoomAccountCache(@ApplicationContext context: Context): RoomAccountCache =
        DatabaseFactory.create(context)

    @Provides fun provideProfileCache(cache: RoomAccountCache): AccountProfileCache = cache
    @Provides fun provideCacheManager(cache: RoomAccountCache): AccountScopedCacheManager = cache

    @Provides
    @Singleton
    fun provideAuthenticatedClient(
        workerApiClient: WorkerApiClient,
        tokenProvider: AuthTokenProvider,
        appAttestation: AppAttestationTokenProvider,
    ): AuthenticatedWorkerApiClient =
        DefaultAuthenticatedWorkerApiClient(workerApiClient, tokenProvider, appAttestation)

    @Provides
    @Singleton
    @ProtectedWorkerClient
    fun provideProtectedWorkerClient(
        authenticatedClient: AuthenticatedWorkerApiClient,
    ): WorkerApiClient = AuthenticatedV2WorkerApiClient(authenticatedClient)

    @Provides
    @Singleton
    fun provideWorkerAccountRepository(
        client: AuthenticatedWorkerApiClient,
        profileCache: AccountProfileCache,
        cacheManager: AccountScopedCacheManager,
    ): WorkerAccountRepository = WorkerAccountRepository(client, profileCache, cacheManager)

    @Provides fun provideAccountRepository(repository: WorkerAccountRepository): AccountRepository = repository
    @Provides fun provideProfileRepository(repository: WorkerAccountRepository): ProfileRepository = repository

    @Provides
    fun provideIdentitySession(manager: AuthSessionManager): IdentitySession = object : IdentitySession {
        override fun hasIdentity(): Boolean = manager.currentIdentity() != null
        override suspend fun signOut() = manager.signOut()
    }

    @Provides
    fun provideLegalAcceptanceRepository(): LegalAcceptanceRepository = object : LegalAcceptanceRepository {
        override fun termsVersion(): String = BuildConfig.TERMS_VERSION
        override fun privacyVersion(): String = BuildConfig.PRIVACY_VERSION
    }

    @Provides
    fun provideBootstrapUseCase(
        repository: AccountRepository,
        legal: LegalAcceptanceRepository,
    ): BootstrapAccountUseCase = DefaultBootstrapAccountUseCase(repository) {
        val locale = Locale.getDefault()
        val currency = runCatching { Currency.getInstance(locale).currencyCode }.getOrDefault("USD")
        AccountBootstrapCommand(
            locale = locale.toLanguageTag(),
            defaultCurrency = currency,
            termsVersion = legal.termsVersion(),
            privacyVersion = legal.privacyVersion(),
        )
    }

    @Provides
    fun provideResolveAccountSession(
        identitySession: IdentitySession,
        repository: AccountRepository,
        bootstrap: BootstrapAccountUseCase,
    ): ResolveAccountSessionUseCase =
        DefaultResolveAccountSessionUseCase(identitySession, repository, bootstrap)

    @Provides
    fun provideRequireAuthenticatedAccount(
        identitySession: IdentitySession,
        repository: AccountRepository,
        pendingActions: PendingAuthActionRepository,
    ): RequireAuthenticatedAccountUseCase =
        DefaultRequireAuthenticatedAccountUseCase(identitySession, repository, pendingActions)

    @Provides
    fun provideResumePendingAction(repository: PendingAuthActionRepository): ResumePendingAuthActionUseCase =
        DefaultResumePendingAuthActionUseCase(repository)

    @Provides
    fun provideSignOut(
        identitySession: IdentitySession,
        repository: AccountRepository,
        pendingActions: PendingAuthActionRepository,
    ): SignOutUseCase = DefaultSignOutUseCase(identitySession, repository, pendingActions)

    @Provides fun provideUpdateProfile(repository: ProfileRepository): UpdateProfileUseCase =
        DefaultUpdateProfileUseCase(repository)

    @Provides fun provideRequestDeletion(repository: AccountRepository): RequestAccountDeletionUseCase =
        DefaultRequestAccountDeletionUseCase(repository)

    @Provides fun provideCancelDeletion(repository: AccountRepository): CancelAccountDeletionUseCase =
        DefaultCancelAccountDeletionUseCase(repository)
}
