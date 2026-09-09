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
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import org.jellyfin.sdk.api.client.exception.InvalidStatusException
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.ImageType

class SeriesDetailViewModel(
    private val movieRepository: MovieRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val seriesId: String = savedStateHandle.get<String>(Routes.ARG_SERIES_ID).orEmpty()

    data class SeasonWithEpisodes(
        val season: BaseItemDto,
        val episodes: List<BaseItemDto>
    )

    sealed interface UiState {
        data object Loading : UiState
        data class Error(val message: String) : UiState
        data class Success(
            val series: BaseItemDto,
            val backdropUrl: String?,
            val seasons: List<SeasonWithEpisodes>
        ) : UiState
    }

    private val _uiState = MutableStateFlow<UiState>(UiState.Loading)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val _unauthorizedEvents = Channel<Unit>(Channel.BUFFERED)
    val unauthorizedEvents: Flow<Unit> = _unauthorizedEvents.receiveAsFlow()

    init {
        if (seriesId.isEmpty()) {
            _uiState.value = UiState.Error("Could not load this show.")
        } else {
            load()
        }
    }

    fun load() {
        viewModelScope.launch {
            _uiState.value = UiState.Loading
            try {
                val series = movieRepository.getMovie(seriesId)
                val seasons = movieRepository.getSeasons(seriesId)
                val seasonsWithEpisodes = coroutineScope {
                    seasons.map { season ->
                        async {
                            val episodes = try {
                                movieRepository.getEpisodes(season.id.toString())
                            } catch (e: CancellationException) {
                                throw e
                            } catch (_: Exception) {
                                emptyList()
                            }
                            SeasonWithEpisodes(season, episodes)
                        }
                    }.awaitAll()
                }

                val credentials = movieRepository.imageCredentials()
                val tag = series.imageTags?.get(ImageType.BACKDROP)
                val backdropUrl = tag?.let {
                    runCatching {
                        buildImageUrl(
                            baseUrl = credentials.baseUrl,
                            accessToken = credentials.accessToken,
                            itemId = seriesId,
                            imageTag = it,
                            imageType = ImageType.BACKDROP
                        )
                    }.getOrNull()
                }

                _uiState.value = UiState.Success(series, backdropUrl, seasonsWithEpisodes)
            } catch (e: CancellationException) {
                throw e
            } catch (e: InvalidStatusException) {
                if (e.status == 401) {
                    _unauthorizedEvents.send(Unit)
                } else {
                    _uiState.value = UiState.Error("Could not load this show (${e.status}).")
                }
            } catch (e: Exception) {
                _uiState.value = UiState.Error("Could not load this show.")
            }
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as HomeFlixApplication
                SeriesDetailViewModel(
                    movieRepository = application.container.movieRepository,
                    savedStateHandle = createSavedStateHandle()
                )
            }
        }
    }
}
