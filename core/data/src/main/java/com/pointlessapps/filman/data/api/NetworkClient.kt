package com.pointlessapps.filman.data.api

import android.webkit.CookieManager
import android.util.Log
import com.pointlessapps.filman.config.PLAYER_USER_AGENT
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

object NetworkClient {
    /** Legacy session values retained for existing session-manager APIs. */
    private val cloudflareCookies = ConcurrentHashMap<String, String>()

    fun setCloudflareCookie(
        domain: String,
        cookieString: String,
    ) {
        val cleanDomain = domain.removePrefix("https://").removePrefix("http://").substringBefore("/")
        if (cookieString.isBlank()) {
            cloudflareCookies.remove(cleanDomain)
            return
        }
        cloudflareCookies[cleanDomain] = cookieString
        // Also sync into WebView's CookieManager so future WebView loads benefit
        try {
            val baseUrl = "https://$cleanDomain"
            val manager = CookieManager.getInstance()
            val existingNames = manager.getCookie(baseUrl).orEmpty().split(";")
                .map { it.trim().substringBefore("=") }.toSet()
            cookieString.split(";").map { it.trim() }
                .filter { it.isNotBlank() && it.substringBefore("=") !in existingNames }.forEach { cookie ->
                manager.setCookie(baseUrl, "$cookie; Path=/; Secure")
            }
            manager.flush()
        } catch (e: Exception) {
            Log.w("NetworkClient", "Could not restore provider cookies", e)
        }
    }

    fun getCloudflareCookie(domain: String): String? {
        val cleanDomain = domain.removePrefix("https://").removePrefix("http://").substringBefore("/")
        return cloudflareCookies[cleanDomain]
    }

    /** Flushes cookies already restored through setCloudflareCookie. */
    fun preSeedCookiesForUrl(url: String) {
        try {
            if (url.toHttpUrlOrNull() == null) return
            CookieManager.getInstance().flush()
        } catch (e: Exception) {
            Log.w("NetworkClient", "Could not flush provider cookies", e)
        }
    }

    private fun parseCookieString(
        url: HttpUrl,
        cookiePair: String,
    ): Cookie? {
        val eqIdx = cookiePair.indexOf('=')
        if (eqIdx <= 0) return null
        val name = cookiePair.substring(0, eqIdx).trim()
        val value = cookiePair.substring(eqIdx + 1).trim()
        if (name.isBlank()) return null
        return try {
            Cookie
                .Builder()
                .name(name)
                .value(value)
                .hostOnlyDomain(url.host)
                .path("/")
                .build()
        } catch (_: Exception) {
            null
        }
    }

    private val bridgeCookieJar =
        object : CookieJar {
            override fun saveFromResponse(
                url: HttpUrl,
                cookies: List<Cookie>,
            ) {
                val cfCookies =
                    cookies.filter {
                        it.name == "cf_clearance" || it.name == "__cf_bm"
                    }
                if (cfCookies.isNotEmpty()) {
                    val domain = url.host
                    val existing = cloudflareCookies[domain] ?: ""
                    val existingParts =
                        existing
                            .split(";")
                            .map { it.trim() }
                            .filter { it.isNotBlank() }
                            .associate {
                                val eqIdx = it.indexOf('=')
                                if (eqIdx >= 0) it.substring(0, eqIdx) to it else it to ""
                            }.toMutableMap()

                    cfCookies.forEach { cookie ->
                        existingParts[cookie.name] = "${cookie.name}=${cookie.value}"
                        try {
                            CookieManager.getInstance().setCookie(url.toString(), cookie.toString())
                        } catch (e: Exception) {
                            Log.w("NetworkClient", "Could not store response cookie", e)
                        }
                    }

                    cloudflareCookies[domain] = existingParts.values.joinToString("; ")
                    try {
                        CookieManager.getInstance().flush()
                    } catch (e: Exception) {
                        Log.w("NetworkClient", "Could not flush response cookies", e)
                    }
                }
            }

            override fun loadForRequest(url: HttpUrl): List<Cookie> {
                val result = mutableListOf<Cookie>()
                // CookieManager applies the original domain, path, expiry and secure restrictions.
                try {
                    val managerCookies = CookieManager.getInstance().getCookie(url.toString())
                    if (!managerCookies.isNullOrBlank()) {
                        managerCookies
                            .split(";")
                            .map { it.trim() }
                            .filter { it.isNotBlank() }
                            .forEach { part ->
                                parseCookieString(url, part)?.let { cookie ->
                                    result.add(cookie)
                                }
                            }
                    }
                } catch (e: Exception) {
                    Log.w("NetworkClient", "Could not load request cookies", e)
                }

                return result
            }
        }

    val okHttpClient: OkHttpClient by lazy {
        OkHttpClient
            .Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .cookieJar(bridgeCookieJar)
            .addInterceptor { chain ->
                val request = chain.request()
                val hasUserAgent = request.header("User-Agent") != null
                val newRequest =
                    if (!hasUserAgent) {
                        request.newBuilder().header("User-Agent", PLAYER_USER_AGENT).build()
                    } else {
                        request
                    }
                chain.proceed(newRequest)
            }.build()
    }
}
