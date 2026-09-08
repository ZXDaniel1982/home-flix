package com.homeflix.app.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

data class Session(
    val accessToken: String,
    val userId: String,
    val username: String
)

interface SessionRepository {
    val session: Flow<Session?>

    suspend fun save(session: Session)

    suspend fun clear()
}

class DataStoreSessionRepository(private val dataStore: DataStore<Preferences>) : SessionRepository {

    override val session: Flow<Session?> = dataStore.data.map { preferences ->
        val token = preferences[ACCESS_TOKEN]
        val userId = preferences[USER_ID]
        val username = preferences[USERNAME]
        if (token != null && userId != null) {
            Session(accessToken = token, userId = userId, username = username.orEmpty())
        } else {
            null
        }
    }

    override suspend fun save(session: Session) {
        dataStore.edit { preferences ->
            preferences[ACCESS_TOKEN] = session.accessToken
            preferences[USER_ID] = session.userId
            preferences[USERNAME] = session.username
        }
    }

    override suspend fun clear() {
        dataStore.edit { preferences ->
            preferences.remove(ACCESS_TOKEN)
            preferences.remove(USER_ID)
            preferences.remove(USERNAME)
        }
    }

    companion object {
        private val ACCESS_TOKEN = stringPreferencesKey("access_token")
        private val USER_ID = stringPreferencesKey("user_id")
        private val USERNAME = stringPreferencesKey("username")
    }
}
