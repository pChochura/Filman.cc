package com.pointlessapps.filman.ui.player

import android.text.TextUtils
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.TrackGroup
import androidx.media3.common.Tracks
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class SubtitleTrackSelectionTest {
    @After
    fun tearDown() = unmockkStatic(TextUtils::class)

    @Test
    fun `exact subtitle id wins over an earlier track in the same language`() {
        mockkStatic(TextUtils::class)
        every { TextUtils.isEmpty(any()) } answers { firstArg<CharSequence?>().isNullOrEmpty() }
        fun group(id: String): Tracks.Group {
            val format = Format.Builder().setId(id).setLanguage("pl").setSampleMimeType(MimeTypes.APPLICATION_SUBRIP).build()
            return Tracks.Group(TrackGroup(id, format), false, intArrayOf(C.FORMAT_HANDLED), booleanArrayOf(false))
        }
        val first = group("first")
        val second = group("second")
        val tracks = Tracks(listOf(first, second))
        val selected = subtitleTrackOverride(tracks, "second", "pl")!!
        assertSame(second.mediaTrackGroup, selected.mediaTrackGroup)
        assertEquals(listOf(0), selected.trackIndices)
        assertSame(first.mediaTrackGroup, subtitleTrackOverride(tracks, "missing", "pl")!!.mediaTrackGroup)
        assertNull(subtitleTrackOverride(tracks, "missing", null))
    }
}
