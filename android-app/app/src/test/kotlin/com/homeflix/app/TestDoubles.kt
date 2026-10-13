package com.homeflix.app

import com.homeflix.app.data.AdjacentEpisodes
import com.homeflix.app.data.AuthService
import com.homeflix.app.data.HttpBody
import com.homeflix.app.data.HttpTransport
import com.homeflix.app.data.ImageCredentials
import com.homeflix.app.data.InstallResult
import com.homeflix.app.data.MovieRepository
import com.homeflix.app.data.PlaybackStream
import com.homeflix.app.data.Session
import com.homeflix.app.data.SessionRepository
import com.homeflix.app.data.SettingsRepository
import com.homeflix.app.data.UpdateInstaller
import com.homeflix.app.data.UpdateManifest
import com.homeflix.app.data.UpdateRepository
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.security.MessageDigest
import java.util.UUID
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import org.jellyfin.sdk.model.api.AuthenticationResult
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.UserItemDataDto

class FakeSettingsRepository(
    initialUrl: String? = null,
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
    var result: AuthenticationResult = AuthenticationResult()
    var error: Exception? = null

    override suspend fun authenticate(
        baseUrl: String,
        username: String,
        password: String
    ): AuthenticationResult {
        error?.let { throw it }
        return result
    }
}

class FakeMovieRepository : MovieRepository {
    var movies: List<BaseItemDto> = emptyList()
    var movie: BaseItemDto? = null
    var stream: PlaybackStream? = null
    var tvSeries: List<BaseItemDto> = emptyList()
    var seasons: List<BaseItemDto> = emptyList()
    var episodes: List<BaseItemDto> = emptyList()
    var adjacentEpisodes: AdjacentEpisodes? = null
    var adjacentEpisodesError: Exception? = null
    var episodesError: Exception? = null
    var episodesCalls: Int = 0
    var searchResults: List<BaseItemDto> = emptyList()
    var resumeItems: List<BaseItemDto> = emptyList()
    var credentials: ImageCredentials = ImageCredentials("http://example/api", "token")
    var error: Exception? = null
    val searchQueries = mutableListOf<String>()

    override suspend fun getMovies(startIndex: Int, limit: Int): List<BaseItemDto> {
        error?.let { throw it }
        return movies
    }

    override suspend fun getMovie(itemId: String): BaseItemDto {
        error?.let { throw it }
        return movie ?: throw IllegalStateException("No movie set")
    }

    override suspend fun getTvSeries(startIndex: Int, limit: Int): List<BaseItemDto> {
        error?.let { throw it }
        return tvSeries
    }

    override suspend fun getSeasons(seriesId: String): List<BaseItemDto> {
        error?.let { throw it }
        return seasons
    }

    override suspend fun getEpisodes(seasonId: String): List<BaseItemDto> {
        episodesCalls += 1
        episodesError?.let { throw it }
        error?.let { throw it }
        return episodes
    }

    override suspend fun getAdjacentEpisodes(itemId: String): AdjacentEpisodes? {
        adjacentEpisodesError?.let { throw it }
        error?.let { throw it }
        return adjacentEpisodes
    }

    override suspend fun search(query: String, limit: Int): List<BaseItemDto> {
        error?.let { throw it }
        searchQueries.add(query)
        return searchResults
    }

    override suspend fun getResumeItems(limit: Int): List<BaseItemDto> {
        error?.let { throw it }
        return resumeItems
    }

    override suspend fun getStream(itemId: String): PlaybackStream {
        error?.let { throw it }
        return stream ?: throw IllegalStateException("No stream set")
    }

    override suspend fun imageCredentials(): ImageCredentials = credentials

    override suspend fun reportPlaybackStarted(itemId: String, mediaSourceId: String, positionTicks: Long) = Unit

    override suspend fun reportPlaybackProgress(
        itemId: String,
        mediaSourceId: String,
        positionTicks: Long,
        isPaused: Boolean
    ) = Unit

    override suspend fun reportPlaybackStopped(itemId: String, mediaSourceId: String, positionTicks: Long) = Unit
}

private val fixtureJson = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
}

const val MOVIE_ID = "00000000-0000-0000-0000-000000000001"
const val SERIES_ID = "00000000-0000-0000-0000-000000000002"
const val EPISODE_ID = "00000000-0000-0000-0000-000000000003"
const val SEASON_ID = "00000000-0000-0000-0000-000000000030"

fun authResult(token: String, userId: UUID, username: String? = null): AuthenticationResult {
    val obj = buildJsonObject {
        put("AccessToken", token)
        putJsonObject("User") {
            put("Id", userId.toString())
            username?.let { put("Name", it) }
            put("HasPassword", false)
            put("HasConfiguredPassword", false)
            put("HasConfiguredEasyPassword", false)
        }
    }
    return fixtureJson.decodeFromJsonElement(AuthenticationResult.serializer(), obj)
}

