package com.pointlessapps.filman.data.scraper

import android.util.Log
import com.pointlessapps.filman.config.PLAYER_USER_AGENT
import com.pointlessapps.filman.config.ZaluknijConfig
import com.pointlessapps.filman.data.api.CloudflareChallengeDetected
import com.pointlessapps.filman.data.api.NetworkClient
import com.pointlessapps.filman.data.model.EmbedLink
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.Request
import org.json.JSONObject
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.net.URL
import android.util.Base64

class ZaluknijScraper {
    private val tag = "ZaluknijScraper"

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
        var matchedUrl = directUrl
        var matchedEpUrl = directEpisodeUrl

        try {
            if (matchedUrl == null) {
                var searchResults = search(title, cookie, userAgent)
                var currentSearchTitle = title

                if (searchResults.isEmpty() && !originalTitle.isNullOrBlank() && !originalTitle.equals(title, ignoreCase = true)) {
                    searchResults = search(originalTitle, cookie, userAgent)
                    currentSearchTitle = originalTitle
                }

                if (searchResults.isEmpty()) return@withContext ScraperMatchResult(null, null, emptyList())

                if (season != null && episode != null) {
                    val tvShow = searchResults
                        .filter { it.isTvShow }
                        .firstOrNull { titleMatches(it.title, currentSearchTitle) || (it.titleEn != null && titleMatches(it.titleEn, currentSearchTitle)) }
                        ?: searchResults.firstOrNull { it.isTvShow }

                    if (tvShow != null) {
                        matchedUrl = tvShow.url
                    }
                } else {
                    val movie = searchResults
                        .filter { !it.isTvShow }
                        .firstOrNull { r ->
                            val titleOk = titleMatches(r.title, currentSearchTitle) || (r.titleEn != null && titleMatches(r.titleEn, currentSearchTitle))
                            val yearOk = year == null || r.year == null || r.year == year
                            titleOk && yearOk
                        }
                        ?: searchResults.firstOrNull { !it.isTvShow }
                        ?: searchResults.firstOrNull()

                    if (movie != null) {
                        matchedUrl = movie.url
                    }
                }
            }

            if (matchedUrl == null) {
                Log.d(tag, "Zaluknij: no matching entry found for \"$title\"")
                return@withContext ScraperMatchResult(null, null, emptyList())
            }

            var targetDoc: Document?
            if (season != null && episode != null) {
                if (matchedEpUrl == null) {
                    val showDoc = fetchDoc(matchedUrl, cookie, userAgent)
                        ?: return@withContext ScraperMatchResult(matchedUrl, null, emptyList())
                    val epUrl = findEpisodeUrl(showDoc, season, episode)
                    if (epUrl != null) {
                        matchedEpUrl = if (epUrl.startsWith("http")) epUrl else "${ZaluknijConfig.BASE_URL.trimEnd('/')}/${epUrl.trimStart('/')}"
                    }
                }

                if (matchedEpUrl == null) {
                    Log.w(tag, "Zaluknij: could not find episode S${season}E${episode} for \"$title\"")
                    return@withContext ScraperMatchResult(matchedUrl, null, emptyList())
                }

                targetDoc = fetchDoc(matchedEpUrl, cookie, userAgent)
            } else {
                targetDoc = fetchDoc(matchedUrl, cookie, userAgent)
            }

            if (targetDoc == null) {
                return@withContext ScraperMatchResult(matchedUrl, matchedEpUrl, emptyList())
            }

            val playerServers = targetDoc.select("#playera li, ul.player-servers li, .player-item, a[data-player], a[onclick*='loadPlayer'], a[onClick*='loadPlayer']")
            for (server in playerServers) {
                var url = server.attr("data-url").ifEmpty {
                    server.selectFirst("a")?.attr("data-url")
                }.orEmpty()

                if (url.isEmpty()) {
                    val dataId = server.attr("data-id").ifEmpty {
                        server.selectFirst("a")?.attr("data-id")
                    }.orEmpty()
                    if (dataId.isNotEmpty()) {
                        val resolved = resolvePlayerUrl(dataId, cookie, userAgent)
                        if (resolved != null) url = resolved
                    }
                }

                if (url.isEmpty()) {
                    val onclick = server.attr("onclick").ifEmpty { server.selectFirst("a")?.attr("onclick") }.orEmpty()
                    val match = Regex("""loadPlayer\('([^']+)'\)""").find(onclick)
                    if (match != null) {
                        val encoded = match.groupValues[1]
                        url = try {
                            String(Base64.decode(encoded, Base64.DEFAULT))
                        } catch (e: Exception) {
                            encoded
                        }
                    }
                }

                val serverName = server.selectFirst(".server-name, .title")?.text()?.trim()
                    ?: server.text().trim()
                val quality = server.selectFirst(".quality, .badge")?.text()?.trim().orEmpty()
                val version = server.selectFirst(".version, .lang")?.text()?.trim().orEmpty()

                if (url.isNotEmpty()) {
                    val fullUrl = if (url.startsWith("//")) "https:$url" else url
                    embeds.add(
                        EmbedLink(
                            url = fullUrl,
                            serverName = serverName.ifEmpty { "Zaluknij" },
                            version = version,
                            quality = quality,
                            sourceWebsite = ZaluknijConfig.DOMAIN,
                        )
                    )
                }
            }

            if (embeds.isEmpty()) {
                val iframes = targetDoc.select("iframe[src]")
                for (iframe in iframes) {
                    val src = iframe.attr("src")
                    if (src.contains("embed") || src.contains("player") || src.contains("video")) {
                        val fullUrl = if (src.startsWith("//")) "https:$src" else src
                        embeds.add(
                            EmbedLink(
                                url = fullUrl,
                                serverName = "Zaluknij (Embed)",
                                sourceWebsite = ZaluknijConfig.DOMAIN,
                            )
                        )
                    }
                }
            }

            Log.d(tag, "Zaluknij: found ${embeds.size} embeds for \"$title\"")

        } catch (e: CloudflareChallengeDetected) {
            throw e
        } catch (e: Exception) {
            Log.e(tag, "Zaluknij: scrape failed for \"$title\"", e)
        }

