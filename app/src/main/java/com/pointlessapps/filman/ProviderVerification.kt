package com.pointlessapps.filman

import com.pointlessapps.filman.config.FilmanConfig
import okhttp3.Request

internal fun clearanceCookieValue(cookies: String?): String? =
    cookies?.split(';')?.firstNotNullOfOrNull { part ->
        val pair = part.trim().split('=', limit = 2)
        if (pair.size == 2 && pair[0].trim() == "cf_clearance") {
            pair[1].trim().takeIf { it.isNotEmpty() }
        } else {
            null
        }
    }

internal class ClearanceCookieTracker(initialCookies: String?) {
    private val initialClearance = clearanceCookieValue(initialCookies)

    fun hasNewClearance(cookies: String?): Boolean {
        val current = clearanceCookieValue(cookies) ?: return false
        return current != initialClearance
    }
}

internal suspend fun verifyNewClearance(
    tracker: ClearanceCookieTracker,
    readCookies: () -> String?,
    verify: suspend () -> Boolean,
): String? {
    val before = readCookies()
    if (!tracker.hasNewClearance(before) || !verify()) return null
    val after = readCookies()
    return after?.takeIf { clearanceCookieValue(it) == clearanceCookieValue(before) }
}

internal fun verificationPageUrl(targetUrl: String, request: Request?): String =
    when {
        request?.method == "GET" -> request.url.toString()
        targetUrl == FilmanConfig.BASE_URL -> FilmanConfig.LOGIN_URL
        else -> targetUrl
    }

internal suspend fun <T> verifyCurrentProviderSession(
    current: () -> T,
    verify: suspend (T) -> Boolean,
): Boolean {
    val request = current()
    return verify(request) && request == current()
}

internal suspend fun probeExistingProviderSession(
    verify: suspend () -> Boolean,
    onVerified: suspend () -> Unit,
    onWebViewRequired: () -> Unit,
): Boolean {
    if (verify()) {
        onVerified()
        return true
    }
    onWebViewRequired()
    return false
}
