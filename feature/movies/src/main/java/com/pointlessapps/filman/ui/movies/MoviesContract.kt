package com.pointlessapps.filman.ui.movies

import androidx.compose.runtime.Immutable
import com.pointlessapps.filman.data.model.DetailsRequest
import com.pointlessapps.filman.data.model.FilterOption
import com.pointlessapps.filman.data.model.MediaFilterState
import com.pointlessapps.filman.data.model.SortOption
import com.pointlessapps.filman.ui.base.TiwiEvent
import com.pointlessapps.filman.ui.base.SharedState
import com.pointlessapps.filman.ui.base.StateWithShared
import com.pointlessapps.filman.ui.components.sections.MoviesSection
import com.pointlessapps.filman.ui.core.TextValue

sealed interface MoviesEvent : TiwiEvent {
    data object LoadHomeData : MoviesEvent

    data class LoadMoreForSection(
        val sectionTitle: Int,
    ) : MoviesEvent

    data object OpenFilterMenu : MoviesEvent

    data class UpdateFilter(
        val filterState: MediaFilterState,
    ) : MoviesEvent

    data class ToggleGenre(
        val genreId: Int,
    ) : MoviesEvent

    data class SelectSort(
        val sortOption: SortOption,
    ) : MoviesEvent

    data object ClearDecade : MoviesEvent

    data object ClearRating : MoviesEvent

    data object ResetFilters : MoviesEvent
}

@Immutable
data class MoviesState(
    override val shared: SharedState = SharedState(),
    val filterState: MediaFilterState = MediaFilterState(),
    val availableGenres: List<FilterOption> = emptyList(),
    val isFiltering: Boolean = false,
    val filteredSection: MoviesSection? = null,
    val filterErrorMessage: TextValue? = null,
) : StateWithShared<MoviesState> {
    override fun copyWithShared(shared: SharedState) = copy(shared = shared)
}

sealed interface MoviesEffect {
    data object ScrollToTop : MoviesEffect

    data object NavigateToAuth : MoviesEffect

    data class NavigateToDetails(
        val request: DetailsRequest,
    ) : MoviesEffect
}
