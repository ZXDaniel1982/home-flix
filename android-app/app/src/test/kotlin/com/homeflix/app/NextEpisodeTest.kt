package com.homeflix.app

import com.homeflix.app.data.nextEpisodeInSeason
import com.homeflix.app.data.previousEpisodeInSeason
import org.jellyfin.sdk.model.api.BaseItemKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NextEpisodeTest {

    private val episode1 = "00000000-0000-0000-0000-000000000201"
    private val episode2 = "00000000-0000-0000-0000-000000000202"
    private val episode3 = "00000000-0000-0000-0000-000000000203"

    private val episodes = listOf(
        baseItem(episode1, "One", type = BaseItemKind.EPISODE, indexNumber = 1),
        baseItem(episode2, "Two", type = BaseItemKind.EPISODE, indexNumber = 2),
        baseItem(episode3, "Three", type = BaseItemKind.EPISODE, indexNumber = 3)
    )

    @Test
    fun nextEpisodeInSeason_returnsFollowingEpisode() {
        assertEquals(episode2, nextEpisodeInSeason(episodes, episode1)?.id?.toString())
    }

    @Test
    fun nextEpisodeInSeason_returnsNullForLastEpisode() {
        assertNull(nextEpisodeInSeason(episodes, episode3))
    }

    @Test
    fun nextEpisodeInSeason_returnsNullWhenCurrentIsMissing() {
        assertNull(nextEpisodeInSeason(episodes, "00000000-0000-0000-0000-000000000999"))
    }

    @Test
    fun previousEpisodeInSeason_returnsPrecedingEpisode() {
        assertEquals(episode2, previousEpisodeInSeason(episodes, episode3)?.id?.toString())
    }

    @Test
    fun previousEpisodeInSeason_returnsNullForFirstEpisode() {
        assertNull(previousEpisodeInSeason(episodes, episode1))
    }

    @Test
    fun previousEpisodeInSeason_returnsNullWhenCurrentIsMissing() {
        assertNull(previousEpisodeInSeason(episodes, "00000000-0000-0000-0000-000000000999"))
    }
}
