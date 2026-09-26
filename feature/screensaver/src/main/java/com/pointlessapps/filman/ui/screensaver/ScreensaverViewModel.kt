package com.pointlessapps.filman.ui.screensaver

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pointlessapps.filman.data.api.FilmanRepository
import com.pointlessapps.filman.data.api.TrendingMovie
import com.pointlessapps.filman.data.local.WatchlistManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch

class ScreensaverViewModel(
    private val filmanScraper: FilmanRepository,
    private val watchlistManager: WatchlistManager,
) : ViewModel() {

    private val _movies = MutableStateFlow<List<TrendingMovie>>(emptyList())
    val movies = _movies.asStateFlow()

    init {
        viewModelScope.launch {
            val res = filmanScraper.getCategoryPage("")
            _movies.value = res.movies.map {
                TrendingMovie(
                    title = it.titlePl,
                    overview = it.description,
                    rating = 0.0,
                    posterUrl = it.posterUrl,
                    releaseYear = it.year,
                )
            }

        }
    }

    fun addToWatchlist(movie: TrendingMovie, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            val results = filmanScraper.searchMovies(movie.title)
                .firstOrNull { it.movies.isNotEmpty() || it.tvShows.isNotEmpty() }
            if (results != null) {
                val foundMovie = results.movies.firstOrNull {
                    it.titlePl.equals(
                        movie.title,
                        ignoreCase = true,
                    ) || it.titleEn.equals(movie.title, ignoreCase = true)
                }
                    ?: results.tvShows.firstOrNull {
                        it.titlePl.equals(
                            movie.title,
                            ignoreCase = true,
                        ) || it.titleEn.equals(movie.title, ignoreCase = true)
                    }
                    ?: results.movies.firstOrNull()
                    ?: results.tvShows.firstOrNull()

                if (foundMovie != null) {
                    watchlistManager.addToWatchlist(foundMovie.copy(posterUrl = foundMovie.posterUrl))
                    onResult(true)
                    return@launch
                }
            }
            onResult(false)
        }
    }
}
