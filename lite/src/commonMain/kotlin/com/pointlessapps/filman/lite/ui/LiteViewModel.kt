package com.pointlessapps.filman.lite.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pointlessapps.filman.lite.FilmanSession
import com.pointlessapps.filman.lite.ServerSettings
import com.pointlessapps.filman.lite.data.AuthRequiredException
import com.pointlessapps.filman.lite.data.Episode
import com.pointlessapps.filman.lite.data.LiteRepository
import com.pointlessapps.filman.lite.data.MediaItem
import com.pointlessapps.filman.lite.data.Season
import com.pointlessapps.filman.lite.data.StreamSource
import com.pointlessapps.filman.lite.download.DownloadItem
import com.pointlessapps.filman.lite.download.DownloadManager
import com.pointlessapps.filman.lite.player.PlaybackSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class MediaAction { WATCH, DOWNLOAD }

data class SearchState(
    val query: String = "",
    val isLoading: Boolean = false,
    val results: List<MediaItem> = emptyList(),
    val error: String? = null,
    val hasSearched: Boolean = false,
)

data class SheetState(
    val item: MediaItem,
    val seasons: List<Season> = emptyList(),
    val isLoadingSeasons: Boolean = false,
    val selectedSeason: Int? = null,
    val episode: Episode? = null,
    val pendingAction: MediaAction? = null,
    val isResolving: Boolean = false,
    val sources: List<StreamSource> = emptyList(),
    val error: String? = null,
)

@OptIn(FlowPreview::class)
class LiteViewModel(
    private val repository: LiteRepository,
    private val downloads: DownloadManager,
    private val serverSettings: ServerSettings,
    private val filmanSession: FilmanSession,
) : ViewModel() {

    val serverUrl: StateFlow<String> = serverSettings.url

    fun updateServerUrl(url: String) {
        serverSettings.update(url)
        retrySearch()
    }

    fun resetServerUrl() {
        serverSettings.reset()
        retrySearch()
    }

    val isLoggedIn: StateFlow<Boolean> = filmanSession.credentials
        .map { it != null }
        .stateIn(viewModelScope, SharingStarted.Eagerly, filmanSession.credentials.value != null)

    private val _showLogin = MutableStateFlow(false)
    val showLogin: StateFlow<Boolean> = _showLogin.asStateFlow()

    fun openLogin() { _showLogin.value = true }

    fun closeLogin() { _showLogin.value = false }

    fun onLoggedIn(cookie: String, userAgent: String) {
        filmanSession.save(cookie, userAgent)
        _showLogin.value = false
        updateSheet { it.copy(error = null) }
    }

    fun logout() {
        filmanSession.clear()
        clearFilmanWebSession()
    }

    private val _search = MutableStateFlow(SearchState())
    val search: StateFlow<SearchState> = _search.asStateFlow()

    private val _sheet = MutableStateFlow<SheetState?>(null)
    val sheet: StateFlow<SheetState?> = _sheet.asStateFlow()

    private val _player = MutableStateFlow<PlaybackSource?>(null)
    val player: StateFlow<PlaybackSource?> = _player.asStateFlow()

    val downloadItems: StateFlow<List<DownloadItem>> = downloads.items

    private var sheetJob: Job? = null

    init {
        viewModelScope.launch {
            _search.map { it.query }
                .distinctUntilChanged()
                .debounce(SEARCH_DEBOUNCE_MS)
                .collectLatest { query -> performSearch(query) }
        }
    }

    fun onQueryChange(query: String) = _search.update { it.copy(query = query) }

    private suspend fun performSearch(query: String) {
        if (query.isBlank()) {
            _search.update { it.copy(results = emptyList(), isLoading = false, error = null, hasSearched = false) }
            return
        }
        _search.update { it.copy(isLoading = true, error = null) }
        try {
            val results = repository.search(query)
            _search.update { it.copy(results = results, isLoading = false, hasSearched = true) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _search.update {
                it.copy(isLoading = false, hasSearched = true, results = emptyList(), error = e.message ?: "Search failed")
            }
        }
    }

    fun retrySearch() {
        viewModelScope.launch { performSearch(_search.value.query) }
    }

    fun openItem(item: MediaItem) {
        sheetJob?.cancel()
        _sheet.value = SheetState(item, isLoadingSeasons = item.isTvShow)
        if (!item.isTvShow) return
        sheetJob = viewModelScope.launch {
            try {
                val seasons = repository.seasons(item)
                updateSheet {
                    it.copy(
                        seasons = seasons,
                        isLoadingSeasons = false,
                        selectedSeason = seasons.firstOrNull()?.number,
                        error = if (seasons.isEmpty()) "No episodes available" else null,
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                updateSheet { it.copy(isLoadingSeasons = false, error = e.message ?: "Couldn't load episodes") }
            }
        }
    }

    fun selectSeason(number: Int) = updateSheet { it.copy(selectedSeason = number, episode = null) }

    fun selectEpisode(episode: Episode) = updateSheet { it.copy(episode = episode, error = null) }

    fun closeSheet() {
        sheetJob?.cancel()
        _sheet.value = null
    }

    fun clearSources() = updateSheet { it.copy(sources = emptyList(), pendingAction = null, error = null) }

    fun requestAction(action: MediaAction) {
        val state = _sheet.value ?: return
        if (state.item.isTvShow && state.episode == null) {
            updateSheet { it.copy(error = "Choose an episode first") }
            return
        }
        sheetJob?.cancel()
        updateSheet { it.copy(isResolving = true, pendingAction = action, error = null) }
        sheetJob = viewModelScope.launch {
            try {
                val sources = repository.sources(state.item, state.episode)
                updateSheet {
                    it.copy(
                        isResolving = false,
                        sources = sources,
                        pendingAction = if (sources.isEmpty()) null else action,
                        error = if (sources.isEmpty()) "No playable sources found" else null,
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (e is AuthRequiredException) {
                    filmanSession.clear()
                    _showLogin.value = true
                }
                updateSheet {
                    it.copy(isResolving = false, pendingAction = null, error = e.message ?: "Couldn't load sources")
                }
            }
        }
    }

    fun chooseSource(source: StreamSource) {
        val state = _sheet.value ?: return
        val item = state.item
        val episode = state.episode
        val title = if (episode != null) "${item.title} · ${episode.label}" else item.title
        when (state.pendingAction) {
            MediaAction.WATCH -> _player.value = PlaybackSource(title, source.url, source.headers)
            MediaAction.DOWNLOAD -> {
                val id = if (episode != null) "${item.id}_${episode.season}_${episode.episode}" else "${item.id}"
                downloads.start(id, title, item.posterUrl, source)
            }
            null -> return
        }
        closeSheet()
    }

    fun playDownload(item: DownloadItem) {
        _player.value = PlaybackSource(item.title, downloads.pathOf(item))
    }

    fun deleteDownload(item: DownloadItem) = downloads.remove(item.id)

    fun closePlayer() {
        _player.value = null
    }

    private inline fun updateSheet(transform: (SheetState) -> SheetState) =
        _sheet.update { it?.let(transform) }

    private companion object {
        const val SEARCH_DEBOUNCE_MS = 400L
    }
}
