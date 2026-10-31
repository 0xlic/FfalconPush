package io.github.lichen.raybirdpush.core

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.net.SocketTimeoutException
import java.nio.ByteBuffer

class ProtocolTest {
    @Test fun utf8LengthIsBytes() {
        val text = "223>>安装>>"
        val frame = Protocol.encode(text)
        assertEquals(text.toByteArray().size, ByteBuffer.wrap(frame).int)
        assertEquals(text, Protocol.Decoder().read(ByteArrayInputStream(frame)))
        assertEquals(listOf("223", "安装", ""), text.split(">>"))
    }
    @Test fun fragmentedAndCoalescedFrames() {
        val raw = Protocol.encode("第一帧") + Protocol.encode("second")
        val input = object : ByteArrayInputStream(raw) {
            override fun read(b: ByteArray, off: Int, len: Int) = super.read(b, off, minOf(1, len))
        }
        val decoder = Protocol.Decoder()
        assertEquals("第一帧", decoder.read(input))
        assertEquals("second", decoder.read(input))
    }
    @Test fun partialFrameSurvivesReadTimeout() {
        val raw = Protocol.encode("中文测试")
        val input = object : InputStream() {
            var at = 0
            var pause = true
            override fun read(): Int = error("unused")
            override fun read(b: ByteArray, off: Int, len: Int): Int {
                if ((at == 2 || at == 7) && pause) { pause = false; throw SocketTimeoutException() }
                if (at == raw.size) return -1
                b[off] = raw[at++]; pause = true; return 1
            }
        }
        val decoder = Protocol.Decoder()
        repeat(2) { assertThrows(SocketTimeoutException::class.java) { decoder.read(input) } }
        assertEquals("中文测试", decoder.read(input))
    }
    @Test fun rejectsBadLengths() {
        for (length in listOf(-1, 0, Protocol.MAX_FRAME + 1, Int.MAX_VALUE)) {
            assertThrows(IllegalArgumentException::class.java) {
                Protocol.Decoder().read(ByteArrayInputStream(ByteBuffer.allocate(4).putInt(length).array()))
            }
        }
    }
    @Test fun rejectsInvalidUtf8AndTruncation() {
        assertThrows(java.nio.charset.CharacterCodingException::class.java) {
            Protocol.Decoder().read(ByteArrayInputStream(byteArrayOf(0, 0, 0, 1, 0xFF.toByte())))
        }
        assertThrows(java.io.EOFException::class.java) {
            Protocol.Decoder().read(ByteArrayInputStream(Protocol.encode("hello").dropLast(1).toByteArray()))
        }
    }
    @Test fun preventsFieldInjection() {
        for (field in listOf("hello>>bad", "a\nb", "a\rb", "a\u0000", "", "a".repeat(2049))) {
            assertThrows(IllegalArgumentException::class.java) { Protocol.safeField(field) }
        }
    }
    @Test fun usesActualSizeAndKeepsEmptyFields() {
        val f = Protocol.request("app.test", "中文", "http://10.0.0.1/a", 123, 1048577).split(">>")
        assertEquals(12, f.size)
        assertEquals("2", f[7]); assertEquals("123", f[6])
        assertEquals(listOf("", "", ""), f.subList(8, 11))
    }
    @Test fun matchesPackageAndSessionAndBounds() {
        assertNotNull(receipt("223>>PROGRESS>>app.test>>name>>5>>100>>current", "app.test", "current"))
        for (text in listOf("223>>PROGRESS>>other>>name>>5>>100>>current",
            "223>>PROGRESS>>app.test>>name>>5>>100>>old",
            "223>>PROGRESS>>app.test>>name>>5>>101>>current",
            "223>>PROGRESS>>app.test>>name>>5>>-1>>current",
            "223>>PROGRESS>>app.test>>name>>unknown>>50>>current", "garbage")) {
            assertNull(receipt(text, "app.test", "current"))
        }
    }
    @Test fun restrictsTargetsWithoutDns() {
        listOf("192.168.1.2", "10.0.0.2", "172.16.0.2").forEach { privateIpv4(it) }
        listOf("localhost", "8.8.8.8", "127.0.0.1", "192.168.1.255", "192.168.1.0",
            "172.32.0.1", "10.0.0.256", "010.0.0.1", "10.0.0.1:6553", "::1").forEach {
            assertThrows(IllegalArgumentException::class.java) { privateIpv4(it) }
        }
    }
}
