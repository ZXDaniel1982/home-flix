@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
package com.homeflix.app

import androidx.lifecycle.SavedStateHandle
import com.homeflix.app.data.PlaybackStream
import com.homeflix.app.navigation.Routes
import com.homeflix.app.ui.screens.PlayerViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.jellyfin.sdk.api.client.exception.InvalidStatusException
import org.junit.Assert.assertEquals
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
}
