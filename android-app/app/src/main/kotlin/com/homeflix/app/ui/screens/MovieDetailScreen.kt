package com.homeflix.app.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun MovieDetailScreen(movieId: String, onPlay: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxSize().padding(24.dp)) {
        Text(text = "Movie detail", style = MaterialTheme.typography.headlineMedium)
        Text(text = "movieId: $movieId", style = MaterialTheme.typography.bodyLarge)
        Button(onClick = onPlay, modifier = Modifier.padding(top = 16.dp)) {
            Text("Play")
        }
    }
}
