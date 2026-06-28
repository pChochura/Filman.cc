package com.example.filman.data.scraper

import com.example.filman.data.local.SessionManager
import com.example.filman.data.model.EmbedLink
import com.example.filman.data.model.Movie
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

    private fun getDocument(path: String, passCookies: Boolean = false): Document {
        val cleanPath = path.trim().replace("\n", "").replace("\r", "").trimEnd('/')
        val url = if (cleanPath.startsWith("http")) {
            cleanPath
        } else {
            val separator = if (cleanPath.startsWith("/")) "" else "/"
            "$baseUrl$separator$cleanPath"
        }
        android.util.Log.d("FilmanDebug", "getDocument fetching: $url")
        val cookie = sessionManager.getCookie()
        val userAgent = sessionManager.getUserAgent()
        val conn = Jsoup.connect(url)
//            .cookies(cookie?.split(";")?.associate { it.split("=").let { it[0] to it[1] } }.orEmpty())
            .userAgent(userAgent ?: "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/115.0.0.0 Safari/537.36")
//            .header("Cookie", cookie ?: "")
//            .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.7")
//            .header("Accept-Language", "pl-PL,pl;q=0.9,en-US;q=0.8,en;q=0.7")
//            .header("Connection", "keep-alive")
//            .header("Upgrade-Insecure-Requests", "1")
//            .header("Sec-Fetch-Dest", "document")
//            .header("Sec-Fetch-Mode", "navigate")
//            .header("Sec-Fetch-Site", "none")
//            .header("Sec-Fetch-User", "?1")
//            .header("Cache-Control", "max-age=0")
            .ignoreHttpErrors(true)
            .followRedirects(true)

        if (passCookies) {
            conn.header("Cookie", cookie ?: "")
        }

        val doc = conn.get()
        val currentUrl = conn.response().url().toString()
        if (currentUrl.contains("/logowanie") || currentUrl.endsWith("/404")) {
            throw AuthException("Cookie expired or invalid. Redirected to /logowanie or /404.")
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

    suspend fun getCategoryMovies(path: String, page: Int = 1): List<Movie> = withContext(Dispatchers.IO) {
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
                
                val posterUrl = webpSource?.attr("data-src") ?: imgTag?.attr("data-src") ?: imgTag?.attr("src") ?: ""
                val title = imgTag?.attr("alt") ?: aTag.attr("data-title")
                
                movies.add(Movie(url, title, posterUrl))
            }
            
            // Then try .poster
            val posterItems = doc.select(".poster")
            for (element in posterItems) {
                val aTag = element.selectFirst("a") ?: continue
                val url = aTag.attr("href")
                
                val imgTag = aTag.selectFirst("img")
                val posterUrl = imgTag?.attr("data-src") ?: imgTag?.attr("src") ?: ""
                
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

    suspend fun getMediaDetails(mediaUrl: String): com.example.filman.data.model.MediaDetails? = withContext(Dispatchers.IO) {
        try {
            val doc = getDocument(mediaUrl)
            android.util.Log.d("FilmanDebug", "getDocument success")
            val titleMeta = doc.selectFirst("meta[property=\"og:title\"]")
            val title = titleMeta?.attr("content") ?: doc.selectFirst("title")?.text()?.substringBefore(" - ") ?: "Unknown Title"
            android.util.Log.d("FilmanDebug", "Title: $title")
            
            val posterMeta = doc.selectFirst("meta[property=\"og:image\"]")
            val posterUrl = posterMeta?.attr("content") ?: ""
            android.util.Log.d("FilmanDebug", "Poster: $posterUrl")
            
            val descMeta = doc.selectFirst("meta[property=\"og:description\"]")
            val description = descMeta?.attr("content") ?: doc.selectFirst("meta[name=\"description\"]")?.attr("content") ?: "No description available."
            android.util.Log.d("FilmanDebug", "Description length: ${description.length}")
            
            // Check if it's a series (has episode-list)
            val episodeList = doc.selectFirst("#episode-list, .episode-list")
            if (episodeList != null) {
                val seasons = mutableListOf<com.example.filman.data.model.Season>()
                val seasonNodes = episodeList.children().filter { it.tagName() == "li" }
                
                for (seasonNode in seasonNodes) {
                    val seasonName = seasonNode.selectFirst("span")?.text() ?: "Unknown Season"
                    val episodesList = seasonNode.select("ul li a")
                    val episodes = episodesList.map { aTag ->
                        com.example.filman.data.model.Episode(
                            url = aTag.attr("href"),
                            title = aTag.text().trim()
                        )
                    }.sortedBy { extractNumber(it.title) }
                    if (episodes.isNotEmpty()) {
                        seasons.add(com.example.filman.data.model.Season(seasonName, episodes))
                    }
                }
                seasons.sortBy { extractNumber(it.name) }
                return@withContext com.example.filman.data.model.MediaDetails.Series(title, posterUrl, description, seasons)
            } else {
                // It's a Movie or an Episode, parse embeds
                val links = mutableListOf<com.example.filman.data.model.EmbedLink>()
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
                    val serverName = element.selectFirst("img")?.attr("alt") ?: element.text().trim()
                    links.add(com.example.filman.data.model.EmbedLink(id, serverName))
                }
                
                var seriesUrl: String? = null
                val breadcrumbLinks = doc.select("ul.breadcrumb li a, ol.breadcrumb li a, .breadcrumbs a, .path a, .brd li a, ul.b-crumbs li a")
                val seriesLink = breadcrumbLinks.find { it.attr("href").contains("/serial-online/") && !it.attr("href").contains(mediaUrl) }
                if (seriesLink != null) {
                    seriesUrl = seriesLink.attr("href")
                }
                if (seriesUrl == null && mediaUrl.contains("/serial-online/")) {
                    val parts = mediaUrl.split("/")
                    val lastPart = parts.lastOrNull { it.isNotBlank() }
                    if (lastPart != null && lastPart.matches(Regex("s\\d+e\\d+", RegexOption.IGNORE_CASE))) {
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
                    } else if (text.contains("następny") || text.contains("nastepny") || link.hasClass("pull-right")) {
                        nextEpisodeUrl = href
                    }
                }

                return@withContext com.example.filman.data.model.MediaDetails.MovieOrEpisode(
                    title, posterUrl, description, routeToken, links, seriesUrl, prevEpisodeUrl, nextEpisodeUrl
                )
            }
        } catch (e: Exception) {
            android.util.Log.e("FilmanDebug", "Error in getMediaDetails", e)
            if (e is AuthException) throw e
        }
        android.util.Log.d("FilmanDebug", "Returning null from getMediaDetails")
        null
    }
}
