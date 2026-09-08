package com.homeflix.app.data

import java.util.UUID
import kotlinx.coroutines.flow.first
import org.jellyfin.sdk.api.client.extensions.itemsApi
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ItemSortBy
import org.jellyfin.sdk.model.api.SortOrder
import org.jellyfin.sdk.model.api.request.GetItemsRequest

interface MovieRepository {
    suspend fun getMovies(startIndex: Int, limit: Int): List<BaseItemDto>
}

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
}
