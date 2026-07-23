package com.dps.evenup.data.account.api

import com.dps.evenup.domain.account.api.BootstrappedAccount
import com.dps.evenup.domain.account.api.PendingAuthAction
import com.dps.evenup.domain.account.api.Profile
import com.dps.evenup.domain.account.api.ProfileVersion
import kotlinx.coroutines.flow.Flow

data class AccountBootstrapCommand(
    val locale: String,
    val defaultCurrency: String,
    val termsVersion: String,
    val privacyVersion: String,
)

interface AccountRepository {
    suspend fun bootstrap(command: AccountBootstrapCommand): BootstrappedAccount

    suspend fun cachedAccount(): BootstrappedAccount?

    suspend fun requestDeletion(confirmation: String): String?

    suspend fun cancelDeletion(): BootstrappedAccount

    suspend fun signOut()
}

interface ProfileRepository {
    fun observeProfile(): Flow<Profile?>

    suspend fun getProfile(forceRefresh: Boolean = false): Profile?

    suspend fun updateProfile(
        displayName: String,
        username: String,
        defaultCurrency: String,
        locale: String,
        expectedVersion: ProfileVersion,
    ): Profile

    suspend fun updateAvatar(
        contentType: String,
        bytes: ByteArray,
    ): Profile
}

interface PendingAuthActionRepository {
    fun observe(): Flow<PendingAuthAction?>

    suspend fun get(): PendingAuthAction?

    suspend fun replace(action: PendingAuthAction)

    suspend fun claim(actionId: String): PendingAuthAction?

    suspend fun update(action: PendingAuthAction)

    suspend fun clear()
}

interface LegalAcceptanceRepository {
    fun termsVersion(): String

    fun privacyVersion(): String
}

class AccountDataException(
    val reason: AccountDataFailureReason,
    message: String? = null,
) : RuntimeException(message)

enum class AccountDataFailureReason {
    AuthenticationRequired,
    Forbidden,
    VersionConflict,
    UsernameUnavailable,
    RateLimited,
    Connection,
    InvalidResponse,
    DependencyUnavailable,
    Unknown,
}
