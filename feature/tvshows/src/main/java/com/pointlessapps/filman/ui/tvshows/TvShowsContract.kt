package com.pointlessapps.filman.ui.tvshows

import androidx.compose.runtime.Immutable
import com.pointlessapps.filman.data.model.DetailsRequest
import com.pointlessapps.filman.data.model.FilterOption
import com.pointlessapps.filman.data.model.MediaFilterState
import com.pointlessapps.filman.data.model.SortOption
import com.pointlessapps.filman.ui.base.FilmanEvent
import com.pointlessapps.filman.ui.base.SharedState
import com.pointlessapps.filman.ui.base.StateWithShared
import com.pointlessapps.filman.ui.components.sections.MoviesSection
import com.pointlessapps.filman.ui.core.TextValue

sealed interface TvShowsEvent : FilmanEvent {
    data object LoadHomeData : TvShowsEvent

    data class LoadMoreForSection(
        val sectionTitle: Int,
    ) : TvShowsEvent

    data object OpenFilterMenu : TvShowsEvent

    data class UpdateFilter(
        val filterState: MediaFilterState,
    ) : TvShowsEvent

    data class ToggleGenre(
        val genreId: Int,
    ) : TvShowsEvent

    data class SelectSort(
        val sortOption: SortOption,
    ) : TvShowsEvent

    data object ClearDecade : TvShowsEvent

    data object ClearRating : TvShowsEvent

    data object ResetFilters : TvShowsEvent
}

@Immutable
data class TvShowsState(
    override val shared: SharedState = SharedState(),
    val filterState: MediaFilterState = MediaFilterState(),
    val availableGenres: List<FilterOption> = emptyList(),
    val isFiltering: Boolean = false,
    val filteredSection: MoviesSection? = null,
    val filterErrorMessage: TextValue? = null,
) : StateWithShared<TvShowsState> {
    override fun copyWithShared(shared: SharedState) = copy(shared = shared)
}

sealed interface TvShowsEffect {
    data object ScrollToTop : TvShowsEffect

    data object NavigateToAuth : TvShowsEffect

    data class NavigateToDetails(
        val request: DetailsRequest,
    ) : TvShowsEffect
}