fun baseItem(
    id: String,
    name: String,
    type: BaseItemKind = BaseItemKind.MOVIE,
    imageTag: String? = null,
    seriesId: String? = null,
    seasonId: String? = null,
    indexNumber: Int? = null,
    parentIndexNumber: Int? = null,
    runTimeTicks: Long? = null,
    productionYear: Int? = null,
    overview: String? = null,
    userData: UserItemDataDto? = null
): BaseItemDto {
    val obj = buildJsonObject {
        put("Id", id)
        put("Name", name)
        put("Type", fixtureJson.encodeToString(BaseItemKind.serializer(), type).trim('"'))
        imageTag?.let { putJsonObject("ImageTags") { put("Primary", it) } }
        seriesId?.let { put("SeriesId", it) }
        seasonId?.let { put("SeasonId", it) }
        indexNumber?.let { put("IndexNumber", it) }
        parentIndexNumber?.let { put("ParentIndexNumber", it) }
        runTimeTicks?.let { put("RunTimeTicks", it) }
        productionYear?.let { put("ProductionYear", it) }
        overview?.let { put("Overview", it) }
        userData?.let { put("UserData", fixtureJson.encodeToJsonElement(UserItemDataDto.serializer(), it)) }
    }
    return fixtureJson.decodeFromJsonElement(BaseItemDto.serializer(), obj)
}

fun userData(playbackPositionTicks: Long = 0, playedPercentage: Double? = null): UserItemDataDto {
    val obj = buildJsonObject {
        put("PlaybackPositionTicks", playbackPositionTicks)
        put("PlayCount", 0)
        put("IsFavorite", false)
        put("Played", false)
        put("Key", "key")
        put("ItemId", "00000000-0000-0000-0000-000000000000")
        playedPercentage?.let { put("PlayedPercentage", it) }
    }
    return fixtureJson.decodeFromJsonElement(UserItemDataDto.serializer(), obj)
}

class FakeHttpTransport : HttpTransport {
    var text: String = ""
    var textError: Exception? = null
    var streamBytes: ByteArray = ByteArray(0)
    var streamError: Exception? = null
    val textUrls = mutableListOf<String>()
    val streamUrls = mutableListOf<String>()

    override fun getText(url: String): String {
        textUrls.add(url)
        textError?.let { throw it }
        return text
    }

    override fun openStream(url: String): HttpBody {
        streamUrls.add(url)
        streamError?.let { throw it }
        return HttpBody(ByteArrayInputStream(streamBytes), streamBytes.size.toLong())
    }
}

fun sha256Hex(bytes: ByteArray): String =
    MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

class FakeUpdateInstaller : UpdateInstaller {
    private val channel = Channel<InstallResult>(Channel.BUFFERED)
    override val results: Flow<InstallResult> = channel.receiveAsFlow()

    var canInstall: Boolean = true
    var result: InstallResult = InstallResult.Success
    var installCalls: Int = 0
    var committed: Boolean = false
    var writtenBytes: ByteArray? = null

    override fun canInstallPackages(): Boolean = canInstall

    override suspend fun install(write: suspend (OutputStream) -> Unit) {
        installCalls += 1
        val sink = ByteArrayOutputStream()
        // Mirrors the real installer: a throwing write aborts the session and
        // produces no result.
        write(sink)
        writtenBytes = sink.toByteArray()
        committed = true
        channel.send(result)
    }
}

fun updateManifest(
    versionCode: Int = 2,
    versionName: String = "0.2.0",
    minSdk: Int = 24,
    apkUrl: String = "/updates/android/home-flix-$versionName.apk",
    sha256: String = "unused",
    size: Long = 0L,
    releaseNotes: String? = null
): UpdateManifest = UpdateManifest(
    schemaVersion = 1,
    versionCode = versionCode,
    versionName = versionName,
    minSdk = minSdk,
    apkUrl = apkUrl,
    sha256 = sha256,
    size = size,
    releaseNotes = releaseNotes
)

class FakeUpdateRepository : UpdateRepository {
    var manifest: UpdateManifest = updateManifest()
    var manifestError: Exception? = null
    var downloadError: Exception? = null
    var downloadedBytes: ByteArray = ByteArray(0)
    var manifestCalls: Int = 0
    var manifestOrigins = mutableListOf<String>()
    var downloadUrls = mutableListOf<String>()

    override suspend fun fetchManifest(origin: String): UpdateManifest {
        manifestCalls += 1
        manifestOrigins.add(origin)
        manifestError?.let { throw it }
        return manifest
    }

    override suspend fun download(
        apkUrl: String,
        size: Long,
        onProgress: (downloaded: Long, total: Long) -> Unit,
        sink: OutputStream
    ): String {
        downloadUrls.add(apkUrl)
        downloadError?.let { throw it }
        sink.write(downloadedBytes)
        onProgress(downloadedBytes.size.toLong(), size)
        return sha256Hex(downloadedBytes)
    }
}
