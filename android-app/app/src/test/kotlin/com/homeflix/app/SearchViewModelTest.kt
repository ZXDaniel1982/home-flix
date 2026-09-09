@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
package com.homeflix.app

import com.homeflix.app.ui.screens.SearchViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.jellyfin.sdk.api.client.exception.InvalidStatusException
import org.jellyfin.sdk.model.api.BaseItemKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class SearchViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun clearingQuery_returnsToIdle() = runTest(mainDispatcherRule.testDispatcher) {
        val repo = FakeMovieRepository().apply {
            searchResults = listOf(baseItem(MOVIE_ID, "Movie"))
        }
        val viewModel = SearchViewModel(repo)

        viewModel.onQueryChange("movie")
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value is SearchViewModel.UiState.Success)

        viewModel.onQueryChange("")
        advanceUntilIdle()
        assertEquals(SearchViewModel.UiState.Idle, viewModel.uiState.value)
    }

    @Test
    fun query_runsSearchAfterDebounce() = runTest(mainDispatcherRule.testDispatcher) {
        val repo = FakeMovieRepository().apply {
            searchResults = listOf(baseItem(SERIES_ID, "Show", type = BaseItemKind.SERIES))
        }
        val viewModel = SearchViewModel(repo)

        viewModel.onQueryChange("show")
        advanceUntilIdle()

        assertEquals(listOf("show"), repo.searchQueries)
        val state = viewModel.uiState.value as SearchViewModel.UiState.Success
        assertTrue(state.results[0].isSeries)
    }

    @Test
    fun rapidQueries_onlyLatestRuns() = runTest(mainDispatcherRule.testDispatcher) {
        val repo = FakeMovieRepository()
        val viewModel = SearchViewModel(repo)

        viewModel.onQueryChange("a")
        viewModel.onQueryChange("ab")
        viewModel.onQueryChange("abc")
        advanceUntilIdle()

        assertEquals(listOf("abc"), repo.searchQueries)
    }

    @Test
    fun query_401_emitsUnauthorized() = runTest(mainDispatcherRule.testDispatcher) {
        val repo = FakeMovieRepository().apply { error = InvalidStatusException(401) }
        val viewModel = SearchViewModel(repo)

        viewModel.onQueryChange("show")
        advanceUntilIdle()

        assertEquals(Unit, viewModel.unauthorizedEvents.first())
    }
}
