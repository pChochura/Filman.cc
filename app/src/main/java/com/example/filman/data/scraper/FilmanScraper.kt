package com.example.filman.data.scraper

import com.example.filman.data.model.DetailedMedia
import com.example.filman.data.model.FilterData
import com.example.filman.data.model.MovieItem
import com.example.filman.data.model.Rating
import com.example.filman.data.model.TvShow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class FilmanScraper(private val client: FilmanClient) {

    suspend fun getHomeMovies(): List<MovieItem> = withContext(Dispatchers.IO) {
        try {
            val doc = client.getDocument("/")
            return@withContext FilmanParser.parseHomeMovies(doc)
        } catch (e: Exception) {
            if (e is AuthException) throw e
            e.printStackTrace()
            return@withContext emptyList()
        }
    }

    suspend fun getFilters(path: String): FilterData = withContext(Dispatchers.IO) {
        try {
            val doc = client.getDocument(path)
            return@withContext FilmanParser.parseFilters(doc)
        } catch (e: Exception) {
            if (e is AuthException) throw e
            e.printStackTrace()
            return@withContext FilterData(
                emptyList(),
                emptyList(),
                emptyList(),
                emptyList(),
                emptyList(),
            )
        }
    }

    suspend fun getFeaturedItems(path: String = "/"): List<MovieItem> =
        withContext(Dispatchers.IO) {
            try {
                val doc = client.getDocument(path)
                return@withContext FilmanParser.parseFeaturedItems(doc)
            } catch (e: Exception) {
                if (e is AuthException) throw e
                e.printStackTrace()
                return@withContext emptyList()
            }
        }

    suspend fun getCategoryMovies(path: String, page: Int = 1): List<MovieItem> =
        withContext(Dispatchers.IO) {
            try {
                val fullPath = path.trimEnd('/')
                val urlPath = "$fullPath/?page=$page"
                val doc = client.getDocument(urlPath)
                return@withContext FilmanParser.parseCategoryMovies(doc, mutableSetOf())
            } catch (e: Exception) {
                if (e is AuthException) throw e
                e.printStackTrace()
                return@withContext emptyList()
            }
        }

    suspend fun searchMovies(query: String): List<MovieItem> = withContext(Dispatchers.IO) {
        try {
            val doc =
                client.getDocument("/search?phrase=${query.replace(" ", "+")}", passCookies = true)
            return@withContext FilmanParser.parseSearchMovies(doc)
        } catch (e: Exception) {
            if (e is AuthException) throw e
            e.printStackTrace()
            return@withContext emptyList()
        }
    }

    suspend fun getActorDetails(actorUrl: String): com.example.filman.data.model.ActorDetails? = withContext(Dispatchers.IO) {
        try {
            val doc = client.getDocument(actorUrl)
            return@withContext FilmanParser.parseActorDetails(doc)
        } catch (e: Exception) {
            if (e is AuthException) throw e
            e.printStackTrace()
            return@withContext null
        }
    }

    suspend fun getMediaDetails(mediaUrl: String): DetailedMedia? = withContext(Dispatchers.IO) {
        try {
            val doc = client.getDocument(mediaUrl)
            val titleMeta = doc.selectFirst("meta[property=\"og:title\"]")
            val rawTitle = titleMeta?.attr("content")
                ?: doc.selectFirst("title")?.text()?.substringBefore(" - ")
                ?: "Unknown Title"
            val (titlePl, titleEn, year) = FilmanParser.parseTitleAndYear(rawTitle)

            val posterMeta = doc.selectFirst("meta[property=\"og:image\"]")
            val posterUrl = posterMeta?.attr("content") ?: ""

            val descMeta = doc.selectFirst("meta[property=\"og:description\"]")
            val description = descMeta?.attr("content")
                ?: doc.selectFirst("meta[name=\"description\"]")?.attr("content")
                ?: "No description available."

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
                return@withContext DetailedMedia(
                    baseItem = TvShow(
                        url = mediaUrl,
                        titlePl = titlePl,
                        titleEn = titleEn,
                        filmanRating = filmanRating,
                        imdbRating = imdbRating,
                        posterUrl = posterUrl,
                        backgroundUrl = null,
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
                    it.attr("href").contains("/serial-online/") && !it.attr("href")
                        .contains(mediaUrl)
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

                return@withContext DetailedMedia(
                    baseItem = MovieItem(
                        url = mediaUrl,
                        titlePl = titlePl,
                        titleEn = titleEn,
                        filmanRating = filmanRating,
                        imdbRating = imdbRating,
                        posterUrl = posterUrl,
                        backgroundUrl = null,
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
        } catch (e: Exception) {
            if (e is AuthException) throw e
        }
        null
    }
}
