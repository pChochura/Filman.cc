package com.pointlessapps.filman.ui.home

import androidx.compose.runtime.Immutable
import com.pointlessapps.filman.data.model.DetailsRequest
import com.pointlessapps.filman.data.model.MovieItem
import com.pointlessapps.filman.data.model.ProgressItem
import com.pointlessapps.filman.ui.base.TiwiEvent
import com.pointlessapps.filman.ui.base.SharedState
import com.pointlessapps.filman.ui.base.StateWithShared

sealed interface HomeEvent : TiwiEvent {
    data object LoadHomeData : HomeEvent
    data class ClearNewEpisode(val url: String) : HomeEvent
    data class LoadMoreForSection(val sectionTitle: Int) : HomeEvent
}

@Immutable
data class HomeState(
    override val shared: SharedState = SharedState(),
    val progressItems: List<ProgressItem> = emptyList(),
    val jumpBackInItems: List<ProgressItem> = emptyList(),
    val watchlist: List<MovieItem> = emptyList(),
    val newEpisodes: List<MovieItem> = emptyList(),
) : StateWithShared<HomeState> {
    override fun copyWithShared(shared: SharedState) = copy(shared = shared)
}

sealed interface HomeEffect {
    data object ScrollToTop : HomeEffect

    data object FocusFirstGridItem : HomeEffect

    data object NavigateToAuth : HomeEffect

    data class NavigateToDetails(
        val request: DetailsRequest,
        val autoplay: Boolean,
        val episodeUrl: String? = null,
    ) : HomeEffect

    data class OverrideFocus(
        val itemId: String,
    ) : HomeEffect
}
