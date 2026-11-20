package com.pointlessapps.filman.ui.search

import androidx.compose.runtime.Immutable
import com.pointlessapps.filman.data.model.DetailsRequest
import com.pointlessapps.filman.data.model.FilterOption
import com.pointlessapps.filman.data.model.MediaFilterState
import com.pointlessapps.filman.data.model.MediaTypeFilter
import com.pointlessapps.filman.data.model.SortOption
import com.pointlessapps.filman.ui.base.TiwiEvent
import com.pointlessapps.filman.ui.base.SharedState
import com.pointlessapps.filman.ui.base.StateWithShared

sealed interface SearchEvent : TiwiEvent {
    data object RetrySearch : SearchEvent

    data object LoadHomeData : SearchEvent

    data class LoadSearchData(
        val query: String,
        val force: Boolean = false,
    ) : SearchEvent

    data class LoadSearchDataByCategory(
        val category: FilterOption,
        val force: Boolean = false,
    ) : SearchEvent

    data object ClearSearch : SearchEvent

    data object ClearQuery : SearchEvent

    data object OpenFilterMenu : SearchEvent

    data class UpdateFilter(
        val filterState: MediaFilterState,
    ) : SearchEvent

    data class ToggleGenre(
        val genreId: Int,
    ) : SearchEvent

    data class SelectSort(
        val sortOption: SortOption,
    ) : SearchEvent

    data class SelectMediaType(
        val mediaTypeFilter: MediaTypeFilter,
    ) : SearchEvent

    data object ClearDecade : SearchEvent

    data object ClearRating : SearchEvent

    data object ResetFilters : SearchEvent

    data class LoadMoreForSection(
        val sectionTitle: Int,
    ) : SearchEvent

    data class OpenSearchHistoryContextMenu(
        val query: String,
    ) : SearchEvent

    data class RemoveSearchHistory(
        val query: String,
    ) : SearchEvent

    data object ClearAllSearchHistory : SearchEvent

    data object RequestAuth : SearchEvent
}

@Immutable
data class SearchState(
    override val shared: SharedState = SharedState(),
    val categories: List<FilterOption> = emptyList(),
    val selectedCategory: FilterOption? = null,
    val filterState: MediaFilterState = MediaFilterState(),
    val query: String = "",
    val searchHistory: List<String> = emptyList(),
    val isSearching: Boolean = false,
) : StateWithShared<SearchState> {
    override fun copyWithShared(shared: SharedState) = copy(shared = shared)
}

sealed interface SearchEffect {
    data object ScrollToTop : SearchEffect

    data object NavigateToAuth : SearchEffect

    data class NavigateToDetails(
        val request: DetailsRequest,
    ) : SearchEffect

    data class FocusHistoryItem(
        val query: String?,
    ) : SearchEffect

    data object FocusSearchResults : SearchEffect
}
