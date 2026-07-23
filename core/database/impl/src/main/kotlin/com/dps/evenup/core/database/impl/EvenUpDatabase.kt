package com.dps.evenup.core.database.impl

import androidx.room.Database
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.RoomDatabase

@Entity(tableName = "account_profiles")
internal data class ProfileEntity(
    @PrimaryKey val ownerAccountId: String,
    val username: String,
    val displayName: String,
    val avatarUrl: String?,
    val defaultCurrency: String,
    val locale: String,
    val version: Long,
    val updatedAt: String,
)

@Entity(tableName = "account_cache_session")
internal data class AccountCacheSessionEntity(
    @PrimaryKey val singletonId: Int = 1,
    val activeAccountId: String?,
    val lastAccountId: String?,
)

@Database(
    entities = [
        ProfileEntity::class,
        AccountCacheSessionEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
internal abstract class EvenUpDatabase : RoomDatabase() {
    abstract fun accountCacheDao(): AccountCacheDao
}
