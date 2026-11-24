package com.pointlessapps.filman.data.api

import com.pointlessapps.filman.data.api.extractors.ExtractedVideo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoQualityUtilsTest {
    @Test
    fun `parseQualityResolution parses common formats`() {
        assertEquals(2160, VideoQualityUtils.parseQualityResolution("4K"))
        assertEquals(2160, VideoQualityUtils.parseQualityResolution("2160p"))
        assertEquals(1440, VideoQualityUtils.parseQualityResolution("1440p"))
        assertEquals(1080, VideoQualityUtils.parseQualityResolution("1080p"))
        assertEquals(720, VideoQualityUtils.parseQualityResolution("720p"))
        assertEquals(480, VideoQualityUtils.parseQualityResolution("480p"))
        assertEquals(360, VideoQualityUtils.parseQualityResolution("360p"))
        assertEquals(0, VideoQualityUtils.parseQualityResolution("unknown"))
        assertEquals(0, VideoQualityUtils.parseQualityResolution(""))
    }

    @Test
    fun `selectVideoMatchingQuality chooses exact match when present`() {
        val v1080 = ExtractedVideo(url = "1080", quality = "1080p")
        val v720 = ExtractedVideo(url = "720", quality = "720p")
        val v480 = ExtractedVideo(url = "480", quality = "480p")
        val list = listOf(v1080, v720, v480)

        val selected = VideoQualityUtils.selectVideoMatchingQuality(list, "720p")
        assertEquals(v720, selected)
    }

    @Test
    fun `selectVideoMatchingQuality chooses highest resolution on auto`() {
        val v480 = ExtractedVideo(url = "480", quality = "480p")
        val v1080 = ExtractedVideo(url = "1080", quality = "1080p")
        val v720 = ExtractedVideo(url = "720", quality = "720p")
        val list = listOf(v480, v1080, v720)

        val selected = VideoQualityUtils.selectVideoMatchingQuality(list, "auto")
        assertEquals(v1080, selected)
    }

    @Test
    fun `selectVideoMatchingQuality falls back to closest resolution when preferred is unavailable`() {
        val v720 = ExtractedVideo(url = "720", quality = "720p")
        val v480 = ExtractedVideo(url = "480", quality = "480p")
        val list = listOf(v720, v480)

        // Preferred 1080p, should pick 720p (closest available <= 1080p)
        val selected = VideoQualityUtils.selectVideoMatchingQuality(list, "1080p")
        assertEquals(v720, selected)
    }

    @Test
    fun `qualityRank prioritizes higher resolutions on auto`() {
        val rank1080 = VideoQualityUtils.qualityRank("1080p", "", "auto")
        val rank720 = VideoQualityUtils.qualityRank("720p", "", "auto")
        val rank480 = VideoQualityUtils.qualityRank("480p", "", "auto")

        assertTrue(rank1080 < rank720)
        assertTrue(rank720 < rank480)
    }

    @Test
    fun `qualityRank prioritizes exact match over other resolutions`() {
        val rankExact = VideoQualityUtils.qualityRank("720p", "", "720p")
        val rankOther = VideoQualityUtils.qualityRank("1080p", "", "720p")

        assertTrue(rankExact < rankOther)
    }
}
