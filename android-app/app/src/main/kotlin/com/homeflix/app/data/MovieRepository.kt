package com.homeflix.app.data

import java.util.UUID
import android.net.Uri
import kotlinx.coroutines.flow.first
import org.jellyfin.sdk.api.client.extensions.itemsApi
import org.jellyfin.sdk.api.client.extensions.mediaInfoApi
import org.jellyfin.sdk.api.client.extensions.playStateApi
import org.jellyfin.sdk.api.client.extensions.userLibraryApi
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ImageType
import org.jellyfin.sdk.model.api.ItemSortBy
import org.jellyfin.sdk.model.api.PlayMethod
import org.jellyfin.sdk.model.api.PlaybackInfoDto
import org.jellyfin.sdk.model.api.PlaybackProgressInfo
import org.jellyfin.sdk.model.api.PlaybackStartInfo
import org.jellyfin.sdk.model.api.PlaybackStopInfo
import org.jellyfin.sdk.model.api.PlaybackOrder
import org.jellyfin.sdk.model.api.RepeatMode
import org.jellyfin.sdk.model.api.SortOrder
import org.jellyfin.sdk.model.api.request.GetItemsRequest

interface MovieRepository {
    suspend fun getMovies(startIndex: Int, limit: Int): List<BaseItemDto>

    suspend fun getMovie(itemId: String): BaseItemDto

    suspend fun getTvSeries(startIndex: Int, limit: Int): List<BaseItemDto>

    suspend fun getSeasons(seriesId: String): List<BaseItemDto>

    suspend fun getEpisodes(seasonId: String): List<BaseItemDto>

    suspend fun search(query: String, limit: Int): List<BaseItemDto>

    suspend fun getStream(itemId: String): PlaybackStream

    suspend fun imageCredentials(): ImageCredentials

    suspend fun reportPlaybackStarted(itemId: String, mediaSourceId: String, positionTicks: Long)

    suspend fun reportPlaybackProgress(itemId: String, mediaSourceId: String, positionTicks: Long, isPaused: Boolean)

    suspend fun reportPlaybackStopped(itemId: String, mediaSourceId: String, positionTicks: Long)
}

data class ImageCredentials(val baseUrl: String, val accessToken: String)

data class PlaybackStream(val url: String, val mediaSourceId: String)

fun buildImageUrl(
    baseUrl: String,
    accessToken: String,
    itemId: String,
    imageTag: String,
    imageType: ImageType
): String =
    "${baseUrl.trimEnd('/')}/Items/$itemId/Images/${imageType.serialName}" +
        "?tag=${Uri.encode(imageTag)}&api_key=${Uri.encode(accessToken)}"

fun buildStreamUrl(
    baseUrl: String,
    accessToken: String,
    itemId: String,
    mediaSourceId: String
): String =
    "${baseUrl.trimEnd('/')}/Videos/$itemId/stream" +
        "?static=true&MediaSourceId=${Uri.encode(mediaSourceId)}&api_key=${Uri.encode(accessToken)}"

