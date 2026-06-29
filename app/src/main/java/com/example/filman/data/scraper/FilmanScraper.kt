package com.example.filman.data.scraper

import com.example.filman.data.local.SessionManager
import com.example.filman.data.model.EmbedLink
import com.example.filman.data.model.MediaDetails.MovieOrEpisode
import com.example.filman.data.model.MediaDetails.Series
import com.example.filman.data.model.Movie
import com.example.filman.data.model.Season
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import org.jsoup.nodes.Document

class AuthException(message: String) : Exception(message)

class FilmanScraper(private val sessionManager: SessionManager) {

    private val baseUrl = "https://filman.cc"

    private fun extractNumber(text: String): Int {
        val match = Regex("\\d+").find(text)
        return match?.value?.toIntOrNull() ?: 0
    }

    private fun extractEpisodeNumber(text: String): Int {
        val exMatch = Regex("(?i)e\\s*(\\d+)").find(text)
        if (exMatch != null) return exMatch.groupValues[1].toIntOrNull() ?: 0

        val odcMatch = Regex("(?i)odcinek\\s*(\\d+)").find(text)
        if (odcMatch != null) return odcMatch.groupValues[1].toIntOrNull() ?: 0

        val xMatch = Regex("(?i)\\d+x(\\d+)").find(text)
        if (xMatch != null) return xMatch.groupValues[1].toIntOrNull() ?: 0

        val numbers = Regex("\\d+").findAll(text).mapNotNull { it.value.toIntOrNull() }.toList()
        return numbers.lastOrNull { it < 1000 } ?: numbers.firstOrNull() ?: 0
    }

    private fun getDocument(path: String, passCookies: Boolean = false): Document {
        val cleanPath = path.trim().replace("\n", "").replace("\r", "").trimEnd('/')
        val url = if (cleanPath.startsWith("http")) {
            cleanPath
        } else {
            val separator = if (cleanPath.startsWith("/")) "" else "/"
            "$baseUrl$separator$cleanPath"
        }
        val cookie = sessionManager.getCookie()
        val userAgent = sessionManager.getUserAgent()

        var conn = Jsoup.connect(url)
            .userAgent(userAgent)
            .ignoreHttpErrors(true)
            .followRedirects(true)

        if (passCookies && !cookie.isNullOrBlank()) {
            conn.header("Cookie", cookie)
        }

        var doc = conn.get()
        var currentUrl = conn.response().url().toString()

        if (!passCookies && currentUrl.contains("/logowanie") && !cookie.isNullOrBlank()) {
            conn = Jsoup.connect(url)
                .userAgent(userAgent)
                .ignoreHttpErrors(true)
                .followRedirects(true)
                .header("Cookie", cookie)

            doc = conn.get()
            currentUrl = conn.response().url().toString()
        }

        if (currentUrl.contains("/logowanie")) {
            throw AuthException("Cookie expired or invalid. Redirected to /logowanie.")
        }

        if (currentUrl.endsWith("/404")) {
            throw Exception("Page not found (404)")
        }

        return doc
    }

    suspend fun getHomeMovies(): List<Movie> = withContext(Dispatchers.IO) {
        val movies = mutableListOf<Movie>()
        try {
            val doc = getDocument("/")
            val elements = doc.select(".movie-item")
            for (element in elements) {
                val aTag = element.selectFirst("a") ?: continue
                val url = aTag.attr("href")

                // Try webp source first, then img
                val webpSource = element.selectFirst("source[type=image/webp]")
                val imgTag = element.selectFirst("img")

                val posterUrl = webpSource?.attr("data-src") ?: imgTag?.attr("data-src") ?: ""
                val title = imgTag?.attr("alt") ?: aTag.attr("data-title")

                movies.add(Movie(url, title, posterUrl))
            }
        } catch (e: Exception) {
            if (e is AuthException) throw e
            e.printStackTrace()
        }
        return@withContext movies
    }

    suspend fun getCategoryMovies(path: String, page: Int = 1): List<Movie> =
        withContext(Dispatchers.IO) {
            val movies = mutableListOf<Movie>()
            try {
                val doc = getDocument("$path?page=$page")
                // First try .movie-item
                val movieItems = doc.select(".movie-item")
                for (element in movieItems) {
                    val aTag = element.selectFirst("a") ?: continue
                    val url = aTag.attr("href")

                    val webpSource = element.selectFirst("source[type=image/webp]")
                    val imgTag = element.selectFirst("img")

                    val posterUrl = webpSource?.attr("data-src") ?: imgTag?.attr("data-src")
                    ?: imgTag?.attr("src") ?: ""
                    val title = imgTag?.attr("alt") ?: aTag.attr("data-title")

                    movies.add(Movie(url, title, posterUrl))
                }

                // Then try .poster
                val posterItems = doc.select(".poster")
                for (element in posterItems) {
                    val aTag = element.selectFirst("a") ?: continue
                    val url = aTag.attr("href")

                    val imgTag = aTag.selectFirst("img")
                    val posterUrl = imgTag?.attr("data-src")?.takeIf {
                        it.isNotEmpty()
                    } ?: imgTag?.attr("src").orEmpty()

                    val filmTitleDiv = element.parent()?.selectFirst(".film_title")
                    val title =
                        filmTitleDiv?.text() ?: imgTag?.attr("alt") ?: aTag.attr("data-title")

                    if (url.isNotEmpty() && title.isNotEmpty()) {
                        movies.add(Movie(url, title, posterUrl))
                    }
                }
            } catch (e: Exception) {
                if (e is AuthException) throw e
                e.printStackTrace()
            }
            return@withContext movies
        }

