@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
package com.homeflix.app

import com.homeflix.app.ui.screens.LoginViewModel
import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.jellyfin.sdk.api.client.exception.InvalidStatusException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

class LoginViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun login_success_savesSessionAndEmitsEvent() = runTest(mainDispatcherRule.testDispatcher) {
        val settings = FakeSettingsRepository()
        settings.setServerUrl("http://host/api")
        val session = FakeSessionRepository()
        val auth = FakeAuthService().apply {
            result = authResult("token", UUID.fromString("11111111-1111-1111-1111-111111111111"), "user")
        }
        val viewModel = LoginViewModel(settings, session, auth)
        advanceUntilIdle()

        viewModel.onUsernameChange("user")
        viewModel.onPasswordChange("pass")
        viewModel.login()
        advanceUntilIdle()

        assertEquals("token", session.session.first()?.accessToken)
        assertEquals("11111111-1111-1111-1111-111111111111", session.session.first()?.userId)
        assertEquals(Unit, viewModel.loginEvents.first())
        assertEquals(false, viewModel.isLoading.first())
    }

    @Test
    fun login_401_setsInvalidCredentialsError() = runTest(mainDispatcherRule.testDispatcher) {
        val settings = FakeSettingsRepository()
        settings.setServerUrl("http://host/api")
        val session = FakeSessionRepository()
        val auth = FakeAuthService().apply { error = InvalidStatusException(401) }
        val viewModel = LoginViewModel(settings, session, auth)
        advanceUntilIdle()

        viewModel.onUsernameChange("user")
        viewModel.onPasswordChange("pass")
        viewModel.login()
        advanceUntilIdle()

        assertEquals("Invalid username or password.", viewModel.error.first())
        assertNull(session.session.first())
    }

    @Test
    fun login_networkError_setsReachabilityError() = runTest(mainDispatcherRule.testDispatcher) {
        val settings = FakeSettingsRepository()
        settings.setServerUrl("http://host/api")
        val auth = FakeAuthService().apply { error = IOException("boom") }
        val viewModel = LoginViewModel(settings, FakeSessionRepository(), auth)
        advanceUntilIdle()

        viewModel.onUsernameChange("user")
        viewModel.onPasswordChange("pass")
        viewModel.login()
        advanceUntilIdle()

        assertEquals("Could not reach the server. Check the server address.", viewModel.error.first())
    }

    @Test
    fun login_emptyInput_doesNothing() = runTest(mainDispatcherRule.testDispatcher) {
        val session = FakeSessionRepository()
        val viewModel = LoginViewModel(FakeSettingsRepository(), session, FakeAuthService())
        advanceUntilIdle()

        viewModel.login()
        advanceUntilIdle()

        assertNull(session.session.first())
        assertNull(viewModel.error.first())
    }
}
