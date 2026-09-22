package com.pointlessapps.filman.ui.watchhistory

import android.text.format.DateFormat
import androidx.compose.runtime.Immutable
import androidx.lifecycle.viewModelScope
import com.pointlessapps.filman.core.ui.R
import com.pointlessapps.filman.data.local.FavoritesManager
import com.pointlessapps.filman.data.local.ProgressManager
import com.pointlessapps.filman.data.local.WatchlistManager
import com.pointlessapps.filman.data.model.DetailsRequest
import com.pointlessapps.filman.data.model.MovieItem
import com.pointlessapps.filman.data.model.ProgressItem
import com.pointlessapps.filman.ui.base.BaseViewModel
import com.pointlessapps.filman.ui.base.FilmanEvent
import com.pointlessapps.filman.ui.base.SharedState
import com.pointlessapps.filman.ui.base.StateWithShared
import com.pointlessapps.filman.ui.components.sections.MoviesGridItem
import com.pointlessapps.filman.ui.core.TextValue
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

internal data class WatchHistorySummary(
    val totalWatchTime: TextValue,
    val moviesCount: Int,
    val episodesCount: Int,
)

internal class WatchHistoryViewModel(
    progressManager: ProgressManager,
    favoritesManager: FavoritesManager,
    watchlistManager: WatchlistManager,
) : BaseViewModel<WatchHistoryState, WatchHistoryEvent, WatchHistoryEffect>(
    initialState = WatchHistoryState(shared = SharedState(isLoading = false)),
    favoritesManager = favoritesManager,
    watchlistManager = watchlistManager,
    progressManager = progressManager,
) {

    val summary = progressManager.progressItemsFlow.map { list ->
        val totalMs = list.sumOf { item ->
            when (item) {
                is ProgressItem.InProgress -> item.progressMs
                is ProgressItem.Watched -> item.progressMs
                else -> 0L
            }
        }

        if (totalMs > 0) {
            val totalMinutes = (totalMs / 1000) / 60
            val hours = totalMinutes / 60
            val minutes = totalMinutes % 60
            val days = hours / 24
            val remainingHours = hours % 24

            val formattedTime = if (days > 0 && remainingHours > 0 && minutes > 0) {
                TextValue.StringResource(R.string.time_format_d_h_m, days, remainingHours, minutes)
            } else if (days > 0 && remainingHours == 0L && minutes > 0) {
                TextValue.StringResource(R.string.time_format_d_m, days, minutes)
            } else if (days > 0 && remainingHours > 0) {
                TextValue.StringResource(R.string.time_format_d_h_m, days, remainingHours, 0)
            } else if (days == 0L && remainingHours > 0 && minutes > 0) {
                TextValue.StringResource(R.string.time_format_h_m, remainingHours, minutes)
            } else if (days == 0L && remainingHours > 0) {
                TextValue.StringResource(R.string.time_format_h_m, remainingHours, 0)
            } else {
                TextValue.StringResource(R.string.time_format_m, minutes.coerceAtLeast(1))
            }

            WatchHistorySummary(
                totalWatchTime = formattedTime,
                moviesCount = list.count { it.season == null },
                episodesCount = list.count { it.season != null },
            )
        } else {
            null
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = null,
    )

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
