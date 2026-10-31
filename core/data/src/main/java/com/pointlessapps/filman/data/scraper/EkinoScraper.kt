package com.pointlessapps.filman.data.scraper

import android.util.Log
import com.pointlessapps.filman.config.EkinoConfig
import com.pointlessapps.filman.config.PLAYER_USER_AGENT
import com.pointlessapps.filman.data.api.NetworkClient
import com.pointlessapps.filman.data.model.EmbedLink
import com.pointlessapps.filman.data.model.MovieItem
import com.pointlessapps.filman.data.model.SearchResults
import com.pointlessapps.filman.utils.findBestMatchWithFallback
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

class EkinoScraper {
    private val tag = "EkinoScraper"

    suspend fun getEmbeds(
        title: String,
        year: Int?,
        originalTitle: String? = null,
        season: Int? = null,
        episode: Int? = null,
        cookie: String? = null,
        userAgent: String? = null,
        directUrl: String? = null,
        directEpisodeUrl: String? = null,
    ): ScraperMatchResult = withContext(Dispatchers.IO) {
        val embeds = mutableListOf<EmbedLink>()
        val isTvShow = season != null
        var matchedMediaUrl = directUrl?.takeIf { it.isNotBlank() }?.takeUnless {
            (isTvShow && it.contains("/film/")) || (!isTvShow && it.contains("/serie/"))
        }
        var matchedEpisodeUrl = directEpisodeUrl?.takeIf { it.isNotBlank() }

        try {
            if (matchedMediaUrl == null) {
                matchedMediaUrl = findEkinoMediaUrl(title, originalTitle, year, season != null, cookie, userAgent)
            }

            if (matchedMediaUrl == null) {
                Log.d(tag, "Ekino: no match found for \"$title\"")
                return@withContext ScraperMatchResult(null, null, emptyList())
            }

            val targetUrl: String
            if (season != null && episode != null) {
                if (matchedEpisodeUrl == null) {
                    val cleanBase = matchedMediaUrl
                        .substringBefore("+season")
                        .removeSuffix("/")
                        .replace("/watch/", "/show/")
                    matchedEpisodeUrl = cleanBase.replace("/show/", "/watch/") + "+season[$season]+episode[$episode]+"
                    matchedMediaUrl = cleanBase
                }
                targetUrl = matchedEpisodeUrl
            } else {
                targetUrl = matchedMediaUrl
            }

            Log.d(tag, "Ekino: scraping embeds from $targetUrl")
            val movieDoc = fetchDoc(targetUrl, cookie, userAgent)
                ?: return@withContext ScraperMatchResult(matchedMediaUrl, matchedEpisodeUrl, emptyList())

            val playerLinks = movieDoc.select("a[onclick*='ShowPlayer'], a[onClick*='ShowPlayer']")
            for (player in playerLinks) {
                val onClick = player.attr("onclick").ifEmpty { player.attr("onClick") }
                val match = Regex("ShowPlayer\\('([^']+)',\\s*'([^']+)'\\)").find(onClick) ?: continue
                val host = match.groupValues[1]
                val id = match.groupValues[2]
                val watchUrl = "${EkinoConfig.BASE_URL}${EkinoConfig.PATH_WATCH}$host/$id"
                embeds.add(
                    EmbedLink(
                        url = watchUrl,
                        serverName = host,
                        sourceWebsite = EkinoConfig.DOMAIN,
                    )
                )
            }
            Log.d(tag, "Ekino: found ${embeds.size} embeds for \"$title\"")
        } catch (e: Exception) {
            Log.e(tag, "Ekino: scrape failed for \"$title\"", e)
        }
        ScraperMatchResult(matchedMediaUrl, matchedEpisodeUrl, embeds)
    }

