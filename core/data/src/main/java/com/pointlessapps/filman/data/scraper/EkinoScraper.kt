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
    ): ScraperMatchResult = withContext(Dispatchers.IO) {
        val embeds = mutableListOf<EmbedLink>()
        var selectedUrl = directUrl

        try {
            if (selectedUrl == null) {
                selectedUrl = findEkinoUrl(title, year, season, episode, cookie, userAgent)

                if (selectedUrl == null && !originalTitle.isNullOrBlank() && !originalTitle.equals(title, ignoreCase = true)) {
                    selectedUrl = findEkinoUrl(originalTitle, year, season, episode, cookie, userAgent)
                }
            }

            if (selectedUrl == null) {
                Log.d(tag, "Ekino: no match found for \"$title\"")
                return@withContext ScraperMatchResult(null, null, emptyList())
            }

            Log.d(tag, "Ekino: scraping embeds from $selectedUrl")
            val movieDoc = fetchDoc(selectedUrl, cookie, userAgent)
                ?: return@withContext ScraperMatchResult(selectedUrl, null, emptyList())

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
        ScraperMatchResult(selectedUrl, null, embeds)
    }

    private fun findEkinoUrl(
        queryTitle: String,
        year: Int?,
        season: Int?,
        episode: Int?,
        cookie: String?,
        userAgent: String?,
    ): String? {
        val searchUrl = "${EkinoConfig.BASE_URL}${EkinoConfig.PATH_SEARCH}${queryTitle.replace(" ", "+")}"
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

        fun clean(s: String) = s.lowercase().replace(Regex("[^a-z0-9\\s]"), "").trim()
        val cleanQuery = clean(queryTitle)

        fun textMatches(text: String): Boolean {
            if (text.contains(queryTitle, ignoreCase = true) || queryTitle.contains(text, ignoreCase = true)) return true
            val c = clean(text)
            return c.isNotEmpty() && (c == cleanQuery || c.contains(cleanQuery) || cleanQuery.contains(c))
        }

        if (season != null && episode != null) {
            val tvShowItem = tvShows.firstOrNull { item ->
                val itemTitle = item.selectFirst(".title > a")?.text()?.trim().orEmpty()
                textMatches(itemTitle)
            } ?: tvShows.firstOrNull()

            if (tvShowItem != null) {
                val tvShowHref = tvShowItem.selectFirst(".title > a")?.attr("href") ?: ""
                if (tvShowHref.isNotEmpty()) {
                    val tvShowUrl = if (tvShowHref.startsWith("http")) tvShowHref else "${EkinoConfig.BASE_URL.trimEnd('/')}/${tvShowHref.trimStart('/')}"
                    val baseUrl = tvShowUrl.removeSuffix("/")
                    return baseUrl.replace("/show/", "/watch/") + "+season[$season]+episode[$episode]+"
                }
            }
        }

        val movieItem = movies.firstOrNull { item ->
            val itemTitle = item.selectFirst(".title > a")?.text()?.trim().orEmpty()
            val itemText = item.text()
            val titleOk = textMatches(itemTitle)
            val yearOk = year == null || itemText.contains(year.toString())
            titleOk && yearOk
        } ?: movies.firstOrNull { item ->
            val itemTitle = item.selectFirst(".title > a")?.text()?.trim().orEmpty()
            textMatches(itemTitle)
        }

        val href = movieItem?.selectFirst("a[href*='/movie/show/']")?.attr("href")
            ?: movieItem?.selectFirst(".title > a")?.attr("href")

        return if (href != null) {
            if (href.startsWith("http")) href else "${EkinoConfig.BASE_URL.trimEnd('/')}/${href.trimStart('/')}"
        } else null
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
