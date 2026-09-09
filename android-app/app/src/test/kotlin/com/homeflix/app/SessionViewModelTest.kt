@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
package com.homeflix.app

import com.homeflix.app.data.Session
import com.homeflix.app.ui.SessionViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

class SessionViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun logout_clearsSessionAndEmitsEvent() = runTest(mainDispatcherRule.testDispatcher) {
        val repo = FakeSessionRepository()
        repo.save(Session("token", "user", "name"))
        val viewModel = SessionViewModel(repo)
        advanceUntilIdle()

        viewModel.logout()
        advanceUntilIdle()

        assertNull(repo.session.first())
        assertEquals(Unit, viewModel.logoutEvents.first())
    }
}
