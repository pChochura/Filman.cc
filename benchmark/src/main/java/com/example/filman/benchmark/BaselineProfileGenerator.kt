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
        // We can wait for any element that indicates data has loaded. 
        // We assume a UI element with a resource id or a focusable item appears.
        device.wait(Until.hasObject(By.focusable(true)), 10_000)

        // TV navigation typically uses D-Pad
        // Scroll down a few times to load rows
        for (i in 1..5) {
            device.pressDPadDown()
            Thread.sleep(500)
            
            // Scroll right within the row (if applicable) to capture horizontal scrolling items
            for (j in 1..3) {
                device.pressDPadRight()
                Thread.sleep(200)
            }
            
            // Return to the left edge before going down again
            for (j in 1..3) {
                device.pressDPadLeft()
                Thread.sleep(200)
            }
        }
        
        // Scroll back up to capture anything else
        for (i in 1..5) {
            device.pressDPadUp()
            Thread.sleep(200)
        }
    }
}
