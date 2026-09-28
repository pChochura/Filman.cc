package com.pointlessapps.filman.data.api

import android.util.Log
import com.pointlessapps.filman.data.api.dto.LinksResponse
import com.pointlessapps.filman.data.model.ActorDetails
import com.pointlessapps.filman.data.model.ActorInfo
import com.pointlessapps.filman.data.model.ActorRole
import com.pointlessapps.filman.data.model.CategoryInfo
import com.pointlessapps.filman.data.model.DetailedMedia
import com.pointlessapps.filman.data.model.DetailsRequest
import com.pointlessapps.filman.data.model.EpisodeLink
import com.pointlessapps.filman.data.model.FilterOption
import com.pointlessapps.filman.data.model.MediaMetadata
import com.pointlessapps.filman.data.model.MovieItem
import com.pointlessapps.filman.data.model.PageResult
import com.pointlessapps.filman.data.model.Rating
import com.pointlessapps.filman.data.model.SearchResults
import com.pointlessapps.filman.data.model.Season
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlin.time.Duration

class FilmanRepository(private val api: FilmanApi) {
    private val tmdbImageBaseUrl = "https://image.tmdb.org/t/p/w500"

suspend fun getCategoryPage(path: String, page: Int = 1): PageResult {
        return try {
            val featuredItems = mutableListOf<MovieItem>()
            val movies = mutableListOf<MovieItem>()
            val genreId = if (path.contains("category:")) path.substringAfter("category:").substringBefore("/").toIntOrNull() else null

            if (genreId != null) {
                val res = api.search(genreId = genreId, page = page)
                res.results.forEach {
                    movies.add(it.toMovieItem(it.mediaType == "TV_SHOW"))
                }
            } else if (path.contains("seriale") || path.contains("tv_shows")) {
                val res = api.getFeaturedShows(page)
                res.results.forEach {
                    movies.add(it.toMovieItem())
                }
            } else if (path.contains("filmy") || path.contains("movies")) {
                val res = api.getFeaturedMovies(page)
                res.results.forEach {
                    movies.add(it.toMovieItem())
                }
            } else {
                val res = api.getFeaturedAll(page)
                res.results.forEach {
                    val isTv = it.mediaType == "TV_SHOW"
                    val item = it.toMovieItem(isTvShow = isTv)
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

    private fun com.pointlessapps.filman.data.api.dto.MediaSummary.toMovieItem(isTvShow: Boolean): MovieItem =
        MovieItem(
            url = id.toString(),
            detailsRequest = DetailsRequest.Url(id.toString(), isTvShow),
            titlePl = title,
            posterUrl = if (posterPath != null) tmdbImageBaseUrl + posterPath else "",
            backgroundUrl = if (backdropPath != null) tmdbImageBaseUrl + backdropPath else "",
            description = overview,
            isTvShow = isTvShow,
        )

    private fun com.pointlessapps.filman.data.api.dto.Movie.toMovieItem(): MovieItem =
        MovieItem(
            url = id.toString(),
            detailsRequest = DetailsRequest.Url(id.toString(), false),
            titlePl = title,
            posterUrl = if (posterPath != null) tmdbImageBaseUrl + posterPath else "",
            backgroundUrl = if (backdropPath != null) tmdbImageBaseUrl + backdropPath else "",
            description = overview,
            year = releaseDate?.take(4)?.toIntOrNull(),
            isTvShow = false,
        )

    private fun com.pointlessapps.filman.data.api.dto.TvShow.toMovieItem(): MovieItem =
        MovieItem(
            url = id.toString(),
            detailsRequest = DetailsRequest.Url(id.toString(), true),
            titlePl = title,
            posterUrl = if (posterPath != null) tmdbImageBaseUrl + posterPath else "",
            backgroundUrl = if (backdropPath != null) tmdbImageBaseUrl + backdropPath else "",
            description = overview,
            year = firstAirDate?.take(4)?.toIntOrNull(),
            isTvShow = true,
        )

    fun searchMovies(query: String): Flow<SearchResults> =
        flow {
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
                        detailsRequest = DetailsRequest.Url(
                            it.id.toString(),
                            it.mediaType == "TV_SHOW",
                        ),
                        titlePl = it.title,
                        posterUrl = if (it.posterPath != null) tmdbImageBaseUrl + it.posterPath else "",
                        description = it.overview,
                        isTvShow = it.mediaType == "TV_SHOW",
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

    suspend fun getMediaDetails(mediaUrlRaw: String, isTvShow: Boolean? = null): DetailedMedia? {
        val cleanUrl = mediaUrlRaw.removePrefix("search_").removePrefix("show_").removePrefix("movie_")
        val id = cleanUrl.substringBefore("_").toIntOrNull() ?: return null
        val tvShowFlag = isTvShow ?: (mediaUrlRaw.contains("_") || mediaUrlRaw.startsWith("show_"))
        return try {
            if (tvShowFlag) {
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
                        url = mediaUrlRaw,
                        titlePl = show.title,
                        posterUrl = if (show.posterPath != null) tmdbImageBaseUrl + show.posterPath else "",
                        backgroundUrl = if (show.backdropPath != null) tmdbImageBaseUrl + show.backdropPath else "",
                        description = show.overview,
                        year = show.firstAirDate?.take(4)?.toIntOrNull(),
                        filmanRating = show.voteAverage?.let { Rating(it.toFloat(), 10f) },
                        imdbRating = show.voteAverage?.let { Rating(it.toFloat(), 10f) },
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
                    categories = show.genres.map { CategoryInfo(name = it.name, url = it.id.toString(), id = it.id) },
                    metaInfo = MediaMetadata(
                        year = show.firstAirDate?.take(4)?.toIntOrNull(),
                        views = null,
                        duration = show.duration?.let { kotlin.time.Duration.parseIsoString("PT${it}M") },
                        countries = show.countries
                    ),
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
                        filmanRating = movie.voteAverage?.let { Rating(it.toFloat(), 10f) },
                        imdbRating = movie.voteAverage?.let { Rating(it.toFloat(), 10f) },
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
                    categories = movie.genres.map { CategoryInfo(name = it.name, url = it.id.toString(), id = it.id) },
                    metaInfo = MediaMetadata(
                        year = movie.releaseDate?.take(4)?.toIntOrNull(),
                        views = null,
                        duration = movie.duration?.let { kotlin.time.Duration.parseIsoString("PT${it}M") },
                        countries = movie.countries
                    ),
                    similarMovies = emptyList()
                )
            }
        } catch (e: Exception) {
            Log.e("FilmanRepository", "Error getting media details", e)
            null
        }
    }

    suspend fun getActorDetails(actorUrl: String): ActorDetails? {
        val id = actorUrl.toIntOrNull() ?: return null
        return try {
            val actorDetails = api.getActorDetails(id)
            val actorMovies = api.getActorMovies(id)

            ActorDetails(
                name = actorDetails.name,
                avatarUrl = if (actorDetails.profilePath != null) tmdbImageBaseUrl + actorDetails.profilePath else "",
                birthDate = actorDetails.birthday,
                birthPlace = actorDetails.placeOfBirth,
                height = null,
                description = actorDetails.biography,
                filmwebRating = null,
                moviesDirector = emptyList(),
                moviesWriter = emptyList(),
                moviesCast = actorMovies.map { movie ->
                    MovieItem(
                        url = movie.id.toString(),
                        detailsRequest = DetailsRequest.Url(movie.id.toString(), false),
                        titlePl = movie.title,
                        posterUrl = if (movie.posterPath != null) tmdbImageBaseUrl + movie.posterPath else "",
                        backgroundUrl = if (movie.backdropPath != null) tmdbImageBaseUrl + movie.backdropPath else "",
                        description = movie.overview,
                        year = movie.releaseDate?.take(4)?.toIntOrNull(),
                        filmanRating = movie.voteAverage?.let { Rating(it.toFloat(), 10f) },
                        imdbRating = movie.voteAverage?.let { Rating(it.toFloat(), 10f) },
                        isTvShow = false
                    )
                }
            )
        } catch (e: Exception) {
            Log.e("FilmanRepository", "Error getting actor details", e)
            null
        }
    }

    suspend fun getCategories(): List<FilterOption> {
        return try {
            api.getCategories().map { FilterOption(id = it.id.toString(), label = it.name) }
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun getLinks(url: String, isTvShow: Boolean): LinksResponse? {
        val cleanUrl = url.removePrefix("search_").removePrefix("show_").removePrefix("movie_")
        val idStr = cleanUrl.substringBefore("_")
        val id = idStr.toIntOrNull() ?: return null
        return try {
            if (isTvShow) {
                try {
                    api.getTvShowLinks(id)
                } catch (e: Exception) {
                    if (e is AuthException || e is CloudflareChallengeDetected) throw e
                    api.getMovieLinks(id)
                }
            } else {
                api.getMovieLinks(id)
            }
        } catch (e: AuthException) {
            throw e
        } catch (e: CloudflareChallengeDetected) {
            throw e
        } catch (e: Exception) {
            Log.e("FilmanRepository", "Error getting links", e)
            null
        }
    }
}
