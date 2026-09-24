package com.homeflix.app

import com.homeflix.app.data.buildSeasonEpisodes
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.jellyfin.sdk.model.api.BaseItemKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SeasonEpisodesTest {

    private val season1 = "00000000-0000-0000-0000-000000000101"
    private val season2 = "00000000-0000-0000-0000-000000000102"
    private val episode1 = "00000000-0000-0000-0000-000000000201"

    @Test
    fun buildSeasonEpisodes_groupsEpisodesInOrder() = runTest {
        val seasons = listOf(
            baseItem(season1, "Season 1", type = BaseItemKind.SEASON),
            baseItem(season2, "Season 2", type = BaseItemKind.SEASON)
        )

        val result = buildSeasonEpisodes(seasons) { seasonId ->
            if (seasonId == season1) {
                listOf(baseItem(episode1, "S1E1", type = BaseItemKind.EPISODE))
            } else {
                emptyList()
            }
        }

        assertEquals(listOf(season1, season2), result.map { it.season.id.toString() })
        assertEquals(listOf(episode1), result[0].episodes.map { it.id.toString() })
        assertTrue(result[1].episodes.isEmpty())
    }

    @Test
    fun buildSeasonEpisodes_isolatesSeasonFailure() = runTest {
        val seasons = listOf(
            baseItem(season1, "Season 1", type = BaseItemKind.SEASON),
            baseItem(season2, "Season 2", type = BaseItemKind.SEASON)
        )

        val result = buildSeasonEpisodes(seasons) { seasonId ->
            if (seasonId == season1) throw RuntimeException("boom")
            listOf(baseItem(episode1, "S2E1", type = BaseItemKind.EPISODE))
        }

        assertTrue(result[0].episodes.isEmpty())
        assertEquals(listOf(episode1), result[1].episodes.map { it.id.toString() })
    }

    @Test
    fun buildSeasonEpisodes_rethrowsCancellation() = runTest {
        val seasons = listOf(baseItem(season1, "Season 1", type = BaseItemKind.SEASON))

        var thrown = false
        try {
            buildSeasonEpisodes(seasons) { throw CancellationException("stop") }
        } catch (_: CancellationException) {
            thrown = true
        }

        assertTrue(thrown)
    }
}
