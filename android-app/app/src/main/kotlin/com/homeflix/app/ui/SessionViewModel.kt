package com.homeflix.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.homeflix.app.HomeFlixApplication
import com.homeflix.app.data.Session
import com.homeflix.app.data.SessionRepository
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SessionViewModel(private val sessionRepository: SessionRepository) : ViewModel() {

    val session: StateFlow<Session?> = sessionRepository.session
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _logoutEvents = Channel<Unit>(Channel.BUFFERED)
    val logoutEvents: Flow<Unit> = _logoutEvents.receiveAsFlow()

    fun logout() {
        viewModelScope.launch {
            sessionRepository.clear()
            _logoutEvents.send(Unit)
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as HomeFlixApplication
                SessionViewModel(application.container.sessionRepository)
            }
        }
    }
}
