package com.pointlessapps.filman.ui.watchhistory

import android.text.format.DateFormat
import androidx.compose.runtime.Immutable
import androidx.lifecycle.viewModelScope
import com.pointlessapps.filman.data.local.FavoritesManager
import com.pointlessapps.filman.data.local.ProgressManager
import com.pointlessapps.filman.data.model.DetailsRequest
import com.pointlessapps.filman.data.model.MovieItem
import com.pointlessapps.filman.ui.base.BaseViewModel
import com.pointlessapps.filman.ui.base.FilmanEvent
import com.pointlessapps.filman.ui.base.SharedState
import com.pointlessapps.filman.ui.base.StateWithShared
import com.pointlessapps.filman.ui.components.sections.MoviesGridItem
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

internal sealed interface WatchHistoryEvent : FilmanEvent

@Immutable
internal data class WatchHistoryState(
    override val shared: SharedState = SharedState(),
) : StateWithShared<WatchHistoryState> {
    override fun copyWithShared(shared: SharedState) = copy(shared = shared)
}

internal sealed interface WatchHistoryEffect {
    data object NavigateToAuth : WatchHistoryEffect

    data class NavigateToDetails(
        val request: DetailsRequest,
    ) : WatchHistoryEffect
}

internal data class WatchHistorySectionModel(
    val title: String,
    val items: List<MoviesGridItem>,
)

internal class WatchHistoryViewModel(
    progressManager: ProgressManager,
    favoritesManager: FavoritesManager,
) : BaseViewModel<WatchHistoryState, WatchHistoryEvent, WatchHistoryEffect>(
    initialState = WatchHistoryState(shared = SharedState(isLoading = false)),
    favoritesManager = favoritesManager,
    progressManager = progressManager,
) {

    val groupedItems = progressManager.progressItemsFlow.map { list ->
        list.distinctBy { it.parentUrl ?: it.url }
            .groupBy { DateFormat.format(DATE_FORMAT, it.timestamp).toString() }
            .map { (dateStr, itemsForDate) ->
                WatchHistorySectionModel(
                    title = dateStr,
                    items = itemsForDate.map { progressItem ->
                        MoviesGridItem.Single(
                            movieItem = MovieItem(
                                url = progressItem.parentUrl ?: progressItem.url,
                                titlePl = progressItem.seriesTitle ?: progressItem.titlePl,
                                posterUrl = progressItem.posterUrl,
                                isTvShow = progressItem.season != null,
                            ),
                        )
                    },
                )
            }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList(),
    )

    override fun getAuthErrorEffect(): WatchHistoryEffect = WatchHistoryEffect.NavigateToAuth

    override fun getNavigateToDetailsEffect(
        url: String,
        autoplay: Boolean,
        episodeUrl: String?,
        request: DetailsRequest?,
    ): WatchHistoryEffect = WatchHistoryEffect.NavigateToDetails(request ?: DetailsRequest.Url(url))

    override fun handleEvent(event: WatchHistoryEvent) = Unit

    companion object {
        private const val DATE_FORMAT = "dd MMMM yyyy"
    }
}
