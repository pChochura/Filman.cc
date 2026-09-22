package com.pointlessapps.filman.ui.screensaver

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pointlessapps.filman.data.scraper.TmdbClient
import com.pointlessapps.filman.data.scraper.TrendingMovie
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

import com.pointlessapps.filman.data.local.WatchlistManager
import com.pointlessapps.filman.data.scraper.FilmanScraper
import kotlinx.coroutines.flow.firstOrNull

class ScreensaverViewModel(
    private val tmdbClient: TmdbClient,
    private val filmanScraper: FilmanScraper,
    private val watchlistManager: WatchlistManager,
) : ViewModel() {

    private val _movies = MutableStateFlow<List<TrendingMovie>>(emptyList())
    val movies = _movies.asStateFlow()

    init {
        viewModelScope.launch {
            _movies.value = tmdbClient.getTrendingMovies()
        }
    }

    fun addToWatchlist(movie: TrendingMovie, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            val results = filmanScraper.searchMovies(movie.title).firstOrNull { it.movies.isNotEmpty() || it.tvShows.isNotEmpty() }
            if (results != null) {
                val foundMovie = results.movies.firstOrNull { it.titlePl.equals(movie.title, ignoreCase = true) || it.titleEn.equals(movie.title, ignoreCase = true) }
                    ?: results.tvShows.firstOrNull { it.titlePl.equals(movie.title, ignoreCase = true) || it.titleEn.equals(movie.title, ignoreCase = true) }
                    ?: results.movies.firstOrNull()
                    ?: results.tvShows.firstOrNull()
                
                if (foundMovie != null) {
                    watchlistManager.addToWatchlist(foundMovie.copy(posterUrl = foundMovie.posterUrl ?: movie.posterUrl))
                    onResult(true)
                    return@launch
                }
            }
            onResult(false)
        }
    }
}
