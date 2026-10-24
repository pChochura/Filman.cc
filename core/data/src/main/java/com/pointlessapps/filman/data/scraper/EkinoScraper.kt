package com.pointlessapps.filman.data.scraper

import android.util.Log
import com.pointlessapps.filman.config.EkinoConfig
import com.pointlessapps.filman.config.PLAYER_USER_AGENT
import com.pointlessapps.filman.data.api.NetworkClient
import com.pointlessapps.filman.data.model.EmbedLink
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
        var matchedMediaUrl = directUrl
        var matchedEpisodeUrl = directEpisodeUrl

        try {
            if (matchedMediaUrl == null) {
                matchedMediaUrl = findEkinoMediaUrl(title, year, season != null, cookie, userAgent)

                if (matchedMediaUrl == null && !originalTitle.isNullOrBlank() && !originalTitle.equals(title, ignoreCase = true)) {
                    matchedMediaUrl = findEkinoMediaUrl(originalTitle, year, season != null, cookie, userAgent)
                }
            }

            if (matchedMediaUrl == null) {
                Log.d(tag, "Ekino: no match found for \"$title\"")
                return@withContext ScraperMatchResult(null, null, emptyList())
            }

            val targetUrl: String
            if (season != null && episode != null) {
                if (matchedEpisodeUrl == null) {
                    val baseUrl = matchedMediaUrl.removeSuffix("/")
                    matchedEpisodeUrl = baseUrl.replace("/show/", "/watch/") + "+season[$season]+episode[$episode]+"
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

    private fun normalize(s: String): String =
        java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFD)
            .replace(Regex("\\p{M}"), "")
            .lowercase()
            .replace(Regex("[^a-z0-9\\s]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

    private fun extractItemTitles(item: Element): List<String> {
        val titles = mutableListOf<String>()
        item.selectFirst(".title > a")?.text()?.trim()?.takeIf { it.isNotEmpty() }?.let { titles.add(it) }
        item.select(".title .blue, .title span a, .title span").forEach { el ->
            el.text().trim().takeIf { it.isNotEmpty() }?.let { titles.add(it) }
        }
        item.selectFirst(".title")?.text()?.trim()?.takeIf { it.isNotEmpty() }?.let { titles.add(it) }
        item.attr("id").takeIf { it.isNotEmpty() }?.let { titles.add(it.replace("-", " ")) }
        return titles
    }

    private fun scoreMatch(item: Element, queryTitle: String, year: Int?): Int {
        val normQuery = normalize(queryTitle)
        if (normQuery.isEmpty()) return 0

        val itemTitles = extractItemTitles(item)
        val itemText = item.text()
        val yearMatches = year != null && itemText.contains(year.toString())

        var bestScore = 0
        for (rawTitle in itemTitles) {
            val normTitle = normalize(rawTitle)
            if (normTitle.isEmpty()) continue

            val score = when {
                normTitle == normQuery -> if (yearMatches) 100 else 85
                normTitle.startsWith("$normQuery ") || normTitle.endsWith(" $normQuery") || normTitle.contains(" $normQuery ") -> {
                    if (yearMatches) 75 else 55
                }
                normTitle.contains(normQuery) || normQuery.contains(normTitle) -> {
                    if (yearMatches) 45 else 30
                }
                else -> 0
            }
            if (score > bestScore) bestScore = score
        }
        return bestScore
    }

    private fun findEkinoMediaUrl(
        queryTitle: String,
        year: Int?,
        isTvShow: Boolean,
        cookie: String?,
        userAgent: String?,
    ): String? {
        val encodedQuery = java.net.URLEncoder.encode(queryTitle, "UTF-8")
        val searchUrl = "${EkinoConfig.BASE_URL}${EkinoConfig.PATH_SEARCH}$encodedQuery"
        val searchDoc = fetchDoc(searchUrl, cookie, userAgent) ?: return null

        val movieWrap = searchDoc.selectFirst(".movie-wrap")
        val divs = movieWrap?.select("> div")

        val movies = mutableListOf<Element>()
        val tvShows = mutableListOf<Element>()

        if (divs != null) {
            for (div in divs) {
                val h4 = div.selectFirst("h4")
                if (h4 != null && h4.text().contains("Serial", ignoreCase = true)) {
                    tvShows.addAll(div.select(".movies-list-item"))
                } else {
                    movies.addAll(div.select(".movies-list-item"))
                }
            }
        } else {
            movies.addAll(searchDoc.select(".movies-list-item"))
        }

        if (isTvShow) {
            val tvShowItem = tvShows
                .map { it to scoreMatch(it, queryTitle, year) }
                .filter { it.second > 0 }
                .maxByOrNull { it.second }
                ?.first
                ?: tvShows.firstOrNull()

            if (tvShowItem != null) {
                val tvShowHref = tvShowItem.selectFirst(".title > a")?.attr("href")
                    ?: tvShowItem.selectFirst("a[href*='/serie/show/']")?.attr("href")
                    ?: ""
                if (tvShowHref.isNotEmpty()) {
                    return if (tvShowHref.startsWith("http")) tvShowHref else "${EkinoConfig.BASE_URL.trimEnd('/')}/${tvShowHref.trimStart('/')}"
                }
            }
        } else {
            val movieItem = movies
                .map { it to scoreMatch(it, queryTitle, year) }
                .filter { it.second > 0 }
                .maxByOrNull { it.second }
                ?.first

            val href = movieItem?.selectFirst("a[href*='/movie/show/']")?.attr("href")
                ?: movieItem?.selectFirst(".title > a")?.attr("href")

            if (href != null) {
                return if (href.startsWith("http")) href else "${EkinoConfig.BASE_URL.trimEnd('/')}/${href.trimStart('/')}"
            }
        }

        return null
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
