package com.homeflix.app.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.homeflix.app.HomeFlixApplication
import com.homeflix.app.data.SettingsRepository
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

class ServerUrlViewModel(private val repository: SettingsRepository) : ViewModel() {

    private val _serverUrl = MutableStateFlow("")
    val serverUrl: StateFlow<String> = _serverUrl.asStateFlow()

    private val _saveEvents = Channel<Unit>(Channel.BUFFERED)
    val saveEvents: Flow<Unit> = _saveEvents.receiveAsFlow()

    init {
        viewModelScope.launch {
            repository.serverUrl.collect { url -> _serverUrl.value = url.orEmpty() }
        }
    }

    fun onUrlChange(url: String) {
        _serverUrl.value = url
    }

    fun save() {
        val url = _serverUrl.value.trim()
        if (url.isEmpty()) return
        viewModelScope.launch {
            repository.setServerUrl(normalize(url))
            _saveEvents.send(Unit)
        }
    }

    private fun normalize(url: String): String {
        val withScheme = if (url.contains("://")) url else "http://$url"
        val trimmed = withScheme.trimEnd('/')
        return if (trimmed.endsWith("/api")) trimmed else "$trimmed/api"
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as HomeFlixApplication
                ServerUrlViewModel(application.container.settingsRepository)
            }
        }
    }
}
