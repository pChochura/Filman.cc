package com.pointlessapps.filman.data.scraper

import android.util.Base64
import android.util.Log
import com.pointlessapps.filman.config.PLAYER_USER_AGENT
import com.pointlessapps.filman.config.ZaluknijConfig
import com.pointlessapps.filman.data.api.CloudflareChallengeDetected
import com.pointlessapps.filman.data.api.NetworkClient
import com.pointlessapps.filman.data.api.isCloudflareChallengePage
import com.pointlessapps.filman.data.model.EmbedLink
import com.pointlessapps.filman.data.model.MovieItem
import com.pointlessapps.filman.data.model.SearchResults
import com.pointlessapps.filman.utils.findBestMatchWithFallback
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.Request
import org.json.JSONObject
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.net.URL

class ZaluknijScraper {
    private val tag = "ZaluknijScraper"

    suspend fun getEmbeds(
        title: String,
        year: Int?,
        originalTitle: String? = null,
        season: Int? = null,
        episode: Int? = null,
        userAgent: String? = null,
        directUrl: String? = null,
        directEpisodeUrl: String? = null,
    ): ScraperMatchResult = withContext(Dispatchers.IO) {
        val embeds = mutableListOf<EmbedLink>()
        val isTvShow = season != null
        var matchedUrl = directUrl?.takeIf { it.isNotBlank() }?.takeUnless {
            (isTvShow && it.contains("/film/")) || (!isTvShow && it.contains("/serial/"))
        }
        var matchedEpUrl = directEpisodeUrl?.takeIf { it.isNotBlank() }

        try {
            if (matchedUrl == null) {
                val isTv = season != null && episode != null
                var searchResults = search(title, userAgent)
                val candidates = if (isTv) searchResults.tvShows else searchResults.movies
                var matchedItem = candidates.findBestMatchWithFallback(title, originalTitle, year)
                if (matchedItem == null && !originalTitle.isNullOrBlank() && !originalTitle.equals(title, ignoreCase = true)) {
                    val altResults = search(originalTitle, userAgent)
                    val altCandidates = if (isTv) altResults.tvShows else altResults.movies
                    matchedItem = altCandidates.findBestMatchWithFallback(originalTitle, title, year)
                }
                if (matchedItem != null) {
                    matchedUrl = matchedItem.url
                } else {
                    matchedUrl = candidates.firstOrNull()?.url
                }
            }

            if (matchedUrl == null) {
                Log.d(tag, "Zaluknij: no matching entry found for \"$title\"")
                return@withContext ScraperMatchResult(null, null, emptyList())
            }

            var targetDoc: Document?
            if (season != null && episode != null) {
                if (matchedEpUrl == null) {
                    val showDoc = fetchDoc(matchedUrl, userAgent)
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

                targetDoc = fetchDoc(matchedEpUrl, userAgent)
            } else {
                targetDoc = fetchDoc(matchedUrl, userAgent)
            }

            if (targetDoc == null) {
                return@withContext ScraperMatchResult(matchedUrl, matchedEpUrl, emptyList())
            }

            // 1. Table rows with data-iframe (standard Zaluknij format)
            targetDoc.select("tr").forEach { row ->
                val linkTd = row.selectFirst("td.link-to-video") ?: return@forEach
                val anchor = linkTd.selectFirst("a[data-iframe]") ?: return@forEach

                val dataIframe = anchor.attr("data-iframe")
                if (dataIframe.isBlank()) return@forEach

                val embedUrl =
                    try {
                        val jsonStr = String(Base64.decode(dataIframe, Base64.DEFAULT))
                        val json = JSONObject(jsonStr)
                        json.optString("src").takeIf { it.isNotBlank() }
                    } catch (e: Exception) {
                        null
                    } ?: return@forEach

                val tds = row.select("td")
                val version = tds.getOrNull(2)?.text()?.trim() ?: ""
                val quality = tds.getOrNull(3)?.text()?.trim() ?: ""

                val serverName =
                    linkTd.selectFirst("img[alt]")?.attr("alt")?.trim()
                        ?: anchor
                            .text()
                            .trim()
                            .ifEmpty { runCatching { URL(embedUrl).host }.getOrDefault("") }

                val fullUrl = if (embedUrl.startsWith("//")) "https:$embedUrl" else embedUrl
                embeds.add(
                    EmbedLink(
                        url = fullUrl,
                        serverName = serverName.ifEmpty { "Zaluknij" },
                        version = version,
                        quality = quality,
                        sourceWebsite = ZaluknijConfig.DOMAIN,
                    ),
                )
            }

            if (embeds.isEmpty()) {
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
                            val resolved = resolvePlayerUrl(dataId, userAgent)
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

    private fun search(query: String, userAgent: String?): SearchResults {
        val movies = mutableListOf<MovieItem>()
        val tvShows = mutableListOf<MovieItem>()

        try {
            val searchUrl = "${ZaluknijConfig.BASE_URL}${ZaluknijConfig.PATH_SEARCH}"
            val formBody = FormBody.Builder()
                .add("phrase", query)
                .build()

            val requestBuilder = Request.Builder()
                .url(searchUrl)
                .post(formBody)
                .header("User-Agent", userAgent ?: PLAYER_USER_AGENT)
                .header("Accept", "*/*")
                .header("Accept-Language", "pl-PL,pl;q=0.9,en-US;q=0.8,en;q=0.7")
                .header("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
                .header("Origin", ZaluknijConfig.BASE_URL)
                .header("Referer", "${ZaluknijConfig.BASE_URL}/")
                .header("X-Requested-With", "XMLHttpRequest")

            val request = requestBuilder.build()
            val (code, html, isMitigated) = NetworkClient.okHttpClient.newCall(request).execute().use { resp ->
                Triple(resp.code, resp.body.string(), resp.header("cf-mitigated").equals("challenge", ignoreCase = true))
            }

            if (isMitigated || isCloudflareChallengePage(html)) {
                throw CloudflareChallengeDetected("zaluknij.cc", searchUrl, request = request)
            }
            if (code !in 200..299) {
                Log.w(tag, "Zaluknij: search returned HTTP $code")
                return SearchResults(errorMessage = "HTTP $code")
            }

            val doc = Jsoup.parse(html)
            val allNodes = doc.select("h1, h2, h3, h4, h5, h6, .title, .header, div.col-xs-3, div.col-xs-3.col-lg-2, div.item, .movie-item")
            var currentGroupIsTvShow = false

            allNodes.forEach { node ->
                val tagName = node.tagName().lowercase()
                if (tagName.matches(Regex("h[1-6]")) || node.hasClass("title") || node.hasClass("header")) {
                    val text = node.text()
                    if (text.contains("serial", ignoreCase = true) && !text.contains("film", ignoreCase = true)) {
                        currentGroupIsTvShow = true
                    } else if (text.contains("film", ignoreCase = true)) {
                        currentGroupIsTvShow = false
                    }
                    return@forEach
                }

                if (!node.hasClass("col-xs-3") && !node.hasClass("col-lg-2") && !node.hasClass("item") && !node.hasClass("movie-item")) {
                    return@forEach
                }

                val a = node.selectFirst("a[href]") ?: return@forEach
                val href = a.attr("href").trim()
                if (href.isEmpty()) return@forEach

                val url = if (href.startsWith("http")) href else "${ZaluknijConfig.BASE_URL.trimEnd('/')}/${href.trimStart('/')}"
                val titleText = node.selectFirst(".title")?.text()?.trim()
                    ?: a.attr("title").takeIf { it.isNotBlank() }
                    ?: a.text().trim()
                if (titleText.isEmpty()) return@forEach

                val parsedYear = Regex("""\b(19\d{2}|20\d{2})\b""").find(node.text())?.value?.toIntOrNull()
                val cleanTitle = titleText.replace(Regex("""\s*\(\d{4}\)|\s*\[\d{4}\]|\s*\b(19\d{2}|20\d{2})\b"""), "").trim()

                val parts = cleanTitle.split(" / ", " - ")
                val titlePl = parts[0].trim()
                val titleEn = if (parts.size > 1) parts[1].trim() else null

                val isTvShow = currentGroupIsTvShow ||
                    url.contains("/serial/") ||
                    url.contains("/seriale/") ||
                    url.contains("/serial-online/") ||
                    node.text().contains("sezon", ignoreCase = true)

                val item = MovieItem(
                    url = url,
                    titlePl = titlePl,
                    titleEn = titleEn,
                    posterUrl = node.selectFirst("img")?.attr("src").orEmpty(),
                    year = parsedYear,
                    isTvShow = isTvShow,
                )
                if (isTvShow) tvShows.add(item) else movies.add(item)
            }
        } catch (e: CloudflareChallengeDetected) {
            throw e
        } catch (e: Exception) {
            Log.e(tag, "Zaluknij: search error for $query", e)
        }
        return SearchResults(movies = movies, tvShows = tvShows)
    }

    private fun findEpisodeUrl(showDoc: Document, season: Int, episode: Int): String? {
        val episodeList = showDoc.selectFirst("#episode-list, .episode-list, ul.episodes-list")
        if (episodeList != null) {
            val seasonNodes = episodeList.children().filter { it.tagName() == "li" }
            for (seasonNode in seasonNodes) {
                val seasonName = seasonNode.selectFirst("span, .season-title, h4, h3")?.text() ?: ""
                val seasonNum = Regex("""\d+""").find(seasonName)?.value?.toIntOrNull() ?: 0
                if (seasonNum == season) {
                    val episodeLinks = seasonNode.select("ul li a, .episodes a, a")
                    for (aTag in episodeLinks) {
                        val epTitle = aTag.text().trim()
                        val epNum = extractEpisodeNumber(epTitle).takeIf { it > 0 }
                            ?: extractEpisodeNumber(aTag.attr("href"))
                        if (epNum == episode) {
                            val href = aTag.attr("href")
                            return if (href.startsWith("http")) href else "${ZaluknijConfig.BASE_URL.trimEnd('/')}/${href.trimStart('/')}"
                        }
                    }
                }
            }
        }

        val episodePatterns = listOf(
            Regex("""(?:s(?:eason)?\s*0?${season}\s*e(?:pisode)?\s*0?${episode}\b)""", RegexOption.IGNORE_CASE),
            Regex("""\b0?${season}x0?${episode}\b""", RegexOption.IGNORE_CASE),
            Regex("""(?:sezon\s*0?${season}.*odcinek\s*0?${episode}\b)""", RegexOption.IGNORE_CASE),
            Regex("""\b[sS]0?${season}[eE]0?${episode}\b"""),
        )

        val episodeLinks = showDoc.select("a[href*='/odcinek/'], a[href*='/serial/'], #episodes a, ul.episodes a, .episode a, #episode-list a")
        for (link in episodeLinks) {
            val text = link.text().trim()
            val href = link.attr("href")

            for (pattern in episodePatterns) {
                if (pattern.containsMatchIn(text) || pattern.containsMatchIn(href)) {
                    return if (href.startsWith("http")) href else "${ZaluknijConfig.BASE_URL.trimEnd('/')}/${href.trimStart('/')}"
                }
            }
        }
        return null
    }

    private fun extractEpisodeNumber(text: String): Int {
        Regex("""[eE](\d+)""").find(text)?.groupValues?.get(1)?.toIntOrNull()?.let { return it }
        Regex("""odcinek\s*(\d+)""", RegexOption.IGNORE_CASE).find(text)?.groupValues?.get(1)?.toIntOrNull()?.let { return it }
        Regex("""\b(\d+)x(\d+)\b""").find(text)?.groupValues?.get(2)?.toIntOrNull()?.let { return it }
        return Regex("""\d+""").find(text)?.value?.toIntOrNull() ?: 0
    }

    private fun resolvePlayerUrl(dataId: String, userAgent: String?): String? {
        return try {
            val url = "${ZaluknijConfig.BASE_URL}/engine/ajax/controller.php?mod=player"
            val formBody = FormBody.Builder().add("id", dataId).build()
            val req = Request.Builder()
                .url(url)
                .post(formBody)
                .header("User-Agent", userAgent ?: PLAYER_USER_AGENT)
                .header("X-Requested-With", "XMLHttpRequest")
                .header("Referer", ZaluknijConfig.BASE_URL)

            val body = NetworkClient.okHttpClient.newCall(req.build()).execute().use { res ->
                res.body.string()
            }
            val json = JSONObject(body)
            if (json.has("url")) json.getString("url") else null
        } catch (e: Exception) {
            null
        }
    }

    private fun fetchDoc(url: String, userAgent: String?): Document? {
        return try {
            val req = Request.Builder()
                .url(url)
                .get()
                .header("User-Agent", userAgent ?: PLAYER_USER_AGENT)
                .header("Referer", ZaluknijConfig.BASE_URL)

            val request = req.build()
            val (code, html, isMitigated) = NetworkClient.okHttpClient.newCall(request).execute().use { res ->
                Triple(res.code, res.body.string(), res.header("cf-mitigated").equals("challenge", ignoreCase = true))
            }

            if (isMitigated || isCloudflareChallengePage(html)) {
                throw CloudflareChallengeDetected("zaluknij.cc", url, request = request)
            }
            if (code !in 200..299) {
                Log.w(tag, "Zaluknij: page returned HTTP $code")
                return null
            }

            Jsoup.parse(html)
        } catch (e: CloudflareChallengeDetected) {
            throw e
        } catch (e: Exception) {
            Log.e(tag, "Zaluknij: failed to fetch $url", e)
            null
        }
    }
}
