package com.dps.evenup.data.account.impl

import com.dps.evenup.core.database.api.AccountProfileCache
import com.dps.evenup.core.database.api.AccountScopedCacheManager
import com.dps.evenup.core.database.api.CachedProfileRecord
import com.dps.evenup.core.network.api.AuthenticatedWorkerApiClient
import com.dps.evenup.core.network.api.WorkerApiResult
import com.dps.evenup.core.network.api.WorkerNetworkError
import com.dps.evenup.data.account.api.AccountBootstrapCommand
import com.dps.evenup.data.account.api.AccountDataException
import com.dps.evenup.data.account.api.AccountDataFailureReason
import com.dps.evenup.data.account.api.AccountRepository
import com.dps.evenup.data.account.api.ProfileRepository
import com.dps.evenup.domain.account.api.Account
import com.dps.evenup.domain.account.api.AccountAuthProvider
import com.dps.evenup.domain.account.api.AccountId
import com.dps.evenup.domain.account.api.AccountState
import com.dps.evenup.domain.account.api.BootstrapStatus
import com.dps.evenup.domain.account.api.BootstrappedAccount
import com.dps.evenup.domain.account.api.Profile
import com.dps.evenup.domain.account.api.ProfileVersion
import com.dps.evenup.domain.account.api.Username
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.net.URI

