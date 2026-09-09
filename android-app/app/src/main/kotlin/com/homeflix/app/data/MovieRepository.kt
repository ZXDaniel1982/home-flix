package com.homeflix.app.data

import java.util.UUID
import android.net.Uri
import kotlinx.coroutines.flow.first
import org.jellyfin.sdk.api.client.extensions.itemsApi
import org.jellyfin.sdk.api.client.extensions.mediaInfoApi
import org.jellyfin.sdk.api.client.extensions.userLibraryApi
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ImageType
import org.jellyfin.sdk.model.api.ItemSortBy
import org.jellyfin.sdk.model.api.PlaybackInfoDto
import org.jellyfin.sdk.model.api.SortOrder
import org.jellyfin.sdk.model.api.request.GetItemsRequest

interface MovieRepository {
    suspend fun getMovies(startIndex: Int, limit: Int): List<BaseItemDto>

    suspend fun getMovie(itemId: String): BaseItemDto

    suspend fun getStream(itemId: String): PlaybackStream

    suspend fun imageCredentials(): ImageCredentials
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
}
