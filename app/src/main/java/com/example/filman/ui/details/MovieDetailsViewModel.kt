package com.example.filman.ui.details

import androidx.compose.runtime.Immutable
import androidx.lifecycle.viewModelScope
import com.example.filman.data.local.FavoritesManager
import com.example.filman.data.local.ProgressManager
import com.example.filman.data.model.DetailedMedia
import com.example.filman.data.model.MovieItem
import com.example.filman.data.model.ProgressItem
import com.example.filman.data.source.ContentSource
import com.example.filman.ui.base.BaseViewModel
import com.example.filman.ui.base.FilmanEvent
import com.example.filman.ui.base.SharedState
import com.example.filman.ui.base.StateWithShared
import com.example.filman.ui.components.sections.TabRowSectionItem
import com.example.filman.ui.details.MovieDetailsEffect.NavigateToActor
import com.example.filman.ui.details.MovieDetailsEffect.NavigateToPlayer
import kotlinx.coroutines.launch

internal sealed interface MovieDetailsEvent : FilmanEvent {
    data class OpenActorDetails(val url: String) : MovieDetailsEvent
    data class LoadDetails(val url: String) : MovieDetailsEvent
    data object ToggleFavorite : MovieDetailsEvent
    data class PlayItem(val url: String) : MovieDetailsEvent
    data class TabChanged(val tab: TabRowSectionItem) : MovieDetailsEvent
}

@Immutable
internal data class MovieDetailsState(
    override val shared: SharedState = SharedState(),
    val mediaDetails: DetailedMedia? = null,
    val isFavorite: Boolean = false,
    val progressMap: Map<String, ProgressItem> = emptyMap(),
    val progressList: List<ProgressItem> = emptyList(),
    val selectedTabId: Int = TabRowItemId.Similar.id,
) : StateWithShared<MovieDetailsState> {
    override fun copyWithShared(shared: SharedState) = copy(shared = shared)
}

internal enum class TabRowItemId(val id: Int) {
    Episodes(0), Similar(1), Details(2)
}

internal sealed interface WatchButtonState {
    val url: String

    data class Default(override val url: String = "") : WatchButtonState
    data class WatchAgain(override val url: String) : WatchButtonState
    data class Continue(override val url: String) : WatchButtonState
    data class WatchNextEpisode(
        val season: String,
        val episode: String,
        override val url: String,
    ) : WatchButtonState

    data class ContinueEpisode(
        val season: String,
        val episode: String,
        override val url: String,
    ) : WatchButtonState
}

internal sealed interface MovieDetailsEffect {
    data object NavigateToAuth : MovieDetailsEffect
    data class NavigateToPlayer(val url: String) : MovieDetailsEffect
    data class NavigateToDetails(val url: String) : MovieDetailsEffect
    data class NavigateToActor(val url: String) : MovieDetailsEffect
}

internal class MovieDetailsViewModel(
    private val scraper: ContentSource,
    favoritesManager: FavoritesManager,
    progressManager: ProgressManager,
) : BaseViewModel<MovieDetailsState, MovieDetailsEvent, MovieDetailsEffect>(
    initialState = MovieDetailsState(),
    favoritesManager = favoritesManager,
    progressManager = progressManager,
) {

    init {
        viewModelScope.launch {
            progressManager.progressItemsFlow.collect { progressList ->
                updateState { state ->
                    state.copy(
                        progressMap = progressList.associateBy { it.url },
                        progressList = progressList,
                    )
                }
            }
        }
    }

    override fun getAuthErrorEffect(): MovieDetailsEffect = MovieDetailsEffect.NavigateToAuth

    override fun getNavigateToDetailsEffect(url: String): MovieDetailsEffect =
        MovieDetailsEffect.NavigateToDetails(url)

    override fun handleEvent(event: MovieDetailsEvent) {
        when (event) {
            is MovieDetailsEvent.OpenActorDetails -> sendEffect(NavigateToActor(event.url))
            is MovieDetailsEvent.LoadDetails -> loadDetails(event.url)
            is MovieDetailsEvent.ToggleFavorite -> toggleFavorite()
            is MovieDetailsEvent.PlayItem -> sendEffect(NavigateToPlayer(event.url))
            is MovieDetailsEvent.TabChanged -> updateState { it.copy(selectedTabId = event.tab.id) }
        }
    }

    private fun loadDetails(url: String) {
        launchHandled(
            onError = {
                updateSharedState { it.copy(isLoading = false) }
                handleError(it)
            },
        ) {
            updateSharedState { it.copy(isLoading = true) }

            val details = scraper.getMediaDetails(url)
            val isFavorite = favoritesManager?.isFavorite(url) == true

            updateState {
                it.copy(
                    shared = it.shared.copy(isLoading = false),
                    mediaDetails = details,
                    isFavorite = isFavorite,
                    selectedTabId = details.getDefaultTabId(),
                )
            }
        }
    }

    private fun toggleFavorite() {
        val current = currentState
        val details = current.mediaDetails?.baseItem ?: return

        if (current.isFavorite) {
            favoritesManager?.removeFavorite(details.url)
            updateState { it.copy(isFavorite = false) }
        } else {
            val targetTitle = details.titlePl.substringBefore(" - ").trim()
            val movieToSave = MovieItem(
                url = details.url,
                titlePl = targetTitle,
                posterUrl = details.posterUrl,
            )
            favoritesManager?.addFavorite(movieToSave)
            updateState { it.copy(isFavorite = true) }
        }
    }

    override fun handleStaleData(staleData: Any) {
        val details = staleData as? DetailedMedia ?: return
        val isFavorite = favoritesManager?.isFavorite(details.baseItem.url) == true
        updateState {
            it.copy(
                mediaDetails = details,
                isFavorite = isFavorite,
                selectedTabId = details.getDefaultTabId(),
            )
        }
    }

    private fun DetailedMedia?.getDefaultTabId() = if (this?.baseItem?.seasons != null) {
        TabRowItemId.Episodes.id
    } else {
        TabRowItemId.Similar.id
    }
}