class WorkerAccountRepository(
    private val client: AuthenticatedWorkerApiClient,
    private val profileCache: AccountProfileCache,
    private val cacheManager: AccountScopedCacheManager,
    private val json: Json = Json { ignoreUnknownKeys = true; explicitNulls = false },
) : AccountRepository, ProfileRepository {
    override suspend fun bootstrap(command: AccountBootstrapCommand): BootstrappedAccount {
        val request = BootstrapRequestDto(
            locale = command.locale,
            defaultCurrency = command.defaultCurrency,
            legalAcceptance = LegalAcceptanceDto(
                termsVersion = command.termsVersion,
                privacyVersion = command.privacyVersion,
            ),
        )
        val response = client.sendJson(
            method = "POST",
            path = "/v2/account/bootstrap",
            body = json.encodeToString(request),
            headers = mapOf("Idempotency-Key" to "bootstrap-v1"),
        ).bodyOrThrow()
        val account = decodeAccountResponse(response)
        cache(account)
        return account
    }

    override suspend fun cachedAccount(): BootstrappedAccount? {
        val accountId = cacheManager.activeAccountId() ?: return null
        val profile = profileCache.read(accountId)?.toDomain() ?: return null
        return BootstrappedAccount(
            account = Account(AccountId(accountId), AccountState.Active),
            profile = profile,
            providers = emptySet(),
            status = BootstrapStatus.Ready,
        )
    }

    override suspend fun requestDeletion(confirmation: String): String? {
        val request = DeleteAccountRequestDto(confirmation)
        val response = client.sendJson(
            method = "DELETE",
            path = "/v2/account",
            body = json.encodeToString(request),
            headers = mapOf("Idempotency-Key" to "delete-${System.currentTimeMillis()}"),
        ).bodyOrThrow()
        val dto = runCatching { json.decodeFromString<DeletionResponseDto>(response) }
            .getOrElse { throw AccountDataException(AccountDataFailureReason.InvalidResponse) }
        cacheManager.lock()
        return dto.recoveryEndsAt
    }

    override suspend fun cancelDeletion(): BootstrappedAccount {
        val response = client.sendJson(
            method = "POST",
            path = "/v2/account/deletion/cancel",
            body = "{}",
            headers = mapOf("Idempotency-Key" to "cancel-deletion-${System.currentTimeMillis()}"),
        ).bodyOrThrow()
        val account = decodeAccountResponse(response)
        cache(account)
        return account
    }

    override suspend fun signOut() {
        cacheManager.lock()
    }

    override fun observeProfile(): Flow<Profile?> = flow {
        val accountId = cacheManager.activeAccountId()
        if (accountId == null) emit(null) else profileCache.observe(accountId).collect { emit(it?.toDomain()) }
    }

    override suspend fun getProfile(forceRefresh: Boolean): Profile? {
        val accountId = cacheManager.activeAccountId() ?: return null
        if (!forceRefresh) profileCache.read(accountId)?.let { return it.toDomain() }
        val response = client.get("/v2/profile").bodyOrThrow()
        val dto = runCatching { json.decodeFromString<ProfileDto>(response) }
            .getOrElse { throw AccountDataException(AccountDataFailureReason.InvalidResponse) }
        return dto.toDomain(accountId).also { profileCache.write(it.toRecord()) }
    }

    override suspend fun updateProfile(
        displayName: String,
        username: String,
        defaultCurrency: String,
        locale: String,
        expectedVersion: ProfileVersion,
    ): Profile {
        val accountId = cacheManager.activeAccountId()
            ?: throw AccountDataException(AccountDataFailureReason.AuthenticationRequired)
        val request = UpdateProfileRequestDto(displayName, username, defaultCurrency, locale)
        val response = client.sendJson(
            method = "PATCH",
            path = "/v2/profile",
            body = json.encodeToString(request),
            headers = mapOf("If-Match" to expectedVersion.value.toString()),
        ).bodyOrThrow()
        val profile = runCatching { json.decodeFromString<ProfileDto>(response).toDomain(accountId) }
            .getOrElse { throw AccountDataException(AccountDataFailureReason.InvalidResponse) }
        profileCache.write(profile.toRecord())
        return profile
    }

    override suspend fun updateAvatar(
        contentType: String,
        bytes: ByteArray,
    ): Profile {
        require(contentType in ALLOWED_AVATAR_CONTENT_TYPES) { "Avatar must be JPEG, PNG, or WebP." }
        require(bytes.isNotEmpty() && bytes.size <= MAX_AVATAR_BYTES) { "Avatar must be 5 MB or smaller." }
        val intentBody = client.sendJson(
            method = "POST",
            path = "/v2/profile/avatar-upload-intents",
            body = json.encodeToString(AvatarIntentRequestDto(contentType, bytes.size)),
        ).bodyOrThrow()
        val intent = runCatching { json.decodeFromString<AvatarIntentResponseDto>(intentBody) }
            .getOrElse { throw AccountDataException(AccountDataFailureReason.InvalidResponse) }
        val uploadUri = runCatching { URI(intent.uploadUrl) }
            .getOrElse { throw AccountDataException(AccountDataFailureReason.InvalidResponse) }
        val uploadPath = buildString {
            append(uploadUri.rawPath)
            uploadUri.rawQuery?.let { append('?').append(it) }
        }
        client.sendBytes(
            method = "PUT",
            path = uploadPath,
            body = bytes,
            contentType = contentType,
        ).bodyOrThrow()
        return getProfile(forceRefresh = true)
            ?: throw AccountDataException(AccountDataFailureReason.InvalidResponse)
    }

    private suspend fun cache(account: BootstrappedAccount) {
        cacheManager.replaceActiveAccount(account.account.id.value)
        account.profile?.let { profileCache.write(it.toRecord()) }
    }

    private fun decodeAccountResponse(body: String): BootstrappedAccount {
        val dto = runCatching { json.decodeFromString<BootstrapResponseDto>(body) }
            .getOrElse { throw AccountDataException(AccountDataFailureReason.InvalidResponse) }
        val accountId = AccountId(dto.account.id)
        return BootstrappedAccount(
            account = Account(accountId, dto.account.state.toAccountState()),
            profile = dto.profile?.toDomain(accountId.value),
            providers = dto.providers.mapNotNullTo(linkedSetOf()) {
                when (it.uppercase()) {
                    "GOOGLE" -> AccountAuthProvider.Google
                    "EMAIL_LINK" -> AccountAuthProvider.EmailLink
                    else -> null
                }
            },
            status = if (dto.bootstrapStatus == "USERNAME_REQUIRED") {
                BootstrapStatus.UsernameRequired
            } else {
                BootstrapStatus.Ready
            },
        )
    }
}

