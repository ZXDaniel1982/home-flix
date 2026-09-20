@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
package com.homeflix.app

import androidx.lifecycle.SavedStateHandle
import com.homeflix.app.data.AdjacentEpisodes
import com.homeflix.app.data.PlaybackStream
import com.homeflix.app.navigation.Routes
import com.homeflix.app.ui.screens.PlayerViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.jellyfin.sdk.api.client.exception.InvalidStatusException
import org.jellyfin.sdk.model.api.BaseItemKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class PlayerViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private fun createViewModel(
        repo: FakeMovieRepository,
        movieId: String? = "m1"
    ) = PlayerViewModel(
        movieRepository = repo,
        applicationScope = CoroutineScope(Dispatchers.Unconfined),
        savedStateHandle = SavedStateHandle(movieId?.let { mapOf(Routes.ARG_MOVIE_ID to it) } ?: emptyMap())
    )

    @Test
    fun load_success_setsStreamAndResumeTicks() = runTest(mainDispatcherRule.testDispatcher) {
        val repo = FakeMovieRepository().apply {
            stream = PlaybackStream("http://host/api/Videos/m1/stream?static=true", "ms1")
            movie = baseItem(MOVIE_ID, "Movie", userData = userData(playbackPositionTicks = 12345))
        }
        val viewModel = createViewModel(repo)
        advanceUntilIdle()

        val state = viewModel.uiState.value as PlayerViewModel.UiState.Success
        assertEquals("http://host/api/Videos/m1/stream?static=true", state.stream.url)
        assertEquals("ms1", state.stream.mediaSourceId)
        assertEquals(12345L, state.resumeTicks)
    }

    @Test
    fun load_401_emitsUnauthorized() = runTest(mainDispatcherRule.testDispatcher) {
        val repo = FakeMovieRepository().apply { error = InvalidStatusException(401) }
        val viewModel = createViewModel(repo)
        advanceUntilIdle()

        assertEquals(Unit, viewModel.unauthorizedEvents.first())
    }

    @Test
    fun load_failure_setsError() = runTest(mainDispatcherRule.testDispatcher) {
        val repo = FakeMovieRepository().apply { error = RuntimeException("boom") }
        val viewModel = createViewModel(repo)
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value is PlayerViewModel.UiState.Error)
    }

    @Test
    fun load_emptyId_setsError() = runTest(mainDispatcherRule.testDispatcher) {
        val viewModel = createViewModel(FakeMovieRepository(), movieId = null)
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value is PlayerViewModel.UiState.Error)
    }

    @Test
    fun load_episodeWithNeighbors_setsBoth() = runTest(mainDispatcherRule.testDispatcher) {
        val previousId = "00000000-0000-0000-0000-000000000004"
        val nextId = "00000000-0000-0000-0000-000000000005"
        val repo = FakeMovieRepository().apply {
            stream = PlaybackStream("http://host/stream", "ms1")
            movie = baseItem(EPISODE_ID, "Episode 2", type = BaseItemKind.EPISODE, seriesId = SERIES_ID)
            adjacentEpisodes = AdjacentEpisodes(
                previous = baseItem(previousId, "Episode 1", type = BaseItemKind.EPISODE, seriesId = SERIES_ID),
                next = baseItem(nextId, "Episode 3", type = BaseItemKind.EPISODE, seriesId = SERIES_ID)
            )
        }
        val viewModel = createViewModel(repo, movieId = EPISODE_ID)
        advanceUntilIdle()

        val state = viewModel.uiState.value as PlayerViewModel.UiState.Success
        assertEquals(true, state.isEpisode)
        assertEquals(previousId, state.previousEpisode?.id)
        assertEquals(nextId, state.nextEpisode?.id)
    }

    @Test
    fun load_firstEpisodeOfSeason_hasNoPrevious() = runTest(mainDispatcherRule.testDispatcher) {
        val nextId = "00000000-0000-0000-0000-000000000005"
        val repo = FakeMovieRepository().apply {
            stream = PlaybackStream("http://host/stream", "ms1")
            movie = baseItem(EPISODE_ID, "Episode 1", type = BaseItemKind.EPISODE, seriesId = SERIES_ID)
            adjacentEpisodes = AdjacentEpisodes(
                previous = null,
                next = baseItem(nextId, "Episode 2", type = BaseItemKind.EPISODE, seriesId = SERIES_ID)
            )
        }
        val viewModel = createViewModel(repo, movieId = EPISODE_ID)
        advanceUntilIdle()

        val state = viewModel.uiState.value as PlayerViewModel.UiState.Success
        assertNull(state.previousEpisode)
        assertEquals(nextId, state.nextEpisode?.id)
    }

    @Test
    fun load_lastEpisodeOfSeason_hasNoNext() = runTest(mainDispatcherRule.testDispatcher) {
        val previousId = "00000000-0000-0000-0000-000000000004"
        val repo = FakeMovieRepository().apply {
            stream = PlaybackStream("http://host/stream", "ms1")
            movie = baseItem(EPISODE_ID, "Finale", type = BaseItemKind.EPISODE, seriesId = SERIES_ID)
            adjacentEpisodes = AdjacentEpisodes(
                previous = baseItem(previousId, "Episode 1", type = BaseItemKind.EPISODE, seriesId = SERIES_ID),
                next = null
            )
        }
        val viewModel = createViewModel(repo, movieId = EPISODE_ID)
        advanceUntilIdle()

        val state = viewModel.uiState.value as PlayerViewModel.UiState.Success
        assertEquals(previousId, state.previousEpisode?.id)
        assertNull(state.nextEpisode)
    }

    @Test
    fun load_movie_isNotEpisode() = runTest(mainDispatcherRule.testDispatcher) {
        val repo = FakeMovieRepository().apply {
            stream = PlaybackStream("http://host/stream", "ms1")
            movie = baseItem(MOVIE_ID, "Movie")
            adjacentEpisodes = AdjacentEpisodes(
                previous = baseItem("00000000-0000-0000-0000-000000000004", "Ignored"),
                next = baseItem("00000000-0000-0000-0000-000000000005", "Ignored")
            )
        }
        val viewModel = createViewModel(repo)
        advanceUntilIdle()

        val state = viewModel.uiState.value as PlayerViewModel.UiState.Success
        assertEquals(false, state.isEpisode)
        assertNull(state.previousEpisode)
        assertNull(state.nextEpisode)
    }

    @Test
    fun load_neighborLookupFailure_episodeHasNoNeighbors() = runTest(mainDispatcherRule.testDispatcher) {
        val repo = FakeMovieRepository().apply {
            stream = PlaybackStream("http://host/stream", "ms1")
            movie = baseItem(EPISODE_ID, "Episode 1", type = BaseItemKind.EPISODE, seriesId = SERIES_ID)
            adjacentEpisodesError = RuntimeException("boom")
        }
        val viewModel = createViewModel(repo, movieId = EPISODE_ID)
        advanceUntilIdle()

        val state = viewModel.uiState.value as PlayerViewModel.UiState.Success
        assertEquals(true, state.isEpisode)
        assertNull(state.previousEpisode)
        assertNull(state.nextEpisode)
    }
}
