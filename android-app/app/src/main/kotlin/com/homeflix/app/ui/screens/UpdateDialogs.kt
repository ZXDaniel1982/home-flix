package com.homeflix.app.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.homeflix.app.data.UpdateManifest

@Composable
fun UpdateAvailableDialog(
    manifest: UpdateManifest,
    onUpdate: () -> Unit,
    onLater: () -> Unit
) {
    AlertDialog(
        modifier = Modifier.testTag("updateDialog"),
        onDismissRequest = onLater,
        title = { Text("Update available") },
        text = {
            Column {
                Text("Version ${manifest.versionName}")
                manifest.releaseNotes?.takeIf { it.isNotBlank() }?.let {
                    Text(it, modifier = Modifier.padding(top = 8.dp))
                }
            }
        },
        confirmButton = { TextButton(onClick = onUpdate) { Text("Update") } },
        dismissButton = { TextButton(onClick = onLater) { Text("Later") } }
    )
}

@Composable
fun InstallProgressDialog(
    state: UpdateViewModel.InstallState,
    onDismiss: () -> Unit
) {
    when (state) {
        is UpdateViewModel.InstallState.Idle -> Unit

        is UpdateViewModel.InstallState.Downloading -> AlertDialog(
            modifier = Modifier.testTag("installDialog"),
            onDismissRequest = {},
            title = { Text("Downloading update") },
            text = {
                Column {
                    Text("${state.downloaded / 1024} / ${state.total / 1024} KB")
                    if (state.total > 0) {
                        LinearProgressIndicator(
                            progress = { (state.downloaded.toFloat() / state.total).coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                        )
                    }
                }
            },
            confirmButton = {}
        )

        is UpdateViewModel.InstallState.Verifying,
        is UpdateViewModel.InstallState.Installing -> AlertDialog(
            modifier = Modifier.testTag("installDialog"),
            onDismissRequest = {},
            title = { Text("Installing update") },
            text = { Text("Confirm the install in the system dialog.") },
            confirmButton = {}
        )

        is UpdateViewModel.InstallState.Installed -> AlertDialog(
            modifier = Modifier.testTag("installDialog"),
            onDismissRequest = onDismiss,
            title = { Text("Update installed") },
            text = { Text("Reopen the app to use the new version.") },
            confirmButton = { TextButton(onClick = onDismiss) { Text("OK") } }
        )

        is UpdateViewModel.InstallState.Failed -> AlertDialog(
            modifier = Modifier.testTag("installDialog"),
            onDismissRequest = onDismiss,
            title = { Text("Update failed") },
            text = { Text(state.message) },
            confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } }
        )
    }
}
