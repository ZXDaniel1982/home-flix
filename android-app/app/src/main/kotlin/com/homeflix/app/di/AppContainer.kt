package com.homeflix.app.di

import android.content.Context
import com.homeflix.app.data.AuthService
import com.homeflix.app.data.DataStoreSessionRepository
import com.homeflix.app.data.DataStoreSettingsRepository
import com.homeflix.app.data.JellyfinAuthService
import com.homeflix.app.data.JellyfinMovieRepository
import com.homeflix.app.data.JellyfinProvider
import com.homeflix.app.data.MovieRepository
import com.homeflix.app.data.SessionRepository
import com.homeflix.app.data.SettingsRepository
import com.homeflix.app.data.dataStore

class AppContainer(context: Context) {

    val settingsRepository: SettingsRepository by lazy {
        DataStoreSettingsRepository(context.dataStore)
    }

    val sessionRepository: SessionRepository by lazy {
        DataStoreSessionRepository(context.dataStore)
    }

    val jellyfinProvider: JellyfinProvider by lazy {
        JellyfinProvider(context)
    }

    val authService: AuthService by lazy {
        JellyfinAuthService(jellyfinProvider)
    }

    val movieRepository: MovieRepository by lazy {
        JellyfinMovieRepository(jellyfinProvider, settingsRepository, sessionRepository)
    }
}
