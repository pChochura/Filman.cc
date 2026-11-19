package com.pointlessapps.filman.ui.favorites

import androidx.lifecycle.viewModelScope
import com.pointlessapps.filman.data.local.FavoritesManager
import com.pointlessapps.filman.data.local.ProgressManager
import com.pointlessapps.filman.data.local.WatchlistManager
import com.pointlessapps.filman.data.model.DetailsRequest
import com.pointlessapps.filman.ui.base.BaseViewModel
import com.pointlessapps.filman.ui.base.SharedState
import com.pointlessapps.filman.ui.components.sections.MoviesGridItem
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

class FavoritesViewModel(
    favoritesManager: FavoritesManager,
    watchlistManager: WatchlistManager,
    progressManager: ProgressManager,
) : BaseViewModel<FavoritesState, FavoritesEvent, FavoritesEffect>(
    initialState = FavoritesState(shared = SharedState(isLoading = false)),
    favoritesManager = favoritesManager,
    watchlistManager = watchlistManager,
    progressManager = progressManager,
) {
    val favorites = favoritesManager.favoritesFlow.map { list ->
        list.map { MoviesGridItem.Single(it) }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList(),
    )

    override fun getAuthErrorEffect(): FavoritesEffect = FavoritesEffect.NavigateToAuth

    override fun getNavigateToDetailsEffect(
        url: String,
        autoplay: Boolean,
        episodeUrl: String?,
        request: DetailsRequest?,
    ): FavoritesEffect = FavoritesEffect.NavigateToDetails(request ?: DetailsRequest.Url(url))

    override fun handleEvent(event: FavoritesEvent) = Unit
}
