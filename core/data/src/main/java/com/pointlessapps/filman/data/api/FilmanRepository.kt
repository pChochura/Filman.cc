package com.pointlessapps.filman.data.api

import com.pointlessapps.filman.data.model.*
import com.pointlessapps.filman.data.api.dto.LinksResponse
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
                        url = "show_${it.id}",
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
                        url = "movie_${it.id}",
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
                        url = if (it.mediaType == "TV_SHOW") "show_${it.id}" else "movie_${it.id}",
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
                    url = if (it.mediaType == "TV_SHOW") "show_${it.id}" else "movie_${it.id}",
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
        val isExplicitShow = mediaUrlRaw.startsWith("show_")
        val isExplicitMovie = mediaUrlRaw.startsWith("movie_")
        val idStr = mediaUrlRaw.removePrefix("show_").removePrefix("movie_").substringBefore("_")
        val id = idStr.toIntOrNull() ?: return null
        return try {
            if (isExplicitShow) {
                val show = api.getTvShowDetails(id)
                val mappedSeasons = show.seasons.map { s ->
                    Season(
                        name = s.name,
                        episodes = s.episodes.map { ep ->
                            EpisodeLink(
                                title = ep.name,
                                url = "show_${show.id}_${s.seasonNumber}_${ep.episodeNumber}"
                            )
                        }
                    )
                }
                DetailedMedia(
                    baseItem = MovieItem(
                        url = mediaUrlRaw,
                        titlePl = show.title,
                        posterUrl = if (show.posterPath != null) tmdbImageBaseUrl + show.posterPath else "",
                        backgroundUrl = if (show.backdropPath != null) tmdbImageBaseUrl + show.backdropPath else "",
                        description = show.overview,
                        year = show.firstAirDate?.take(4)?.toIntOrNull(),
                        isTvShow = true,
                        seasons = mappedSeasons
                    ),
                    embeds = emptyList(),
                    actors = show.cast.map { 
                        ActorInfo(
                            role = ActorRole.ACTOR,
                            name = it.name,
                            avatarUrl = if (it.profilePath != null) tmdbImageBaseUrl + it.profilePath else null,
                            url = it.id.toString()
                        )
                    },
                    similarMovies = emptyList()
                )
            } else {
                val movie = api.getMovieDetails(id)
                DetailedMedia(
                    baseItem = MovieItem(
                        url = mediaUrlRaw,
                        titlePl = movie.title,
                        posterUrl = if (movie.posterPath != null) tmdbImageBaseUrl + movie.posterPath else "",
                        backgroundUrl = if (movie.backdropPath != null) tmdbImageBaseUrl + movie.backdropPath else "",
                        description = movie.overview,
                        year = movie.releaseDate?.take(4)?.toIntOrNull(),
                        isTvShow = false
                    ),
                    embeds = emptyList(),
                    actors = movie.cast.map { 
                        ActorInfo(
                            role = ActorRole.ACTOR,
                            name = it.name,
                            avatarUrl = if (it.profilePath != null) tmdbImageBaseUrl + it.profilePath else null,
                            url = it.id.toString()
                        )
                    },
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

    suspend fun getCategories(): List<FilterOption> {
        return try {
            api.getCategories().map { FilterOption(id = it.id.toString(), label = it.name) }
        } catch (e: Exception) {
            emptyList()
        }
    }

    
suspend fun getLinks(url: String, isTvShow: Boolean): LinksResponse? {
        val idStr = url.removePrefix("show_").removePrefix("movie_").substringBefore("_")
        val id = idStr.toIntOrNull() ?: return null
        return try {
            if (!isTvShow) {
                api.getMovieLinks(id)
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e("FilmanRepository", "Error getting links", e)
            null
        }
    }

    fun resolveFallbackUrl(url: String): String? = null
    fun invalidateMediaCache(url: String) {}
}
