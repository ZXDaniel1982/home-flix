package com.homeflix.app.ui.screens

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.homeflix.app.HomeFlixApplication
import com.homeflix.app.data.MovieRepository
import com.homeflix.app.data.PlaybackStream
import com.homeflix.app.navigation.Routes
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import org.jellyfin.sdk.api.client.exception.InvalidStatusException

class PlayerViewModel(
    private val movieRepository: MovieRepository,
    private val applicationScope: CoroutineScope,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val movieId: String = savedStateHandle.get<String>(Routes.ARG_MOVIE_ID).orEmpty()

    private var mediaSourceId: String = ""

    sealed interface UiState {
        data object Loading : UiState
        data class Error(val message: String) : UiState
        data class Success(val stream: PlaybackStream, val resumeTicks: Long) : UiState
    }

    private val _uiState = MutableStateFlow<UiState>(UiState.Loading)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val _unauthorizedEvents = Channel<Unit>(Channel.BUFFERED)
    val unauthorizedEvents: Flow<Unit> = _unauthorizedEvents.receiveAsFlow()

    init {
        if (movieId.isEmpty()) {
            _uiState.value = UiState.Error("Could not play this movie.")
        } else {
            load()
        }
    }

    fun load() {
        viewModelScope.launch {
            _uiState.value = UiState.Loading
            try {
                val stream = movieRepository.getStream(movieId)
                val movie = movieRepository.getMovie(movieId)
                mediaSourceId = stream.mediaSourceId
                val resumeTicks = movie.userData?.playbackPositionTicks ?: 0L
                _uiState.value = UiState.Success(stream, resumeTicks)
            } catch (e: CancellationException) {
                throw e
            } catch (e: InvalidStatusException) {
                if (e.status == 401) {
                    _unauthorizedEvents.send(Unit)
                } else {
                    _uiState.value = UiState.Error("Could not play this movie (${e.status}).")
                }
            } catch (e: Exception) {
                _uiState.value = UiState.Error("Could not play this movie.")
            }
        }
    }

    fun reportStarted(positionTicks: Long) =
        report { movieRepository.reportPlaybackStarted(movieId, mediaSourceId, positionTicks) }

    fun reportProgress(positionTicks: Long, isPaused: Boolean) =
        report { movieRepository.reportPlaybackProgress(movieId, mediaSourceId, positionTicks, isPaused) }

    fun reportStopped(positionTicks: Long) =
        report { movieRepository.reportPlaybackStopped(movieId, mediaSourceId, positionTicks) }

    private fun report(block: suspend () -> Unit) {
        applicationScope.launch {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: InvalidStatusException) {
                if (e.status == 401) {
                    _unauthorizedEvents.send(Unit)
                }
            } catch (_: Exception) {
                // Best-effort reporting; ignore failures.
            }
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as HomeFlixApplication
                PlayerViewModel(
                    movieRepository = application.container.movieRepository,
                    applicationScope = application.container.applicationScope,
                    savedStateHandle = createSavedStateHandle()
                )
            }
        }
    }
}
