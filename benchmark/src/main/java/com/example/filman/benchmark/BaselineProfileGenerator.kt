package com.example.filman.benchmark

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {

    @get:Rule
    val baselineProfileRule = BaselineProfileRule()

    @Test
    fun generate() = baselineProfileRule.collect(
        packageName = "com.example.filman",
        maxIterations = 1,
        includeInStartupProfile = true,
    ) {
        // Press home to start from a clean state
        pressHome()
        startActivityAndWait()

        // Wait until the main screen's lazy grid is loaded.
        device.wait(Until.hasObject(By.focusable(true)), 10_000)

        // Iterate through 3 tabs to ensure we capture layout passes for different screens
        for (tab in 0..2) {
            // Wait a moment for the current tab's data to load
            device.wait(Until.hasObject(By.focusable(true)), 5_000)
            Thread.sleep(1000)

            // Fast scroll down
            for (i in 1..15) {
                device.pressDPadDown()
                Thread.sleep(100) // Fast interval
            }

            // Small pause at the bottom
            Thread.sleep(500)

            // Press the Back button, which automatically scrolls to the top
            device.pressBack()
            
            // Wait for the smooth scroll-to-top animation to finish
            Thread.sleep(2000)

            if (tab < 2) {
                // Press Up a few times to ensure the TabRow regains focus
                for (i in 1..3) {
                    device.pressDPadUp()
                    Thread.sleep(150)
                }

                // Switch to the next tab
                device.pressDPadRight()
                Thread.sleep(500)
            }
        }
    }
}
