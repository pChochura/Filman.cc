package com.pointlessapps.filman.data.api.tmdb

import com.pointlessapps.filman.data.model.MediaRef
import com.pointlessapps.filman.data.model.MediaType
import com.pointlessapps.filman.data.model.SortOption
import com.pointlessapps.filman.data.model.TmdbFeed
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal object TmdbUrlBuilder {
    private const val BASE_URL = "https://api.themoviedb.org/3"

    fun todayIsoDate(): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(Date())

    fun defaultMediaType(feed: TmdbFeed): String? = when (feed) {
        is TmdbFeed.TrendingAll -> null
        is TmdbFeed.TrendingMovies,
        TmdbFeed.MoviesNowPlaying,
        TmdbFeed.MoviesPopular,
        TmdbFeed.MoviesTopRated,
        TmdbFeed.MoviesUpcoming,
        TmdbFeed.MoviesNewReleases,
        TmdbFeed.KidsPopularMovies,
        TmdbFeed.KidsTopRatedFamily,
        TmdbFeed.KidsDisneyPixar,
        TmdbFeed.KidsDreamWorksGhibli,
        -> "MOVIE"

        is TmdbFeed.TrendingTv,
        TmdbFeed.TvOnTheAir,
        TmdbFeed.TvAiringToday,
        TmdbFeed.TvPopular,
        TmdbFeed.TvTopRated,
        TmdbFeed.TvNewReleases,
        TmdbFeed.KidsTvShows,
        -> "TV_SHOW"

        is TmdbFeed.DiscoverByGenre ->
            if (feed.mediaType == MediaType.TV) "TV_SHOW" else "MOVIE"

        is TmdbFeed.DiscoverFiltered ->
            if (feed.mediaType == MediaType.TV) "TV_SHOW" else "MOVIE"

        is TmdbFeed.Recommendations ->
            if (feed.ref.isTvShow) "TV_SHOW" else "MOVIE"

        TmdbFeed.PersonalizedForYou -> null
    }

    fun kidsExtraParams(feed: TmdbFeed): String? = when (feed) {
        TmdbFeed.KidsPopularMovies -> "with_genres=16|10751&sort_by=popularity.desc"
        TmdbFeed.KidsTopRatedFamily -> "with_genres=10751&sort_by=vote_average.desc&vote_count.gte=300"
        TmdbFeed.KidsDisneyPixar -> "with_companies=2|3&sort_by=popularity.desc"
        TmdbFeed.KidsDreamWorksGhibli -> "with_companies=521|10342&sort_by=popularity.desc"
        else -> null
    }

    fun kidsDiscoverUrl(
        apiKey: String,
        lang: String,
        page: Int,
        extraParams: String,
        certCountry: String,
        certLte: String,
    ): String {
        val encodedCert = URLEncoder.encode(certLte, "UTF-8")
        return "$BASE_URL/discover/movie?api_key=$apiKey&page=$page&language=$lang&include_adult=false&$extraParams&certification_country=$certCountry&certification.lte=$encodedCert"
    }

    fun feedUrl(
        feed: TmdbFeed,
        apiKey: String,
        page: Int,
        lang: String,
        country: String,
        localeProvider: TmdbLocaleProvider,
    ): String? = when (feed) {
        is TmdbFeed.TrendingAll ->
            "$BASE_URL/trending/all/${feed.window.apiValue}?api_key=$apiKey&page=$page&language=$lang&include_adult=false"

        is TmdbFeed.TrendingMovies ->
            "$BASE_URL/trending/movie/${feed.window.apiValue}?api_key=$apiKey&page=$page&language=$lang&include_adult=false"

        is TmdbFeed.TrendingTv ->
            "$BASE_URL/trending/tv/${feed.window.apiValue}?api_key=$apiKey&page=$page&language=$lang&include_adult=false"

        TmdbFeed.MoviesNowPlaying ->
            "$BASE_URL/movie/now_playing?api_key=$apiKey&page=$page&language=$lang&region=$country"

        TmdbFeed.MoviesPopular ->
            "$BASE_URL/movie/popular?api_key=$apiKey&page=$page&language=$lang&region=$country"

        TmdbFeed.MoviesTopRated ->
            "$BASE_URL/movie/top_rated?api_key=$apiKey&page=$page&language=$lang&region=$country"

        TmdbFeed.MoviesUpcoming ->
            "$BASE_URL/movie/upcoming?api_key=$apiKey&page=$page&language=$lang&region=$country"

        TmdbFeed.MoviesNewReleases -> {
            val today = todayIsoDate()
            "$BASE_URL/discover/movie?api_key=$apiKey&page=$page&language=$lang&region=$country&include_adult=false&sort_by=primary_release_date.desc&vote_count.gte=30&primary_release_date.lte=$today"
        }

        TmdbFeed.TvOnTheAir ->
            "$BASE_URL/tv/on_the_air?api_key=$apiKey&page=$page&language=$lang"

        TmdbFeed.TvAiringToday ->
            "$BASE_URL/tv/airing_today?api_key=$apiKey&page=$page&language=$lang"

        TmdbFeed.TvPopular ->
            "$BASE_URL/tv/popular?api_key=$apiKey&page=$page&language=$lang"

        TmdbFeed.TvTopRated ->
            "$BASE_URL/tv/top_rated?api_key=$apiKey&page=$page&language=$lang"

        TmdbFeed.TvNewReleases -> {
            val today = todayIsoDate()
            "$BASE_URL/discover/tv?api_key=$apiKey&page=$page&language=$lang&include_adult=false&sort_by=first_air_date.desc&vote_count.gte=20&first_air_date.lte=$today"
        }

        TmdbFeed.KidsTvShows ->
            "$BASE_URL/discover/tv?api_key=$apiKey&page=$page&language=$lang&include_adult=false&with_genres=10762&sort_by=popularity.desc"

        is TmdbFeed.DiscoverByGenre -> {
            if (feed.mediaType == MediaType.TV) {
                val tvGenre = localeProvider.mapTvGenreIds(feed.genreId)
                "$BASE_URL/discover/tv?api_key=$apiKey&with_genres=$tvGenre&page=$page&language=$lang&include_adult=false&sort_by=popularity.desc"
            } else {
                val movieGenre = localeProvider.mapMovieGenreIds(feed.genreId)
                "$BASE_URL/discover/movie?api_key=$apiKey&with_genres=$movieGenre&page=$page&language=$lang&include_adult=false&sort_by=popularity.desc"
            }
        }

        is TmdbFeed.DiscoverFiltered ->
            discoverFilteredUrl(feed, apiKey, page, lang, country, localeProvider)

        is TmdbFeed.Recommendations -> {
            val type = if (feed.ref.isTvShow) "tv" else "movie"
            "$BASE_URL/$type/${feed.ref.tmdbId}/recommendations?api_key=$apiKey&page=$page&language=$lang"
        }

        TmdbFeed.KidsPopularMovies,
        TmdbFeed.KidsTopRatedFamily,
        TmdbFeed.KidsDisneyPixar,
        TmdbFeed.KidsDreamWorksGhibli,
        TmdbFeed.PersonalizedForYou,
        -> null
    }

    fun searchUrl(apiKey: String, query: String, page: Int, language: String): String {
        val encoded = URLEncoder.encode(query, "UTF-8")
        return "$BASE_URL/search/multi?api_key=$apiKey&query=$encoded&page=$page&language=$language&include_adult=false"
    }

    fun findTmdbIdUrl(apiKey: String, title: String, year: Int?, isTvShow: Boolean): String {
        val type = if (isTvShow) "tv" else "movie"
        val encoded = URLEncoder.encode(title, "UTF-8")
        val yearParam = when {
            year == null -> ""
            isTvShow -> "&first_air_date_year=$year"
            else -> "&year=$year"
        }
        return "$BASE_URL/search/$type?api_key=$apiKey&query=$encoded&include_adult=false$yearParam"
    }

    fun externalIdsUrl(apiKey: String, ref: MediaRef): String {
        val type = if (ref.isTvShow) "tv" else "movie"
        return "$BASE_URL/$type/${ref.tmdbId}/external_ids?api_key=$apiKey"
    }

    fun movieGenresUrl(apiKey: String, language: String): String =
        "$BASE_URL/genre/movie/list?api_key=$apiKey&language=$language"

    fun tvGenresUrl(apiKey: String, language: String): String =
        "$BASE_URL/genre/tv/list?api_key=$apiKey&language=$language"

    fun movieDetailsUrl(apiKey: String, id: Int, language: String, videoLanguages: String): String =
        "$BASE_URL/movie/$id?api_key=$apiKey&language=$language&append_to_response=credits,recommendations,similar,videos,external_ids&include_video_language=$videoLanguages"

    fun tvShowDetailsUrl(apiKey: String, id: Int, language: String, videoLanguages: String): String =
        "$BASE_URL/tv/$id?api_key=$apiKey&language=$language&append_to_response=credits,recommendations,similar,videos,external_ids&include_video_language=$videoLanguages"

    fun seasonDetailsUrl(apiKey: String, tvId: Int, seasonNumber: Int, language: String): String =
        "$BASE_URL/tv/$tvId/season/$seasonNumber?api_key=$apiKey&language=$language"

    fun actorDetailsUrl(apiKey: String, id: Int, language: String): String =
        "$BASE_URL/person/$id?api_key=$apiKey&language=$language&append_to_response=combined_credits"

    private fun discoverFilteredUrl(
        feed: TmdbFeed.DiscoverFiltered,
        apiKey: String,
        page: Int,
        lang: String,
        country: String,
        localeProvider: TmdbLocaleProvider,
    ): String {
        val isTv = feed.mediaType == MediaType.TV
        val endpoint = if (isTv) "tv" else "movie"
        val dateParam = if (isTv) "first_air_date" else "primary_release_date"
        val filter = feed.filter
        val params = buildList {
            add("api_key=$apiKey")
            add("page=$page")
            add("language=$lang")
            if (!isTv) add("region=$country")
            add("include_adult=false")
            add("sort_by=${filter.sortBy.apiValue(feed.mediaType)}")
            if (filter.selectedGenreIds.isNotEmpty()) {
                val genres = if (isTv) {
                    localeProvider.mapTvGenreIds(filter.selectedGenreIds)
                } else {
                    localeProvider.mapMovieGenreIds(filter.selectedGenreIds)
                }
                if (genres.isNotEmpty()) add("with_genres=$genres")
            }
            filter.decade.fromYear?.let { add("$dateParam.gte=$it-01-01") }
            val toDate = filter.decade.toYear?.let { "$it-12-31" }
                ?: if (filter.sortBy == SortOption.RELEASE_DATE_DESC) todayIsoDate() else null
            toDate?.let { add("$dateParam.lte=$it") }
            filter.minRating?.let { add("vote_average.gte=$it") }
            val minVotes = when {
                filter.sortBy == SortOption.RATING_DESC || filter.minRating != null -> 50
                filter.sortBy == SortOption.RELEASE_DATE_DESC -> 20
                else -> null
            }
            minVotes?.let { add("vote_count.gte=$it") }
        }
        return "$BASE_URL/discover/$endpoint?${params.joinToString("&")}"
    }
}
