package com.homeflix.app

import com.homeflix.app.data.AuthService
import com.homeflix.app.data.Session
import com.homeflix.app.data.SessionRepository
import com.homeflix.app.data.SettingsRepository
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import org.jellyfin.sdk.model.api.AuthenticationResult
import org.jellyfin.sdk.model.api.UserDto

class FakeSettingsRepository(initialUrl: String? = "http://host/api") : SettingsRepository {
    private val _serverUrl = MutableStateFlow(initialUrl)
    override val serverUrl: Flow<String?> = _serverUrl
    override suspend fun setServerUrl(url: String) {
        _serverUrl.value = url
    }
}

class FakeSessionRepository : SessionRepository {
    private val _session = MutableStateFlow<Session?>(null)
    override val session: Flow<Session?> = _session
    override suspend fun save(session: Session) {
        _session.value = session
    }

    override suspend fun clear() {
        _session.value = null
    }
}

class FakeAuthService : AuthService {
    var result: AuthenticationResult? = null
    var error: Exception? = null

    override suspend fun authenticate(
        baseUrl: String,
        username: String,
        password: String
    ): AuthenticationResult {
        error?.let { throw it }
        return result ?: AuthenticationResult()
    }
}

fun authResult(token: String): AuthenticationResult = AuthenticationResult(
    accessToken = token,
    user = UserDto(
        id = UUID.fromString("11111111-1111-1111-1111-111111111111"),
        name = "user",
        hasPassword = false,
        hasConfiguredPassword = false,
        hasConfiguredEasyPassword = false
    )
)
