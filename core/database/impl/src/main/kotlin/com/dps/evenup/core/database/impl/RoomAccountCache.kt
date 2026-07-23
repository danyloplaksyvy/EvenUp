package com.dps.evenup.core.database.impl

import com.dps.evenup.core.database.api.AccountProfileCache
import com.dps.evenup.core.database.api.AccountScopedCacheManager
import com.dps.evenup.core.database.api.CachedProfileRecord
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomAccountCache internal constructor(
    private val dao: AccountCacheDao,
) : AccountProfileCache, AccountScopedCacheManager {
    override fun observe(ownerAccountId: String): Flow<CachedProfileRecord?> =
        dao.observeProfile(ownerAccountId).map { it?.toRecord() }

    override suspend fun read(ownerAccountId: String): CachedProfileRecord? =
        dao.readProfile(ownerAccountId)?.toRecord()

    override suspend fun write(profile: CachedProfileRecord) {
        dao.writeProfile(profile.toEntity())
    }

    override suspend fun replaceActiveAccount(accountId: String) {
        dao.activate(accountId)
    }

    override suspend fun lock() {
        val current = dao.readSession()
        dao.writeSession(
            AccountCacheSessionEntity(
                activeAccountId = null,
                lastAccountId = current?.lastAccountId,
            ),
        )
    }

    override suspend fun activeAccountId(): String? = dao.readSession()?.activeAccountId

    override suspend fun lastAccountId(): String? = dao.readSession()?.lastAccountId

    override suspend fun clearAccount(accountId: String) {
        dao.deleteProfile(accountId)
        val current = dao.readSession()
        if (current?.activeAccountId == accountId || current?.lastAccountId == accountId) {
            dao.writeSession(AccountCacheSessionEntity(activeAccountId = null, lastAccountId = null))
        }
    }
}

private fun ProfileEntity.toRecord(): CachedProfileRecord = CachedProfileRecord(
    ownerAccountId = ownerAccountId,
    username = username,
    displayName = displayName,
    avatarUrl = avatarUrl,
    defaultCurrency = defaultCurrency,
    locale = locale,
    version = version,
    updatedAt = updatedAt,
)

private fun CachedProfileRecord.toEntity(): ProfileEntity = ProfileEntity(
    ownerAccountId = ownerAccountId,
    username = username,
    displayName = displayName,
    avatarUrl = avatarUrl,
    defaultCurrency = defaultCurrency,
    locale = locale,
    version = version,
    updatedAt = updatedAt,
)
