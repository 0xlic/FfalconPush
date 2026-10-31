package io.github.lichen.raybirdpush.core

import java.io.File
import java.io.InputStream
import java.security.MessageDigest
import java.util.zip.CRC32
import java.util.zip.ZipFile

object ApkFiles {
    const val MAX_BYTES = 512L * 1024 * 1024
    fun copy(input: InputStream, dest: File, resources: Resources, check: () -> Unit,
             maxBytes: Long = MAX_BYTES): Pair<Long, String> {
        val source = resources.add(input)
        val output = resources.add(dest.outputStream())
        val digest = MessageDigest.getInstance("SHA-256")
        var size = 0L
        try {
            source.use { src -> output.use { out ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    resources.check(); check()
                    val n = src.read(buffer)
                    if (n < 0) break
                    size += n
                    require(size <= maxBytes) { "APK 超过大小限制" }
                    digest.update(buffer, 0, n); out.write(buffer, 0, n)
                }
            } }
            require(size > 0) { "文件为空" }
            return size to digest.digest().joinToString("") { "%02x".format(it) }
        } finally { resources.remove(source); resources.remove(output) }
    }
    fun verifyZip(dest: File, resources: Resources, check: () -> Unit,
                  maxExpanded: Long = 2L * 1024 * 1024 * 1024) {
        val zip = resources.add(ZipFile(dest))
        try { zip.use {
            require(it.getEntry("AndroidManifest.xml") != null) { "不是完整单 APK" }
            val names = hashSetOf<String>()
            var expanded = 0L
            val entries = it.entries()
            val buffer = ByteArray(64 * 1024)
            while (entries.hasMoreElements()) {
                resources.check(); check()
                val entry = entries.nextElement()
                require(names.add(entry.name) && names.size <= 100_000) { "APK ZIP 结构异常" }
                val crc = CRC32()
                var count = 0L
                val stream = resources.add(it.getInputStream(entry))
                try { stream.use { input ->
                    while (true) {
                        resources.check(); check()
                        val n = input.read(buffer)
                        if (n < 0) break
                        expanded += n; count += n
                        require(expanded <= maxExpanded) { "APK 解压内容超限" }
                        crc.update(buffer, 0, n)
                    }
                } } finally { resources.remove(stream) }
                require(entry.size == count && entry.crc == crc.value) { "APK 不完整或 CRC 校验失败" }
            }
        } } finally { resources.remove(zip) }
    }
}
