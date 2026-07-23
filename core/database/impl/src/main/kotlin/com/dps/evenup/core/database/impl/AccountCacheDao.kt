package com.dps.evenup.core.database.impl

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
internal interface AccountCacheDao {
    @Query("SELECT * FROM account_profiles WHERE ownerAccountId = :ownerAccountId")
    fun observeProfile(ownerAccountId: String): Flow<ProfileEntity?>

    @Query("SELECT * FROM account_profiles WHERE ownerAccountId = :ownerAccountId")
    suspend fun readProfile(ownerAccountId: String): ProfileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun writeProfile(profile: ProfileEntity)

    @Query("DELETE FROM account_profiles WHERE ownerAccountId = :ownerAccountId")
    suspend fun deleteProfile(ownerAccountId: String)

    @Query("SELECT * FROM account_cache_session WHERE singletonId = 1")
    suspend fun readSession(): AccountCacheSessionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun writeSession(session: AccountCacheSessionEntity)

    @Transaction
    suspend fun activate(accountId: String) {
        val current = readSession()
        val previousAccountId = current?.lastAccountId
        if (previousAccountId != null && previousAccountId != accountId) {
            deleteProfile(previousAccountId)
        }
        writeSession(
            AccountCacheSessionEntity(
                activeAccountId = accountId,
                lastAccountId = accountId,
            ),
        )
    }
}
