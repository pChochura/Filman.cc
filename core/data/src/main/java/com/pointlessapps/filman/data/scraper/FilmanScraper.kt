package com.pointlessapps.filman.data.scraper

import android.util.Log
import com.pointlessapps.filman.config.FilmanConfig
import com.pointlessapps.filman.config.PLAYER_USER_AGENT
import com.pointlessapps.filman.data.api.AuthException
import com.pointlessapps.filman.data.api.CloudflareChallengeDetected
import com.pointlessapps.filman.data.api.NetworkClient
import com.pointlessapps.filman.data.api.extractors.resolveFilmanEmbedLink
import com.pointlessapps.filman.data.model.EmbedLink
import com.pointlessapps.filman.data.model.MovieItem
import com.pointlessapps.filman.utils.findBestMatchWithFallback
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
        val isTvShow = season != null
        var matchedUrl = directUrl?.takeIf { it.isNotBlank() }?.takeUnless {
            (isTvShow && it.contains("/film/")) || (!isTvShow && it.contains("/serial/"))
        }
        var matchedEpUrl = directEpisodeUrl?.takeIf { it.isNotBlank() }

        if (cookie.isNullOrBlank()) {
            throw AuthException("Filman requires authentication to search and resolve links")
        }

        try {
            if (matchedUrl == null) {
                val query = java.net.URLEncoder.encode(title, "UTF-8")
                val searchUrl = "${FilmanConfig.BASE_URL}${FilmanConfig.PATH_SEARCH}$query"
                val searchDoc = fetchDoc(searchUrl, cookie, userAgent)

                matchedUrl = searchDoc?.let { findBestMatch(it, title, originalTitle, year, season != null) }

                if (matchedUrl == null && !originalTitle.isNullOrBlank() && !originalTitle.equals(title, ignoreCase = true)) {
                    val altQuery = java.net.URLEncoder.encode(originalTitle, "UTF-8")
                    val altSearchDoc = fetchDoc("${FilmanConfig.BASE_URL}${FilmanConfig.PATH_SEARCH}$altQuery", cookie, userAgent)
                    matchedUrl = altSearchDoc?.let { findBestMatch(it, originalTitle, title, year, season != null) }
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

        } catch (e: CloudflareChallengeDetected) {
            throw e
        } catch (e: AuthException) {
            throw e
        } catch (e: Exception) {
            Log.e(tag, "Filman: scrape failed for \"$title\"", e)
        }
        ScraperMatchResult(matchedUrl, matchedEpUrl, embeds)
    }

    private fun findBestMatch(
        doc: Document,
        title: String,
        originalTitle: String?,
        year: Int?,
        preferTvShow: Boolean,
    ): String? {
        val allNodes = doc.select("h1, h2, h3, h4, h5, h6, .title, .header, .poster, .movie-item, div.col-xs-3, div.col-xs-3.col-lg-2, div.item")
        val items = mutableListOf<MovieItem>()
        val parsedUrls = mutableSetOf<String>()
        var currentGroupIsTvShow = false

        for (element in allNodes) {
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

            items.add(
                MovieItem(
                    url = fullUrl,
                    titlePl = titlePl,
                    titleEn = titleEn,
                    posterUrl = element.selectFirst("img")?.attr("src").orEmpty(),
                    year = parsedYear,
                    isTvShow = isTvShow,
                )
            )
        }

        if (items.isEmpty()) return null

        val typeFiltered = items.filter { it.isTvShow == preferTvShow }
        val candidatePool = if (typeFiltered.isNotEmpty()) typeFiltered else items

        val match = candidatePool.findBestMatchWithFallback(title, originalTitle, year)
        return match?.url ?: candidatePool.firstOrNull()?.url
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
                            return if (href.startsWith("http")) href else "${FilmanConfig.BASE_URL.trimEnd('/')}/${href.trimStart('/')}"
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

        val episodeLinks = showDoc.select("a[href*='/serial/'], a[href*='/odcinek/'], .episodes a, #episodes a, ul.episodes-list a, .episode-item a, #episode-list a")

        for (link in episodeLinks) {
            val text = link.text().trim()
            val href = link.attr("href")
            val titleAttr = link.attr("title")

            for (pattern in episodePatterns) {
                if (pattern.containsMatchIn(text) || pattern.containsMatchIn(href) || pattern.containsMatchIn(titleAttr)) {
                    return if (href.startsWith("http")) href else "${FilmanConfig.BASE_URL.trimEnd('/')}/${href.trimStart('/')}"
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
            if (epLink != null) {
                val href = epLink.attr("href")
                return if (href.startsWith("http")) href else "${FilmanConfig.BASE_URL.trimEnd('/')}/${href.trimStart('/')}"
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

            val request = requestBuilder.build()
            val response = NetworkClient.okHttpClient.newCall(request).execute()
            val code: Int
            val html: String
            val finalUrl: String
            val challenged: Boolean
            response.use {
                code = it.code
                html = it.body.string()
                finalUrl = it.request.url.toString()
                challenged = it.header("cf-mitigated").equals("challenge", ignoreCase = true)
            }

            if (challenged || html.contains("challenge-platform", ignoreCase = true) ||
                html.contains("cf-browser-verification", ignoreCase = true) ||
                html.contains("<title>Just a moment", ignoreCase = true)
            ) {
                throw CloudflareChallengeDetected(FilmanConfig.DOMAIN, url, request = request)
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
        } catch (e: CloudflareChallengeDetected) {
            throw e
        } catch (e: AuthException) {
            throw e
        } catch (e: Exception) {
            Log.e(tag, "Filman: failed to fetch $url", e)
            null
        }
    }
}