class JellyfinMovieRepository(
    private val jellyfinProvider: JellyfinProvider,
    private val settingsRepository: SettingsRepository,
    private val sessionRepository: SessionRepository
) : MovieRepository {

    override suspend fun getMovies(startIndex: Int, limit: Int): List<BaseItemDto> {
        val baseUrl = settingsRepository.serverUrl.first().orEmpty()
        val session = sessionRepository.session.first()
            ?: throw IllegalStateException("Not authenticated")

        val api = jellyfinProvider.createApi(baseUrl, accessToken = session.accessToken)
        val response = api.itemsApi.getItems(
            GetItemsRequest(
                userId = UUID.fromString(session.userId),
                startIndex = startIndex,
                limit = limit,
                recursive = true,
                includeItemTypes = listOf(BaseItemKind.MOVIE),
                sortBy = listOf(ItemSortBy.SORT_NAME),
                sortOrder = listOf(SortOrder.ASCENDING)
            )
        )
        return response.content.items.orEmpty()
    }

    override suspend fun getMovie(itemId: String): BaseItemDto {
        val baseUrl = settingsRepository.serverUrl.first().orEmpty()
        val session = sessionRepository.session.first()
            ?: throw IllegalStateException("Not authenticated")

        val api = jellyfinProvider.createApi(baseUrl, accessToken = session.accessToken)
        return api.userLibraryApi.getItem(
            userId = UUID.fromString(session.userId),
            itemId = UUID.fromString(itemId)
        ).content
    }

    override suspend fun getTvSeries(startIndex: Int, limit: Int): List<BaseItemDto> {
        val baseUrl = settingsRepository.serverUrl.first().orEmpty()
        val session = sessionRepository.session.first()
            ?: throw IllegalStateException("Not authenticated")

        val api = jellyfinProvider.createApi(baseUrl, accessToken = session.accessToken)
        val response = api.itemsApi.getItems(
            GetItemsRequest(
                userId = UUID.fromString(session.userId),
                startIndex = startIndex,
                limit = limit,
                recursive = true,
                includeItemTypes = listOf(BaseItemKind.SERIES),
                sortBy = listOf(ItemSortBy.SORT_NAME),
                sortOrder = listOf(SortOrder.ASCENDING)
            )
        )
        return response.content.items.orEmpty()
    }

    override suspend fun getSeasons(seriesId: String): List<BaseItemDto> {
        val baseUrl = settingsRepository.serverUrl.first().orEmpty()
        val session = sessionRepository.session.first()
            ?: throw IllegalStateException("Not authenticated")

        val api = jellyfinProvider.createApi(baseUrl, accessToken = session.accessToken)
        val response = api.itemsApi.getItems(
            GetItemsRequest(
                userId = UUID.fromString(session.userId),
                parentId = UUID.fromString(seriesId),
                includeItemTypes = listOf(BaseItemKind.SEASON),
                sortBy = listOf(ItemSortBy.INDEX_NUMBER),
                limit = 100
            )
        )
        return response.content.items.orEmpty()
    }

    override suspend fun getEpisodes(seasonId: String): List<BaseItemDto> {
        val baseUrl = settingsRepository.serverUrl.first().orEmpty()
        val session = sessionRepository.session.first()
            ?: throw IllegalStateException("Not authenticated")

        val api = jellyfinProvider.createApi(baseUrl, accessToken = session.accessToken)
        val response = api.itemsApi.getItems(
            GetItemsRequest(
                userId = UUID.fromString(session.userId),
                parentId = UUID.fromString(seasonId),
                includeItemTypes = listOf(BaseItemKind.EPISODE),
                sortBy = listOf(ItemSortBy.INDEX_NUMBER),
                limit = 500
            )
        )
        return response.content.items.orEmpty()
    }

    override suspend fun search(query: String, limit: Int): List<BaseItemDto> {
        val baseUrl = settingsRepository.serverUrl.first().orEmpty()
        val session = sessionRepository.session.first()
            ?: throw IllegalStateException("Not authenticated")

        val api = jellyfinProvider.createApi(baseUrl, accessToken = session.accessToken)
        val response = api.itemsApi.getItems(
            GetItemsRequest(
                userId = UUID.fromString(session.userId),
                searchTerm = query,
                recursive = true,
                includeItemTypes = listOf(BaseItemKind.MOVIE, BaseItemKind.SERIES),
                sortBy = listOf(ItemSortBy.SORT_NAME),
                limit = limit
            )
        )
        return response.content.items.orEmpty()
    }

    override suspend fun imageCredentials(): ImageCredentials {
        val baseUrl = settingsRepository.serverUrl.first().orEmpty()
        val session = sessionRepository.session.first()
            ?: throw IllegalStateException("Not authenticated")

        return ImageCredentials(baseUrl = baseUrl, accessToken = session.accessToken)
    }

    override suspend fun getStream(itemId: String): PlaybackStream {
        val baseUrl = settingsRepository.serverUrl.first().orEmpty()
        val session = sessionRepository.session.first()
            ?: throw IllegalStateException("Not authenticated")

        val api = jellyfinProvider.createApi(baseUrl, accessToken = session.accessToken)
        val response = api.mediaInfoApi.getPostedPlaybackInfo(
            UUID.fromString(itemId),
            PlaybackInfoDto()
        )
        val sources = response.content.mediaSources.orEmpty()
        val source = sources.firstOrNull { it.supportsDirectPlay } ?: sources.firstOrNull()
            ?: throw IllegalStateException("No playable media source")
        val mediaSourceId = source.id
            ?: throw IllegalStateException("No playable media source")

        return PlaybackStream(
            url = buildStreamUrl(baseUrl, session.accessToken, itemId, mediaSourceId),
            mediaSourceId = mediaSourceId
        )
    }

    override suspend fun reportPlaybackStarted(itemId: String, mediaSourceId: String, positionTicks: Long) {
        val api = authenticatedApi()
        api.playStateApi.reportPlaybackStart(
            PlaybackStartInfo(
                itemId = UUID.fromString(itemId),
                mediaSourceId = mediaSourceId,
                positionTicks = positionTicks,
                playMethod = PlayMethod.DIRECT_PLAY,
                canSeek = true,
                isPaused = false,
                isMuted = false,
                repeatMode = RepeatMode.REPEAT_NONE,
                playbackOrder = PlaybackOrder.DEFAULT
            )
        )
    }

    override suspend fun reportPlaybackProgress(
        itemId: String,
        mediaSourceId: String,
        positionTicks: Long,
        isPaused: Boolean
    ) {
        val api = authenticatedApi()
        api.playStateApi.reportPlaybackProgress(
            PlaybackProgressInfo(
                itemId = UUID.fromString(itemId),
                mediaSourceId = mediaSourceId,
                positionTicks = positionTicks,
                playMethod = PlayMethod.DIRECT_PLAY,
                canSeek = true,
                isPaused = isPaused,
                isMuted = false,
                repeatMode = RepeatMode.REPEAT_NONE,
                playbackOrder = PlaybackOrder.DEFAULT
            )
        )
    }

    override suspend fun reportPlaybackStopped(itemId: String, mediaSourceId: String, positionTicks: Long) {
        val api = authenticatedApi()
        api.playStateApi.reportPlaybackStopped(
            PlaybackStopInfo(
                itemId = UUID.fromString(itemId),
                mediaSourceId = mediaSourceId,
                positionTicks = positionTicks,
                failed = false
            )
        )
    }

    private suspend fun authenticatedApi() = jellyfinProvider.createApi(
        baseUrl = settingsRepository.serverUrl.first().orEmpty(),
        accessToken = sessionRepository.session.first()?.accessToken
            ?: throw IllegalStateException("Not authenticated")
    )
}
