package com.pointlessapps.filman.data.device

import android.content.Context
import android.content.SharedPreferences
import com.pointlessapps.filman.data.local.SessionManager
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class RemoteDeviceManagerTest {

    private lateinit var server: MockWebServer
    private lateinit var context: Context
    private lateinit var sharedPrefs: SharedPreferences
    private lateinit var prefsEditor: SharedPreferences.Editor
    private lateinit var sessionManager: SessionManager

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()

        context = mockk(relaxed = true)
        sharedPrefs = mockk(relaxed = true)
        prefsEditor = mockk(relaxed = true)

        every { context.getSharedPreferences("tiwi_device", Context.MODE_PRIVATE) } returns sharedPrefs
        every { sharedPrefs.getString("device_id", null) } returns "test-device-uuid-123"
        every { sharedPrefs.edit() } returns prefsEditor
        every { prefsEditor.putString(any(), any()) } returns prefsEditor

        sessionManager = mockk(relaxed = true)
        every { sessionManager.authTokenFlow } returns MutableStateFlow(null)
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `sendHeartbeat posts device info with auth headers`() = runBlocking {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"success":true,"timestamp":1700000000}"""),
        )

        val manager = RemoteDeviceManager(
            context = context,
            okHttpClient = OkHttpClient(),
            sessionManager = sessionManager,
            baseUrl = server.url("/").toString(),
            apiKey = "test-api-key",
        )

        val result = manager.sendHeartbeat("valid_jwt_token")
        assertTrue(result.isSuccess)

        val request = server.takeRequest()
        assertEquals("/api/devices/heartbeat", request.path)
        assertEquals("POST", request.method)
        assertEquals("Bearer valid_jwt_token", request.getHeader("Authorization"))
        assertEquals("test-api-key", request.getHeader("X-Api-Key"))
        val bodyText = request.body.readUtf8()
        assertTrue(bodyText.contains("test-device-uuid-123"))
        assertTrue(bodyText.contains("android_tv"))
    }

    @Test
    fun `auto device name resolution falls back to Build info`() {
        val manager = RemoteDeviceManager(
            context = context,
            okHttpClient = OkHttpClient(),
            sessionManager = sessionManager,
            baseUrl = server.url("/").toString(),
            apiKey = "test-api-key",
        )

        val name = manager.resolveAutoDeviceName()
        assertTrue(name.isNotBlank())
    }
}
