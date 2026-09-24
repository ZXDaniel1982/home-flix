package com.homeflix.app.ui.screens

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.homeflix.app.HomeFlixApplication
import com.homeflix.app.data.AdjacentEpisodes
import com.homeflix.app.data.MovieRepository
import com.homeflix.app.data.PlaybackStream
import com.homeflix.app.data.SeasonEpisodes
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
import org.jellyfin.sdk.model.api.BaseItemKind

class PlayerViewModel(
    private val movieRepository: MovieRepository,
    private val applicationScope: CoroutineScope,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val movieId: String = savedStateHandle.get<String>(Routes.ARG_MOVIE_ID).orEmpty()

    private var mediaSourceId: String = ""

    val playingItemId: String get() = movieId

    private var currentSeriesId: String? = null

    data class EpisodeRef(val id: String)

    sealed interface UiState {
        data object Loading : UiState
        data class Error(val message: String) : UiState
        data class Success(
            val stream: PlaybackStream,
            val resumeTicks: Long,
            val isEpisode: Boolean,
            val previousEpisode: EpisodeRef?,
            val nextEpisode: EpisodeRef?
        ) : UiState
    }

    sealed interface EpisodesState {
        data object Idle : EpisodesState
        data object Loading : EpisodesState
        data class Error(val message: String) : EpisodesState
        data class Loaded(val seasons: List<SeasonEpisodes>) : EpisodesState
    }

    private val _uiState = MutableStateFlow<UiState>(UiState.Loading)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val _unauthorizedEvents = Channel<Unit>(Channel.BUFFERED)
    val unauthorizedEvents: Flow<Unit> = _unauthorizedEvents.receiveAsFlow()

    private val _episodesState = MutableStateFlow<EpisodesState>(EpisodesState.Idle)
    val episodesState: StateFlow<EpisodesState> = _episodesState.asStateFlow()

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
                currentSeriesId = movie.seriesId?.toString()
                mediaSourceId = stream.mediaSourceId
                val resumeTicks = movie.userData?.playbackPositionTicks ?: 0L
                val adjacent = if (movie.type == BaseItemKind.EPISODE) {
                    try {
                        movieRepository.getAdjacentEpisodes(movieId)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {
                        AdjacentEpisodes(previous = null, next = null)
                    }
                } else {
                    null
                }
                _uiState.value = UiState.Success(
                    stream = stream,
                    resumeTicks = resumeTicks,
                    isEpisode = movie.type == BaseItemKind.EPISODE,
                    previousEpisode = adjacent?.previous?.let { EpisodeRef(it.id.toString()) },
                    nextEpisode = adjacent?.next?.let { EpisodeRef(it.id.toString()) }
                )
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

    fun loadEpisodes() {
        val current = _episodesState.value
        if (current is EpisodesState.Loading || current is EpisodesState.Loaded) return
        val seriesId = currentSeriesId
        if (seriesId.isNullOrEmpty()) {
            _episodesState.value = EpisodesState.Error("This item has no series.")
            return
        }
        viewModelScope.launch {
            _episodesState.value = EpisodesState.Loading
            try {
                val seasons = movieRepository.getSeriesEpisodes(seriesId)
                _episodesState.value = EpisodesState.Loaded(seasons)
            } catch (e: CancellationException) {
                throw e
            } catch (e: InvalidStatusException) {
                if (e.status == 401) {
                    _unauthorizedEvents.send(Unit)
                }
                _episodesState.value = EpisodesState.Error("Could not load episodes.")
            } catch (_: Exception) {
                _episodesState.value = EpisodesState.Error("Could not load episodes.")
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
