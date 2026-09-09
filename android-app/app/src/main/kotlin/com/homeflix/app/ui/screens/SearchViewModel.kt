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
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import org.jellyfin.sdk.api.client.exception.InvalidStatusException
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ImageType

data class SearchResultItem(
    val id: String,
    val name: String,
    val imageUrl: String?,
    val isSeries: Boolean
)

class SearchViewModel(private val movieRepository: MovieRepository) : ViewModel() {

    sealed interface UiState {
        data object Idle : UiState
        data object Loading : UiState
        data class Error(val message: String) : UiState
        data class Success(val results: List<SearchResultItem>) : UiState
    }

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _uiState = MutableStateFlow<UiState>(UiState.Idle)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val _unauthorizedEvents = Channel<Unit>(Channel.BUFFERED)
    val unauthorizedEvents: Flow<Unit> = _unauthorizedEvents.receiveAsFlow()

    private var searchJob: Job? = null

    fun onQueryChange(query: String) {
        _query.value = query
        searchJob?.cancel()
        val trimmed = query.trim()
        if (trimmed.isEmpty()) {
            _uiState.value = UiState.Idle
            return
        }

        searchJob = viewModelScope.launch {
            delay(300)
            _uiState.value = UiState.Loading
            try {
                val results = movieRepository.search(trimmed, limit = 50)
                val credentials = movieRepository.imageCredentials()
                val items = results.map { item ->
                    val tag = item.imageTags?.get(ImageType.PRIMARY)
                    SearchResultItem(
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
                        isSeries = item.type == BaseItemKind.SERIES
                    )
                }
                _uiState.value = UiState.Success(items)
            } catch (e: CancellationException) {
                throw e
            } catch (e: InvalidStatusException) {
                if (e.status == 401) {
                    _unauthorizedEvents.send(Unit)
                } else {
                    _uiState.value = UiState.Error("Search failed (${e.status}).")
                }
            } catch (e: Exception) {
                _uiState.value = UiState.Error("Search failed.")
            }
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as HomeFlixApplication
                SearchViewModel(application.container.movieRepository)
            }
        }
    }
}
