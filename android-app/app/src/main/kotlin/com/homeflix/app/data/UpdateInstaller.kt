package com.homeflix.app.data

import java.io.OutputStream
import kotlinx.coroutines.flow.Flow

sealed interface InstallResult {
    data object Success : InstallResult
    data class Failed(val reason: String) : InstallResult
    data object Cancelled : InstallResult
}

interface UpdateInstaller {
    /** Whether the app may currently install packages. */
    fun canInstallPackages(): Boolean

    /**
     * Open a package-installer session, run [write] to fill it, then commit.
     * If [write] throws, the session is abandoned and nothing is installed.
     * Outcomes are emitted on [results].
     */
    suspend fun install(write: suspend (OutputStream) -> Unit)

    val results: Flow<InstallResult>
}