    suspend fun searchMovies(query: String): List<Movie> = withContext(Dispatchers.IO) {
        val movies = mutableListOf<Movie>()
        try {
            val doc = getDocument("/search?phrase=${query.replace(" ", "+")}", true)
            val elements = doc.select(".poster")
            for (element in elements) {
                val aTag = element.selectFirst("a") ?: continue
                val url = aTag.attr("href")

                val imgTag = aTag.selectFirst("img")
                val posterUrl = imgTag?.attr("src") ?: ""

                val filmTitleDiv = element.parent()?.selectFirst(".film_title")
                val title = filmTitleDiv?.text() ?: imgTag?.attr("alt") ?: aTag.attr("data-title")

                if (url.isNotEmpty() && title.isNotEmpty()) {
                    movies.add(Movie(url, title, posterUrl))
                }
            }
        } catch (e: Exception) {
            if (e is AuthException) throw e
            e.printStackTrace()
        }
        movies
    }

    suspend fun getMediaDetails(mediaUrl: String): com.example.filman.data.model.MediaDetails? =
        withContext(Dispatchers.IO) {
            try {
                val doc = getDocument(mediaUrl)
                val titleMeta = doc.selectFirst("meta[property=\"og:title\"]")
                val title = titleMeta?.attr("content") ?: doc.selectFirst("title")?.text()
                    ?.substringBefore(" - ") ?: "Unknown Title"

                val posterMeta = doc.selectFirst("meta[property=\"og:image\"]")
                val posterUrl = posterMeta?.attr("content") ?: ""

                val descMeta = doc.selectFirst("meta[property=\"og:description\"]")
                val description =
                    descMeta?.attr("content") ?: doc.selectFirst("meta[name=\"description\"]")
                        ?.attr("content") ?: "No description available."

                // Check if it's a series (has episode-list)
                val episodeList = doc.selectFirst("#episode-list, .episode-list")
                if (episodeList != null) {
                    val seasons = mutableListOf<Season>()
                    val seasonNodes = episodeList.children().filter { it.tagName() == "li" }

                    for (seasonNode in seasonNodes) {
                        val seasonName = seasonNode.selectFirst("span")?.text() ?: "Unknown Season"
                        val episodesList = seasonNode.select("ul li a")
                        val episodes = episodesList.map { aTag ->
                            com.example.filman.data.model.Episode(
                                url = aTag.attr("href"),
                                title = aTag.text().trim(),
                            )
                        }.sortedBy { extractEpisodeNumber(it.title) }
                        if (episodes.isNotEmpty()) {
                            seasons.add(Season(seasonName, episodes))
                        }
                    }
                    seasons.sortBy { extractNumber(it.name) }
                    return@withContext Series(
                        title = title,
                        posterUrl = posterUrl,
                        description = description,
                        seasons = seasons,
                    )
                } else {
                    // It's a Movie or an Episode, parse embeds
                    val links = mutableListOf<EmbedLink>()
                    var routeToken = ""

                    val scripts = doc.select("script")
                    for (script in scripts) {
                        val scriptData = script.data()
                        if (scriptData.contains("var routeToken")) {
                            val match = Regex("var routeToken = '([a-zA-Z0-9]+)'").find(scriptData)
                            if (match != null) {
                                routeToken = match.groupValues[1]
                                break
                            }
                        }
                    }

                    val elements = doc.select("a[data-link-id]")
                    for (element in elements) {
                        val id = element.attr("data-link-id")
                        val serverName =
                            element.selectFirst("img")?.attr("alt") ?: element.text().trim()
                        links.add(EmbedLink(id, serverName))
                    }

                    var seriesUrl: String? = null
                    val breadcrumbLinks =
                        doc.select("ul.breadcrumb li a, ol.breadcrumb li a, .breadcrumbs a, .path a, .brd li a, ul.b-crumbs li a")
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
                        if (lastPart != null && lastPart.matches(
                                Regex(
                                    "s\\d+e\\d+",
                                    RegexOption.IGNORE_CASE,
                                ),
                            )
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
                        } else if (text.contains("następny") || text.contains("nastepny") || link.hasClass(
                                "pull-right",
                            )
                        ) {
                            nextEpisodeUrl = href
                        }
                    }

                    return@withContext MovieOrEpisode(
                        title = title,
                        posterUrl = posterUrl,
                        description = description,
                        routeToken = routeToken,
                        embeds = links,
                        seriesUrl = seriesUrl,
                        prevEpisodeUrl = prevEpisodeUrl,
                        nextEpisodeUrl = nextEpisodeUrl,
                    )
                }
            } catch (e: Exception) {
                if (e is AuthException) throw e
            }

            null
        }
}
