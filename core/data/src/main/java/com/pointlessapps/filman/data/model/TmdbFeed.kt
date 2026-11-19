package com.pointlessapps.filman.data.model

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

@Serializable
enum class TrendingWindow(val apiValue: String) {
    DAY("day"),
    WEEK("week"),
}

@Serializable
@Immutable
sealed interface TmdbFeed {
    @Serializable
    data class TrendingAll(val window: TrendingWindow = TrendingWindow.WEEK) : TmdbFeed

    @Serializable
    data class TrendingMovies(val window: TrendingWindow = TrendingWindow.WEEK) : TmdbFeed

    @Serializable
    data class TrendingTv(val window: TrendingWindow = TrendingWindow.WEEK) : TmdbFeed

    // Movies
    @Serializable data object MoviesNowPlaying : TmdbFeed
    @Serializable data object MoviesPopular : TmdbFeed
    @Serializable data object MoviesTopRated : TmdbFeed
    @Serializable data object MoviesUpcoming : TmdbFeed
    @Serializable data object MoviesNewReleases : TmdbFeed

    // TV Shows
    @Serializable data object TvOnTheAir : TmdbFeed
    @Serializable data object TvAiringToday : TmdbFeed
    @Serializable data object TvPopular : TmdbFeed
    @Serializable data object TvTopRated : TmdbFeed
    @Serializable data object TvNewReleases : TmdbFeed

    // For Kids
    @Serializable data object KidsPopularMovies : TmdbFeed
    @Serializable data object KidsTvShows : TmdbFeed
    @Serializable data object KidsTopRatedFamily : TmdbFeed
    @Serializable data object KidsDisneyPixar : TmdbFeed
    @Serializable data object KidsDreamWorksGhibli : TmdbFeed

    // Discover by genre
    @Serializable
    data class DiscoverByGenre(
        val mediaType: MediaType,
        val genreId: Int,
    ) : TmdbFeed

    // Discover with granular filters
    @Serializable
    data class DiscoverFiltered(
        val mediaType: MediaType,
        val filter: MediaFilterState,
    ) : TmdbFeed

    // Recommendations for a specific media item
    @Serializable
    data class Recommendations(val ref: MediaRef) : TmdbFeed

    // Personalized recommendations based on user's library
    @Serializable
    data object PersonalizedForYou : TmdbFeed
}
