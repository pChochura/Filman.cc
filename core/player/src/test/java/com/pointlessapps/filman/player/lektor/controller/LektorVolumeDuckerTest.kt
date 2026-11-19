package com.pointlessapps.filman.player.lektor.controller

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LektorVolumeDuckerTest {

    @Test
    fun `duck and restore ramp smoothly across multiple steps`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val recorded = mutableListOf<Float>()
            val ducker = LektorVolumeDucker(CoroutineScope(SupervisorJob() + dispatcher)) {
                recorded.add(it)
            }
            ducker.setDuckingVolume(0.6f)

            ducker.duck()
            runCurrent()
            advanceTimeBy(200)
            runCurrent()

            assertTrue("Expected multi-step ducking ramp, got $recorded", recorded.size >= 4)
            assertEquals(0.6f, recorded.last(), 0.001f)

            recorded.clear()
            ducker.restore()
            runCurrent()
            advanceTimeBy(350)
            runCurrent()

            assertTrue("Expected multi-step restore ramp, got $recorded", recorded.size >= 5)
            assertEquals(1.0f, recorded.last(), 0.001f)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `releaseImmediate snaps volume back to full immediately`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val recorded = mutableListOf<Float>()
            val ducker = LektorVolumeDucker(CoroutineScope(SupervisorJob() + dispatcher)) {
                recorded.add(it)
            }
            ducker.setDuckingVolume(0.6f)
            ducker.duck()
            runCurrent()
            ducker.releaseImmediate()

            assertEquals(1.0f, recorded.last(), 0.001f)
        } finally {
            Dispatchers.resetMain()
        }
    }
}
