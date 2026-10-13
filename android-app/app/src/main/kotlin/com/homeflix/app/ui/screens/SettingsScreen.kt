package com.homeflix.app.ui.screens

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onLogout: () -> Unit,
    updateViewModel: UpdateViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val checkState by updateViewModel.checkState.collectAsState()
    val canInstall by updateViewModel.canInstallPackages.collectAsState()

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        updateViewModel.refreshInstallPermission()
    }

    LaunchedEffect(Unit) {
        updateViewModel.refreshInstallPermission()
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets(0.dp),
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                windowInsets = WindowInsets(0.dp),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            ListItem(
                headlineContent = { Text("Version") },
                supportingContent = { Text(updateViewModel.currentVersionLabel) }
            )
            HorizontalDivider()

            when (val state = checkState) {
                is UpdateViewModel.CheckState.Idle -> ListItem(
                    headlineContent = { Text("Check for updates") },
                    modifier = Modifier
                        .testTag("checkForUpdates")
                        .clickable { updateViewModel.check() }
                )

                is UpdateViewModel.CheckState.Checking -> ListItem(
                    headlineContent = { Text("Checking…") }
                )

                is UpdateViewModel.CheckState.UpToDate -> ListItem(
                    headlineContent = { Text("Up to date") },
                    trailingContent = {
                        TextButton(onClick = { updateViewModel.check() }) { Text("Check again") }
                    }
                )

                is UpdateViewModel.CheckState.Available -> ListItem(
                    headlineContent = { Text("Update available: ${state.manifest.versionName}") },
                    supportingContent = if (state.manifest.releaseNotes.isNullOrBlank()) {
                        null
                    } else {
                        { Text(state.manifest.releaseNotes) }
                    },
                    trailingContent = {
                        TextButton(onClick = { updateViewModel.update(state.manifest) }) {
                            Text("Update")
                        }
                    }
                )

                is UpdateViewModel.CheckState.Error -> ListItem(
                    headlineContent = { Text(state.message) },
                    trailingContent = {
                        TextButton(onClick = { updateViewModel.check() }) { Text("Retry") }
                    }
                )
            }

            if (!canInstall) {
                HorizontalDivider()
                ListItem(
                    headlineContent = { Text("Allow app installs") },
                    supportingContent = { Text("Needed to install updates from this app.") },
                    trailingContent = {
                        TextButton(onClick = {
                            permissionLauncher.launch(
                                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES)
                                    .setData(Uri.parse("package:${context.packageName}"))
                            )
                        }) { Text("Allow") }
                    }
                )
            }

            HorizontalDivider()
            ListItem(
                headlineContent = { Text("Log out") },
                modifier = Modifier
                    .testTag("logout")
                    .clickable { onLogout() }
            )
        }
    }
}
