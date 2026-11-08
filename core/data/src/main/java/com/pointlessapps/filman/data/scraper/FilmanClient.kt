package com.pointlessapps.filman.data.scraper

import android.webkit.CookieManager
import com.pointlessapps.filman.config.FilmanConfig
import com.pointlessapps.filman.data.local.SessionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import org.jsoup.Jsoup
import org.jsoup.nodes.Document

class AuthException(
    message: String,
) : Exception(message)

class FilmanClient(
    private val sessionManager: SessionManager,
) {
    private val baseUrl = FilmanConfig.BASE_URL

    private fun getEffectiveCookies(): String? {
        val sessionCookie = sessionManager.getCookie().orEmpty()
        val cfCookie = sessionManager.getCloudflareCookie().orEmpty()
        val cookieManagerCookie = try {
            CookieManager.getInstance().getCookie(FilmanConfig.BASE_URL).orEmpty()
        } catch (_: Exception) {
            ""
        }

        if (sessionCookie.isBlank() && cfCookie.isBlank() && cookieManagerCookie.isBlank()) {
            return null
        }

        val cookieMap = mutableMapOf<String, String>()
        listOf(cookieManagerCookie, cfCookie, sessionCookie).forEach { cookieStr ->
            cookieStr.split(";").forEach { part ->
                val trimmed = part.trim()
                val eqIdx = trimmed.indexOf('=')
                if (eqIdx > 0) {
                    val key = trimmed.substring(0, eqIdx).trim()
                    val value = trimmed.substring(eqIdx + 1).trim()
                    if (key.isNotBlank()) {
                        cookieMap[key] = value
                    }
                }
            }
        }
        return if (cookieMap.isEmpty()) null else cookieMap.entries.joinToString("; ") { "${it.key}=${it.value}" }
    }

    suspend fun getDocument(
        path: String,
        passCookies: Boolean = false,
    ): Document = runInterruptible(Dispatchers.IO) {
        val cleanPath = path.trim().replace("\n", "").replace("\r", "")
        val url =
            if (cleanPath.startsWith("http")) {
                cleanPath
            } else {
                val separator = if (cleanPath.startsWith("/")) "" else "/"
                "$baseUrl$separator$cleanPath"
            }
        val userAgent = sessionManager.getUserAgent()
        val cookie = getEffectiveCookies()

        val conn =
            Jsoup
                .connect(url)
                .userAgent(userAgent)
                .timeout(15_000)
                .ignoreHttpErrors(true)
                .followRedirects(true)
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .header("Accept-Language", "pl-PL,pl;q=0.9,en-US;q=0.8,en;q=0.7")
                .header("Referer", baseUrl)

        if (!cookie.isNullOrBlank()) {
            conn.header("Cookie", cookie)
        }

        val doc = conn.get()
        val currentUrl = conn.response().url().toString()
        val response = conn.response()
        val statusCode = response.statusCode()
        val title = doc.title().lowercase()
        val html = doc.html().lowercase()

        val isCloudflare =
            statusCode == 403 || statusCode == 503 ||
            response.header("cf-mitigated").equals("challenge", ignoreCase = true) ||
            title.contains("just a moment") ||
            title.contains("attention required") ||
            html.contains("challenges.cloudflare.com") ||
            html.contains("cf-turnstile")

        if (isCloudflare) {
            sessionManager.requestChallenge()
            throw Exception("Wymagana weryfikacja Cloudflare (Filman)")
        }

        if (currentUrl.contains(FilmanConfig.LOGIN_PATH)) {
            if (sessionManager.hasCookie()) {
                sessionManager.clearCookie()
                throw AuthException("Cookie expired or invalid. Redirected to ${FilmanConfig.LOGIN_PATH}.")
            }
        }

        if (currentUrl.endsWith("/404")) {
            throw Exception("Page not found (404)")
        }

        if (statusCode != 200) {
            throw Exception("HTTP Error: $statusCode")
        }

        doc
    }
}
