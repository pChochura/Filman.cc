package com.example.filman.ui.base

import androidx.compose.runtime.Immutable
import com.example.filman.data.model.MovieItem
import com.example.filman.ui.components.OverlayMenuData
import com.example.filman.ui.components.sections.MoviesSection

@Immutable
internal data class SharedState(
    val isLoading: Boolean = true,
    val isLoadingNextPage: Boolean = false,
    val errorMessage: String? = null,
    val overlayMenuData: OverlayMenuData? = null,
    val featuredItems: List<MovieItem> = emptyList(),
    val moviesSections: List<MoviesSection> = emptyList(),
)

internal interface StateWithShared<S> {
    val shared: SharedState
    fun copyWithShared(shared: SharedState): S

    val isLoading: Boolean get() = shared.isLoading
    val isLoadingNextPage: Boolean get() = shared.isLoadingNextPage
    val errorMessage: String? get() = shared.errorMessage
    val overlayMenuData: OverlayMenuData? get() = shared.overlayMenuData
    val featuredItems: List<MovieItem> get() = shared.featuredItems
    val moviesSections: List<MoviesSection> get() = shared.moviesSections
}
