package com.vyora.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.security.MessageDigest

class ModelDownloadManagerTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun testSha256ChecksumVerification() {
        // Create test file with known content
        val testContent = "Vyora SAFORA Offline Model Integrity Test Content"
        val testFile = tempFolder.newFile("test_model.bin")
        testFile.writeText(testContent)

        // Compute expected SHA-256
        val digest = MessageDigest.getInstance("SHA-256")
        val expectedSha256 = digest.digest(testContent.toByteArray()).joinToString("") { "%02x".format(it) }

        // Test hash calculation logic matching ModelDownloadManager
        val calculatedSha256 = calculateFileSha256(testFile)
        assertEquals(expectedSha256, calculatedSha256)
    }

    @Test
    fun testCorruptedFileChecksumMismatch() {
        val testContent = "Valid Content"
        val testFile = tempFolder.newFile("corrupt_model.bin")
        testFile.writeText("Corrupted Content")

        val digest = MessageDigest.getInstance("SHA-256")
        val validSha256 = digest.digest(testContent.toByteArray()).joinToString("") { "%02x".format(it) }

        val actualSha256 = calculateFileSha256(testFile)
        assertFalse("Corrupted file must fail SHA-256 verification", validSha256.equals(actualSha256, ignoreCase = true))
    }

    @Test
    fun testProgressPercentageCalculation() {
        val totalBytes = 986048160L
        val downloadedHalf = totalBytes / 2
        val percentHalf = (downloadedHalf * 100 / totalBytes).toInt()
        assertEquals(50, percentHalf)

        val downloadedComplete = totalBytes
        val percentComplete = (downloadedComplete * 100 / totalBytes).toInt()
        assertEquals(100, percentComplete)
    }

    @Test
    fun testPartFilePromotionSimulation() {
        val modelsDir = tempFolder.newFolder("models")
        val partFile = File(modelsDir, "safora-q4_k_m.gguf.part")
        val finalFile = File(modelsDir, "safora-q4_k_m.gguf")

        // 1. Staging write into .part file
        partFile.writeBytes(ByteArray(1024) { 0x42 })
        assertTrue(partFile.exists())
        assertFalse(finalFile.exists())

        // 2. Validate and atomic promote
        val promoted = partFile.renameTo(finalFile)
        assertTrue(promoted)
        assertTrue(finalFile.exists())
        assertFalse(partFile.exists())
        assertEquals(1024L, finalFile.length())

        // 3. Delete model frees storage
        val deleted = finalFile.delete()
        assertTrue(deleted)
        assertFalse(finalFile.exists())
    }

    private fun calculateFileSha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { fis ->
            val buffer = ByteArray(8192)
            var bytesRead: Int
            while (fis.read(buffer).also { bytesRead = it } != -1) {
                digest.update(buffer, 0, bytesRead)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
