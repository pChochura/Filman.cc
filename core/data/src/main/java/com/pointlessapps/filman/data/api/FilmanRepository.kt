package com.pointlessapps.filman.data.api

import android.util.Log
import com.pointlessapps.filman.data.api.dto.LinksResponse
import com.pointlessapps.filman.data.api.dto.Movie
import com.pointlessapps.filman.data.api.dto.TvShow
import com.pointlessapps.filman.data.api.tmdb.TmdbApiClient
import com.pointlessapps.filman.data.model.ActorDetails
import com.pointlessapps.filman.data.model.DetailedMedia
import com.pointlessapps.filman.data.model.FilterOption
import com.pointlessapps.filman.data.model.MediaRef
import com.pointlessapps.filman.data.model.PageResult
import com.pointlessapps.filman.data.model.SearchResults
import com.pointlessapps.filman.data.model.TmdbFeed
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.util.concurrent.ConcurrentHashMap

class FilmanRepository(
    private val tmdbApiClient: TmdbApiClient,
    private val streamApiLinkSource: StreamApiLinkSource,
) {
    private val tvShowCache = ConcurrentHashMap<Int, TvShow>()
    private val movieCache = ConcurrentHashMap<Int, Movie>()

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

    fun searchMovies(query: String): Flow<SearchResults> =
        flow {
            try {
                if (query.isBlank()) {
                    emit(SearchResults(emptyList(), emptyList()))
                    return@flow
                }
                val results = tmdbApiClient.search(query = query, page = 1)
                val (tvShows, movies) = results.results
                    .filter { it.mediaType != "PERSON" && !it.posterPath.isNullOrBlank() }
                    .map { it.toMovieItem() }
                    .partition { it.isTvShow }
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
            fun prewarm(@Suppress("UNUSED_PARAMETER") show: TvShow) {
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

            show.toDetailedMedia(ref)
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
            movie.toDetailedMedia()
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
            tmdbApiClient.getActorDetails(id).toActorDetails()
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

    suspend fun getMovieCategories(): List<FilterOption> {
        return try {
            tmdbApiClient.getMovieGenres().map { FilterOption(id = it.id.toString(), label = it.name) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e("FilmanRepository", "Error getting movie categories", e)
            emptyList()
        }
    }

    suspend fun getTvCategories(): List<FilterOption> {
        return try {
            tmdbApiClient.getTvGenres().map { FilterOption(id = it.id.toString(), label = it.name) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e("FilmanRepository", "Error getting TV categories", e)
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
