package com.dps.evenup.core.database.api

import kotlinx.coroutines.flow.Flow

data class CachedProfileRecord(
    val ownerAccountId: String,
    val username: String,
    val displayName: String,
    val avatarUrl: String?,
    val defaultCurrency: String,
    val locale: String,
    val version: Long,
    val updatedAt: String,
)

interface AccountProfileCache {
    fun observe(ownerAccountId: String): Flow<CachedProfileRecord?>

    suspend fun read(ownerAccountId: String): CachedProfileRecord?

    suspend fun write(profile: CachedProfileRecord)
}

interface AccountScopedCacheManager {
    suspend fun replaceActiveAccount(accountId: String)

    suspend fun lock()

    suspend fun activeAccountId(): String?

    suspend fun lastAccountId(): String?

    suspend fun clearAccount(accountId: String)
}
