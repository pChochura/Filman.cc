package com.pointlessapps.filman.data.local

import android.content.Context
import io.mockk.every
import io.mockk.mockk
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.util.Locale

class SettingsManagerTest {

    private lateinit var tempDir: File
    private lateinit var context: Context

    @Before
    fun setUp() {
        tempDir = Files.createTempDirectory("settings_test").toFile()
        context = mockk(relaxed = true)
        every { context.applicationContext } returns context
        every { context.filesDir } returns tempDir
    }

    @After
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    @Test
    fun `getEffectiveLanguage resolves explicit language when valid`() {
        val settingsManager = SettingsManager(context)

        assertEquals("pl", settingsManager.getEffectiveLanguage("pl"))
        assertEquals("en", settingsManager.getEffectiveLanguage("en"))
    }

    @Test
    fun `getEffectiveLanguage defaults to Polish if device language is Polish`() {
        val previous = Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag("pl-PL"))
            val settingsManager = SettingsManager(context)

            assertEquals("pl", settingsManager.getEffectiveLanguage(null))
        } finally {
            Locale.setDefault(previous)
        }
    }

    @Test
    fun `getEffectiveLanguage defaults to English if device language is not Polish`() {
        val previous = Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag("en-US"))
            val settingsManager = SettingsManager(context)

            assertEquals("en", settingsManager.getEffectiveLanguage(null))

            Locale.setDefault(Locale.forLanguageTag("de-DE"))
            assertEquals("en", settingsManager.getEffectiveLanguage(null))
        } finally {
            Locale.setDefault(previous)
        }
    }

    @Test
    fun `getEffectiveLocale returns correct Locale object`() {
        val settingsManager = SettingsManager(context)

        val plLocale = settingsManager.getEffectiveLocale("pl")
        assertEquals("pl", plLocale.language)

        val enLocale = settingsManager.getEffectiveLocale("en")
        assertEquals("en", enLocale.language)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `setAppLanguage throws on unsupported language`() {
        val settingsManager = SettingsManager(context)
        settingsManager.setAppLanguage("es")
    }
}
