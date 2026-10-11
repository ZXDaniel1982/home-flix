package com.homeflix.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "home-flix-settings")

interface SettingsRepository {
    val serverUrl: Flow<String?>

    suspend fun setServerUrl(url: String)

    /** The most recent release the user declined to install, if any. */
    val skippedVersionCode: Flow<Int?>

    suspend fun setSkippedVersionCode(code: Int)
}

class DataStoreSettingsRepository(private val dataStore: DataStore<Preferences>) : SettingsRepository {

    override val serverUrl: Flow<String?> = dataStore.data.map { preferences -> preferences[SERVER_URL] }

    override suspend fun setServerUrl(url: String) {
        dataStore.edit { preferences -> preferences[SERVER_URL] = url }
    }

    override val skippedVersionCode: Flow<Int?> =
        dataStore.data.map { preferences -> preferences[SKIPPED_VERSION_CODE] }

    override suspend fun setSkippedVersionCode(code: Int) {
        dataStore.edit { preferences -> preferences[SKIPPED_VERSION_CODE] = code }
    }

    companion object {
        private val SERVER_URL = stringPreferencesKey("server_url")
        private val SKIPPED_VERSION_CODE = intPreferencesKey("skipped_version_code")
    }
}