        ScraperMatchResult(matchedUrl, matchedEpUrl, embeds)
    }

    private data class SearchResult(val title: String, val titleEn: String?, val url: String, val year: Int?, val isTvShow: Boolean)

    private fun search(query: String, cookie: String?, userAgent: String?): List<SearchResult> {
        val results = mutableListOf<SearchResult>()
        try {
            val searchApiUrl = "${ZaluknijConfig.BASE_URL}/engine/ajax/controller.php?mod=search"
            val formBody = FormBody.Builder()
                .add("query", query)
                .build()

            val requestBuilder = Request.Builder()
                .url(searchApiUrl)
                .post(formBody)
                .header("User-Agent", userAgent ?: PLAYER_USER_AGENT)
                .header("X-Requested-With", "XMLHttpRequest")
                .header("Referer", ZaluknijConfig.BASE_URL)

            if (!cookie.isNullOrBlank()) {
                requestBuilder.header("Cookie", cookie)
            }

            val (code, html, isMitigated) = NetworkClient.okHttpClient.newCall(requestBuilder.build()).execute().use { resp ->
                Triple(resp.code, resp.body.string(), resp.header("cf-mitigated") != null)
            }

            if (code == 403 || code == 503 || isMitigated ||
                html.contains("cf-browser-verification") ||
                html.contains("Just a moment...") ||
                html.contains("challenge-platform")
            ) {
                throw CloudflareChallengeDetected("zaluknij.cc", searchApiUrl)
            }

            val doc = Jsoup.parse(html)
            val items = doc.select("a, li, .search-result, .item")
            for (item in items) {
                val href = if (item.tagName().equals("a", ignoreCase = true)) item.attr("href") else item.selectFirst("a")?.attr("href")
                if (href.isNullOrBlank()) continue

                val fullUrl = if (href.startsWith("http")) href else "${ZaluknijConfig.BASE_URL.trimEnd('/')}/${href.trimStart('/')}"
                val titleText = item.text().trim()
                if (titleText.isEmpty()) continue

                val parsedYear = Regex("""\b(19\d{2}|20\d{2})\b""").find(titleText)?.value?.toIntOrNull()
                val cleanTitle = titleText.replace(Regex("""\s*\(\d{4}\)|\s*\[\d{4}\]|\s*\b(19\d{2}|20\d{2})\b"""), "").trim()

                val parts = cleanTitle.split(" / ", " - ")
                val titlePl = parts[0].trim()
                val titleEn = if (parts.size > 1) parts[1].trim() else null

                val isTvShow = fullUrl.contains("/serial/") ||
                    fullUrl.contains("/seriale/") ||
                    titleText.contains("sezon", ignoreCase = true)

                results.add(SearchResult(titlePl, titleEn, fullUrl, parsedYear, isTvShow))
            }
        } catch (e: CloudflareChallengeDetected) {
            throw e
        } catch (e: Exception) {
            Log.e(tag, "Zaluknij: search error for $query", e)
        }
        return results
    }

    private fun findEpisodeUrl(showDoc: Document, season: Int, episode: Int): String? {
        val episodePatterns = listOf(
            Regex("""(?:s(?:eason)?\s*0?${season}\s*e(?:pisode)?\s*0?${episode}\b)""", RegexOption.IGNORE_CASE),
            Regex("""\b0?${season}x0?${episode}\b""", RegexOption.IGNORE_CASE),
            Regex("""(?:sezon\s*0?${season}.*odcinek\s*0?${episode}\b)""", RegexOption.IGNORE_CASE),
            Regex("""\b[sS]0?${season}[eE]0?${episode}\b"""),
        )

        val episodeLinks = showDoc.select("a[href*='/odcinek/'], a[href*='/serial/'], #episodes a, ul.episodes a, .episode a")
        for (link in episodeLinks) {
            val text = link.text().trim()
            val href = link.attr("href")

            for (pattern in episodePatterns) {
                if (pattern.containsMatchIn(text) || pattern.containsMatchIn(href)) {
                    return href
                }
            }
        }
        return null
    }

    private fun resolvePlayerUrl(dataId: String, cookie: String?, userAgent: String?): String? {
        return try {
            val url = "${ZaluknijConfig.BASE_URL}/engine/ajax/controller.php?mod=player"
            val formBody = FormBody.Builder().add("id", dataId).build()
            val req = Request.Builder()
                .url(url)
                .post(formBody)
                .header("User-Agent", userAgent ?: PLAYER_USER_AGENT)
                .header("X-Requested-With", "XMLHttpRequest")
                .header("Referer", ZaluknijConfig.BASE_URL)

            if (!cookie.isNullOrBlank()) req.header("Cookie", cookie)

            val body = NetworkClient.okHttpClient.newCall(req.build()).execute().use { res ->
                res.body.string()
            }
            val json = JSONObject(body)
            if (json.has("url")) json.getString("url") else null
        } catch (e: Exception) {
            null
        }
    }

    private fun fetchDoc(url: String, cookie: String?, userAgent: String?): Document? {
        return try {
            val req = Request.Builder()
                .url(url)
                .get()
                .header("User-Agent", userAgent ?: PLAYER_USER_AGENT)
                .header("Referer", ZaluknijConfig.BASE_URL)

            if (!cookie.isNullOrBlank()) req.header("Cookie", cookie)

            val (code, html, isMitigated) = NetworkClient.okHttpClient.newCall(req.build()).execute().use { res ->
                Triple(res.code, res.body.string(), res.header("cf-mitigated") != null)
            }

            if (code == 403 || code == 503 || isMitigated ||
                html.contains("cf-browser-verification") ||
                html.contains("Just a moment...") ||
                html.contains("challenge-platform")
            ) {
                throw CloudflareChallengeDetected("zaluknij.cc", url)
            }

            Jsoup.parse(html)
        } catch (e: CloudflareChallengeDetected) {
            throw e
        } catch (e: Exception) {
            Log.e(tag, "Zaluknij: failed to fetch $url", e)
            null
        }
    }

    private fun normalize(s: String): String =
        java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFD)
            .replace(Regex("\\p{M}"), "")
            .lowercase()
            .replace(Regex("[^a-z0-9\\s]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

    private fun titleMatches(a: String, b: String): Boolean {
        val ca = normalize(a)
        val cb = normalize(b)
        return ca.isNotEmpty() && cb.isNotEmpty() && (ca == cb || ca.contains(cb) || cb.contains(ca))
    }
}
