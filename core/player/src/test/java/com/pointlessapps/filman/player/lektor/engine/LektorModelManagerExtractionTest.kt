package com.pointlessapps.filman.player.lektor.engine

import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream

class LektorModelManagerExtractionTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun testTarBz2Extraction() {
        val archiveFile = tempFolder.newFile("test_archive.tar.bz2")
        val outputDir = tempFolder.newFolder("output")

        // Create a test tar.bz2 archive
        FileOutputStream(archiveFile).use { fileOut ->
            BufferedOutputStream(fileOut).use { buffOut ->
                BZip2CompressorOutputStream(buffOut).use { bzOut ->
                    TarArchiveOutputStream(bzOut).use { tarOut ->
                        // Entry 1: model file
                        val modelBytes = "fake onnx model content".toByteArray()
                        val entry1 = TarArchiveEntry("vits-piper-test/model.onnx")
                        entry1.size = modelBytes.size.toLong()
                        tarOut.putArchiveEntry(entry1)
                        tarOut.write(modelBytes)
                        tarOut.closeArchiveEntry()

                        // Entry 2: tokens file
                        val tokensBytes = "token1\ntoken2\n".toByteArray()
                        val entry2 = TarArchiveEntry("vits-piper-test/tokens.txt")
                        entry2.size = tokensBytes.size.toLong()
                        tarOut.putArchiveEntry(entry2)
                        tarOut.write(tokensBytes)
                        tarOut.closeArchiveEntry()

                        // Entry 3: espeak data file in subdir
                        val espeakBytes = "espeak data".toByteArray()
                        val entry3 = TarArchiveEntry("vits-piper-test/espeak-ng-data/pl_dict")
                        entry3.size = espeakBytes.size.toLong()
                        tarOut.putArchiveEntry(entry3)
                        tarOut.write(espeakBytes)
                        tarOut.closeArchiveEntry()
                    }
                }
            }
        }

        // Test extraction logic
        val manager = LektorModelManager()
        manager.extractTarBz2(archiveFile, outputDir, "vits-piper-test")

        val extractedModel = File(outputDir, "model.onnx")
        val extractedTokens = File(outputDir, "tokens.txt")
        val extractedEspeak = File(outputDir, "espeak-ng-data/pl_dict")

        assertTrue(extractedModel.exists())
        assertEquals("fake onnx model content", extractedModel.readText())

        assertTrue(extractedTokens.exists())
        assertEquals("token1\ntoken2\n", extractedTokens.readText())

        assertTrue(extractedEspeak.exists())
        assertEquals("espeak data", extractedEspeak.readText())
    }
}
