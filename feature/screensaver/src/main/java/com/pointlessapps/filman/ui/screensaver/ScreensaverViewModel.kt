package com.pointlessapps.filman.ui.screensaver

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pointlessapps.filman.data.api.TiwiRepository
import com.pointlessapps.filman.data.local.WatchlistManager
import com.pointlessapps.filman.data.model.MovieItem
import com.pointlessapps.filman.data.model.TmdbFeed
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ScreensaverViewModel(
    private val repository: TiwiRepository,
    private val watchlistManager: WatchlistManager,
) : ViewModel() {

    private val _movies = MutableStateFlow<List<MovieItem>>(emptyList())
    val movies = _movies.asStateFlow()
    val watchlist = watchlistManager.watchlistFlow

    init {
        viewModelScope.launch {
            _movies.value = repository.getFeedPage(TmdbFeed.TrendingAll()).movies
        }
    }

    fun toggleWatchlist(movie: MovieItem): Boolean {
        return if (watchlistManager.isInWatchlist(movie.url)) {
            watchlistManager.removeFromWatchlist(movie.url)
            false
        } else {
            watchlistManager.addToWatchlist(movie)
            true
        }
    }
}
