package com.homeflix.app.data

import org.jellyfin.sdk.model.api.BaseItemDto

/**
 * Returns the episode immediately after [currentId] in [episodes] (which must be
 * sorted by IndexNumber), or null if [currentId] is absent or is the last episode.
 */
internal fun nextEpisodeInSeason(episodes: List<BaseItemDto>, currentId: String): BaseItemDto? {
    val index = episodes.indexOfFirst { it.id.toString() == currentId }
    if (index == -1) return null
    return episodes.getOrNull(index + 1)
}

/**
 * Returns the season immediately after [currentSeasonId] in [seasons] (which must be
 * sorted by IndexNumber), or null if [currentSeasonId] is absent or is the last season.
 */
internal fun nextSeason(seasons: List<BaseItemDto>, currentSeasonId: String): BaseItemDto? {
    val index = seasons.indexOfFirst { it.id.toString() == currentSeasonId }
    if (index == -1) return null
    return seasons.getOrNull(index + 1)
}
