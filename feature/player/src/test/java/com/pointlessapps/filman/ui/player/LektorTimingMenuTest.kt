package com.pointlessapps.filman.ui.player

import com.pointlessapps.filman.data.model.LektorTiming
import com.pointlessapps.filman.ui.components.FilmanOverlayMenuItem
import com.pointlessapps.filman.ui.base.FilmanEvent
import com.pointlessapps.filman.data.api.VideoUrlResolver
import com.pointlessapps.filman.data.local.SettingsManager
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LektorTimingMenuTest {
    @Test
    fun `timing options display persisted value and emit selected offset`() {
        val selected = mutableListOf<Int>()
        val items = lektorTimingMenuItems(LektorTiming.DEFAULT_OFFSET_MS, selected::add)
        val options = items.filterIsInstance<FilmanOverlayMenuItem.Option>()
        assertEquals(5, options.size)
        assertEquals("lektor_timing_-500", options.single { it.isSelected }.id)
        options.single { it.id == "lektor_timing_500" }.onClick()
        assertEquals(listOf(500), selected)
        assertTrue(items.any { it.id == "header_lektor_timing" })
    }

    @Test
    fun `player menu sends timing and ducking events`() {
        val settings = mockk<SettingsManager> {
            every { sourcesPriorityFlow } returns MutableStateFlow(emptyList())
        }
        val resolver = mockk<VideoUrlResolver> {
            every { settingsManager } returns settings
        }
        val events = mutableListOf<FilmanEvent>()
        val menu = PlayerMenuBuilder.buildOverlayMenuData(PlayerState(), resolver, null) { events.add(it) }
        val lektor = menu.items.filterIsInstance<FilmanOverlayMenuItem.NestedMenu>()
            .single { it.id == "menu_lektor" }
        val timingMenu = lektor.items.filterIsInstance<FilmanOverlayMenuItem.NestedMenu>()
            .single { it.id == "menu_lektor_timing" }
        val duckingMenu = lektor.items.filterIsInstance<FilmanOverlayMenuItem.NestedMenu>()
            .single { it.id == "menu_lektor_ducking" }
        timingMenu.items.filterIsInstance<FilmanOverlayMenuItem.Option>()
            .single { it.id == "lektor_timing_1000" }.onClick()
        duckingMenu.items.filterIsInstance<FilmanOverlayMenuItem.Option>()
            .single { it.id == "lektor_ducking_0.7" }.onClick()
        assertEquals(1_000, (events[0] as PlayerEvent.ChangeLektorTimingOffset).offsetMs)
        assertEquals(0.7f, (events[1] as PlayerEvent.ChangeLektorDuckingVolume).volume)
    }
}
