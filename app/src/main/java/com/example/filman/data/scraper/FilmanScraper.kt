package com.example.filman.data.scraper

import com.example.filman.data.cache.CachePolicy
import com.example.filman.data.cache.ModelCache
import com.example.filman.data.cache.StaleDataException
import com.example.filman.data.model.ActorDetails
import com.example.filman.data.model.DetailedMedia
import com.example.filman.data.model.FilterData
import com.example.filman.data.model.FilterOption
import com.example.filman.data.model.MovieItem
import com.example.filman.data.model.PageResult
import com.example.filman.data.model.Rating
import com.example.filman.data.model.SearchResults
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext

class FilmanScraper(
    private val client: FilmanClient,
    private val modelCache: ModelCache,
) {

    companion object {
        private const val CACHE_TTL_FILTERS = 24L * 60 * 60 * 1000
        private const val CACHE_TTL_CATEGORY = 5L * 60 * 1000
        private const val CACHE_TTL_ACTOR_DETAILS = 60L * 60 * 1000
        private const val CACHE_TTL_MEDIA_DETAILS = 60L * 60 * 1000
    }

    suspend fun getFilters(path: String): FilterData = withContext(Dispatchers.IO) {
        try {
            modelCache.getOrFetch("filters_$path", CachePolicy.TTL(CACHE_TTL_FILTERS)) {
                val doc = client.getDocument(path)
                FilmanParser.parseFilters(doc)
            }
        } catch (e: Exception) {
            if (e is AuthException || e is StaleDataException) throw e
            e.printStackTrace()

            FilterData(
                emptyList(),
                emptyList(),
                emptyList(),
                emptyList(),
                emptyList(),
            )
        }
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
                        if (page > 1) "/?page=$page" else "/"
                    } else {
                        if (page > 1) "$fullPath/?page=$page" else "$fullPath/"
                    }
                    val doc = client.getDocument(urlPath)

                    val featuredItems = if (page == 1) {
                        FilmanParser.parseFeaturedItems(doc)
                    } else {
                        emptyList()
                    }

                    val movies = if (path == "/") {
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

    suspend fun searchMovies(query: String): SearchResults = withContext(Dispatchers.IO) {
        try {
            modelCache.getOrFetch("search_$query", CachePolicy.AlwaysInvalid) {
                val doc = client.getDocument(
                    path = "/search?phrase=${query.replace(" ", "+")}",
                    passCookies = true,
                )

                FilmanParser.parseSearchMovies(doc)
            }
        } catch (e: Exception) {
            if (e is AuthException || e is StaleDataException) throw e
            e.printStackTrace()
            SearchResults(emptyList(), emptyList(), e.message ?: "Unknown error")
        }
    }

    suspend fun getActorDetails(actorUrl: String): ActorDetails? = withContext(Dispatchers.IO) {
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

    suspend fun getMediaDetails(mediaUrl: String): DetailedMedia? = withContext(Dispatchers.IO) {
        val invalidateCondition: (String) -> Boolean = { key ->
            key.startsWith("media_") && key != "media_$mediaUrl"
        }

        try {
            modelCache.getOrFetch(
                key = "media_$mediaUrl",
                policy = CachePolicy.TTL(CACHE_TTL_MEDIA_DETAILS),
                invalidateCondition = invalidateCondition,
            ) {
                val doc = client.getDocument(mediaUrl)
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
                    val breadcrumbLinks = doc.select(
                        "ul.breadcrumb li a, ol.breadcrumb li a, .breadcrumbs a, .path a, .brd li a, ul.b-crumbs li a",
                    )
                    val seriesLink = breadcrumbLinks.find {
                        it.attr("href").contains("/serial-online/") &&
                                !it.attr("href").contains(mediaUrl)
                    }
                    if (seriesLink != null) {
                        seriesUrl = seriesLink.attr("href")
                    }
                    if (seriesUrl == null && mediaUrl.contains("/serial-online/")) {
                        val parts = mediaUrl.split("/")
                        val lastPart = parts.lastOrNull { it.isNotBlank() }
                        if (
                            lastPart != null &&
                            lastPart.matches(Regex("s\\d+e\\d+", RegexOption.IGNORE_CASE))
                        ) {
                            seriesUrl = parts.dropLast(1).joinToString("/")
                        }
                    }

                    var prevEpisodeUrl: String? = null
                    var nextEpisodeUrl: String? = null
                    val navLinks = doc.select("#single-info div a")
                    for (link in navLinks) {
                        val text = link.text().lowercase()
                        val href = link.attr("href")
                        if (text.contains("poprzedni") || link.hasClass("pull-left")) {
                            prevEpisodeUrl = href
                        } else if (
                            text.contains("następny") ||
                            text.contains("nastepny") ||
                            link.hasClass("pull-right")
                        ) {
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
                        ),
                        embeds = links,
                        prevEpisodeUrl = prevEpisodeUrl,
                        nextEpisodeUrl = nextEpisodeUrl,
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

    suspend fun getCategories(): List<FilterOption> = withContext(Dispatchers.IO) {
        val movieCategories = async {
            modelCache.getOrFetch(
                key = "movies_categories",
                policy = CachePolicy.AlwaysValid,
            ) {
                val doc = client.getDocument("/filmy/")
                FilmanParser.parseFilters(doc).categoryOptions
            }
        }
        val tvShowsCategories = async {
            modelCache.getOrFetch(
                key = "tv_shows_categories",
                policy = CachePolicy.AlwaysValid,
            ) {
                val doc = client.getDocument("/seriale/")
                FilmanParser.parseFilters(doc).categoryOptions
            }
        }

        (movieCategories.await() + tvShowsCategories.await())
            .distinctBy { it.id }
            .sortedBy { it.id.toIntOrNull() ?: 0 }
    }
}
