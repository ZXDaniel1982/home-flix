package com.homeflix.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun MoviesScreen(onMovieClick: (String) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxSize().padding(24.dp)) {
        Text(text = "Movies", style = MaterialTheme.typography.headlineMedium)
        Text(
            text = "Frozen (dummy item)",
            modifier = Modifier
                .clickable { onMovieClick("movie-1") }
                .padding(vertical = 12.dp),
            style = MaterialTheme.typography.bodyLarge
        )
    }
}
