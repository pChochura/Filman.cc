package com.pointlessapps.filman.data.api

import com.pointlessapps.filman.data.model.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import android.util.Log

class FilmanRepository(private val api: FilmanApi) {
    private val tmdbImageBaseUrl = "https://image.tmdb.org/t/p/w500"

    suspend fun getCategoryPage(path: String, page: Int = 1): PageResult {
        return try {
            val featuredItems = mutableListOf<MovieItem>()
            val movies = mutableListOf<MovieItem>()

            if (path.contains("seriale") || path.contains("tv_shows")) {
                val res = api.getFeaturedShows(page)
                res.results.forEach {
                    movies.add(MovieItem(
                        url = it.id.toString(),
                        titlePl = it.title,
                        posterUrl = if (it.posterPath != null) tmdbImageBaseUrl + it.posterPath else "",
                        backgroundUrl = if (it.backdropPath != null) tmdbImageBaseUrl + it.backdropPath else "",
                        description = it.overview,
                        year = it.firstAirDate?.take(4)?.toIntOrNull(),
                        isTvShow = true
                    ))
                }
            } else if (path.contains("filmy") || path.contains("movies")) {
                val res = api.getFeaturedMovies(page)
                res.results.forEach {
                    movies.add(MovieItem(
                        url = it.id.toString(),
                        titlePl = it.title,
                        posterUrl = if (it.posterPath != null) tmdbImageBaseUrl + it.posterPath else "",
                        backgroundUrl = if (it.backdropPath != null) tmdbImageBaseUrl + it.backdropPath else "",
                        description = it.overview,
                        year = it.releaseDate?.take(4)?.toIntOrNull(),
                        isTvShow = false
                    ))
                }
            } else {
                val res = api.getFeaturedAll(page)
                res.results.forEach {
                    val isTv = it.mediaType == "TV_SHOW"
                    val item = MovieItem(
                        url = it.id.toString(),
                        titlePl = it.title,
                        posterUrl = if (it.posterPath != null) tmdbImageBaseUrl + it.posterPath else "",
                        backgroundUrl = if (it.backdropPath != null) tmdbImageBaseUrl + it.backdropPath else "",
                        description = it.overview,
                        isTvShow = isTv
                    )
                    movies.add(item)
                    if (featuredItems.size < 5) {
                        featuredItems.add(item)
                    }
                }
            }

            PageResult(featuredItems, movies, path = path)
        } catch (e: Exception) {
            Log.e("FilmanRepository", "Error during API call", e)
            PageResult(emptyList(), emptyList(), errorMessage = e.message, path = path)
        }
    }

    fun searchMovies(query: String, prioritySource: MediaSource? = null): Flow<SearchResults> = flow {
        try {
            if (query.isBlank()) {
                emit(SearchResults(emptyList(), emptyList()))
                return@flow
            }
            val results = api.search(query, 1)
            val movies = mutableListOf<MovieItem>()
            val tvShows = mutableListOf<MovieItem>()

            results.results.forEach {
                val item = MovieItem(
                    url = it.id.toString(),
                    titlePl = it.title,
                    posterUrl = if (it.posterPath != null) tmdbImageBaseUrl + it.posterPath else "",
                    description = it.overview,
                    isTvShow = it.mediaType == "TV_SHOW"
                )
                if (item.isTvShow) {
                    tvShows.add(item)
                } else {
                    movies.add(item)
                }
            }
            emit(SearchResults(movies, tvShows))
        } catch (e: Exception) {
            Log.e("FilmanRepository", "Error during API call", e)
            emit(SearchResults(errorMessage = e.message))
        }
    }

    suspend fun getMediaDetails(mediaUrlRaw: String): DetailedMedia? {
        val id = mediaUrlRaw.toIntOrNull() ?: return null
        return try {
            try {
                val movie = api.getMovieDetails(id)
                DetailedMedia(
                    baseItem = MovieItem(
                        url = movie.id.toString(),
                        titlePl = movie.title,
                        posterUrl = if (movie.posterPath != null) tmdbImageBaseUrl + movie.posterPath else "",
                        backgroundUrl = if (movie.backdropPath != null) tmdbImageBaseUrl + movie.backdropPath else "",
                        description = movie.overview,
                        year = movie.releaseDate?.take(4)?.toIntOrNull(),
                        isTvShow = false
                    ),
                    embeds = emptyList(),
                    actors = emptyList(),
                    similarMovies = emptyList()
                )
            } catch (e: Exception) {
                val show = api.getTvShowDetails(id)
                val mappedSeasons = show.seasons.map { s ->
                    Season(
                        name = s.name,
                        episodes = s.episodes.map { ep ->
                            EpisodeLink(
                                title = ep.name,
                                url = "${show.id}_${s.seasonNumber}_${ep.episodeNumber}"
                            )
                        }
                    )
                }
                DetailedMedia(
                    baseItem = MovieItem(
                        url = show.id.toString(),
                        titlePl = show.title,
                        posterUrl = if (show.posterPath != null) tmdbImageBaseUrl + show.posterPath else "",
                        backgroundUrl = if (show.backdropPath != null) tmdbImageBaseUrl + show.backdropPath else "",
                        description = show.overview,
                        year = show.firstAirDate?.take(4)?.toIntOrNull(),
                        isTvShow = true,
                        seasons = mappedSeasons
                    ),
                    embeds = emptyList(),
                    actors = emptyList(),
                    similarMovies = emptyList()
                )
            }
        } catch (e: Exception) {
            Log.e("FilmanRepository", "Error during API call", e)
            null
        }
    }

    suspend fun getActorDetails(actorUrl: String, page: Int = 1): ActorDetails? {
        return ActorDetails(
            name = "Not Supported",
            avatarUrl = "",
            birthDate = null,
            birthPlace = null,
            height = null,
            description = null,
            filmwebRating = null,
            moviesDirector = emptyList(),
            moviesWriter = emptyList(),
            moviesCast = emptyList()
        )
    }

    suspend fun getCategories(): List<FilterOption> = emptyList()

    fun resolveFallbackUrl(url: String): String? = null
    fun invalidateMediaCache(url: String) {}
}
