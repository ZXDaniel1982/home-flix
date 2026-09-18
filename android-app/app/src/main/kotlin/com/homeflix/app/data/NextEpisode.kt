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

/**
 * Resolves the next episode when the current episode is [currentId].
 * Returns null when [currentId] is not present in [currentSeasonEpisodes].
 * Otherwise returns the next episode in the season, or [nextSeasonFirstEpisode]
 * (invoked lazily, only when the current episode is the season finale).
 *
 * Marked `inline` so [nextSeasonFirstEpisode] may invoke suspend repository work from a
 * suspend caller while this helper itself remains a pure, non-suspend function.
 */
internal inline fun resolveNextEpisode(
    currentSeasonEpisodes: List<BaseItemDto>,
    currentId: String,
    nextSeasonFirstEpisode: () -> BaseItemDto?
): BaseItemDto? {
    if (currentSeasonEpisodes.none { it.id.toString() == currentId }) return null
    return nextEpisodeInSeason(currentSeasonEpisodes, currentId) ?: nextSeasonFirstEpisode()
}
