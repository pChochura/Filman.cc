package com.pointlessapps.filman.data.api

import android.util.Log
import com.pointlessapps.filman.data.api.dto.LinksResponse
import com.pointlessapps.filman.data.api.tmdb.TmdbApiClient
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class FilmanRepository(
    private val tmdbApiClient: TmdbApiClient,
    private val streamApiLinkSource: StreamApiLinkSource,
) {
    private val tmdbImageBaseUrl = "https://image.tmdb.org/t/p/w500"
    private val tvShowCache = java.util.concurrent.ConcurrentHashMap<Int, com.pointlessapps.filman.data.api.dto.TvShow>()
    private val movieCache = java.util.concurrent.ConcurrentHashMap<Int, com.pointlessapps.filman.data.api.dto.Movie>()

    fun getCachedLinks(url: String): LinksResponse? {
        val cleanUrl = url.removePrefix("search_").removePrefix("show_").removePrefix("movie_")
        val parts = cleanUrl.split("_")
        val id = parts.firstOrNull()?.toIntOrNull() ?: return null
        val season = parts.getOrNull(1)?.toIntOrNull()
        val episode = parts.getOrNull(2)?.toIntOrNull()
        val key = streamApiLinkSource.linkKey(id, season, episode)
        return streamApiLinkSource.getLinks(key)
    }

    suspend fun getCategoryPage(path: String, page: Int = 1): PageResult {
        return try {
            val featuredItems = mutableListOf<MovieItem>()
            val movies = mutableListOf<MovieItem>()
            val genreId = if (path.contains("category:")) path.substringAfter("category:").substringBefore("/").toIntOrNull() else null

            if (genreId != null) {
                val res = tmdbApiClient.search(genreId = genreId, page = page)
                res.results.forEach {
                    movies.add(it.toMovieItem(it.mediaType == "TV_SHOW"))
                }
            } else if (path.contains("seriale") || path.contains("tv_shows")) {
                val res = tmdbApiClient.getFeaturedShows(page)
                res.results.forEach {
                    movies.add(it.toMovieItem())
                }
            } else if (path.contains("filmy") || path.contains("movies")) {
                val res = tmdbApiClient.getFeaturedMovies(page)
                res.results.forEach {
                    movies.add(it.toMovieItem())
                }
            } else {
                val res = tmdbApiClient.getFeaturedAll(page)
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
            Log.e("FilmanRepository", "Error during TMDB API call", e)
            PageResult(emptyList(), emptyList(), errorMessage = e.message, path = path)
        }
    }

    private fun com.pointlessapps.filman.data.api.dto.MediaSummary.toMovieItem(isTvShow: Boolean): MovieItem {
        val itemUrl = if (isTvShow) "show_$id" else "movie_$id"
        return MovieItem(
            url = itemUrl,
            detailsRequest = DetailsRequest.Url(itemUrl, isTvShow),
            titlePl = title,
            posterUrl = if (!posterPath.isNullOrBlank()) tmdbImageBaseUrl + posterPath else "",
            backgroundUrl = if (backdropPath != null) tmdbImageBaseUrl + backdropPath else "",
            description = overview,
            isTvShow = isTvShow,
        )
    }

    private fun com.pointlessapps.filman.data.api.dto.Movie.toMovieItem(): MovieItem {
        val itemUrl = "movie_$id"
        return MovieItem(
            url = itemUrl,
            detailsRequest = DetailsRequest.Url(itemUrl, false),
            titlePl = title,
            posterUrl = if (posterPath != null) tmdbImageBaseUrl + posterPath else "",
            backgroundUrl = if (backdropPath != null) tmdbImageBaseUrl + backdropPath else "",
            description = overview,
            year = releaseDate?.take(4)?.toIntOrNull(),
            isTvShow = false,
        )
    }

    private fun com.pointlessapps.filman.data.api.dto.TvShow.toMovieItem(): MovieItem {
        val itemUrl = "show_$id"
        return MovieItem(
            url = itemUrl,
            detailsRequest = DetailsRequest.Url(itemUrl, true),
            titlePl = title,
            posterUrl = if (posterPath != null) tmdbImageBaseUrl + posterPath else "",
            backgroundUrl = if (backdropPath != null) tmdbImageBaseUrl + backdropPath else "",
            description = overview,
            year = firstAirDate?.take(4)?.toIntOrNull(),
            isTvShow = true,
        )
    }

    fun searchMovies(query: String): Flow<SearchResults> =
        flow {
            try {
                if (query.isBlank()) {
                    emit(SearchResults(emptyList(), emptyList()))
                    return@flow
                }
                val results = tmdbApiClient.search(query = query, page = 1)
                val movies = mutableListOf<MovieItem>()
                val tvShows = mutableListOf<MovieItem>()

                results.results.filter { !it.posterPath.isNullOrBlank() }.forEach {
                    val isTv = it.mediaType == "TV_SHOW"
                    val itemUrl = if (isTv) "show_${it.id}" else "movie_${it.id}"
                    val item = MovieItem(
                        url = itemUrl,
                        detailsRequest = DetailsRequest.Url(
                            itemUrl,
                            isTv,
                        ),
                        titlePl = it.title,
                        posterUrl = if (it.posterPath != null) tmdbImageBaseUrl + it.posterPath else "",
                        description = it.overview,
                        isTvShow = isTv,
                    )
                    if (item.isTvShow) {
                        tvShows.add(item)
                    } else {
                        movies.add(item)
                    }
                }
                emit(SearchResults(movies, tvShows))
            } catch (e: Exception) {
                Log.e("FilmanRepository", "Error during TMDB search", e)
                emit(SearchResults(errorMessage = e.message))
            }
        }

    suspend fun getMediaDetails(mediaUrlRaw: String, isTvShow: Boolean? = null): DetailedMedia? {
        val cleanUrl = mediaUrlRaw.removePrefix("search_").removePrefix("show_").removePrefix("movie_")
        val id = cleanUrl.substringBefore("_").toIntOrNull() ?: return null
        val tvShowFlag = isTvShow ?: (mediaUrlRaw.startsWith("show_") || cleanUrl.contains("_"))

        return if (tvShowFlag) {
            getMediaDetailsOfType(mediaUrlRaw, true) ?: getMediaDetailsOfType(mediaUrlRaw, false)
        } else {
            getMediaDetailsOfType(mediaUrlRaw, false) ?: getMediaDetailsOfType(mediaUrlRaw, true)
        }
    }

    private suspend fun getMediaDetailsOfType(mediaUrlRaw: String, isTvShow: Boolean): DetailedMedia? {
        val cleanUrl = mediaUrlRaw.removePrefix("search_").removePrefix("show_").removePrefix("movie_")
        val id = cleanUrl.substringBefore("_").toIntOrNull() ?: return null
        return try {
            if (isTvShow) {
                val parts = cleanUrl.split("_")
                val season = parts.getOrNull(1)?.toIntOrNull()
                val episode = parts.getOrNull(2)?.toIntOrNull()
                fun prewarm(show: com.pointlessapps.filman.data.api.dto.TvShow) {
                    if (season != null && episode != null) {
                        streamApiLinkSource.startExtraction(
                            tmdbId = id,
                            isTvShow = true,
                            season = season,
                            episode = episode,
                        )
                    }
                }
                val cachedShow = tvShowCache[id]
                val show = if (cachedShow != null) {
                    prewarm(cachedShow)
                    cachedShow
                } else {
                    tmdbApiClient.getTvShowDetails(id, onMetadata = ::prewarm).also {
                        tvShowCache.putIfAbsent(id, it)
                    }
                }
                val mappedSeasons = show.seasons.filter { it.seasonNumber > 0 }.map { s ->
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

                var currentEpisodeTitle: String? = null
                var nextEpisodeUrl: String? = null
                var prevEpisodeUrl: String? = null

                if (season != null && episode != null) {
                    val seasonObj = mappedSeasons.find { s ->
                        s.episodes.any { it.url.startsWith("show_${show.id}_${season}_") }
                    }
                    val epList = seasonObj?.episodes.orEmpty()
                    val epIndex = epList.indexOfFirst { it.url == "show_${show.id}_${season}_${episode}" }
                    if (epIndex != -1) {
                        currentEpisodeTitle = epList[epIndex].title
                        val nextEp = epList.getOrNull(epIndex + 1)
                            ?: mappedSeasons.getOrNull(season)?.episodes?.firstOrNull()
                        nextEpisodeUrl = nextEp?.url
                        val prevEp = if (epIndex > 0) epList.getOrNull(epIndex - 1)
                            else mappedSeasons.getOrNull(season - 2)?.episodes?.lastOrNull()
                        prevEpisodeUrl = prevEp?.url
                    }
                }

                DetailedMedia(
                    tmdbId = id,
                    imdbId = show.imdbId,
                    baseItem = MovieItem(
                        url = if (mediaUrlRaw.startsWith("show_")) mediaUrlRaw else "show_$mediaUrlRaw",
                        seriesUrl = "show_${show.id}",
                        seasonNumber = season,
                        episodeNumber = episode,
                        episodeTitle = currentEpisodeTitle,
                        nextEpisodeUrl = nextEpisodeUrl,
                        prevEpisodeUrl = prevEpisodeUrl,
                        titlePl = if (currentEpisodeTitle != null) "${show.title} - S${season}E${episode}" else show.title,
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
                val movie = movieCache.getOrPut(id) { tmdbApiClient.getMovieDetails(id) }
                // Pre-warm link extraction in the background
                streamApiLinkSource.startExtraction(
                    tmdbId = id,
                    isTvShow = false,
                )

                DetailedMedia(
                    tmdbId = id,
                    imdbId = movie.imdbId,
                    baseItem = MovieItem(
                        url = if (mediaUrlRaw.startsWith("movie_")) mediaUrlRaw else "movie_$mediaUrlRaw",
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
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e("FilmanRepository", "Error getting media details for $mediaUrlRaw (isTvShow=$isTvShow)", e)
            null
        }
    }

    suspend fun getActorDetails(actorUrl: String): ActorDetails? {
        val id = actorUrl.toIntOrNull() ?: return null
        return try {
            val actorDetails = tmdbApiClient.getActorDetails(id)
            val actorMovies = tmdbApiClient.getActorMovies(id)

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
                    val itemUrl = "movie_${movie.id}"
                    MovieItem(
                        url = itemUrl,
                        detailsRequest = DetailsRequest.Url(itemUrl, false),
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
            tmdbApiClient.getCategories().map { FilterOption(id = it.id.toString(), label = it.name) }
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun getLinks(url: String, isTvShow: Boolean, force: Boolean = false): LinksResponse? {
        val cleanUrl = url.removePrefix("search_").removePrefix("show_").removePrefix("movie_")
        val parts = cleanUrl.split("_")
        val id = parts.firstOrNull()?.toIntOrNull() ?: return null
        val season = parts.getOrNull(1)?.toIntOrNull()
        val episode = parts.getOrNull(2)?.toIntOrNull()
        val key = streamApiLinkSource.linkKey(id, season, episode)

        val cached = streamApiLinkSource.getLinks(key)
        if (cached != null && !force) {
            return cached
        }

        return try {
            if (isTvShow && (season == null || episode == null)) return null
            streamApiLinkSource.startExtraction(id, isTvShow, season, episode, force)
        } catch (e: CancellationException) {
            throw e
        } catch (e: AuthException) {
            throw e
        } catch (e: CloudflareChallengeDetected) {
            throw e
        } catch (e: Exception) {
            Log.e("FilmanRepository", "Error getting links for $url", e)
            null
        }
    }
}
