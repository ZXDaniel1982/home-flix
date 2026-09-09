package com.homeflix.app

import com.homeflix.app.data.AuthService
import com.homeflix.app.data.ImageCredentials
import com.homeflix.app.data.MovieRepository
import com.homeflix.app.data.PlaybackStream
import com.homeflix.app.data.Session
import com.homeflix.app.data.SessionRepository
import com.homeflix.app.data.SettingsRepository
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
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

class FakeSettingsRepository : SettingsRepository {
    private val _serverUrl = MutableStateFlow<String?>(null)
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
        error?.let { throw it }
        return episodes
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
    indexNumber: Int? = null,
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
        indexNumber?.let { put("IndexNumber", it) }
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
