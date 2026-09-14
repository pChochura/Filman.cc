package com.pointlessapps.filman

import com.pointlessapps.filman.data.model.TvShowSourceSettings
import com.pointlessapps.filman.data.scraper.extractors.ExtractedVideo
import org.junit.Assert.assertEquals
import org.junit.Test
import java.net.URL

class VideoUrlResolverMatchingTest {
    private fun scoreVideo(
        video: ExtractedVideo,
        target: TvShowSourceSettings,
    ): Int {
        var score = 0
        val targetServer = target.serverName?.lowercase()?.trim()
        val targetVersion = target.version?.lowercase()?.trim()
        val targetQuality = target.quality?.lowercase()?.trim()
        val targetWebsite = target.sourceWebsite?.lowercase()?.trim()

        val videoServer =
            video.serverName
                .ifEmpty {
                    runCatching { URL(video.url).host }.getOrNull().orEmpty()
                }.lowercase()
                .trim()
        val videoVersion = video.version.lowercase().trim()
        val videoQuality = video.quality.lowercase().trim()
        val videoWebsite = video.sourceWebsite.lowercase().trim()

        val serverMatches =
            !targetServer.isNullOrEmpty() && (
                videoServer == targetServer ||
                    videoServer.contains(targetServer) ||
                    targetServer.contains(videoServer)
            )

        val versionMatches =
            !targetVersion.isNullOrEmpty() && (
                videoVersion == targetVersion ||
                    videoVersion.contains(targetVersion) ||
                    targetVersion.contains(videoVersion)
            )

        if (serverMatches && versionMatches) {
            score += 1000
        } else if (versionMatches) {
            score += 500
        } else if (serverMatches) {
            score += 300
        }

        if (!targetQuality.isNullOrEmpty() && (videoQuality.contains(targetQuality) || targetQuality.contains(videoQuality))) {
            score += 50
        }

        if (!targetWebsite.isNullOrEmpty() && videoWebsite == targetWebsite) {
            score += 30
        } else if (videoWebsite == "filman.cc") {
            score += 10
        }

        return score
    }

    private fun selectBestMatch(
        videos: List<ExtractedVideo>,
        target: TvShowSourceSettings,
    ): ExtractedVideo? {
        val scoredVideos = videos.map { it to scoreVideo(it, target) }
        return scoredVideos
            .filter { it.second > 0 }
            .maxWithOrNull(
                compareBy<Pair<ExtractedVideo, Int>> { it.second }
                    .thenBy { -it.first.latency },
            )?.first
    }

    @Test
    fun testExactMatchScoresHighest() {
        val target =
            TvShowSourceSettings(
                serverName = "vidoza",
                version = "Dubbing",
                quality = "1080p",
                sourceWebsite = "filman.cc",
            )

        val candidate1 =
            ExtractedVideo(
                url = "https://vidoza.net/v1",
                serverName = "vidoza",
                version = "Dubbing",
                quality = "1080p",
                sourceWebsite = "filman.cc",
                latency = 100,
            )

        val candidate2 =
            ExtractedVideo(
                url = "https://upstream.to/v2",
                serverName = "upstream",
                version = "Dubbing",
                quality = "1080p",
                sourceWebsite = "filman.cc",
                latency = 50,
            )

        val candidate3 =
            ExtractedVideo(
                url = "https://vidoza.net/v3",
                serverName = "vidoza",
                version = "Lektor",
                quality = "1080p",
                sourceWebsite = "filman.cc",
                latency = 80,
            )

        val selected = selectBestMatch(listOf(candidate3, candidate2, candidate1), target)
        assertEquals("https://vidoza.net/v1", selected?.url)
    }

    @Test
    fun testDubbingPreferredOverServerMismatch() {
        // When user wanted Dubbing on vidoza, but vidoza only has Lektor,
        // another server with Dubbing should be preferred over Lektor on vidoza.
        val target =
            TvShowSourceSettings(
                serverName = "vidoza",
                version = "Dubbing",
                quality = "1080p",
                sourceWebsite = "filman.cc",
            )

        val candidateUpstreamDubbing =
            ExtractedVideo(
                url = "https://upstream.to/v1",
                serverName = "upstream",
                version = "Dubbing",
                quality = "1080p",
                sourceWebsite = "filman.cc",
                latency = 150,
            )

        val candidateVidozaLektor =
            ExtractedVideo(
                url = "https://vidoza.net/v2",
                serverName = "vidoza",
                version = "Lektor",
                quality = "1080p",
                sourceWebsite = "filman.cc",
                latency = 100,
            )

        val selected = selectBestMatch(listOf(candidateVidozaLektor, candidateUpstreamDubbing), target)
        assertEquals("https://upstream.to/v1", selected?.url)
    }

    @Test
    fun testLatencyBreaksTies() {
        val target =
            TvShowSourceSettings(
                serverName = "vidoza",
                version = "Dubbing",
                quality = "1080p",
                sourceWebsite = "filman.cc",
            )

        val candidateFast =
            ExtractedVideo(
                url = "https://vidoza.net/fast",
                serverName = "vidoza",
                version = "Dubbing",
                quality = "1080p",
                sourceWebsite = "filman.cc",
                latency = 50,
            )

        val candidateSlow =
            ExtractedVideo(
                url = "https://vidoza.net/slow",
                serverName = "vidoza",
                version = "Dubbing",
                quality = "1080p",
                sourceWebsite = "filman.cc",
                latency = 300,
            )

        val selected = selectBestMatch(listOf(candidateSlow, candidateFast), target)
        assertEquals("https://vidoza.net/fast", selected?.url)
    }
}
