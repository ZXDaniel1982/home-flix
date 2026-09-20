package com.homeflix.app.data

import org.jellyfin.sdk.model.api.BaseItemDto

/**
 * Returns the episode immediately before [currentId] in [episodes] (which must be
 * sorted by IndexNumber), or null if [currentId] is absent or is the first episode.
 */
internal fun previousEpisodeInSeason(episodes: List<BaseItemDto>, currentId: String): BaseItemDto? {
    val index = episodes.indexOfFirst { it.id.toString() == currentId }
    if (index == -1) return null
    return if (index == 0) null else episodes[index - 1]
}

/**
 * Returns the episode immediately after [currentId] in [episodes] (which must be
 * sorted by IndexNumber), or null if [currentId] is absent or is the last episode.
 */
internal fun nextEpisodeInSeason(episodes: List<BaseItemDto>, currentId: String): BaseItemDto? {
    val index = episodes.indexOfFirst { it.id.toString() == currentId }
    if (index == -1) return null
    return episodes.getOrNull(index + 1)
}
