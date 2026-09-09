package com.homeflix.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.homeflix.app.navigation.AppNavHost
import com.homeflix.app.ui.SessionViewModel
import com.homeflix.app.ui.screens.LoginViewModel
import com.homeflix.app.ui.screens.ServerUrlViewModel
import com.homeflix.app.ui.theme.HomeFlixTheme
import org.junit.Rule
import org.junit.Test

class NavigationTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun startsOnServer_andNavigatesToLogin() {
        val serverUrlViewModel = ServerUrlViewModel(FakeSettingsRepository(initialUrl = null))
        val loginViewModel = LoginViewModel(FakeSettingsRepository(), FakeSessionRepository(), FakeAuthService())

        composeRule.setContent {
            HomeFlixTheme {
                AppNavHost(
                    sessionViewModel = SessionViewModel(FakeSessionRepository()),
                    serverUrlViewModel = serverUrlViewModel,
                    loginViewModel = loginViewModel
                )
            }
        }

        composeRule.onNodeWithText("Server").assertIsDisplayed()
        composeRule.onNodeWithTag("serverUrl").performTextInput("http://host/api")
        composeRule.onNodeWithTag("saveButton").performClick()
        composeRule.onNodeWithTag("username").assertIsDisplayed()
    }
}