private fun WorkerApiResult.bodyOrThrow(): String = when (this) {
    is WorkerApiResult.Success -> response.body
    is WorkerApiResult.Failure -> throw error.toAccountException()
}

private fun WorkerNetworkError.toAccountException(): AccountDataException = when (this) {
    WorkerNetworkError.ConnectionFailed,
    WorkerNetworkError.Timeout,
    -> AccountDataException(AccountDataFailureReason.Connection)
    is WorkerNetworkError.HttpFailure -> AccountDataException(
        reason = when (statusCode) {
            401 -> AccountDataFailureReason.AuthenticationRequired
            403 -> AccountDataFailureReason.Forbidden
            409 -> if ("USERNAME" in body) {
                AccountDataFailureReason.UsernameUnavailable
            } else {
                AccountDataFailureReason.VersionConflict
            }
            429 -> AccountDataFailureReason.RateLimited
            503 -> AccountDataFailureReason.DependencyUnavailable
            else -> AccountDataFailureReason.Unknown
        },
    )
    WorkerNetworkError.InvalidBaseUrl,
    WorkerNetworkError.InvalidPath,
    WorkerNetworkError.Unknown,
    -> AccountDataException(AccountDataFailureReason.Unknown)
}

private fun String.toAccountState(): AccountState = when (uppercase()) {
    "DELETION_PENDING" -> AccountState.DeletionPending
    "DELETED" -> AccountState.Deleted
    else -> AccountState.Active
}

private fun ProfileDto.toDomain(accountId: String): Profile = Profile(
    ownerAccountId = AccountId(accountId),
    username = Username.parse(username)
        ?: throw AccountDataException(AccountDataFailureReason.InvalidResponse),
    displayName = displayName,
    avatarUrl = avatarUrl,
    defaultCurrency = defaultCurrency,
    locale = locale,
    version = ProfileVersion(version),
)

private fun CachedProfileRecord.toDomain(): Profile = Profile(
    ownerAccountId = AccountId(ownerAccountId),
    username = Username.parse(username)
        ?: throw AccountDataException(AccountDataFailureReason.InvalidResponse),
    displayName = displayName,
    avatarUrl = avatarUrl,
    defaultCurrency = defaultCurrency,
    locale = locale,
    version = ProfileVersion(version),
)

private fun Profile.toRecord(): CachedProfileRecord = CachedProfileRecord(
    ownerAccountId = ownerAccountId.value,
    username = username.value,
    displayName = displayName,
    avatarUrl = avatarUrl,
    defaultCurrency = defaultCurrency,
    locale = locale,
    version = version.value,
    updatedAt = "",
)

@Serializable
private data class BootstrapRequestDto(
    val locale: String,
    val defaultCurrency: String,
    val legalAcceptance: LegalAcceptanceDto,
)

@Serializable
private data class LegalAcceptanceDto(
    val termsVersion: String,
    val privacyVersion: String,
)

@Serializable
private data class BootstrapResponseDto(
    val account: AccountDto,
    val profile: ProfileDto? = null,
    val providers: List<String> = emptyList(),
    val bootstrapStatus: String,
)

@Serializable
private data class AccountDto(
    val id: String,
    val state: String,
)

@Serializable
private data class ProfileDto(
    val username: String,
    val displayName: String,
    val avatarUrl: String? = null,
    val defaultCurrency: String,
    val locale: String,
    val version: Long,
)

@Serializable
private data class UpdateProfileRequestDto(
    val displayName: String,
    val username: String,
    val defaultCurrency: String,
    val locale: String,
)

@Serializable
private data class DeleteAccountRequestDto(
    val confirmation: String,
)

@Serializable
private data class DeletionResponseDto(
    val state: String,
    val recoveryEndsAt: String? = null,
)

@Serializable
private data class AvatarIntentRequestDto(
    val contentType: String,
    val contentLength: Int,
)

@Serializable
private data class AvatarIntentResponseDto(
    val uploadUrl: String,
)

private const val MAX_AVATAR_BYTES = 5 * 1024 * 1024
private val ALLOWED_AVATAR_CONTENT_TYPES = setOf("image/jpeg", "image/png", "image/webp")
