package com.pointlessapps.filman.data.api

import android.util.Log
import com.pointlessapps.filman.data.api.dto.LinksResponse
import com.pointlessapps.filman.data.api.dto.MediaSummary
import com.pointlessapps.filman.data.api.tmdb.TmdbApiClient
import com.pointlessapps.filman.data.model.ActorDetails
import com.pointlessapps.filman.data.model.ActorInfo
import com.pointlessapps.filman.data.model.CategoryInfo
import com.pointlessapps.filman.data.model.DetailedMedia
import com.pointlessapps.filman.data.model.DetailsRequest
import com.pointlessapps.filman.data.model.EpisodeLink
import com.pointlessapps.filman.data.model.FilterOption
import com.pointlessapps.filman.data.model.MediaMetadata
import com.pointlessapps.filman.data.model.MediaRef
import com.pointlessapps.filman.data.model.MovieItem
import com.pointlessapps.filman.data.model.PageResult
import com.pointlessapps.filman.data.model.Rating
import com.pointlessapps.filman.data.model.SearchResults
import com.pointlessapps.filman.data.model.Season
import com.pointlessapps.filman.data.model.TmdbFeed
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration

class FilmanRepository(
    private val tmdbApiClient: TmdbApiClient,
    private val streamApiLinkSource: StreamApiLinkSource,
) {
    private val tvShowCache = ConcurrentHashMap<Int, com.pointlessapps.filman.data.api.dto.TvShow>()
    private val movieCache = ConcurrentHashMap<Int, com.pointlessapps.filman.data.api.dto.Movie>()

    fun wakeUp(): Job = streamApiLinkSource.wakeUp()

    fun getCachedLinks(url: String): LinksResponse? {
        val ref = MediaRef.parse(url) ?: return null
        val key = streamApiLinkSource.linkKey(ref.tmdbId, ref.season, ref.episode)
        return streamApiLinkSource.getLinks(key)
    }

    suspend fun getFeedPage(feed: TmdbFeed, page: Int = 1): PageResult {
        return try {
            val res = tmdbApiClient.fetchFeed(feed, page)
            val movies = res.results
                .filter { it.mediaType != "PERSON" }
                .map { it.toMovieItem() }

            val featuredCandidates = movies.filter { !it.backgroundUrl.isNullOrBlank() }
            val featuredItems = (featuredCandidates.ifEmpty { movies }).take(5)

            PageResult(
                featuredItems = featuredItems,
                movies = movies,
                feed = feed,
                page = res.page,
                totalPages = res.totalPages,
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e("FilmanRepository", "Error during TMDB feed request for $feed", e)
            PageResult(emptyList(), emptyList(), errorMessage = e.message, feed = feed, page = page)
        }
    }

    private fun MediaSummary.toMovieItem(): MovieItem {
        val isTv = mediaType == "TV_SHOW"
        val itemUrl = if (isTv) "show_$id" else "movie_$id"
        return MovieItem(
            url = itemUrl,
            detailsRequest = DetailsRequest.Url(itemUrl, isTv),
            titlePl = title,
            titleEn = originalTitle,
            posterUrl = if (!posterPath.isNullOrBlank()) TmdbApiClient.POSTER_BASE_URL + posterPath else "",
            backgroundUrl = if (!backdropPath.isNullOrBlank()) TmdbApiClient.BACKDROP_BASE_URL + backdropPath else "",
            description = overview,
            year = releaseDate?.take(4)?.toIntOrNull(),
            filmanRating = voteAverage?.takeIf { it > 0.0 }?.let { Rating(it.toFloat(), 10f) },
            imdbRating = voteAverage?.takeIf { it > 0.0 }?.let { Rating(it.toFloat(), 10f) },
            isTvShow = isTv,
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

                results.results
                    .filter { it.mediaType != "PERSON" && !it.posterPath.isNullOrBlank() }
                    .forEach { summary ->
                        val item = summary.toMovieItem()
                        if (item.isTvShow) {
                            tvShows.add(item)
                        } else {
                            movies.add(item)
                        }
                    }
                emit(SearchResults(movies, tvShows))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("FilmanRepository", "Error during TMDB search", e)
                emit(SearchResults(errorMessage = e.message))
            }
        }

    suspend fun getMediaDetails(mediaUrlRaw: String, isTvShow: Boolean? = null): DetailedMedia? {
        val ref = MediaRef.parse(mediaUrlRaw, isTvShow) ?: return null
        return if (ref.isTvShow) {
            getTvShowDetails(ref) ?: getMovieDetails(ref.tmdbId)
        } else {
            getMovieDetails(ref.tmdbId) ?: getTvShowDetails(ref)
        }
    }

    private suspend fun getTvShowDetails(ref: MediaRef): DetailedMedia? {
        val id = ref.tmdbId
        val season = ref.season
        val episode = ref.episode
        return try {
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
                            url = "show_${show.id}_${s.seasonNumber}_${ep.episodeNumber}",
                        )
                    },
                )
            }

            var currentEpisodeTitle: String? = null
            var nextEpisodeUrl: String? = null
            var prevEpisodeUrl: String? = null

            if (season != null && episode != null) {
                val seasonIndex = mappedSeasons.indexOfFirst { s ->
                    s.episodes.any { it.url.startsWith("show_${show.id}_${season}_") }
                }
                val epList = mappedSeasons.getOrNull(seasonIndex)?.episodes.orEmpty()
                val epIndex = epList.indexOfFirst { it.url == "show_${show.id}_${season}_${episode}" }
                if (epIndex != -1) {
                    currentEpisodeTitle = epList[epIndex].title
                    val nextEp = epList.getOrNull(epIndex + 1)
                        ?: mappedSeasons.getOrNull(seasonIndex + 1)?.episodes?.firstOrNull()
                    nextEpisodeUrl = nextEp?.url
                    val prevEp = if (epIndex > 0) {
                        epList.getOrNull(epIndex - 1)
                    } else {
                        mappedSeasons.getOrNull(seasonIndex - 1)?.episodes?.lastOrNull()
                    }
                    prevEpisodeUrl = prevEp?.url
                }
            }

            val canonicalShowUrl = if (season != null && episode != null) {
                "show_${id}_${season}_${episode}"
            } else {
                "show_$id"
            }

            DetailedMedia(
                tmdbId = id,
                imdbId = show.imdbId,
                baseItem = MovieItem(
                    url = canonicalShowUrl,
                    seriesUrl = "show_${show.id}",
                    seasonNumber = season,
                    episodeNumber = episode,
                    episodeTitle = currentEpisodeTitle,
                    nextEpisodeUrl = nextEpisodeUrl,
                    prevEpisodeUrl = prevEpisodeUrl,
                    titlePl = if (currentEpisodeTitle != null) "${show.title} - S${season}E${episode}" else show.title,
                    titleEn = show.originalTitle,
                    posterUrl = if (show.posterPath != null) TmdbApiClient.POSTER_BASE_URL + show.posterPath else "",
                    backgroundUrl = if (show.backdropPath != null) TmdbApiClient.BACKDROP_BASE_URL + show.backdropPath else "",
                    description = show.overview,
                    year = show.firstAirDate?.take(4)?.toIntOrNull(),
                    filmanRating = show.voteAverage?.takeIf { it > 0.0 }?.let { Rating(it.toFloat(), 10f) },
                    imdbRating = show.voteAverage?.takeIf { it > 0.0 }?.let { Rating(it.toFloat(), 10f) },
                    isTvShow = true,
                    seasons = mappedSeasons,
                ),
                embeds = emptyList(),
                actors = show.cast.map {
                    ActorInfo(
                        role = it.role,
                        name = it.name,
                        avatarUrl = if (it.profilePath != null) TmdbApiClient.PROFILE_BASE_URL + it.profilePath else null,
                        url = it.id.toString(),
                    )
                },
                categories = show.genres.map { CategoryInfo(name = it.name, url = it.id.toString(), id = it.id) },
                metaInfo = MediaMetadata(
                    year = show.firstAirDate?.take(4)?.toIntOrNull(),
                    views = null,
                    duration = show.duration?.let { Duration.parseIsoString("PT${it}M") },
                    countries = show.countries,
                ),
                similarMovies = show.similar.filter { !it.posterPath.isNullOrBlank() }.map { it.toMovieItem() },
                recommendations = show.recommendations.filter { !it.posterPath.isNullOrBlank() }.map { it.toMovieItem() },
                recommendationsTotalPages = show.recommendationsTotalPages,
                trailerUrl = show.trailerUrl,
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e("FilmanRepository", "Error getting TV show details for $ref", e)
            null
        }
    }

    private suspend fun getMovieDetails(id: Int): DetailedMedia? {
        return try {
            val movie = movieCache.getOrPut(id) { tmdbApiClient.getMovieDetails(id) }
            streamApiLinkSource.startExtraction(
                tmdbId = id,
                isTvShow = false,
            )

            DetailedMedia(
                tmdbId = id,
                imdbId = movie.imdbId,
                baseItem = MovieItem(
                    url = "movie_$id",
                    titlePl = movie.title,
                    titleEn = movie.originalTitle,
                    posterUrl = if (movie.posterPath != null) TmdbApiClient.POSTER_BASE_URL + movie.posterPath else "",
                    backgroundUrl = if (movie.backdropPath != null) TmdbApiClient.BACKDROP_BASE_URL + movie.backdropPath else "",
                    description = movie.overview,
                    year = movie.releaseDate?.take(4)?.toIntOrNull(),
                    filmanRating = movie.voteAverage?.takeIf { it > 0.0 }?.let { Rating(it.toFloat(), 10f) },
                    imdbRating = movie.voteAverage?.takeIf { it > 0.0 }?.let { Rating(it.toFloat(), 10f) },
                    isTvShow = false,
                ),
                embeds = emptyList(),
                actors = movie.cast.map {
                    ActorInfo(
                        role = it.role,
                        name = it.name,
                        avatarUrl = if (it.profilePath != null) TmdbApiClient.PROFILE_BASE_URL + it.profilePath else null,
                        url = it.id.toString(),
                    )
                },
                categories = movie.genres.map { CategoryInfo(name = it.name, url = it.id.toString(), id = it.id) },
                metaInfo = MediaMetadata(
                    year = movie.releaseDate?.take(4)?.toIntOrNull(),
                    views = null,
                    duration = movie.duration?.let { Duration.parseIsoString("PT${it}M") },
                    countries = movie.countries,
                ),
                similarMovies = movie.similar.filter { !it.posterPath.isNullOrBlank() }.map { it.toMovieItem() },
                recommendations = movie.recommendations.filter { !it.posterPath.isNullOrBlank() }.map { it.toMovieItem() },
                recommendationsTotalPages = movie.recommendationsTotalPages,
                trailerUrl = movie.trailerUrl,
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e("FilmanRepository", "Error getting movie details for $id", e)
            null
        }
    }

    suspend fun getActorDetails(actorUrl: String): ActorDetails? {
        val id = actorUrl.toIntOrNull() ?: return null
        return try {
            val actorDetails = tmdbApiClient.getActorDetails(id)
            ActorDetails(
                name = actorDetails.name,
                avatarUrl = if (actorDetails.profilePath != null) TmdbApiClient.PROFILE_BASE_URL + actorDetails.profilePath else "",
                birthDate = actorDetails.birthday,
                birthPlace = actorDetails.placeOfBirth,
                height = null,
                description = actorDetails.biography,
                filmwebRating = null,
                moviesDirector = actorDetails.directorCredits.filter { !it.posterPath.isNullOrBlank() }.map { it.toMovieItem() },
                moviesWriter = actorDetails.writerCredits.filter { !it.posterPath.isNullOrBlank() }.map { it.toMovieItem() },
                moviesCast = actorDetails.castCredits.filter { !it.posterPath.isNullOrBlank() }.map { it.toMovieItem() },
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e("FilmanRepository", "Error getting actor details", e)
            null
        }
    }

    suspend fun getCategories(): List<FilterOption> {
        return try {
            tmdbApiClient.getCategories().map { FilterOption(id = it.id.toString(), label = it.name) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun getLinks(url: String, isTvShow: Boolean, force: Boolean = false): LinksResponse? {
        val ref = MediaRef.parse(url, isTvShow) ?: return null
        val key = streamApiLinkSource.linkKey(ref.tmdbId, ref.season, ref.episode)

        val cached = streamApiLinkSource.getLinks(key)
        if (cached != null && !force) {
            return cached
        }

        return try {
            if (isTvShow && (ref.season == null || ref.episode == null)) return null
            streamApiLinkSource.startExtraction(ref.tmdbId, isTvShow, ref.season, ref.episode, force)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e("FilmanRepository", "Error getting links for $url", e)
            null
        }
    }
}
