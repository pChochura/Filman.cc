package com.pointlessapps.filman.ui.screensaver

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pointlessapps.filman.data.api.TiwiRepository
import com.pointlessapps.filman.data.local.FavoritesManager
import com.pointlessapps.filman.data.local.WatchlistManager
import com.pointlessapps.filman.data.model.MovieItem
import com.pointlessapps.filman.data.model.TmdbFeed
import com.pointlessapps.filman.data.model.TrendingWindow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ScreensaverViewModel(
    private val repository: TiwiRepository,
    private val watchlistManager: WatchlistManager,
    private val favoritesManager: FavoritesManager? = null,
) : ViewModel() {

    private val _movies = MutableStateFlow<List<MovieItem>>(emptyList())
    val movies = _movies.asStateFlow()
    val watchlist = watchlistManager.watchlistFlow

    init {
        viewModelScope.launch {
            loadSlides()
        }
    }

    private suspend fun loadSlides() {
        val initial = repository.getFeedPage(TmdbFeed.TrendingAll(TrendingWindow.DAY)).movies
            .filter { it.backgroundUrl?.isNotBlank() == true || it.posterUrl.isNotBlank() }
        _movies.value = initial.distinctBy { it.url }

        val additionalFeeds = listOf(
            TmdbFeed.MoviesPopular,
            TmdbFeed.TvPopular,
            TmdbFeed.MoviesTopRated,
            TmdbFeed.TrendingAll(TrendingWindow.WEEK),
        )
        val pool = mutableListOf<MovieItem>()
        for (feed in additionalFeeds) {
            val page = repository.getFeedPage(feed, page = 1)
            pool.addAll(page.movies)
        }
        pool.addAll(watchlistManager.watchlistFlow.value)
        favoritesManager?.let { pool.addAll(it.favoritesFlow.value) }

        val validPool = pool
            .filter { it.backgroundUrl?.isNotBlank() == true || it.posterUrl.isNotBlank() }
            .distinctBy { it.url }

        if (validPool.isNotEmpty()) {
            val combined = (_movies.value + validPool.shuffled()).distinctBy { it.url }
            _movies.value = combined
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
