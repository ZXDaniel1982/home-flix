@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
package com.homeflix.app

import com.homeflix.app.data.InstallResult
import com.homeflix.app.ui.screens.UpdateViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class UpdateViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private fun viewModel(
        settings: FakeSettingsRepository = FakeSettingsRepository(initialUrl = "http://host/api"),
        repo: FakeUpdateRepository = FakeUpdateRepository(),
        installer: FakeUpdateInstaller = FakeUpdateInstaller(),
        versionCode: Int = 1,
        sdkInt: Int = 33
    ) = UpdateViewModel(settings, repo, installer, versionCode, "0.1.0", sdkInt)

    @Test
    fun check_newerVersion_setsAvailable() = runTest(mainDispatcherRule.testDispatcher) {
        val repo = FakeUpdateRepository().apply { manifest = updateManifest(versionCode = 2) }
        val vm = viewModel(repo = repo)

        vm.check()
        advanceUntilIdle()

        assertEquals(UpdateViewModel.CheckState.Available(repo.manifest), vm.checkState.value)
        assertEquals("http://host", repo.manifestOrigins.single())
    }

    @Test
    fun check_sameVersion_setsUpToDate() = runTest(mainDispatcherRule.testDispatcher) {
        val repo = FakeUpdateRepository().apply { manifest = updateManifest(versionCode = 1) }
        val vm = viewModel(repo = repo)

        vm.check()
        advanceUntilIdle()

        assertEquals(UpdateViewModel.CheckState.UpToDate, vm.checkState.value)
    }

    @Test
    fun check_olderVersion_setsUpToDate() = runTest(mainDispatcherRule.testDispatcher) {
        val repo = FakeUpdateRepository().apply { manifest = updateManifest(versionCode = 1) }
        val vm = viewModel(repo = repo, versionCode = 5)

        vm.check()
        advanceUntilIdle()

        assertEquals(UpdateViewModel.CheckState.UpToDate, vm.checkState.value)
    }

    @Test
    fun check_belowMinSdk_setsError() = runTest(mainDispatcherRule.testDispatcher) {
        val repo = FakeUpdateRepository().apply { manifest = updateManifest(versionCode = 2, minSdk = 30) }
        val vm = viewModel(repo = repo, sdkInt = 24)

        vm.check()
        advanceUntilIdle()

        assertTrue(vm.checkState.value is UpdateViewModel.CheckState.Error)
    }

    @Test
    fun check_manualFailure_setsError() = runTest(mainDispatcherRule.testDispatcher) {
        val repo = FakeUpdateRepository().apply { manifestError = RuntimeException("boom") }
        val vm = viewModel(repo = repo)

        vm.check()
        advanceUntilIdle()

        assertTrue(vm.checkState.value is UpdateViewModel.CheckState.Error)
    }

    @Test
    fun checkOnLaunch_failure_staysIdle() = runTest(mainDispatcherRule.testDispatcher) {
        val repo = FakeUpdateRepository().apply { manifestError = RuntimeException("boom") }
        val vm = viewModel(repo = repo)

        vm.checkOnLaunch()
        advanceUntilIdle()

        assertEquals(UpdateViewModel.CheckState.Idle, vm.checkState.value)
    }

    @Test
    fun checkOnLaunch_newerVersion_emitsPrompt() = runTest(mainDispatcherRule.testDispatcher) {
        val repo = FakeUpdateRepository().apply {
            manifest = updateManifest(versionCode = 2, releaseNotes = "Notes")
        }
        val vm = viewModel(repo = repo)

        vm.checkOnLaunch()
        advanceUntilIdle()

        assertEquals(repo.manifest, vm.promptEvents.first())
    }

    @Test
    fun check_noServerUrl_manualSetsError() = runTest(mainDispatcherRule.testDispatcher) {
        val vm = viewModel(settings = FakeSettingsRepository(initialUrl = null))

        vm.check()
        advanceUntilIdle()

        val state = vm.checkState.value
        assertTrue(state is UpdateViewModel.CheckState.Error)
        assertTrue((state as UpdateViewModel.CheckState.Error).message.contains("server"))
    }

    @Test
    fun shouldPrompt_isFalseForTheSkippedVersion() {
        assertFalse(UpdateViewModel.shouldPrompt(updateManifest(versionCode = 3), skippedVersionCode = 3))
        assertTrue(UpdateViewModel.shouldPrompt(updateManifest(versionCode = 4), skippedVersionCode = 3))
        assertTrue(UpdateViewModel.shouldPrompt(updateManifest(versionCode = 3), skippedVersionCode = null))
    }

    @Test
    fun update_success_finishesInstalled() = runTest(mainDispatcherRule.testDispatcher) {
        val bytes = "hello".toByteArray()
        val repo = FakeUpdateRepository().apply {
            manifest = updateManifest(versionCode = 2, size = bytes.size.toLong(), sha256 = sha256Hex(bytes))
            downloadedBytes = bytes
        }
        val installer = FakeUpdateInstaller()
        val vm = viewModel(repo = repo, installer = installer)

        vm.update(repo.manifest)
        advanceUntilIdle()

        assertEquals(UpdateViewModel.InstallState.Installed, vm.installState.value)
        assertTrue(installer.committed)
        assertEquals("http://host/updates/android/home-flix-0.2.0.apk", repo.downloadUrls.single())
    }

    @Test
    fun update_installingDoesNotClobberTerminalInstalled() = runTest(mainDispatcherRule.testDispatcher) {
        val bytes = "hello".toByteArray()
        val repo = FakeUpdateRepository().apply {
            manifest = updateManifest(versionCode = 2, size = bytes.size.toLong(), sha256 = sha256Hex(bytes))
            downloadedBytes = bytes
        }
        val installer = FakeUpdateInstaller()
        val vm = viewModel(repo = repo, installer = installer)

        val states = mutableListOf<UpdateViewModel.InstallState>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            vm.installState.collect { states.add(it) }
        }
        advanceUntilIdle()

        vm.update(repo.manifest)
        advanceUntilIdle()

        val downloadingIndex = states.indexOfFirst { it is UpdateViewModel.InstallState.Downloading }
        val verifyingIndex = states.indexOfFirst { it is UpdateViewModel.InstallState.Verifying }
        val installingIndex = states.indexOfFirst { it is UpdateViewModel.InstallState.Installing }
        val installedIndex = states.indexOfFirst { it is UpdateViewModel.InstallState.Installed }

        assertTrue("Downloading not observed: $states", downloadingIndex >= 0)
        assertTrue("Verifying not observed: $states", verifyingIndex >= 0)
        assertTrue("Installing not observed: $states", installingIndex >= 0)
        assertTrue("Installed not observed: $states", installedIndex >= 0)

        assertTrue(
            "Expected Downloading -> Verifying -> Installing -> Installed order: $states",
            downloadingIndex < verifyingIndex &&
                verifyingIndex < installingIndex &&
                installingIndex < installedIndex
        )

        // A terminal result must not be clobbered: no Installing after Installed.
        assertTrue(
            "Installing appeared after Installed: $states",
            states.indexOfLast { it is UpdateViewModel.InstallState.Installing } < installedIndex
        )
    }

    @Test
    fun update_checksumMismatch_failsWithoutCommitting() = runTest(mainDispatcherRule.testDispatcher) {
        val bytes = "hello".toByteArray()
        val repo = FakeUpdateRepository().apply {
            manifest = updateManifest(versionCode = 2, size = bytes.size.toLong(), sha256 = "deadbeef")
            downloadedBytes = bytes
        }
        val installer = FakeUpdateInstaller()
        val vm = viewModel(repo = repo, installer = installer)

        // The advertised digest does not match the downloaded bytes.
        vm.update(repo.manifest)
        advanceUntilIdle()

        assertTrue(vm.installState.value is UpdateViewModel.InstallState.Failed)
        assertFalse(installer.committed)
    }

    @Test
    fun update_sizeMismatch_failsWithoutCommitting() = runTest(mainDispatcherRule.testDispatcher) {
        val bytes = "hello".toByteArray()
        val repo = FakeUpdateRepository().apply {
            manifest = updateManifest(versionCode = 2, size = bytes.size.toLong() + 1, sha256 = sha256Hex(bytes))
            downloadedBytes = bytes
        }
        val installer = FakeUpdateInstaller()
        val vm = viewModel(repo = repo, installer = installer)

        // The digest matches but the advertised size does not.
        vm.update(repo.manifest)
        advanceUntilIdle()

        assertTrue(vm.installState.value is UpdateViewModel.InstallState.Failed)
        assertFalse(installer.committed)
    }

    @Test
    fun update_downloadError_setsFailed() = runTest(mainDispatcherRule.testDispatcher) {
        val repo = FakeUpdateRepository().apply { downloadError = RuntimeException("boom") }
        val installer = FakeUpdateInstaller()
        val vm = viewModel(repo = repo, installer = installer)

        vm.update(repo.manifest)
        advanceUntilIdle()

        assertTrue(vm.installState.value is UpdateViewModel.InstallState.Failed)
        assertFalse(installer.committed)
    }

    @Test
    fun update_cancelled_returnsToIdle() = runTest(mainDispatcherRule.testDispatcher) {
        val bytes = "hello".toByteArray()
        val repo = FakeUpdateRepository().apply {
            manifest = updateManifest(versionCode = 2, size = bytes.size.toLong(), sha256 = sha256Hex(bytes))
            downloadedBytes = bytes
        }
        val installer = FakeUpdateInstaller().apply { result = InstallResult.Cancelled }
        val vm = viewModel(repo = repo, installer = installer)

        vm.update(repo.manifest)
        advanceUntilIdle()

        assertEquals(UpdateViewModel.InstallState.Idle, vm.installState.value)
    }

    @Test
    fun dismiss_remembersTheVersion() = runTest(mainDispatcherRule.testDispatcher) {
        val settings = FakeSettingsRepository(initialUrl = "http://host/api")
        val vm = viewModel(settings = settings)

        vm.dismiss(updateManifest(versionCode = 7))
        advanceUntilIdle()

        assertEquals(7, settings.skippedVersionCode.first())
    }

    @Test
    fun update_refreshesStaleManifest_usesTheCurrentRelease() = runTest(mainDispatcherRule.testDispatcher) {
        val bytes = "hello".toByteArray()
        val repo = FakeUpdateRepository().apply {
            // A newer release was published after the prompt was shown, so the
            // stale manifest's APK no longer exists on the server.
            manifest = updateManifest(
                versionCode = 3,
                versionName = "0.3.0",
                apkUrl = "/updates/android/home-flix-0.3.0-3.apk",
                sha256 = sha256Hex(bytes),
                size = bytes.size.toLong()
            )
            downloadedBytes = bytes
        }
        val installer = FakeUpdateInstaller()
        val vm = viewModel(repo = repo, installer = installer)
        val stale = updateManifest(
            versionCode = 2,
            versionName = "0.2.0",
            apkUrl = "/updates/android/home-flix-0.2.0-2.apk",
            sha256 = "stale",
            size = 0L
        )

        vm.update(stale)
        advanceUntilIdle()

        assertEquals("http://host/updates/android/home-flix-0.3.0-3.apk", repo.downloadUrls.single())
        assertEquals(UpdateViewModel.InstallState.Installed, vm.installState.value)
    }

    @Test
    fun update_refreshFails_fallsBackToTheProvidedManifest() = runTest(mainDispatcherRule.testDispatcher) {
        val bytes = "hello".toByteArray()
        val repo = FakeUpdateRepository().apply {
            manifestError = RuntimeException("offline")
            downloadedBytes = bytes
        }
        val installer = FakeUpdateInstaller()
        val vm = viewModel(repo = repo, installer = installer)
        val provided = updateManifest(
            versionCode = 2,
            versionName = "0.2.0",
            apkUrl = "/updates/android/home-flix-0.2.0-2.apk",
            sha256 = sha256Hex(bytes),
            size = bytes.size.toLong()
        )

        vm.update(provided)
        advanceUntilIdle()

        assertEquals("http://host/updates/android/home-flix-0.2.0-2.apk", repo.downloadUrls.single())
        assertEquals(UpdateViewModel.InstallState.Installed, vm.installState.value)
    }
}
