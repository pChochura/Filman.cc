package com.pointlessapps.filman.data.scraper

import android.util.Log
import com.pointlessapps.filman.config.FilmanConfig
import com.pointlessapps.filman.config.PLAYER_USER_AGENT
import com.pointlessapps.filman.data.api.AuthException
import com.pointlessapps.filman.data.api.NetworkClient
import com.pointlessapps.filman.data.api.extractors.resolveFilmanEmbedLink
import com.pointlessapps.filman.data.model.EmbedLink
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.nodes.Document

data class ScraperMatchResult(
    val mediaUrl: String?,
    val episodeUrl: String? = null,
    val embeds: List<EmbedLink> = emptyList(),
)

class FilmanScraper {
    private val tag = "FilmanScraper"

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

        if (cookie.isNullOrBlank()) {
            throw AuthException("Filman requires authentication to search and resolve links")
        }

        try {
            if (matchedUrl == null) {
                val query = java.net.URLEncoder.encode(title, "UTF-8")
                val searchUrl = "${FilmanConfig.BASE_URL}${FilmanConfig.PATH_SEARCH}$query"
                val searchDoc = fetchDoc(searchUrl, cookie, userAgent)

                matchedUrl = searchDoc?.let { findBestMatch(it, title, year, season != null) }

                if (matchedUrl == null && !originalTitle.isNullOrBlank() && !originalTitle.equals(title, ignoreCase = true)) {
                    val altQuery = java.net.URLEncoder.encode(originalTitle, "UTF-8")
                    val altSearchDoc = fetchDoc("${FilmanConfig.BASE_URL}${FilmanConfig.PATH_SEARCH}$altQuery", cookie, userAgent)
                    matchedUrl = altSearchDoc?.let { findBestMatch(it, originalTitle, year, season != null) }
                }
            }

            if (matchedUrl == null) {
                Log.d(tag, "Filman: no match found for \"$title\"")
                return@withContext ScraperMatchResult(null, null, emptyList())
            }

            var targetDoc: Document?
            if (season != null && episode != null) {
                if (matchedEpUrl == null) {
                    val showDoc = fetchDoc(matchedUrl, cookie, userAgent)
                        ?: return@withContext ScraperMatchResult(matchedUrl, null, emptyList())
                    val epUrl = findEpisodeUrl(showDoc, season, episode)
                    if (epUrl != null) {
                        matchedEpUrl = if (epUrl.startsWith("http")) epUrl else "${FilmanConfig.BASE_URL.trimEnd('/')}/${epUrl.trimStart('/')}"
                    }
                }

                if (matchedEpUrl == null) {
                    Log.w(tag, "Filman: could not find episode S${season}E${episode} for \"$title\"")
                    return@withContext ScraperMatchResult(matchedUrl, null, emptyList())
                }

                targetDoc = fetchDoc(matchedEpUrl, cookie, userAgent)
            } else {
                targetDoc = fetchDoc(matchedUrl, cookie, userAgent)
            }

            if (targetDoc == null) {
                return@withContext ScraperMatchResult(matchedUrl, matchedEpUrl, emptyList())
            }

            val routeToken = extractRouteToken(targetDoc)
            val linkElements = targetDoc.select("a[data-link-id]")

            if (linkElements.isEmpty()) {
                Log.d(tag, "Filman: no embed links found on target page for \"$title\"")
                return@withContext ScraperMatchResult(matchedUrl, matchedEpUrl, emptyList())
            }

            // Link resolution requires a logged-in session cookie
            if (cookie.isNullOrBlank()) {
                throw AuthException("Filman requires authentication to resolve links")
            }

            val ua = userAgent ?: PLAYER_USER_AGENT
            val resolveJobs = linkElements.map { element ->
                coroutineScope {
                    async {
                        val linkId = element.attr("data-link-id")
                        var serverName = element.selectFirst("img")?.attr("alt") ?: element.text().trim()
                        var version = ""
                        var quality = ""

                        val tr = element.closest("tr")
                        if (tr != null) {
                            val tds = tr.select("td")
                            for (td in tds) {
                                val text = td.text().trim()
                                if (text.matches(Regex(".*(?:\\d{3,4}p|CAM|TS|HD|SD|4K).*", RegexOption.IGNORE_CASE))) {
                                    quality = text
                                } else if (text.matches(Regex(".*(?:Lektor|Napisy|Dubbing|PL|ENG).*", RegexOption.IGNORE_CASE))) {
                                    version = text
                                }
                            }
                            if (serverName.isEmpty() || serverName.equals("Oglądaj", ignoreCase = true)) {
                                serverName = tr.selectFirst("img")?.attr("alt") ?: serverName
                            }
                        }

                        val realUrl = resolveFilmanEmbedLink(cookie, ua, linkId, routeToken)
                        if (realUrl != null) {
                            EmbedLink(
                                url = realUrl,
                                serverName = serverName,
                                version = version,
                                quality = quality,
                                sourceWebsite = FilmanConfig.DOMAIN
                            )
                        } else null
                    }
                }
            }

            embeds.addAll(resolveJobs.awaitAll().filterNotNull())
            Log.d(tag, "Filman: found ${embeds.size} resolved embeds for \"$title\"")

        } catch (e: AuthException) {
            throw e
        } catch (e: Exception) {
            Log.e(tag, "Filman: scrape failed for \"$title\"", e)
        }
        ScraperMatchResult(matchedUrl, matchedEpUrl, embeds)
    }

    private fun findBestMatch(doc: Document, title: String, year: Int?, preferTvShow: Boolean): String? {
        data class Result(val url: String, val titlePl: String, val titleEn: String?, val year: Int?, val isTvShow: Boolean)

        val results = mutableListOf<Result>()
        val parsedUrls = mutableSetOf<String>()
        var currentGroupIsTvShow = false

        val elements = doc.select("h1, h2, h3, h4, h5, h6, .title, .header, .poster, .movie-item")
        for (element in elements) {
            val tagName = element.tagName().lowercase()
            if (tagName.matches(Regex("h[1-6]")) || element.hasClass("title") || element.hasClass("header")) {
                val text = element.text()
                if (text.contains("serial", ignoreCase = true) && !text.contains("film", ignoreCase = true)) {
                    currentGroupIsTvShow = true
                } else if (text.contains("film", ignoreCase = true)) {
                    currentGroupIsTvShow = false
                }
                continue
            }

            val link = if (element.tagName().equals("a", ignoreCase = true)) element else element.selectFirst("a")
            val href = link?.attr("href") ?: continue
            val fullUrl = if (href.startsWith("http")) href else "${FilmanConfig.BASE_URL.trimEnd('/')}/${href.trimStart('/')}"

            if (!parsedUrls.add(fullUrl)) continue

            val rawTitle = link.attr("data-title").ifEmpty {
                element.selectFirst("img")?.attr("alt") ?: link.text().trim()
            }
            if (rawTitle.isEmpty()) continue

            val parsedYear = Regex("""\b(19\d{2}|20\d{2})\b""").find(rawTitle)?.value?.toIntOrNull()
            val cleanTitle = rawTitle.replace(Regex("""\s*\(\d{4}\)|\s*\[\d{4}\]|\s*\b(19\d{2}|20\d{2})\b"""), "").trim()

            val parts = cleanTitle.split(" / ", " - ")
            val titlePl = parts[0].trim()
            val titleEn = if (parts.size > 1) parts[1].trim() else null

            val isTvShow = currentGroupIsTvShow ||
                fullUrl.contains("/serial/") ||
                fullUrl.contains("/seriale/") ||
                rawTitle.contains("sezon", ignoreCase = true)

            results.add(Result(fullUrl, titlePl, titleEn, parsedYear, isTvShow))
        }

        if (results.isEmpty()) return null

        val cleanTarget = normalize(title)

        fun textMatches(t: String): Boolean {
            val c = normalize(t)
            return c.isNotEmpty() && (c == cleanTarget || c.contains(cleanTarget) || cleanTarget.contains(c))
        }

        val typeFiltered = results.filter { it.isTvShow == preferTvShow }
        val candidatePool = if (typeFiltered.isNotEmpty()) typeFiltered else results

        val match = candidatePool.firstOrNull { r ->
            val titleOk = textMatches(r.titlePl) || (r.titleEn != null && textMatches(r.titleEn))
            val yearOk = year == null || r.year == null || r.year == year
            titleOk && yearOk
        } ?: candidatePool.firstOrNull { r ->
            textMatches(r.titlePl) || (r.titleEn != null && textMatches(r.titleEn))
        }

        return match?.url
    }

    private fun findEpisodeUrl(showDoc: Document, season: Int, episode: Int): String? {
        val episodePatterns = listOf(
            Regex("""(?:s(?:eason)?\s*0?${season}\s*e(?:pisode)?\s*0?${episode}\b)""", RegexOption.IGNORE_CASE),
            Regex("""\b0?${season}x0?${episode}\b""", RegexOption.IGNORE_CASE),
            Regex("""(?:sezon\s*0?${season}.*odcinek\s*0?${episode}\b)""", RegexOption.IGNORE_CASE),
            Regex("""\b[sS]0?${season}[eE]0?${episode}\b"""),
        )

        val episodeLinks = showDoc.select("a[href*='/serial/'], a[href*='/odcinek/'], .episodes a, #episodes a, ul.episodes-list a, .episode-item a")

        for (link in episodeLinks) {
            val text = link.text().trim()
            val href = link.attr("href")
            val titleAttr = link.attr("title")

            for (pattern in episodePatterns) {
                if (pattern.containsMatchIn(text) || pattern.containsMatchIn(href) || pattern.containsMatchIn(titleAttr)) {
                    return href
                }
            }
        }

        val seasonSection = showDoc.select(".season-$season, #season-$season, [data-season='$season']").firstOrNull()
        if (seasonSection != null) {
            val epLink = seasonSection.select("a").firstOrNull {
                it.text().contains("odcinek $episode", ignoreCase = true) ||
                    it.text().contains("e$episode", ignoreCase = true) ||
                    it.attr("href").contains("odcinek-$episode", ignoreCase = true)
            }
            if (epLink != null) return epLink.attr("href")
        }

        return null
    }

    private fun extractRouteToken(doc: Document): String {
        val scripts = doc.select("script")
        for (script in scripts) {
            val html = script.html()
            val match = Regex("""(?:route_token|rt)\s*[:=]\s*['"]([a-zA-Z0-9_-]+)['"]""").find(html)
            if (match != null) return match.groupValues[1]
        }
        return ""
    }

    private fun normalize(s: String): String =
        java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFD)
            .replace(Regex("\\p{M}"), "")
            .lowercase()
            .replace(Regex("[^a-z0-9\\s]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

    private fun fetchDoc(url: String, cookie: String?, userAgent: String?): Document? {
        return try {
            val requestBuilder = Request.Builder()
                .url(url)
                .get()
                .header("User-Agent", userAgent ?: PLAYER_USER_AGENT)
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .header("Accept-Language", "pl-PL,pl;q=0.9,en-US;q=0.8,en;q=0.7")
                .header("Referer", FilmanConfig.BASE_URL)

            if (!cookie.isNullOrBlank()) {
                requestBuilder.header("Cookie", cookie)
            }

            val (code, html, finalUrl) = NetworkClient.okHttpClient.newCall(requestBuilder.build()).execute().use { response ->
                Triple(response.code, response.body.string(), response.request.url.toString())
            }

            if (code == 401 || code == 403 ||
                finalUrl == FilmanConfig.BASE_URL ||
                finalUrl == "${FilmanConfig.BASE_URL}/" ||
                finalUrl.contains(FilmanConfig.LOGIN_PATH) ||
                finalUrl.contains("/logowanie")
            ) {
                throw AuthException("Filman session expired or login required")
            }

            Jsoup.parse(html)
        } catch (e: AuthException) {
            throw e
        } catch (e: Exception) {
            Log.e(tag, "Filman: failed to fetch $url", e)
            null
        }
    }
}
