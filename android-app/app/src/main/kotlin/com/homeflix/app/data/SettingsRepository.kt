package com.homeflix.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "home-flix-settings")

interface SettingsRepository {
    val serverUrl: Flow<String?>

    suspend fun setServerUrl(url: String)
}

class DataStoreSettingsRepository(private val dataStore: DataStore<Preferences>) : SettingsRepository {

    override val serverUrl: Flow<String?> = dataStore.data.map { preferences -> preferences[SERVER_URL] }

    override suspend fun setServerUrl(url: String) {
        dataStore.edit { preferences -> preferences[SERVER_URL] = url }
    }

    companion object {
        private val SERVER_URL = stringPreferencesKey("server_url")
    }
}
