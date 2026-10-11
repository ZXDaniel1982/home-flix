package com.homeflix.app.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.homeflix.app.BuildConfig
import com.homeflix.app.HomeFlixApplication
import com.homeflix.app.data.InstallResult
import com.homeflix.app.data.SettingsRepository
import com.homeflix.app.data.UpdateInstaller
import com.homeflix.app.data.UpdateManifest
import com.homeflix.app.data.UpdateManifestException
import com.homeflix.app.data.UpdateRepository
import com.homeflix.app.data.joinUrl
import com.homeflix.app.data.updateOrigin
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

/** Raises when a downloaded APK does not match the manifest's digest. */
private class UpdateVerificationException : Exception("digest mismatch")

class UpdateViewModel(
    private val settingsRepository: SettingsRepository,
    private val updateRepository: UpdateRepository,
    private val updateInstaller: UpdateInstaller,
    private val currentVersionCode: Int,
    currentVersionName: String,
    private val sdkInt: Int
) : ViewModel() {

    sealed interface CheckState {
        data object Idle : CheckState
        data object Checking : CheckState
        data object UpToDate : CheckState
        data class Available(val manifest: UpdateManifest) : CheckState
        data class Error(val message: String) : CheckState
    }

    sealed interface InstallState {
        data object Idle : InstallState
        data class Downloading(val downloaded: Long, val total: Long) : InstallState
        data object Verifying : InstallState
        data object Installing : InstallState
        data object Installed : InstallState
        data class Failed(val message: String) : InstallState
    }

    val currentVersionLabel: String = "$currentVersionName ($currentVersionCode)"

    private val _checkState = MutableStateFlow<CheckState>(CheckState.Idle)
    val checkState: StateFlow<CheckState> = _checkState.asStateFlow()

    private val _installState = MutableStateFlow<InstallState>(InstallState.Idle)
    val installState: StateFlow<InstallState> = _installState.asStateFlow()

    private val _canInstallPackages = MutableStateFlow(updateInstaller.canInstallPackages())
    val canInstallPackages: StateFlow<Boolean> = _canInstallPackages.asStateFlow()

    private val _promptEvents = Channel<UpdateManifest>(Channel.BUFFERED)
    val promptEvents: Flow<UpdateManifest> = _promptEvents.receiveAsFlow()

    private var checking = false

    init {
        viewModelScope.launch {
            updateInstaller.results.collect { result ->
                _installState.value = when (result) {
                    InstallResult.Success -> InstallState.Installed
                    InstallResult.Cancelled -> InstallState.Idle
                    is InstallResult.Failed -> InstallState.Failed(result.reason)
                }
            }
        }
    }

    /** Manual check: errors are surfaced. */
    fun check() = startCheck(silent = false, prompt = false)

    /** Launch-time check: silent on failure; prompts when a newer release exists. */
    fun checkOnLaunch() = startCheck(silent = true, prompt = true)

    fun refreshInstallPermission() {
        _canInstallPackages.value = updateInstaller.canInstallPackages()
    }

    fun dismiss(manifest: UpdateManifest) {
        viewModelScope.launch { settingsRepository.setSkippedVersionCode(manifest.versionCode) }
    }

    fun dismissInstallResult() {
        _installState.value = InstallState.Idle
    }

    fun update(manifest: UpdateManifest) {
        val current = _installState.value
        if (current is InstallState.Downloading || current is InstallState.Installing) return
        viewModelScope.launch {
            _installState.value = InstallState.Downloading(0, manifest.size)
            try {
                val serverUrl = settingsRepository.serverUrl.first().orEmpty()
                val apkUrl = joinUrl(updateOrigin(serverUrl), manifest.apkUrl)
                updateInstaller.install { sink ->
                    val digest = updateRepository.download(apkUrl, manifest.size, { done, total ->
                        _installState.value = InstallState.Downloading(done, total)
                    }, sink)
                    _installState.value = InstallState.Verifying
                    if (!digest.equals(manifest.sha256, ignoreCase = true)) {
                        throw UpdateVerificationException()
                    }
                    _installState.value = InstallState.Installing
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: UpdateVerificationException) {
                _installState.value = InstallState.Failed("The download was corrupted. Try again.")
            } catch (e: Exception) {
                _installState.value = InstallState.Failed("Could not download the update.")
            }
        }
    }

    private fun startCheck(silent: Boolean, prompt: Boolean) {
        if (checking) return
        checking = true
        viewModelScope.launch {
            if (!silent) _checkState.value = CheckState.Checking
            try {
                val serverUrl = settingsRepository.serverUrl.first()
                if (serverUrl.isNullOrBlank()) {
                    if (!silent) _checkState.value = CheckState.Error("Set a server URL first.")
                    return@launch
                }
                val manifest = updateRepository.fetchManifest(updateOrigin(serverUrl))
                when {
                    manifest.versionCode <= currentVersionCode -> {
                        if (!silent) _checkState.value = CheckState.UpToDate
                    }

                    manifest.minSdk > sdkInt -> {
                        if (!silent) {
                            _checkState.value =
                                CheckState.Error("This update needs a newer Android version.")
                        }
                    }

                    else -> {
                        _checkState.value = CheckState.Available(manifest)
                        val skipped = settingsRepository.skippedVersionCode.first()
                        if (prompt && shouldPrompt(manifest, skipped)) {
                            _promptEvents.send(manifest)
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: UpdateManifestException) {
                if (!silent) _checkState.value = CheckState.Error("Could not check for updates.")
            } catch (e: Exception) {
                if (!silent) _checkState.value = CheckState.Error("Could not check for updates.")
            } finally {
                checking = false
            }
        }
    }

    companion object {
        /** A version is worth prompting for unless the user already declined it. */
        fun shouldPrompt(manifest: UpdateManifest, skippedVersionCode: Int?): Boolean =
            manifest.versionCode != skippedVersionCode

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application =
                    this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as HomeFlixApplication
                UpdateViewModel(
                    settingsRepository = application.container.settingsRepository,
                    updateRepository = application.container.updateRepository,
                    updateInstaller = application.container.updateInstaller,
                    currentVersionCode = BuildConfig.VERSION_CODE,
                    currentVersionName = BuildConfig.VERSION_NAME,
                    sdkInt = android.os.Build.VERSION.SDK_INT
                )
            }
        }
    }
}
