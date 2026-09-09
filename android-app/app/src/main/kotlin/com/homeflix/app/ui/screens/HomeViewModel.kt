package com.homeflix.app.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.homeflix.app.HomeFlixApplication
import com.homeflix.app.data.MovieRepository
import com.homeflix.app.data.buildImageUrl
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import org.jellyfin.sdk.api.client.exception.InvalidStatusException
import org.jellyfin.sdk.model.api.ImageType

data class ResumeItem(
    val id: String,
    val name: String,
    val imageUrl: String?,
    val playedPercentage: Float
)

class HomeViewModel(private val movieRepository: MovieRepository) : ViewModel() {

    sealed interface UiState {
        data object Loading : UiState
        data class Error(val message: String) : UiState
        data class Success(val items: List<ResumeItem>) : UiState
    }

    private val _uiState = MutableStateFlow<UiState>(UiState.Loading)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val _unauthorizedEvents = Channel<Unit>(Channel.BUFFERED)
    val unauthorizedEvents: Flow<Unit> = _unauthorizedEvents.receiveAsFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _uiState.value = UiState.Loading
            try {
                val resume = movieRepository.getResumeItems(limit = 30)
                val credentials = movieRepository.imageCredentials()
                val items = resume.map { item ->
                    val tag = item.imageTags?.get(ImageType.PRIMARY)
                    ResumeItem(
                        id = item.id.toString(),
                        name = item.name.orEmpty(),
                        imageUrl = tag?.let {
                            runCatching {
                                buildImageUrl(
                                    baseUrl = credentials.baseUrl,
                                    accessToken = credentials.accessToken,
                                    itemId = item.id.toString(),
                                    imageTag = it,
                                    imageType = ImageType.PRIMARY
                                )
                            }.getOrNull()
                        },
                        playedPercentage = (item.userData?.playedPercentage ?: 0.0).toFloat()
                    )
                }
                _uiState.value = UiState.Success(items)
            } catch (e: CancellationException) {
                throw e
            } catch (e: InvalidStatusException) {
                if (e.status == 401) {
                    _unauthorizedEvents.send(Unit)
                } else {
                    _uiState.value = UiState.Error("Could not load continue watching (${e.status}).")
                }
            } catch (e: Exception) {
                _uiState.value = UiState.Error("Could not load continue watching.")
            }
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as HomeFlixApplication
                HomeViewModel(application.container.movieRepository)
            }
        }
    }
}
