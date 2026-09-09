package com.homeflix.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.homeflix.app.ui.screens.LoginScreen
import com.homeflix.app.ui.screens.LoginViewModel
import com.homeflix.app.ui.theme.HomeFlixTheme
import org.jellyfin.sdk.api.client.exception.InvalidStatusException
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class LoginScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun loginViewModel(auth: FakeAuthService) =
        LoginViewModel(FakeSettingsRepository(), FakeSessionRepository(), auth)

    @Test
    fun loginButton_disabledUntilFieldsFilled() {
        val viewModel = loginViewModel(FakeAuthService())
        composeRule.setContent {
            HomeFlixTheme { LoginScreen(onLogin = {}, viewModel = viewModel) }
        }

        composeRule.onNodeWithTag("loginButton").assertIsNotEnabled()
        composeRule.onNodeWithTag("username").performTextInput("user")
        composeRule.onNodeWithTag("password").performTextInput("pass")
        composeRule.onNodeWithTag("loginButton").assertIsEnabled()
    }

    @Test
    fun login_showsErrorOn401() {
        val auth = FakeAuthService().apply { error = InvalidStatusException(401) }
        composeRule.setContent {
            HomeFlixTheme { LoginScreen(onLogin = {}, viewModel = loginViewModel(auth)) }
        }

        composeRule.onNodeWithTag("username").performTextInput("user")
        composeRule.onNodeWithTag("password").performTextInput("pass")
        composeRule.onNodeWithTag("loginButton").performClick()

        composeRule.onNodeWithText("Invalid username or password.").assertIsDisplayed()
    }

    @Test
    fun login_invokesOnLoginOnSuccess() {
        val auth = FakeAuthService().apply { result = authResult("token") }
        val loggedIn = java.util.concurrent.atomic.AtomicBoolean(false)
        composeRule.setContent {
            HomeFlixTheme { LoginScreen(onLogin = { loggedIn.set(true) }, viewModel = loginViewModel(auth)) }
        }

        composeRule.onNodeWithTag("username").performTextInput("user")
        composeRule.onNodeWithTag("password").performTextInput("pass")
        composeRule.onNodeWithTag("loginButton").performClick()

        composeRule.waitUntil(timeoutMillis = 5000) { loggedIn.get() }
        assertTrue(loggedIn.get())
    }
}
