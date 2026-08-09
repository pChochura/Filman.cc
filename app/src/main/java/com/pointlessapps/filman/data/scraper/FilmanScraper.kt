package com.pointlessapps.filman.data.scraper

import com.pointlessapps.filman.config.EkinoConfig
import com.pointlessapps.filman.config.FilmanConfig
import com.pointlessapps.filman.data.cache.CachePolicy
import com.pointlessapps.filman.data.cache.ModelCache
import com.pointlessapps.filman.data.cache.StaleDataException
import com.pointlessapps.filman.data.model.ActorDetails
import com.pointlessapps.filman.data.model.DetailedMedia
import com.pointlessapps.filman.data.model.FilterOption
import com.pointlessapps.filman.data.model.MovieItem
import com.pointlessapps.filman.data.model.PageResult
import com.pointlessapps.filman.data.model.Rating
import com.pointlessapps.filman.data.model.SearchResults
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal class FilmanScraper(
    private val client: FilmanClient,
    private val modelCache: ModelCache,
    private val ekinoScraper: EkinoScraper,
    private val zaluknijScraper: ZaluknijScraper,
) {

    companion object {
        private const val CACHE_TTL_CATEGORY = 5L * 60 * 1000
        private const val CACHE_TTL_ACTOR_DETAILS = 60L * 60 * 1000
        private const val CACHE_TTL_MEDIA_DETAILS = 60L * 60 * 1000
    }

    suspend fun getCategoryPage(path: String, page: Int = 1): PageResult =
        withContext(Dispatchers.IO) {
            try {
                modelCache.getOrFetch(
                    "category_page_${path}_page_$page",
                    CachePolicy.TTL(CACHE_TTL_CATEGORY),
                ) {
                    val fullPath = path.trimEnd('/')
                    val urlPath = if (fullPath.isEmpty()) {
                        if (page > 1) "${FilmanConfig.PATH_HOME}?page=$page" else FilmanConfig.PATH_HOME
                    } else {
                        if (page > 1) "$fullPath/?page=$page" else "$fullPath/"
                    }
                    val doc = client.getDocument(urlPath)

                    val featuredItems = if (page == 1) {
                        FilmanParser.parseFeaturedItems(doc)
                    } else {
                        emptyList()
                    }

                    val movies = if (path == FilmanConfig.PATH_HOME) {
                        FilmanParser.parseHomeMovies(doc)
                    } else {
                        FilmanParser.parseCategoryMovies(doc, mutableSetOf())
                    }

                    PageResult(featuredItems, movies, path = urlPath)
                }
            } catch (e: Exception) {
                if (e is AuthException || e is StaleDataException) throw e
                e.printStackTrace()
                PageResult(emptyList(), emptyList(), e.message ?: "Unknown error", path = path)
            }
        }

    fun searchMovies(query: String): Flow<SearchResults> = flow {
        val channel = Channel<SearchResults>()

        coroutineScope {
            launch {
                try {
                    val doc = client.getDocument(
                        path = "${FilmanConfig.PATH_SEARCH}${query.replace(" ", "+")}",
                        passCookies = true,
                    )
                    channel.send(FilmanParser.parseSearchMovies(doc))
                } catch (e: Exception) {
                    if (e is AuthException || e is StaleDataException) {
                        channel.close(e)
                    } else {
                        e.printStackTrace()
                        channel.send(SearchResults(errorMessage = e.message ?: "Unknown error"))
                    }
                }
            }

            launch {
                try {
                    channel.send(ekinoScraper.searchMovies(query))
                } catch (e: Exception) {
                    if (e is AuthException || e is StaleDataException) {
                        channel.close(e)
                    } else {
                        e.printStackTrace()
                        channel.send(SearchResults(errorMessage = e.message ?: "Unknown error"))
                    }
                }
            }

            launch {
                try {
                    channel.send(zaluknijScraper.searchMovies(query))
                } catch (e: Exception) {
                    if (e is AuthException || e is StaleDataException) {
                        channel.close(e)
                    } else {
                        e.printStackTrace()
                        channel.send(SearchResults(errorMessage = e.message ?: "Unknown error"))
                    }
                }
            }

            var movies = emptyList<MovieItem>()
            var tvShows = emptyList<MovieItem>()
            var errorMessage: String? = null
            var count = 0

            try {
                while (count < 3) {
                    val result = channel.receive()
                    movies = movies + result.movies
                    tvShows = tvShows + result.tvShows
                    if (result.errorMessage != null) {
                        errorMessage = result.errorMessage
                    }
                    val shouldEmitError =
                        count == 2 && movies.isEmpty() && tvShows.isEmpty() && errorMessage != null
                    emit(
                        SearchResults(
                            movies = movies,
                            tvShows = tvShows,
                            errorMessage = if (shouldEmitError) errorMessage else null,
                        ),
                    )
                    count++
                }
            } finally {
                channel.close()
            }
        }
    }.flowOn(Dispatchers.IO)

    suspend fun getActorDetails(actorUrlRaw: String, page: Int = 1): ActorDetails? =
        withContext(Dispatchers.IO) {
            val actorUrl = actorUrlRaw.substringBefore("?").substringBefore("#")

            if (actorUrl.startsWith(EkinoConfig.BASE_URL)) {
                val pagedUrl = if (page > 1) "${actorUrl}strona[$page]+" else actorUrl
                return@withContext ekinoScraper.getActorDetails(pagedUrl)
            }

            try {
                modelCache.getOrFetch("actor_$actorUrl", CachePolicy.TTL(CACHE_TTL_ACTOR_DETAILS)) {
                    val doc = client.getDocument(actorUrl)

                    FilmanParser.parseActorDetails(doc)
                }
            } catch (e: Exception) {
                if (e is AuthException || e is StaleDataException) throw e
                e.printStackTrace()
                null
            }
        }

    suspend fun getMediaDetails(mediaUrlRaw: String): DetailedMedia? = withContext(Dispatchers.IO) {
        val mediaUrl = mediaUrlRaw.substringBefore("?").substringBefore("#")

        if (mediaUrl.startsWith(EkinoConfig.BASE_URL)) {
            return@withContext ekinoScraper.getMediaDetails(mediaUrl)
        }

        if (mediaUrl.startsWith(com.pointlessapps.filman.config.ZaluknijConfig.BASE_URL)) {
            return@withContext zaluknijScraper.getMediaDetails(mediaUrl)
        }

        val invalidateCondition: (String) -> Boolean = { key ->
            key.startsWith("media_") && key != "media_$mediaUrl"
        }

        try {
            modelCache.getOrFetch(
                key = "media_$mediaUrl",
                policy = CachePolicy.TTL(CACHE_TTL_MEDIA_DETAILS),
                invalidateCondition = invalidateCondition,
            ) {
                val doc = client.getDocument(mediaUrl, passCookies = true)
                val titleMeta = doc.selectFirst("meta[property=\"og:title\"]")
                val rawTitle = titleMeta?.attr("content")
                    ?: doc.selectFirst("title")?.text()?.substringBefore(" - ")
                    ?: "Unknown Title"
                val (titlePl, titleEn, year) = FilmanParser.parseTitleAndYear(rawTitle)

                val posterMeta = doc.selectFirst("meta[property=\"og:image\"]")
                val posterUrl = posterMeta?.attr("content") ?: ""

                val description = doc.selectFirst(".description")?.text().orEmpty()

                val scoreRows = doc.select(".vote-score-row")
                var filmanRating: Rating? = null
                var imdbRating: Rating? = null

                if (scoreRows.isNotEmpty()) {
                    val score = scoreRows[0].selectFirst(".vote-num")?.text()
                        ?.replace(",", ".")?.toFloatOrNull()
                    val maxValue = scoreRows[0].selectFirst(".vote-max")?.text()
                        ?.replace(Regex("[^0-9.]"), "")
                        ?.toFloatOrNull() ?: DEFAULT_MAX_FILMAN_RATING
                    if (score != null) filmanRating = Rating(score, maxValue)
                }
                if (scoreRows.size > 1) {
                    val score = scoreRows[1].selectFirst(".vote-num")?.text()
                        ?.replace(",", ".")?.toFloatOrNull()
                    val maxValue = scoreRows[1].selectFirst(".vote-max")?.text()
                        ?.replace(Regex("[^0-9.]"), "")
                        ?.toFloatOrNull() ?: DEFAULT_MAX_IMDB_RATING
                    if (score != null) imdbRating = Rating(score, maxValue)
                }

                val mediaMetadata = FilmanParser.parseMediaMetadata(doc, year)
                val categories = FilmanParser.parseCategories(doc)
                val tags = FilmanParser.parseTags(doc)
                val actors = FilmanParser.parseActors(doc)
                val similarMovies = FilmanParser.parseSimilarMovies(doc)

                val seasons = FilmanParser.parseTvShowSeasons(doc)
                if (seasons.isNotEmpty()) {
                    DetailedMedia(
                        baseItem = MovieItem(
                            url = mediaUrl,
                            titlePl = titlePl,
                            titleEn = titleEn,
                            filmanRating = filmanRating,
                            imdbRating = imdbRating,
                            posterUrl = posterUrl,
                            backgroundUrl = posterUrl,
                            description = description,
                            seasons = seasons,
                        ),
                        metaInfo = mediaMetadata,
                        categories = categories,
                        tags = tags,
                        actors = actors,
                        similarMovies = similarMovies,
                    )
                } else {
                    val (routeToken, links) = FilmanParser.parseEmbedLinks(doc)

                    var seriesUrl: String? = null
                    var seasonNumber: Int? = null
                    var episodeNumber: Int? = null
                    var episodeTitle: String? = null
                    var prevEpisodeUrl: String? = null
                    var nextEpisodeUrl: String? = null

                    val singleInfo = doc.selectFirst("#single-info")
                    if (singleInfo != null) {
                        seriesUrl = singleInfo.selectFirst("[itemprop=partOfSeries] > a[href]")
                            ?.attr("href")
                            ?.substringBefore("?")?.substringBefore("#")
                        val epCode = singleInfo.selectFirst(".ep-code")?.text()
                        if (epCode != null) {
                            val match =
                                Regex("s(\\d+)e(\\d+)", RegexOption.IGNORE_CASE).find(epCode)
                            if (match != null) {
                                seasonNumber = match.groupValues[1].toIntOrNull()
                                episodeNumber = match.groupValues[2].toIntOrNull()
                            }
                        }
                        episodeTitle =
                            singleInfo.selectFirst(".episode-subtitle > [itemprop=name]")?.text()
                    }

                    doc.select(".ep-navigation a").forEach { link ->
                        val text = link.text().trim()
                        val href = link.attr("href").substringBefore("?").substringBefore("#")
                        if (text.contains("Poprzedni", ignoreCase = true)) {
                            prevEpisodeUrl = href
                        } else if (text.contains("Następny", ignoreCase = true)) {
                            nextEpisodeUrl = href
                        }
                    }

                    DetailedMedia(
                        baseItem = MovieItem(
                            url = mediaUrl,
                            titlePl = titlePl,
                            titleEn = titleEn,
                            filmanRating = filmanRating,
                            imdbRating = imdbRating,
                            posterUrl = posterUrl,
                            backgroundUrl = posterUrl,
                            description = description,
                            routeToken = routeToken,
                            seriesUrl = seriesUrl,
                            seasonNumber = seasonNumber,
                            episodeNumber = episodeNumber,
                            episodeTitle = episodeTitle,
                            prevEpisodeUrl = prevEpisodeUrl,
                            nextEpisodeUrl = nextEpisodeUrl,
                        ),
                        embeds = links,
                        metaInfo = mediaMetadata,
                        categories = categories,
                        tags = tags,
                        actors = actors,
                        similarMovies = similarMovies,
                    )
                }
            }
        } catch (e: Exception) {
            if (e is AuthException || e is StaleDataException) throw e
            null
        }
    }

    fun invalidateMediaCache(mediaUrlRaw: String) {
        val mediaUrl = mediaUrlRaw.substringBefore("?").substringBefore("#")
        modelCache.remove("media_$mediaUrl")
    }

    suspend fun getCategories(): List<FilterOption> = withContext(Dispatchers.IO) {
        val movieCategories = async {
            modelCache.getOrFetch(
                key = "movies_categories",
                policy = CachePolicy.AlwaysValid,
            ) {
                val doc = client.getDocument(FilmanConfig.PATH_MOVIES)
                FilmanParser.parseFilters(doc).categoryOptions
            }
        }
        val tvShowsCategories = async {
            modelCache.getOrFetch(
                key = "tv_shows_categories",
                policy = CachePolicy.AlwaysValid,
            ) {
                val doc = client.getDocument(FilmanConfig.PATH_TV_SHOWS_ALL)
                FilmanParser.parseFilters(doc).categoryOptions
            }
        }

        (movieCategories.await() + tvShowsCategories.await())
            .distinctBy { it.id }
            .sortedBy { it.id.toIntOrNull() ?: 0 }
    }
}
