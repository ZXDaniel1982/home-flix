package com.homeflix.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.homeflix.app.ui.screens.SettingsScreen
import com.homeflix.app.ui.screens.UpdateViewModel
import com.homeflix.app.ui.theme.HomeFlixTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class SettingsScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun viewModel(
        repo: FakeUpdateRepository = FakeUpdateRepository(),
        installer: FakeUpdateInstaller = FakeUpdateInstaller()
    ) = UpdateViewModel(
        settingsRepository = FakeSettingsRepository(initialUrl = "http://host/api"),
        updateRepository = repo,
        updateInstaller = installer,
        currentVersionCode = 1,
        currentVersionName = "0.1.0",
        sdkInt = 33
    )

    @Test
    fun showsVersionAndChecksForUpdates() {
        val repo = FakeUpdateRepository().apply { manifest = updateManifest(versionCode = 2, versionName = "0.2.0") }
        val vm = viewModel(repo = repo)

        composeRule.setContent {
            HomeFlixTheme { SettingsScreen(onBack = {}, onLogout = {}, updateViewModel = vm) }
        }

        composeRule.onNodeWithText("Version").assertIsDisplayed()
        composeRule.onNodeWithText("0.1.0 (1)").assertIsDisplayed()
        composeRule.onNodeWithTag("checkForUpdates").performClick()
        composeRule.waitUntil(timeoutMillis = 5000) {
            repo.manifestCalls > 0
        }
        composeRule.onNodeWithText("Update available: 0.2.0").assertIsDisplayed()
    }

    @Test
    fun showsPermissionRowWhenInstallsAreNotAllowed() {
        val vm = viewModel(installer = FakeUpdateInstaller().apply { canInstall = false })

        composeRule.setContent {
            HomeFlixTheme { SettingsScreen(onBack = {}, onLogout = {}, updateViewModel = vm) }
        }

        composeRule.onNodeWithText("Allow app installs").assertIsDisplayed()
    }

    @Test
    fun logoutIsInvoked() {
        var loggedOut = false
        val vm = viewModel()

        composeRule.setContent {
            HomeFlixTheme { SettingsScreen(onBack = {}, onLogout = { loggedOut = true }, updateViewModel = vm) }
        }

        composeRule.onNodeWithTag("logout").performClick()
        assertEquals(true, loggedOut)
    }
}
