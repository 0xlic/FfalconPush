package io.github.lichen.raybirdpush.core

import java.io.EOFException
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.security.MessageDigest

object Protocol {
    const val MAX_FRAME = 64 * 1024
    fun encode(text: String): ByteArray {
        val bytes = text.toByteArray(Charsets.UTF_8)
        require(bytes.size in 1..MAX_FRAME) { "协议帧长度无效" }
        return ByteBuffer.allocate(4 + bytes.size).putInt(bytes.size).put(bytes).array()
    }
    fun write(out: OutputStream, text: String) { out.write(encode(text)); out.flush() }
    // Stateful decoder: socket timeouts do not discard partially received frames.
    class Decoder {
        private val header = ByteArray(4)
        private var headerUsed = 0
        private var payload: ByteArray? = null
        private var used = 0
        fun read(input: InputStream): String {
            while (headerUsed < 4) {
                val n = input.read(header, headerUsed, 4 - headerUsed)
                if (n < 0) throw EOFException()
                headerUsed += n
            }
            if (payload == null) {
                val length = ByteBuffer.wrap(header).int
                require(length in 1..MAX_FRAME) { "电视返回异常帧长度" }
                payload = ByteArray(length)
            }
            val bytes = payload!!
            while (used < bytes.size) {
                val n = input.read(bytes, used, bytes.size - used)
                if (n < 0) throw EOFException()
                used += n
            }
            val text = Charsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString()
            headerUsed = 0; payload = null; used = 0
            return text
        }
    }
    fun safeField(value: String): String {
        require(value.isNotBlank() && value.toByteArray().size <= 2048 &&
            !value.contains(">>") && value.none { it.isISOControl() }) { "APK 协议字段含不支持的字符" }
        return value
    }
    fun pathId(url: String): String = MessageDigest.getInstance("MD5")
        .digest(url.toByteArray()).joinToString("") { "%02x".format(it) }
    fun request(pkg: String, label: String, url: String, version: Long, bytes: Long): String {
        require(version >= 0 && bytes > 0)
        // Experimental assumption: binary MiB, rounded up. Not the upstream fixed 20.
        val sizeMiB = (bytes + 1048575) / 1048576
        return listOf("223", "INSTALL", safeField(pkg), safeField(label), safeField(url),
            "2", version.toString(), sizeMiB.toString(), "", "", "", pathId(url)).joinToString(">>")
    }
}

data class Receipt(val kind: String, val stage: String, val percent: Int?)
fun receipt(text: String, pkg: String, id: String): Receipt? {
    val f = text.split(">>")
    if (f.size < 6 || f[0] != "223" || f[2] != pkg) return null
    return when (f[1]) {
        "INSTALL" -> if (f.size == 6 && f[5] == id) Receipt("INSTALL", f[4], null) else null
        "PROGRESS" -> if (f.size == 7 && f[6] == id) {
            val p = f[5].toIntOrNull() ?: return null
            if (p !in 0..100 || f[4] !in listOf("1", "2", "3", "5")) null
            else Receipt("PROGRESS", f[4], p)
        } else null
        else -> null
    }
}

fun privateIpv4(value: String): java.net.InetAddress {
    val parts = value.split('.')
    require(parts.size == 4 && parts.all { it.matches(Regex("0|[1-9][0-9]{0,2}")) && it.toInt() <= 255 }) {
        "请输入有效 IPv4 地址"
    }
    val n = parts.map { it.toInt() }
    require((n[0] == 10 || n[0] == 172 && n[1] in 16..31 || n[0] == 192 && n[1] == 168) &&
        n[3] != 0 && n[3] != 255) { "仅支持私有局域网电视地址（不支持网络/广播地址）" }
    return java.net.InetAddress.getByAddress(n.map { it.toByte() }.toByteArray())
}
