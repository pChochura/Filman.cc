package com.pointlessapps.filman.ui.screensaver

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pointlessapps.filman.data.scraper.TmdbClient
import com.pointlessapps.filman.data.scraper.TrendingMovie
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

internal class ScreensaverViewModel(
    private val tmdbClient: TmdbClient,
) : ViewModel() {

    private val _movies = MutableStateFlow<List<TrendingMovie>>(emptyList())
    val movies = _movies.asStateFlow()

    init {
        viewModelScope.launch {
            _movies.value = tmdbClient.getTrendingMovies()
        }
    }
}
