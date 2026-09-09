@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
package com.homeflix.app

import com.homeflix.app.ui.screens.HomeViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.jellyfin.sdk.api.client.exception.InvalidStatusException
import org.jellyfin.sdk.model.api.BaseItemKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class HomeViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun load_success_mapsResumeItems() = runTest(mainDispatcherRule.testDispatcher) {
        val repo = FakeMovieRepository().apply {
            resumeItems = listOf(
                baseItem(
                    EPISODE_ID,
                    "Episode 1",
                    type = BaseItemKind.EPISODE,
                    imageTag = "tag",
                    userData = userData(playedPercentage = 42.5)
                )
            )
        }
        val viewModel = HomeViewModel(repo)

        viewModel.load()
        advanceUntilIdle()

        val state = viewModel.uiState.value as HomeViewModel.UiState.Success
        assertEquals(1, state.items.size)
        assertEquals("Episode 1", state.items[0].name)
        assertEquals(42.5f, state.items[0].playedPercentage)
    }

    @Test
    fun load_failure_setsError() = runTest(mainDispatcherRule.testDispatcher) {
        val repo = FakeMovieRepository().apply { error = RuntimeException("boom") }
        val viewModel = HomeViewModel(repo)

        viewModel.load()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value is HomeViewModel.UiState.Error)
    }

    @Test
    fun load_401_emitsUnauthorized() = runTest(mainDispatcherRule.testDispatcher) {
        val repo = FakeMovieRepository().apply { error = InvalidStatusException(401) }
        val viewModel = HomeViewModel(repo)

        viewModel.load()
        advanceUntilIdle()

        assertEquals(Unit, viewModel.unauthorizedEvents.first())
    }
}
