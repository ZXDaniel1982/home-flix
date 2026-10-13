package com.homeflix.app

import com.homeflix.app.data.AuthService
import com.homeflix.app.data.InstallResult
import com.homeflix.app.data.Session
import com.homeflix.app.data.SessionRepository
import com.homeflix.app.data.SettingsRepository
import com.homeflix.app.data.UpdateInstaller
import com.homeflix.app.data.UpdateManifest
import com.homeflix.app.data.UpdateRepository
import java.io.OutputStream
import java.util.UUID
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import org.jellyfin.sdk.model.api.AuthenticationResult
import org.jellyfin.sdk.model.api.UserDto

class FakeSettingsRepository(
    initialUrl: String? = "http://host/api",
    initialSkippedVersion: Int? = null
) : SettingsRepository {
    private val _serverUrl = MutableStateFlow(initialUrl)
    override val serverUrl: Flow<String?> = _serverUrl
    override suspend fun setServerUrl(url: String) {
        _serverUrl.value = url
    }

    private val _skippedVersionCode = MutableStateFlow(initialSkippedVersion)
    override val skippedVersionCode: Flow<Int?> = _skippedVersionCode
    override suspend fun setSkippedVersionCode(code: Int) {
        _skippedVersionCode.value = code
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

fun updateManifest(
    versionCode: Int = 2,
    versionName: String = "0.2.0",
    releaseNotes: String? = null
): UpdateManifest = UpdateManifest(
    schemaVersion = 1,
    versionCode = versionCode,
    versionName = versionName,
    minSdk = 24,
    apkUrl = "/updates/android/home-flix-$versionName.apk",
    sha256 = "unused",
    size = 0L,
    releaseNotes = releaseNotes
)

class FakeUpdateRepository : UpdateRepository {
    var manifest: UpdateManifest = updateManifest()
    var manifestCalls: Int = 0

    override suspend fun fetchManifest(origin: String): UpdateManifest {
        manifestCalls += 1
        return manifest
    }

    override suspend fun download(
        apkUrl: String,
        size: Long,
        onProgress: (downloaded: Long, total: Long) -> Unit,
        sink: OutputStream
    ): String = "unused"
}

class FakeUpdateInstaller : UpdateInstaller {
    private val channel = Channel<InstallResult>(Channel.BUFFERED)
    override val results = channel.receiveAsFlow()
    var canInstall: Boolean = true

    override fun canInstallPackages(): Boolean = canInstall

    override suspend fun install(write: suspend (OutputStream) -> Unit) = Unit
}
