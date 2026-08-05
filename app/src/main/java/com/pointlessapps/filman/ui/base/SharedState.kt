package com.pointlessapps.filman.ui.base

import androidx.compose.runtime.Immutable
import com.pointlessapps.filman.data.model.MovieItem
import com.pointlessapps.filman.ui.components.OverlayMenuData
import com.pointlessapps.filman.ui.components.sections.MoviesSection
import com.pointlessapps.filman.ui.core.TextValue

@Immutable
internal data class SharedState(
    val isLoading: Boolean = true,
    val isLoadingNextPage: Boolean = false,
    val errorMessage: TextValue? = null,
    val overlayMenuData: OverlayMenuData? = null,
    val featuredItems: List<MovieItem> = emptyList(),
    val moviesSections: List<MoviesSection> = emptyList(),
    val isShowingStaleData: Boolean = false,
    val progressMap: Map<String, Float> = emptyMap(),
)

internal interface StateWithShared<S> {
    val shared: SharedState
    fun copyWithShared(shared: SharedState): S

    val isLoading: Boolean get() = shared.isLoading
    val isLoadingNextPage: Boolean get() = shared.isLoadingNextPage
    val errorMessage: TextValue? get() = shared.errorMessage
    val overlayMenuData: OverlayMenuData? get() = shared.overlayMenuData
    val featuredItems: List<MovieItem> get() = shared.featuredItems
    val moviesSections: List<MoviesSection> get() = shared.moviesSections
    val isShowingStaleData: Boolean get() = shared.isShowingStaleData
    val progressMap: Map<String, Float> get() = shared.progressMap
}
