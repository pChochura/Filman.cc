package com.pointlessapps.filman.data.api

import android.util.Log
import com.pointlessapps.filman.core.data.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit

class ChallengeVerifier(client: OkHttpClient) {
    private val client = client.newBuilder()
        .followRedirects(false)
        .followSslRedirects(false)
        .cookieJar(object : CookieJar {
            override fun loadForRequest(url: HttpUrl): List<Cookie> = client.cookieJar.loadForRequest(url)

            override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) = Unit
        })
        .callTimeout(8, TimeUnit.SECONDS)
        .build()

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
            client.newCall(probe).awaitResponse().use { response ->
                if (BuildConfig.DEBUG) {
                    Log.d(
                        "ChallengeVerifier",
                        "Provider ${probe.url.host}: HTTP ${response.code}, contentType=${response.body.contentType()}, challenged=${response.header("cf-mitigated").equals("challenge", true)}",
                    )
                }

                if (!response.isSuccessful || response.header("cf-mitigated").equals("challenge", true)) {
                    Log.w("ChallengeVerifier", "Provider ${probe.url.host} native probe rejected: HTTP ${response.code}, challenged=${response.header("cf-mitigated").equals("challenge", true)}")
                    return@use false
                }
                if (response.body.contentType()?.subtype?.contains("html", ignoreCase = true) != true) {
                    Log.w("ChallengeVerifier", "Provider ${probe.url.host} native probe returned non-HTML content")
                    return@use false
                }
                val body = response.peekBody(128 * 1024).string()
                if (BuildConfig.DEBUG) {
                    Log.d(
                        "ChallengeVerifier",
                        "Provider ${probe.url.host}: bodyEmpty=${body.isBlank()}, challengePlatform=${body.contains("challenge-platform", ignoreCase = true)}, browserVerification=${body.contains("cf-browser-verification", ignoreCase = true)}, challengeTitle=${body.contains("<title>Just a moment", ignoreCase = true)}",
                    )
                }
                val verified = body.isNotBlank() && !isCloudflareChallengePage(body)
                if (!verified) {
                    Log.w("ChallengeVerifier", "Provider ${probe.url.host} native probe returned an empty or challenged page")
                } else if (BuildConfig.DEBUG) {
                    Log.d("ChallengeVerifier", "Provider ${probe.url.host} native probe verified")
                }
                verified
            }
        } catch (e: IOException) {
            Log.w("ChallengeVerifier", "Provider verification request failed", e)
            false
        }
    }

    private suspend fun Call.awaitResponse(): Response = suspendCancellableCoroutine { continuation ->
        continuation.invokeOnCancellation { cancel() }
        enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (continuation.isActive) continuation.resumeWith(Result.failure(e))
            }

            override fun onResponse(call: Call, response: Response) {
                continuation.resume(response) { _, value, _ -> value.close() }
            }
        })
    }
}
