package com.pointlessapps.filman.data.api

import org.junit.Assert.assertEquals
import org.junit.Test

class SourceWebsiteConfigNameTest {
    @Test
    fun `provider websites map to their priority configuration`() {
        assertEquals("cda_hd", sourceWebsiteConfigName("CDA-HD.cc"))
        assertEquals("zaluknij", sourceWebsiteConfigName("zaluknij.cc"))
        assertEquals("ekino", sourceWebsiteConfigName("ekino.ws"))
        assertEquals("filman", sourceWebsiteConfigName("filman.cc"))
        assertEquals("tmdb", sourceWebsiteConfigName("tmdb"))
        assertEquals("other.example", sourceWebsiteConfigName("Other.Example"))
    }
}
