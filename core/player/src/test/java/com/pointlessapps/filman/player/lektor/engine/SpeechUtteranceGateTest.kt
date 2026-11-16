package com.pointlessapps.filman.player.lektor.engine

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeechUtteranceGateTest {
    @Test
    fun `next sentence waits until active speech completes`() {
        val gate = SpeechUtteranceGate()
        val first = requireNotNull(gate.tryStart())
        assertNull(gate.tryStart())
        assertTrue(gate.isCurrent(first))
        assertTrue(gate.finish(first))
        assertNotNull(gate.tryStart())
    }

    @Test
    fun `cancel permits new speech and stale callbacks cannot finish it`() {
        val gate = SpeechUtteranceGate()
        val first = requireNotNull(gate.tryStart())
        gate.cancel()
        val next = requireNotNull(gate.tryStart())
        assertNotEquals(first, next)
        assertFalse(gate.isCurrent(first))
        assertFalse(gate.finish(first))
        assertTrue(gate.isCurrent(next))
    }
}
