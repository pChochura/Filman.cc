package com.pointlessapps.filman.ui.details

import androidx.compose.runtime.Immutable
import com.pointlessapps.filman.data.model.DetailedMedia
import com.pointlessapps.filman.data.model.DetailsRequest
import com.pointlessapps.filman.data.model.MovieItem
import com.pointlessapps.filman.data.model.ProgressItem
import com.pointlessapps.filman.ui.base.TiwiEvent
import com.pointlessapps.filman.ui.base.SharedState
import com.pointlessapps.filman.ui.base.StateWithShared
import com.pointlessapps.filman.ui.components.sections.TabRowSectionItem
import com.pointlessapps.filman.ui.core.TextValue

sealed interface MovieDetailsEvent : TiwiEvent {
    data class OpenActorDetails(
        val url: String,
    ) : MovieDetailsEvent

    data class LoadDetails(
        val request: DetailsRequest,
    ) : MovieDetailsEvent

    data class PlayItem(
        val url: String,
    ) : MovieDetailsEvent

    data class WatchTrailer(
        val url: String,
    ) : MovieDetailsEvent

    data class TabChanged(
        val tab: TabRowSectionItem,
    ) : MovieDetailsEvent

    data object LoadMoreRecommendations : MovieDetailsEvent
}

@Immutable
data class MovieDetailsState(
    override val shared: SharedState = SharedState(),
    val mediaDetails: DetailedMedia? = null,
    val isExtractingLinks: Boolean = false,
    val isFavorite: Boolean = false,
    val isInWatchlist: Boolean = false,
    val progressList: List<ProgressItem> = emptyList(),
    val selectedTabId: Int = TabRowItemId.Similar.id,
    val trailerUrl: String? = null,
    val tmdbRecommendations: List<MovieItem> = emptyList(),
    val tmdbRecommendationsPage: Int = 1,
    val tmdbRecommendationsHasMore: Boolean = false,
    val isLoadingMoreRecommendations: Boolean = false,
) : StateWithShared<MovieDetailsState> {
    override fun copyWithShared(shared: SharedState) = copy(shared = shared)
}

enum class TabRowItemId(
    val id: Int,
) {
    Episodes(0),
    Recommended(1),
    Similar(2),
    Details(3),
}

sealed interface WatchButtonState {
    val url: String

    data class Default(
        override val url: String = "",
    ) : WatchButtonState

    data object Unavailable : WatchButtonState {
        override val url: String = ""
    }

    data object Loading : WatchButtonState {
        override val url: String = ""
    }

    data class WatchAgain(
        override val url: String,
    ) : WatchButtonState

    data class Continue(
        override val url: String,
    ) : WatchButtonState

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

sealed interface MovieDetailsEffect {
    data class ShowToast(val message: TextValue) : MovieDetailsEffect

    data object NavigateToAuth : MovieDetailsEffect

    data object NavigateBack : MovieDetailsEffect

    data class NavigateToPlayer(
        val url: String,
    ) : MovieDetailsEffect

    data class NavigateToDetails(
        val request: DetailsRequest,
    ) : MovieDetailsEffect

    data class NavigateToActor(
        val url: String,
    ) : MovieDetailsEffect
}
