package com.pointlessapps.filman.ui.screensaver

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pointlessapps.filman.data.api.FilmanRepository
import com.pointlessapps.filman.data.local.WatchlistManager
import com.pointlessapps.filman.data.model.MovieItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ScreensaverViewModel(
    private val filmanScraper: FilmanRepository,
    private val watchlistManager: WatchlistManager,
) : ViewModel() {

    private val _movies = MutableStateFlow<List<MovieItem>>(emptyList())
    val movies = _movies.asStateFlow()
    val watchlist = watchlistManager.watchlistFlow

    init {
        viewModelScope.launch {
            _movies.value = filmanScraper.getCategoryPage("").movies
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
