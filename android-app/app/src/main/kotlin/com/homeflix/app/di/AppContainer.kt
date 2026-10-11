package com.homeflix.app.di

import android.content.Context
import com.homeflix.app.data.AuthService
import com.homeflix.app.data.DataStoreSessionRepository
import com.homeflix.app.data.DataStoreSettingsRepository
import com.homeflix.app.data.HttpUpdateRepository
import com.homeflix.app.data.JellyfinAuthService
import com.homeflix.app.data.JellyfinMovieRepository
import com.homeflix.app.data.JellyfinProvider
import com.homeflix.app.data.MovieRepository
import com.homeflix.app.data.PackageInstallerUpdateInstaller
import com.homeflix.app.data.SessionRepository
import com.homeflix.app.data.SettingsRepository
import com.homeflix.app.data.UpdateInstaller
import com.homeflix.app.data.UpdateRepository
import com.homeflix.app.data.dataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class AppContainer(context: Context) {

    private val appContext = context.applicationContext

    val applicationScope: CoroutineScope by lazy {
        CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }

    val settingsRepository: SettingsRepository by lazy {
        DataStoreSettingsRepository(appContext.dataStore)
    }

    val sessionRepository: SessionRepository by lazy {
        DataStoreSessionRepository(appContext.dataStore)
    }

    val jellyfinProvider: JellyfinProvider by lazy {
        JellyfinProvider(appContext)
    }

    val authService: AuthService by lazy {
        JellyfinAuthService(jellyfinProvider)
    }

    val movieRepository: MovieRepository by lazy {
        JellyfinMovieRepository(jellyfinProvider, settingsRepository, sessionRepository)
    }

    val updateRepository: UpdateRepository by lazy {
        HttpUpdateRepository()
    }

    val updateInstaller: UpdateInstaller by lazy {
        PackageInstallerUpdateInstaller(appContext)
    }
}
