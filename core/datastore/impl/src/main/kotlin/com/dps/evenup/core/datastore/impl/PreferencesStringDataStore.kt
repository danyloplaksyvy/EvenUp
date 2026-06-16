package com.dps.evenup.core.datastore.impl

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.dps.evenup.core.datastore.api.StringDataStore
import kotlinx.coroutines.flow.first

class PreferencesStringDataStore(
    private val dataStore: DataStore<Preferences>,
) : StringDataStore {
    override suspend fun read(key: String): String? {
        val preferenceKey = stringPreferencesKey(key)
        return dataStore.data.first()[preferenceKey]
    }

    override suspend fun write(
        key: String,
        value: String,
    ) {
        val preferenceKey = stringPreferencesKey(key)
        dataStore.edit { preferences ->
            preferences[preferenceKey] = value
        }
    }

    override suspend fun remove(key: String) {
        val preferenceKey = stringPreferencesKey(key)
        dataStore.edit { preferences ->
            preferences.remove(preferenceKey)
        }
    }
}
