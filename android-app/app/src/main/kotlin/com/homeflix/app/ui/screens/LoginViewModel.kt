package com.homeflix.app.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.homeflix.app.HomeFlixApplication
import com.homeflix.app.data.AuthService
import com.homeflix.app.data.Session
import com.homeflix.app.data.SessionRepository
import com.homeflix.app.data.SettingsRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import org.jellyfin.sdk.api.client.exception.InvalidStatusException

class LoginViewModel(
    private val settingsRepository: SettingsRepository,
    private val sessionRepository: SessionRepository,
    private val authService: AuthService
) : ViewModel() {

    private val _username = MutableStateFlow("")
    val username = _username.asStateFlow()

    private val _password = MutableStateFlow("")
    val password = _password.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()

    private val _loginEvents = Channel<Unit>(Channel.BUFFERED)
    val loginEvents: Flow<Unit> = _loginEvents.receiveAsFlow()

    private var serverUrl: String = ""

    init {
        viewModelScope.launch {
            settingsRepository.serverUrl.collect { url -> serverUrl = url.orEmpty() }
        }
    }

    fun onUsernameChange(value: String) {
        _username.value = value
    }

    fun onPasswordChange(value: String) {
        _password.value = value
    }

    fun login() {
        val url = serverUrl
        val user = _username.value.trim()
        val pass = _password.value
        if (url.isEmpty() || user.isEmpty() || pass.isEmpty()) return

        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                val result = authService.authenticate(url, user, pass)
                val token = result.accessToken
                val userDto = result.user
                val userId = userDto?.id?.toString()
                if (token.isNullOrBlank() || userId == null) {
                    _error.value = "Sign in failed."
                } else {
                    sessionRepository.save(
                        Session(
                            accessToken = token,
                            userId = userId,
                            username = userDto?.name.orEmpty()
                        )
                    )
                    _loginEvents.send(Unit)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: InvalidStatusException) {
                _error.value = if (e.status == 401) {
                    "Invalid username or password."
                } else {
                    "Sign in failed (${e.status})."
                }
            } catch (e: Exception) {
                _error.value = "Could not reach the server. Check the server address."
            } finally {
                _isLoading.value = false
            }
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as HomeFlixApplication
                val container = application.container
                LoginViewModel(
                    settingsRepository = container.settingsRepository,
                    sessionRepository = container.sessionRepository,
                    authService = container.authService
                )
            }
        }
    }
}
