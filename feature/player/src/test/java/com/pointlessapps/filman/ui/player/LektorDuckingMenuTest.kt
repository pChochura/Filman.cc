package com.pointlessapps.filman.ui.player

import com.pointlessapps.filman.ui.components.FilmanOverlayMenuItem
import org.junit.Assert.assertEquals
import org.junit.Test

class LektorDuckingMenuTest {
    @Test
    fun `no ducking is selected by default and optional reductions are selectable`() {
        val selected = mutableListOf<Float>()
        val options = lektorDuckingMenuItems(1.0f, selected::add)
            .filterIsInstance<FilmanOverlayMenuItem.Option>()
        assertEquals(4, options.size)
        assertEquals("lektor_ducking_1.0", options.single { it.isSelected }.id)
        options.single { it.id == "lektor_ducking_0.8" }.onClick()
        assertEquals(listOf(0.8f), selected)
    }
}
