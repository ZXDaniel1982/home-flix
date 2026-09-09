@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
package com.homeflix.app

import com.homeflix.app.data.ImageCredentials
import com.homeflix.app.ui.screens.MoviesViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.jellyfin.sdk.api.client.exception.InvalidStatusException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class MoviesViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun load_success_mapsMoviesWithImages() = runTest(mainDispatcherRule.testDispatcher) {
        val repo = FakeMovieRepository().apply {
            movies = listOf(baseItem(MOVIE_ID, "Movie 1", imageTag = "tag1"))
            credentials = ImageCredentials("http://host/api", "token")
        }
        val viewModel = MoviesViewModel(repo)
        advanceUntilIdle()

        val state = viewModel.uiState.value as MoviesViewModel.UiState.Success
        assertEquals(1, state.movies.size)
        assertEquals("Movie 1", state.movies[0].name)
        assertEquals("http://host/api/Items/$MOVIE_ID/Images/Primary?tag=tag1&api_key=token", state.movies[0].imageUrl)
    }

    @Test
    fun load_401_emitsUnauthorized() = runTest(mainDispatcherRule.testDispatcher) {
        val repo = FakeMovieRepository().apply { error = InvalidStatusException(401) }
        val viewModel = MoviesViewModel(repo)
        advanceUntilIdle()

        assertEquals(Unit, viewModel.unauthorizedEvents.first())
    }

    @Test
    fun load_failure_setsError() = runTest(mainDispatcherRule.testDispatcher) {
        val repo = FakeMovieRepository().apply { error = RuntimeException("boom") }
        val viewModel = MoviesViewModel(repo)
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value is MoviesViewModel.UiState.Error)
    }
}
