package com.pointlessapps.filman.data.api

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

class ChallengeVerifier(client: OkHttpClient) {
    private val client = client.newBuilder().followRedirects(false).followSslRedirects(false).build()

    suspend fun verify(url: String, userAgent: String): Boolean {
        val target = url.toHttpUrlOrNull()
        if (target == null || target.scheme != "https") {
            Log.w("ChallengeVerifier", "Invalid challenge verification URL")
            return false
        }
        return verify(Request.Builder().url(target).build(), userAgent)
    }

    suspend fun verify(request: Request, userAgent: String): Boolean = withContext(Dispatchers.IO) {
        if (request.url.scheme != "https") {
            Log.w("ChallengeVerifier", "Invalid challenge verification URL")
            return@withContext false
        }
        val probe = request.newBuilder().removeHeader("Cookie").header("User-Agent", userAgent).build()
        try {
            client.newCall(probe).execute().use { response ->
                if (!response.isSuccessful || response.header("cf-mitigated").equals("challenge", true)) {
                    return@use false
                }
                if (response.body.contentType()?.subtype?.contains("html", ignoreCase = true) != true) {
                    return@use false
                }
                val body = response.peekBody(128 * 1024).string()
                body.isNotBlank() &&
                    !body.contains("challenge-platform", ignoreCase = true) &&
                    !body.contains("cf-browser-verification", ignoreCase = true) &&
                    !body.contains("<title>Just a moment", ignoreCase = true)
            }
        } catch (e: IOException) {
            Log.w("ChallengeVerifier", "Provider verification request failed", e)
            false
        }
    }
}