    private fun parseEkinoItem(item: Element, isTvShow: Boolean): MovieItem? {
        val a = item.selectFirst(".title > a") ?: item.selectFirst("a[href]") ?: return null
        val href = a.attr("href").trim()
        if (href.isEmpty()) return null
        val url = if (href.startsWith("http")) href else "${EkinoConfig.BASE_URL.trimEnd('/')}/${href.trimStart('/')}"

        val titlePl = a.text().trim()
        val titleEn = item.selectFirst(".title .blue a, .title span a, .title .blue")?.text()?.trim()

        val itemText = item.text()
        val parsedYear = Regex("""\b(19\d{2}|20\d{2})\b""").find(itemText)?.value?.toIntOrNull()

        return MovieItem(
            url = url,
            titlePl = titlePl,
            titleEn = titleEn,
            posterUrl = item.selectFirst("img")?.attr("src").orEmpty(),
            year = parsedYear,
            isTvShow = isTvShow,
        )
    }

    private fun searchEkino(
        query: String,
        cookie: String?,
        userAgent: String?,
    ): SearchResults {
        val encodedQuery = java.net.URLEncoder.encode(query, "UTF-8")
        val searchUrl = "${EkinoConfig.BASE_URL}${EkinoConfig.PATH_SEARCH}$encodedQuery"
        val searchDoc = fetchDoc(searchUrl, cookie, userAgent) ?: return SearchResults()

        val movieWrap = searchDoc.selectFirst(".movie-wrap")
        val divs = movieWrap?.select("> div")

        val movies = mutableListOf<MovieItem>()
        val tvShows = mutableListOf<MovieItem>()

        if (divs != null) {
            for (div in divs) {
                val h4 = div.selectFirst("h4")
                val isTv = h4 != null && h4.text().contains("Serial", ignoreCase = true)
                div.select(".movies-list-item").mapNotNull { parseEkinoItem(it, isTv) }.forEach {
                    if (isTv) tvShows.add(it) else movies.add(it)
                }
            }
        } else {
            searchDoc.select(".movies-list-item").mapNotNull { parseEkinoItem(it, false) }.forEach {
                movies.add(it)
            }
        }

        return SearchResults(movies = movies, tvShows = tvShows)
    }

    private fun findEkinoMediaUrl(
        queryTitle: String,
        originalTitle: String?,
        year: Int?,
        isTvShow: Boolean,
        cookie: String?,
        userAgent: String?,
    ): String? {
        val firstSearch = searchEkino(queryTitle, cookie, userAgent)
        val candidates = if (isTvShow) firstSearch.tvShows else firstSearch.movies

        val match = candidates.findBestMatchWithFallback(queryTitle, originalTitle, year)
        if (match != null) return match.url

        if (!originalTitle.isNullOrBlank() && !originalTitle.equals(queryTitle, ignoreCase = true)) {
            val secondSearch = searchEkino(originalTitle, cookie, userAgent)
            val altCandidates = if (isTvShow) secondSearch.tvShows else secondSearch.movies
            val altMatch = altCandidates.findBestMatchWithFallback(originalTitle, queryTitle, year)
            if (altMatch != null) return altMatch.url
        }

        // Fallback: If query was TV show, take first TV show if any; otherwise take first movie
        return if (isTvShow) firstSearch.tvShows.firstOrNull()?.url else firstSearch.movies.firstOrNull()?.url
    }

    private fun fetchDoc(url: String, cookie: String? = null, userAgent: String? = null): Document? {
        return try {
            val requestBuilder = Request.Builder()
                .url(url)
                .get()
                .header("User-Agent", userAgent ?: PLAYER_USER_AGENT)
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .header("Accept-Language", "pl-PL,pl;q=0.9,en-US;q=0.8,en;q=0.7")
                .header("Referer", EkinoConfig.BASE_URL)

            if (!cookie.isNullOrBlank()) {
                requestBuilder.header("Cookie", cookie)
            }

            val response = NetworkClient.okHttpClient.newCall(requestBuilder.build()).execute()
            val html = response.body.string()

            Jsoup.parse(html)
        } catch (e: Exception) {
            Log.e(tag, "Ekino: failed to fetch $url", e)
            null
        }
    }
}

