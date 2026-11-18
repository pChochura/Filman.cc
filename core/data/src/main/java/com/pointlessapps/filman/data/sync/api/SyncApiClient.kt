package com.pointlessapps.filman.data.sync.api

import android.util.Log
import com.pointlessapps.filman.data.sync.model.SyncAuthRequest
import com.pointlessapps.filman.data.sync.model.SyncAuthResponse
import com.pointlessapps.filman.data.sync.model.SyncPullResponse
import com.pointlessapps.filman.data.sync.model.SyncPushRequest
import com.pointlessapps.filman.data.sync.model.SyncPushResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

class SyncApiClient(
    client: OkHttpClient,
    private val baseUrl: String,
) {
    private val client = client.newBuilder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    private fun getBaseUrlBuilder() = baseUrl.trim().toHttpUrlOrNull()?.newBuilder()

    suspend fun login(request: SyncAuthRequest): Result<SyncAuthResponse> = withContext(Dispatchers.IO) {
        runCatching {
            val urlBuilder = getBaseUrlBuilder()
                ?: throw IllegalStateException("Sync API base URL is not configured")
            val url = urlBuilder
                .addPathSegment("api")
                .addPathSegment("auth")
                .addPathSegment("login")
                .build()

            val body = json.encodeToString(request).toRequestBody(jsonMediaType)
            val httpRequest = Request.Builder()
                .url(url)
                .post(body)
                .build()

            client.newCall(httpRequest).execute().use { response ->
                val responseBody = response.body?.string() ?: ""
                if (!response.isSuccessful) {
                    val errorMsg = runCatching {
                        json.decodeFromString<SyncAuthResponse>(responseBody).message
                    }.getOrNull() ?: "HTTP ${response.code}: ${response.message}"
                    throw IOException(errorMsg)
                }
                json.decodeFromString<SyncAuthResponse>(responseBody)
            }
        }
    }

    suspend fun register(request: SyncAuthRequest): Result<SyncAuthResponse> = withContext(Dispatchers.IO) {
        runCatching {
            val urlBuilder = getBaseUrlBuilder()
                ?: throw IllegalStateException("Sync API base URL is not configured")
            val url = urlBuilder
                .addPathSegment("api")
                .addPathSegment("auth")
                .addPathSegment("register")
                .build()

            val body = json.encodeToString(request).toRequestBody(jsonMediaType)
            val httpRequest = Request.Builder()
                .url(url)
                .post(body)
                .build()

            client.newCall(httpRequest).execute().use { response ->
                val responseBody = response.body?.string() ?: ""
                if (!response.isSuccessful) {
                    val errorMsg = runCatching {
                        json.decodeFromString<SyncAuthResponse>(responseBody).message
                    }.getOrNull() ?: "HTTP ${response.code}: ${response.message}"
                    throw IOException(errorMsg)
                }
                json.decodeFromString<SyncAuthResponse>(responseBody)
            }
        }
    }

    suspend fun pull(since: Long, token: String): Result<SyncPullResponse> = withContext(Dispatchers.IO) {
        runCatching {
            val urlBuilder = getBaseUrlBuilder()
                ?: throw IllegalStateException("Sync API base URL is not configured")
            val url = urlBuilder
                .addPathSegment("api")
                .addPathSegment("sync")
                .addPathSegment("pull")
                .addQueryParameter("since", since.toString())
                .build()

            val httpRequest = Request.Builder()
                .url(url)
                .header("Authorization", "Bearer $token")
                .get()
                .build()

            client.newCall(httpRequest).execute().use { response ->
                val responseBody = response.body?.string() ?: ""
                if (!response.isSuccessful) {
                    throw IOException("HTTP ${response.code}: ${response.message}")
                }
                json.decodeFromString<SyncPullResponse>(responseBody)
            }
        }
    }

    suspend fun push(request: SyncPushRequest, token: String): Result<SyncPushResponse> = withContext(Dispatchers.IO) {
        runCatching {
            val urlBuilder = getBaseUrlBuilder()
                ?: throw IllegalStateException("Sync API base URL is not configured")
            val url = urlBuilder
                .addPathSegment("api")
                .addPathSegment("sync")
                .addPathSegment("push")
                .build()

            val body = json.encodeToString(request).toRequestBody(jsonMediaType)
            val httpRequest = Request.Builder()
                .url(url)
                .header("Authorization", "Bearer $token")
                .post(body)
                .build()

            client.newCall(httpRequest).execute().use { response ->
                val responseBody = response.body?.string() ?: ""
                if (!response.isSuccessful) {
                    throw IOException("HTTP ${response.code}: ${response.message}")
                }
                json.decodeFromString<SyncPushResponse>(responseBody)
            }
        }
    }
}
