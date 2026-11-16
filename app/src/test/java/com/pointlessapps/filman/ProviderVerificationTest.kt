package com.pointlessapps.filman

import com.pointlessapps.filman.config.FilmanConfig
import okhttp3.FormBody
import okhttp3.Request
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ProviderVerificationTest {
    @Test
    fun `only a newly issued nonempty clearance cookie starts a cookie probe`() {
        val tracker = ClearanceCookieTracker("session=x; cf_clearance=old; __cf_bm=y")
        assertFalse(tracker.hasNewClearance(null))
        assertFalse(tracker.hasNewClearance("session=x; __cf_bm=new"))
        assertFalse(tracker.hasNewClearance("cf_clearance="))
        assertFalse(tracker.hasNewClearance("other=1; cf_clearance=old"))
        assertFalse(tracker.hasNewClearance("not_cf_clearance=new"))
        assertTrue(tracker.hasNewClearance("session=x; cf_clearance=new=token"))
    }

    @Test
    fun `a clearance cookie received after an initially empty session is new`() {
        val tracker = ClearanceCookieTracker(null)
        assertFalse(tracker.hasNewClearance("session=x"))
        assertTrue(tracker.hasNewClearance("cf_clearance=issued; session=x"))
    }

    @Test
    fun `new cookie is returned only after the provider accepts it`() = runTest {
        val tracker = ClearanceCookieTracker("cf_clearance=old")
        var cookies: String? = "cf_clearance=new; session=ok"
        var probes = 0
        assertNull(verifyNewClearance(tracker, { "cf_clearance=old" }) { probes++; true })
        assertEquals(0, probes)
        assertNull(verifyNewClearance(tracker, { cookies }) { probes++; false })
        assertEquals(1, probes)
        assertEquals(cookies, verifyNewClearance(tracker, { cookies }) { probes++; true })
        assertEquals(2, probes)
        assertNull(verifyNewClearance(tracker, { cookies }) {
            cookies = "cf_clearance=replaced"
            true
        })
        cookies = "cf_clearance=new"
        assertNull(verifyNewClearance(tracker, { cookies }) {
            cookies = null
            true
        })
    }

    @Test
    fun `verification uses the login page for Filman without changing other providers`() {
        assertEquals(FilmanConfig.LOGIN_URL, verificationPageUrl(FilmanConfig.BASE_URL, null))
        assertEquals("https://zaluknij.cc", verificationPageUrl("https://zaluknij.cc", null))
        assertEquals("https://cda-hd.cc", verificationPageUrl("https://cda-hd.cc", null))
    }

    @Test
    fun `verification preserves challenged GET and never navigates with a POST`() {
        val get = Request.Builder().url("https://filman.cc/filmy/").build()
        val post = Request.Builder().url("https://filman.cc/search")
            .post(FormBody.Builder().add("phrase", "example").build()).build()
        assertEquals("https://filman.cc/filmy/", verificationPageUrl(FilmanConfig.BASE_URL, get))
        assertEquals(FilmanConfig.LOGIN_URL, verificationPageUrl(FilmanConfig.BASE_URL, post))
        assertEquals("https://zaluknij.cc", verificationPageUrl("https://zaluknij.cc", post))
    }

    @Test
    fun `successful native probe completes without creating a webview`() = runTest {
        val events = mutableListOf<String>()
        val result = probeExistingProviderSession(
            verify = { events += "probe"; true },
            onVerified = { events += "verified" },
            onWebViewRequired = { events += "webview" },
        )
        assertTrue(result)
        assertEquals(listOf("probe", "verified"), events)
    }

    @Test
    fun `failed native probe opens a webview without reporting success`() = runTest {
        val events = mutableListOf<String>()
        val result = probeExistingProviderSession(
            verify = { events += "probe"; false },
            onVerified = { events += "verified" },
            onWebViewRequired = { events += "webview" },
        )
        assertFalse(result)
        assertEquals(listOf("probe", "webview"), events)
    }

    @Test
    fun `cancelled probe does not create a webview or report success`() = runTest {
        val events = mutableListOf<String>()
        val job = async {
            probeExistingProviderSession(
                verify = { CompletableDeferred<Boolean>().await() },
                onVerified = { events += "verified" },
                onWebViewRequired = { events += "webview" },
            )
        }
        runCurrent()
        job.cancelAndJoin()
        assertTrue(events.isEmpty())
    }

    @Test
    fun `probe result is ignored when the active request changes`() = runTest {
        var current = "startup"
        val entered = CompletableDeferred<Unit>()
        val finish = CompletableDeferred<Unit>()
        val job = async {
            verifyCurrentProviderSession(
                current = { current },
                verify = {
                    assertEquals("startup", it)
                    entered.complete(Unit)
                    finish.await()
                    true
                },
            )
        }
        entered.await()
        current = "on-demand"
        finish.complete(Unit)
        assertFalse(job.await())
        assertTrue(verifyCurrentProviderSession({ current }) { it == "on-demand" })
    }
}
