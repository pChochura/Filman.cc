package com.example.filman.data.scraper

import com.example.filman.config.FilmanConfig
import com.example.filman.data.local.SessionManager
import org.jsoup.Jsoup
import org.jsoup.nodes.Document

class AuthException(message: String) : Exception(message)

class FilmanClient(private val sessionManager: SessionManager) {
    private val baseUrl = FilmanConfig.BASE_URL

    fun getDocument(path: String, passCookies: Boolean = false): Document {
        val cleanPath = path.trim().replace("\n", "").replace("\r", "")
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

        if (!passCookies && currentUrl.contains(FilmanConfig.LOGIN_PATH) && !cookie.isNullOrBlank()) {
            conn = Jsoup.connect(url)
                .userAgent(userAgent)
                .ignoreHttpErrors(true)
                .followRedirects(true)
                .header("Cookie", cookie)

            doc = conn.get()
            currentUrl = conn.response().url().toString()
        }

        if (currentUrl.contains(FilmanConfig.LOGIN_PATH)) {
            throw AuthException("Cookie expired or invalid. Redirected to ${FilmanConfig.LOGIN_PATH}.")
        }

        if (currentUrl.endsWith("/404")) {
            throw Exception("Page not found (404)")
        }

        if (conn.response().statusCode() != 200) {
            throw Exception("HTTP Error: ${conn.response().statusCode()}")
        }

        return doc
    }
}
