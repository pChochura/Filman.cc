package com.pointlessapps.filman.player.lektor.controller

import com.pointlessapps.filman.player.lektor.model.SubtitleCue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LektorSpeedCalculatorTest {

    private fun cue(index: Int, startMs: Long, endMs: Long, text: String = "Test dialogue") =
        SubtitleCue(index, startMs, endMs, text)

    @Test
    fun `audio longer than available window before next cue speeds up to prevent cut-off`() {
        val current = cue(1, 10_000, 12_000)
        val next = cue(2, 12_500, 14_000)
        // Window = 2500 - 180 = 2320ms. Audio is 3000ms.
        val speed = LektorSpeedCalculator.calculateSpeed(
            audioDurationMs = 3_000,
            cue = current,
            nextCue = next,
            currentPositionMs = 10_000,
            timingOffsetMs = 0,
        )
        // 3000 / 2320 ~= 1.29f
        assertTrue(speed > 1.25f)
        assertTrue(speed <= 1.45f)
    }

    @Test
    fun `audio comfortably fitting in available window plays at normal speed`() {
        val current = cue(1, 10_000, 13_000)
        val next = cue(2, 13_500, 15_000)
        // Window = 3500 - 180 = 3320ms. Audio is 2800ms.
        val speed = LektorSpeedCalculator.calculateSpeed(
            audioDurationMs = 2_800,
            cue = current,
            nextCue = next,
            currentPositionMs = 10_000,
            timingOffsetMs = 0,
        )
        assertEquals(1.0f, speed)
    }

    @Test
    fun `very short audio during long speech eases speed to avoid awkward silence waiting for next cue`() {
        val current = cue(1, 10_000, 14_500) // 4.5s on screen
        val next = cue(2, 15_000, 18_000)
        // Audio is only 1500ms
        val speed = LektorSpeedCalculator.calculateSpeed(
            audioDurationMs = 1_500,
            cue = current,
            nextCue = next,
            currentPositionMs = 10_000,
            timingOffsetMs = 0,
        )
        assertTrue(speed < 1.0f)
        assertTrue(speed >= LektorSpeedCalculator.MIN_SPEED)
    }

    @Test
    fun `extreme long audio is clamped to MAX_SPEED to preserve clarity`() {
        val current = cue(1, 10_000, 11_000)
        val next = cue(2, 11_500, 13_000)
        // Window is ~1320ms. Audio is 6000ms.
        val speed = LektorSpeedCalculator.calculateSpeed(
            audioDurationMs = 6_000,
            cue = current,
            nextCue = next,
            currentPositionMs = 10_000,
            timingOffsetMs = 0,
        )
        assertEquals(LektorSpeedCalculator.MAX_SPEED, speed)
    }

    @Test
    fun `playback latency past start time compensates by speeding up to stay on schedule`() {
        val current = cue(1, 10_000, 12_500)
        val next = cue(2, 13_000, 15_000)
        // Position is 10_400 (400ms late).
        val normalSpeed = LektorSpeedCalculator.calculateSpeed(
            audioDurationMs = 2_800,
            cue = current,
            nextCue = next,
            currentPositionMs = 10_000,
            timingOffsetMs = 0,
        )
        val lateSpeed = LektorSpeedCalculator.calculateSpeed(
            audioDurationMs = 2_800,
            cue = current,
            nextCue = next,
            currentPositionMs = 10_400,
            timingOffsetMs = 0,
        )
        assertTrue(lateSpeed > normalSpeed)
    }

    @Test
    fun `last cue with no next cue allows display duration plus grace buffer`() {
        val current = cue(1, 10_000, 12_000) // duration 2000ms. grace buffer +500ms = 2500ms
        val fittingSpeed = LektorSpeedCalculator.calculateSpeed(
            audioDurationMs = 2_200,
            cue = current,
            nextCue = null,
            currentPositionMs = 10_000,
            timingOffsetMs = 0,
        )
        assertEquals(1.0f, fittingSpeed)

        val longSpeed = LektorSpeedCalculator.calculateSpeed(
            audioDurationMs = 3_500,
            cue = current,
            nextCue = null,
            currentPositionMs = 10_000,
            timingOffsetMs = 0,
        )
        assertTrue(longSpeed > 1.0f)
    }

    @Test
    fun `invalid or zero duration returns NORMAL_SPEED safely`() {
        val current = cue(1, 10_000, 12_000)
        assertEquals(1.0f, LektorSpeedCalculator.calculateSpeed(0, current, null, 10_000, 0))
        assertEquals(1.0f, LektorSpeedCalculator.calculateSpeed(-500, current, null, 10_000, 0))
    }
}
