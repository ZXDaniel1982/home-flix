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
import com.homeflix.app.data.buildImageUrl
import com.homeflix.app.navigation.Routes
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import org.jellyfin.sdk.api.client.exception.InvalidStatusException
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.ImageType

class MovieDetailViewModel(
    private val movieRepository: MovieRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val movieId: String = savedStateHandle.get<String>(Routes.ARG_MOVIE_ID).orEmpty()

    sealed interface UiState {
        data object Loading : UiState
        data class Error(val message: String) : UiState
        data class Success(val movie: BaseItemDto, val posterUrl: String?) : UiState
    }

    private val _uiState = MutableStateFlow<UiState>(UiState.Loading)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val _unauthorizedEvents = Channel<Unit>(Channel.BUFFERED)
    val unauthorizedEvents: Flow<Unit> = _unauthorizedEvents.receiveAsFlow()

    init {
        if (movieId.isEmpty()) {
            _uiState.value = UiState.Error("Could not load this movie.")
        } else {
            load()
        }
    }

    fun load() {
        viewModelScope.launch {
            _uiState.value = UiState.Loading
            try {
                val movie = movieRepository.getMovie(movieId)
                val credentials = movieRepository.imageCredentials()
                val tag = movie.imageTags?.get(ImageType.PRIMARY)
                val posterUrl = tag?.let {
                    runCatching {
                        buildImageUrl(
                            baseUrl = credentials.baseUrl,
                            accessToken = credentials.accessToken,
                            itemId = movieId,
                            imageTag = it,
                            imageType = ImageType.PRIMARY
                        )
                    }.getOrNull()
                }
                _uiState.value = UiState.Success(movie, posterUrl)
            } catch (e: CancellationException) {
                throw e
            } catch (e: InvalidStatusException) {
                if (e.status == 401) {
                    _unauthorizedEvents.send(Unit)
                } else {
                    _uiState.value = UiState.Error("Could not load this movie (${e.status}).")
                }
            } catch (e: Exception) {
                _uiState.value = UiState.Error("Could not load this movie.")
            }
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as HomeFlixApplication
                MovieDetailViewModel(
                    movieRepository = application.container.movieRepository,
                    savedStateHandle = createSavedStateHandle()
                )
            }
        }
    }
}
