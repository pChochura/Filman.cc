package com.pointlessapps.filman.player.lektor.controller

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class InterruptedSystemSpeechTest {
    @Test
    fun `pause preserves the active sentence for replay until successfully restarted`() {
        val state = InterruptedSystemSpeech()
        assertTrue(state.start(4) { true })
        assertFalse(state.start(5) { true })
        var stops = 0
        state.pause { stops++ }
        state.pause { stops++ }
        assertEquals(2, stops)
        assertEquals(4, state.interruptedIndex())
        assertFalse(state.start(4) { false })
        assertEquals(4, state.interruptedIndex())
        assertTrue(state.start(4) { true })
        assertNull(state.interruptedIndex())
        state.finish()
        state.pause {}
        assertNull(state.interruptedIndex())
    }

    @Test
    fun `clear discards interrupted speech on seek or voice change`() {
        val state = InterruptedSystemSpeech()
        state.start(4) { true }
        state.pause {}
        state.clear()
        assertNull(state.interruptedIndex())
        assertTrue(state.start(8) { true })
    }
}
