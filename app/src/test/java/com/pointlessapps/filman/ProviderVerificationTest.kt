package com.pointlessapps.filman

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ProviderVerificationTest {
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
