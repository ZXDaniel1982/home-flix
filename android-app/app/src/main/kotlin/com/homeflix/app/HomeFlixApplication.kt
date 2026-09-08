package com.homeflix.app

import android.app.Application
import com.homeflix.app.di.AppContainer

class HomeFlixApplication : Application() {
    val container: AppContainer by lazy { AppContainer(this) }
}
