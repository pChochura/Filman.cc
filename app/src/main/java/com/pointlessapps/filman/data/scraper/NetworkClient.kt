package com.pointlessapps.filman.data.scraper

import android.webkit.CookieManager
import com.pointlessapps.filman.ui.login.PLAYER_USER_AGENT
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

internal object NetworkClient {

    /**
     * Stores cookies obtained from Cloudflare challenge solving (or other WebView interactions)
     * keyed by domain. These are merged with CookieManager cookies on every request.
     */
    private val cloudflareCookies = ConcurrentHashMap<String, String>()

    fun setCloudflareCookie(domain: String, cookieString: String) {
        val cleanDomain = domain.removePrefix("https://").removePrefix("http://").substringBefore("/")
        cloudflareCookies[cleanDomain] = cookieString
        // Also sync into WebView's CookieManager so future WebView loads benefit
        try {
            val baseUrl = "https://$cleanDomain"
            cookieString.split(";").map { it.trim() }.filter { it.isNotBlank() }.forEach { cookie ->
                CookieManager.getInstance().setCookie(baseUrl, cookie)
            }
            CookieManager.getInstance().flush()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Pre-seeds cookies from our store into CookieManager for a given URL.
     * Call this before loading a URL in a WebView to ensure the WebView
     * has any cookies we previously obtained via OkHttp or other WebViews.
     */
    fun preSeedCookiesForUrl(url: String) {
        try {
            val host = url.toHttpUrlOrNull()?.host ?: return
            for ((domain, cookies) in cloudflareCookies) {
                if (host.contains(domain, ignoreCase = true) || domain.contains(host, ignoreCase = true)) {
                    val baseUrl = "https://$host"
                    cookies.split(";").map { it.trim() }.filter { it.isNotBlank() }
                        .forEach { cookie ->
                            CookieManager.getInstance().setCookie(baseUrl, cookie)
                        }
                }
            }
            CookieManager.getInstance().flush()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun parseCookieString(url: HttpUrl, cookiePair: String): Cookie? {
        val eqIdx = cookiePair.indexOf('=')
        if (eqIdx <= 0) return null
        val name = cookiePair.substring(0, eqIdx).trim()
        val value = cookiePair.substring(eqIdx + 1).trim()
        if (name.isBlank()) return null
        return try {
            Cookie.Builder()
                .name(name)
                .value(value)
                .domain(url.host)
                .path("/")
                .build()
        } catch (_: Exception) {
            null
        }
    }

    private val bridgeCookieJar = object : CookieJar {
        override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
            val cfCookies = cookies.filter {
                it.name == "cf_clearance" || it.name == "__cf_bm"
            }
            if (cfCookies.isNotEmpty()) {
                val domain = url.host
                val existing = cloudflareCookies[domain] ?: ""
                val existingParts = existing.split(";").map { it.trim() }
                    .filter { it.isNotBlank() }
                    .associate {
                        val eqIdx = it.indexOf('=')
                        if (eqIdx >= 0) it.substring(0, eqIdx) to it else it to ""
                    }
                    .toMutableMap()

                cfCookies.forEach { cookie ->
                    existingParts[cookie.name] = "${cookie.name}=${cookie.value}"
                    try {
                        CookieManager.getInstance().setCookie("https://$domain", "${cookie.name}=${cookie.value}; path=/")
                    } catch (_: Exception) {}
                }

                cloudflareCookies[domain] = existingParts.values.joinToString("; ")
                try {
                    CookieManager.getInstance().flush()
                } catch (_: Exception) {}
            }
        }

        override fun loadForRequest(url: HttpUrl): List<Cookie> {
            val result = mutableListOf<Cookie>()
            val host = url.host

            // 1. Load from our stored Cloudflare cookies
            for ((domain, cookieString) in cloudflareCookies) {
                if (host.contains(domain, ignoreCase = true) || domain.contains(host, ignoreCase = true)) {
                    cookieString.split(";").map { it.trim() }.filter { it.isNotBlank() }
                        .forEach { part ->
                            parseCookieString(url, part)?.let { cookie ->
                                if (result.none { it.name == cookie.name }) {
                                    result.add(cookie)
                                }
                            }
                        }
                }
            }

            // 2. Load from Android CookieManager (captures cookies set by any WebView including HttpOnly cf_clearance)
            try {
                val managerCookies = CookieManager.getInstance().getCookie(url.toString())
                if (!managerCookies.isNullOrBlank()) {
                    managerCookies.split(";").map { it.trim() }.filter { it.isNotBlank() }
                        .forEach { part ->
                            parseCookieString(url, part)?.let { cookie ->
                                if (result.none { it.name == cookie.name }) {
                                    result.add(cookie)
                                }
                            }
                        }
                }
            } catch (_: Exception) {
                // CookieManager may not be initialized yet
            }

            return result
        }
    }

    val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .cookieJar(bridgeCookieJar)
            .addInterceptor { chain ->
                val request = chain.request()
                val hasUserAgent = request.header("User-Agent") != null
                val newRequest = if (!hasUserAgent) {
                    request.newBuilder().header("User-Agent", PLAYER_USER_AGENT).build()
                } else {
                    request
                }
                chain.proceed(newRequest)
            }
            .build()
    }
}
