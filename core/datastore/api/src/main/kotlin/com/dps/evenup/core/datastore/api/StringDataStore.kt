package com.dps.evenup.core.datastore.api

interface StringDataStore {
    suspend fun read(key: String): String?

    suspend fun write(
        key: String,
        value: String,
    )

    suspend fun remove(key: String)
}
