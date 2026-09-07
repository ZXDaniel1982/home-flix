package com.homeflix.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun ServerUrlScreen(
    onSaved: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ServerUrlViewModel = viewModel(factory = ServerUrlViewModel.Factory)
) {
    val serverUrl by viewModel.serverUrl.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.saveEvents.collect { onSaved() }
    }

    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text(text = "Server", style = MaterialTheme.typography.headlineMedium)

        OutlinedTextField(
            value = serverUrl,
            onValueChange = viewModel::onUrlChange,
            label = { Text("Server address") },
            placeholder = { Text("http://orangepi3b.local") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp)
        )

        Button(
            onClick = viewModel::save,
            enabled = serverUrl.isNotBlank(),
            modifier = Modifier.padding(top = 16.dp)
        ) {
            Text("Save & continue")
        }
    }
}
