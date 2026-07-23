package com.dps.evenup.core.database.impl

import android.content.Context
import androidx.room.Room

object DatabaseFactory {
    fun create(context: Context): RoomAccountCache {
        val database = Room.databaseBuilder(
            context.applicationContext,
            EvenUpDatabase::class.java,
            "evenup_account_cache.db",
        ).build()
        return RoomAccountCache(database.accountCacheDao())
    }
}
