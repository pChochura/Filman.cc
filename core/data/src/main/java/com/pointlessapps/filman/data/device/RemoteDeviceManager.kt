package com.pointlessapps.filman.data.device

import android.content.Context
import android.os.Build
import android.provider.Settings
import android.util.Log
import com.pointlessapps.filman.data.local.SessionManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.Locale
import java.util.UUID
import java.util.concurrent.TimeUnit

@Serializable
data class RemotePlayCommand(
    val action: String = "PLAY_MEDIA",
    val url: String,
    val episodeUrl: String? = null,
    val title: String? = null,
    val progressMs: Long = 0L,
    val autoPlay: Boolean = true,
    val timestamp: Long = 0L,
)

@Serializable
data class DeviceHeartbeatRequest(
    val deviceId: String,
    val deviceName: String,
    val platform: String = "android_tv",
    val appVersion: String = "",
)

@Serializable
data class DeviceHeartbeatResponse(
    val success: Boolean,
    val timestamp: Long = 0L,
)

class RemoteDeviceManager(
    private val context: Context,
    okHttpClient: OkHttpClient,
    private val sessionManager: SessionManager,
    private val baseUrl: String,
    private val apiKey: String = "",
) {
    companion object {
        private const val TAG = "RemoteDeviceManager"
        private const val PREFS_NAME = "tiwi_device"
        private const val KEY_DEVICE_ID = "device_id"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    // Dedicated client for SSE with disabled readTimeout
    private val sseClient = okHttpClient.newBuilder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()

    // Normal client for REST calls
    private val restClient = okHttpClient.newBuilder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val _remoteCommands = MutableSharedFlow<RemotePlayCommand>(extraBufferCapacity = 1)
    val remoteCommands: SharedFlow<RemotePlayCommand> = _remoteCommands.asSharedFlow()

    val deviceId: String by lazy { getOrCreateDeviceId() }
    val deviceName: String by lazy { resolveAutoDeviceName() }

    private var listenerJob: Job? = null

    init {
        scope.launch {
            sessionManager.authTokenFlow.collect { token ->
                listenerJob?.cancel()
                if (!token.isNullOrBlank()) {
                    listenerJob = launchListenerLoop(token)
                }
            }
        }
    }

    private fun getOrCreateDeviceId(): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        var id = prefs.getString(KEY_DEVICE_ID, null)
        if (id.isNullOrBlank()) {
            id = UUID.randomUUID().toString()
            prefs.edit().putString(KEY_DEVICE_ID, id).apply()
        }
        return id
    }

    fun resolveAutoDeviceName(): String {
        val globalName = runCatching {
            Settings.Global.getString(context.contentResolver, "device_name")
        }.getOrNull()
        if (!globalName.isNullOrBlank()) return globalName.trim()

        val bluetoothName = runCatching {
            Settings.Secure.getString(context.contentResolver, "bluetooth_name")
        }.getOrNull()
        if (!bluetoothName.isNullOrBlank()) return bluetoothName.trim()

        val rawManufacturer = runCatching { Build.MANUFACTURER }.getOrNull()
        val manufacturer = if (!rawManufacturer.isNullOrBlank()) {
            rawManufacturer.replaceFirstChar {
                if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString()
            }
        } else {
            "Android"
        }
        val model = runCatching { Build.MODEL }.getOrNull()?.takeIf { it.isNotBlank() } ?: "TV"
        return if (model.startsWith(manufacturer, ignoreCase = true)) model else "$manufacturer $model"
    }

    private fun Request.Builder.withApiKey(): Request.Builder {
        if (apiKey.isNotBlank()) {
            header("X-Api-Key", apiKey)
        }
        return this
    }

    private fun getBaseUrlBuilder() = baseUrl.trim().toHttpUrlOrNull()?.newBuilder()

    suspend fun sendHeartbeat(token: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val urlBuilder = getBaseUrlBuilder()
                ?: throw IllegalStateException("Stream API base URL is not configured")
            val url = urlBuilder
                .addPathSegment("api")
                .addPathSegment("devices")
                .addPathSegment("heartbeat")
                .build()

            val appVer = runCatching {
                val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
                pInfo.versionName ?: ""
            }.getOrDefault("")

            val reqPayload = DeviceHeartbeatRequest(
                deviceId = deviceId,
                deviceName = deviceName,
                platform = "android_tv",
                appVersion = appVer,
            )

            val body = json.encodeToString(reqPayload).toRequestBody(jsonMediaType)
            val request = Request.Builder()
                .url(url)
                .withApiKey()
                .header("Authorization", "Bearer $token")
                .post(body)
                .build()

            restClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IllegalStateException("Heartbeat failed with HTTP ${response.code}")
                }
            }
        }
    }

    private fun launchListenerLoop(token: String): Job = scope.launch {
        while (isActive) {
            try {
                // 1. Send heartbeat registration
                sendHeartbeat(token).onFailure { e ->
                    Log.w(TAG, "Device heartbeat registration failed: ${e.message}")
                }

                // 2. Open SSE stream
                val urlBuilder = getBaseUrlBuilder()
                if (urlBuilder == null) {
                    delay(10_000L)
                    continue
                }

                val eventsUrl = urlBuilder
                    .addPathSegment("api")
                    .addPathSegment("devices")
                    .addPathSegment("events")
                    .addQueryParameter("deviceId", deviceId)
                    .build()

                val sseRequest = Request.Builder()
                    .url(eventsUrl)
                    .withApiKey()
                    .header("Authorization", "Bearer $token")
                    .header("Accept", "text/event-stream")
                    .build()

                Log.d(TAG, "Connecting to SSE stream: $eventsUrl (device: $deviceName)")
                sseClient.newCall(sseRequest).execute().use { response ->
                    if (!response.isSuccessful) {
                        Log.w(TAG, "SSE connection response code: ${response.code}")
                        return@use
                    }
                    val reader = BufferedReader(InputStreamReader(response.body.byteStream()))
                    var currentEvent = "message"

                    while (isActive) {
                        val line = reader.readLine() ?: break
                        val trimmed = line.trim()
                        if (trimmed.isEmpty()) continue
                        if (trimmed.startsWith(":")) continue // Ping/keep-alive comment

                        if (trimmed.startsWith("event:")) {
                            currentEvent = trimmed.removePrefix("event:").trim()
                        } else if (trimmed.startsWith("data:")) {
                            val dataStr = trimmed.removePrefix("data:").trim()
                            if (currentEvent == "play" || dataStr.contains("\"PLAY_MEDIA\"")) {
                                Log.i(TAG, "Received PLAY_MEDIA command via SSE: $dataStr")
                                val cmd = runCatching {
                                    json.decodeFromString<RemotePlayCommand>(dataStr)
                                }.getOrNull()
                                if (cmd != null) {
                                    _remoteCommands.emit(cmd)
                                }
                            }
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "SSE listener connection error: ${e.message}. Retrying in 5 seconds...")
            }

            delay(5_000L)
        }
    }
}
