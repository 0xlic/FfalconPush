package io.github.lichen.raybirdpush.core

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ApkFilesTest {
    private fun zip(manifest: Boolean = true): File {
        val file = File.createTempFile("apk-zip", ".apk")
        ZipOutputStream(file.outputStream()).use {
            it.putNextEntry(ZipEntry(if (manifest) "AndroidManifest.xml" else "not-an-apk"))
            it.write(ByteArray(4096)); it.closeEntry()
        }
        return file
    }
    @Test fun copiesAndHashesRealContent() {
        val file = File.createTempFile("apk-copy", ".apk")
        val bytes = "example".toByteArray()
        try { Resources().use { resources ->
            val (size, sha) = ApkFiles.copy(ByteArrayInputStream(bytes), file, resources, {})
            assertEquals(bytes.size.toLong(), size); assertArrayEquals(bytes, file.readBytes())
            assertEquals(MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }, sha)
        } } finally { file.delete() }
    }
    @Test fun rejectsEmptyAndOversizedInput() {
        val file = File.createTempFile("apk-copy", ".apk")
        try {
            for (bytes in listOf(byteArrayOf(), ByteArray(11))) Resources().use { resources ->
                assertThrows(IllegalArgumentException::class.java) { ApkFiles.copy(ByteArrayInputStream(bytes), file, resources, {}, 10) }
            }
        } finally { file.delete() }
    }
    @Test fun cancellationClosesInputAndStopsCopy() {
        val file = File.createTempFile("apk-copy", ".apk")
        val resources = Resources()
        var closed = false
        val input = object : ByteArrayInputStream(ByteArray(100_000)) {
            override fun read(b: ByteArray, off: Int, len: Int): Int {
                val n = super.read(b, off, len); resources.close(); return n
            }
            override fun close() { closed = true; super.close() }
        }
        try {
            assertThrows(Exception::class.java) { ApkFiles.copy(input, file, resources, {}) }
            assertTrue(closed)
        } finally { resources.close(); file.delete() }
    }
    @Test fun verifiesZipAndRejectsMissingManifest() {
        for (hasManifest in listOf(true, false)) {
            val file = zip(hasManifest)
            try { Resources().use { resources ->
                if (hasManifest) ApkFiles.verifyZip(file, resources, {})
                else assertThrows(IllegalArgumentException::class.java) { ApkFiles.verifyZip(file, resources, {}) }
            } } finally { file.delete() }
        }
    }
    @Test fun rejectsTruncatedZipAndExpandedLimit() {
        val file = zip()
        try { Resources().use { resources ->
            assertThrows(IllegalArgumentException::class.java) { ApkFiles.verifyZip(file, resources, {}, 100) }
            file.writeBytes(file.readBytes().dropLast(40).toByteArray())
            assertThrows(Exception::class.java) { ApkFiles.verifyZip(file, resources, {}) }
        } } finally { file.delete() }
    }
    @Test fun rejectsCrcMismatchEvenWhenCentralDirectoryParses() {
        val file = File.createTempFile("corrupt-apk", ".apk")
        val payload = "unique-manifest-content".toByteArray()
        val crc = java.util.zip.CRC32().apply { update(payload) }
        try {
            ZipOutputStream(file.outputStream()).use { zip ->
                zip.putNextEntry(ZipEntry("AndroidManifest.xml").apply {
                    method = ZipEntry.STORED; size = payload.size.toLong(); compressedSize = size; this.crc = crc.value
                })
                zip.write(payload); zip.closeEntry()
            }
            val raw = file.readBytes()
            val offset = raw.toString(Charsets.ISO_8859_1).indexOf(payload.toString(Charsets.ISO_8859_1))
            assertTrue(offset > 0)
            raw[offset] = (raw[offset].toInt() xor 1).toByte(); file.writeBytes(raw)
            Resources().use { resources ->
                assertThrows(IllegalArgumentException::class.java) { ApkFiles.verifyZip(file, resources, {}) }
            }
        } finally { file.delete() }
    }
    @Test fun deadlineInterruptsVerification() {
        val file = zip()
        try { Resources().use { resources ->
            assertThrows(IllegalStateException::class.java) { ApkFiles.verifyZip(file, resources, { error("deadline") }) }
        } } finally { file.delete() }
    }
}
