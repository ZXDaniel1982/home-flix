package com.homeflix.app.data

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.IntentSender
import android.content.pm.PackageInstaller
import android.os.Build
import androidx.core.content.ContextCompat
import java.io.OutputStream
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow

/**
 * Installs APKs through Android's [PackageInstaller]. The caller writes the APK
 * bytes into the session; the session is only committed when that write returns,
 * so a failed verification aborts the install. The user always confirms through
 * the system dialog ([PackageInstaller.STATUS_PENDING_USER_ACTION]), which this
 * class launches itself so no `Intent` leaks into the ViewModel.
 */
class PackageInstallerUpdateInstaller(context: Context) : UpdateInstaller {

    private val appContext = context.applicationContext

    private val channel = Channel<InstallResult>(Channel.BUFFERED)
    override val results: Flow<InstallResult> = channel.receiveAsFlow()

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
                PackageInstaller.STATUS_SUCCESS ->
                    channel.trySend(InstallResult.Success)

                PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                    @Suppress("DEPRECATION")
                    val confirm = intent.getParcelableExtra(Intent.EXTRA_INTENT) as? Intent
                    if (confirm != null) {
                        confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        runCatching { appContext.startActivity(confirm) }
                    }
                }

                PackageInstaller.STATUS_FAILURE_ABORTED ->
                    channel.trySend(InstallResult.Cancelled)

                else -> {
                    val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
                    channel.trySend(InstallResult.Failed(message ?: "Install failed."))
                }
            }
        }
    }

    init {
        ContextCompat.registerReceiver(
            appContext,
            receiver,
            IntentFilter(ACTION_INSTALL_RESULT),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    override fun canInstallPackages(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            appContext.packageManager.canRequestPackageInstalls()
        } else {
            true
        }

    override suspend fun install(write: suspend (OutputStream) -> Unit) {
        val installer = appContext.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
        val sessionId = installer.createSession(params)
        val session = installer.openSession(sessionId)
        try {
            val output = session.openWrite("base.apk", 0, -1)
            try {
                write(output)
                session.fsync(output)
            } finally {
                output.close()
            }
            session.commit(createIntentSender(sessionId))
        } catch (e: Exception) {
            runCatching { session.abandon() }
            throw e
        } finally {
            runCatching { session.close() }
        }
    }

    private fun createIntentSender(sessionId: Int): IntentSender {
        val intent = Intent(ACTION_INSTALL_RESULT).setPackage(appContext.packageName)
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        val pending = PendingIntent.getBroadcast(appContext, sessionId, intent, flags)
        return pending.intentSender
    }

    private companion object {
        const val ACTION_INSTALL_RESULT = "com.homeflix.app.INSTALL_RESULT"
    }
}
