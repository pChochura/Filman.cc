package com.pointlessapps.filman

import com.pointlessapps.filman.ui.core.WebViewPointerState
import org.junit.Assert.assertEquals
import org.junit.Test

class WebViewPointerStateTest {
    @Test
    fun `pointer centers once the view has dimensions and stays within bounds`() {
        val state = WebViewPointerState()
        state.ensureInitialized(0, 0)
        state.ensureInitialized(800, 400)
        assertEquals(400f, state.x, 0f)
        assertEquals(200f, state.y, 0f)

        state.move(-500f, 500f, 800, 400)
        assertEquals(0f, state.x, 0f)
        assertEquals(400f, state.y, 0f)

        state.move(0f, 0f, 100, 100)
        assertEquals(100f, state.y, 0f)
        state.reset()
        state.ensureInitialized(100, 100)
        assertEquals(50f, state.x, 0f)
        assertEquals(50f, state.y, 0f)
    }
}
