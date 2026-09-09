@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
package com.homeflix.app

import com.homeflix.app.ui.screens.ServerUrlViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

class ServerUrlViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun save_addsSchemeAndApiSuffix() = runTest(mainDispatcherRule.testDispatcher) {
        val repo = FakeSettingsRepository()
        val viewModel = ServerUrlViewModel(repo)

        viewModel.onUrlChange("orangepi3b.local")
        viewModel.save()
        advanceUntilIdle()

        assertEquals("http://orangepi3b.local/api", repo.serverUrl.first())
        assertEquals(Unit, viewModel.saveEvents.first())
    }

    @Test
    fun save_keepsExistingApiSuffix() = runTest(mainDispatcherRule.testDispatcher) {
        val repo = FakeSettingsRepository()
        val viewModel = ServerUrlViewModel(repo)

        viewModel.onUrlChange("http://host/api/")
        viewModel.save()
        advanceUntilIdle()

        assertEquals("http://host/api", repo.serverUrl.first())
    }

    @Test
    fun save_blankUrlDoesNothing() = runTest(mainDispatcherRule.testDispatcher) {
        val repo = FakeSettingsRepository()
        val viewModel = ServerUrlViewModel(repo)

        viewModel.onUrlChange("   ")
        viewModel.save()
        advanceUntilIdle()

        assertNull(repo.serverUrl.first())
    }
}
