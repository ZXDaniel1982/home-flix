package com.homeflix.app

import com.homeflix.app.data.nextEpisodeInSeason
import com.homeflix.app.data.nextSeason
import org.jellyfin.sdk.model.api.BaseItemKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NextEpisodeTest {

    private val season1 = "00000000-0000-0000-0000-000000000101"
    private val season2 = "00000000-0000-0000-0000-000000000102"
    private val episode1 = "00000000-0000-0000-0000-000000000201"
    private val episode2 = "00000000-0000-0000-0000-000000000202"
    private val episode3 = "00000000-0000-0000-0000-000000000203"

    @Test
    fun nextEpisodeInSeason_returnsFollowingEpisode() {
        val episodes = listOf(
            baseItem(episode1, "One", type = BaseItemKind.EPISODE, indexNumber = 1),
            baseItem(episode2, "Two", type = BaseItemKind.EPISODE, indexNumber = 2),
            baseItem(episode3, "Three", type = BaseItemKind.EPISODE, indexNumber = 3)
        )

        assertEquals(episode2, nextEpisodeInSeason(episodes, episode1)?.id?.toString())
    }

    @Test
    fun nextEpisodeInSeason_returnsNullForLastEpisode() {
        val episodes = listOf(baseItem(episode1, "One", type = BaseItemKind.EPISODE, indexNumber = 1))

        assertNull(nextEpisodeInSeason(episodes, episode1))
    }

    @Test
    fun nextEpisodeInSeason_returnsNullWhenCurrentIsMissing() {
        val episodes = listOf(baseItem(episode1, "One", type = BaseItemKind.EPISODE, indexNumber = 1))

        assertNull(nextEpisodeInSeason(episodes, episode2))
    }

    @Test
    fun nextSeason_returnsFollowingSeason() {
        val seasons = listOf(
            baseItem(season1, "Season 1", type = BaseItemKind.SEASON, indexNumber = 1),
            baseItem(season2, "Season 2", type = BaseItemKind.SEASON, indexNumber = 2)
        )

        assertEquals(season2, nextSeason(seasons, season1)?.id?.toString())
    }

    @Test
    fun nextSeason_returnsNullForLastSeason() {
        val seasons = listOf(baseItem(season1, "Season 1", type = BaseItemKind.SEASON, indexNumber = 1))

        assertNull(nextSeason(seasons, season1))
    }
}
