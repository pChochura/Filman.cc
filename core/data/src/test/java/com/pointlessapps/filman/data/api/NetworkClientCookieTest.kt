package com.pointlessapps.filman.data.api

import android.webkit.CookieManager
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class NetworkClientCookieTest {
    private val manager = mockk<CookieManager>(relaxed = true)

    @Before
    fun setUp() {
        mockkStatic(CookieManager::class)
        every { CookieManager.getInstance() } returns manager
        every { manager.getCookie(any<String>()) } returns null
    }

    @After
    fun tearDown() {
        NetworkClient.setCloudflareCookie("provider.example", "")
        unmockkStatic(CookieManager::class)
    }

    @Test
    fun `request cookies come from the scoped cookie manager rather than legacy strings`() {
        NetworkClient.setCloudflareCookie("provider.example", "cf_clearance=secret")
        val jar = NetworkClient.okHttpClient.cookieJar
        assertTrue(jar.loadForRequest("https://provider.example.attacker.test/".toHttpUrl()).isEmpty())
        assertTrue(jar.loadForRequest("https://provider.example/".toHttpUrl()).isEmpty())

        every { manager.getCookie("https://provider.example/") } returns "cf_clearance=fresh"
        assertEquals("fresh", jar.loadForRequest("https://provider.example/".toHttpUrl()).single().value)
    }

    @Test
    fun `restoring a legacy cookie does not overwrite a newer browser cookie`() {
        every { manager.getCookie("https://provider.example") } returns "cf_clearance=fresh"
        NetworkClient.setCloudflareCookie("provider.example", "cf_clearance=stale")

        verify(exactly = 0) { manager.setCookie(any<String>(), any<String>()) }
    }
}
