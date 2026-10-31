package io.github.lichen.raybirdpush.core

import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.net.InetAddress
import java.net.Socket
import java.net.URI
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class HttpTest {
    private val loop = InetAddress.getByName("127.0.0.1")
    private fun request(url: String, method: String = "GET", path: String? = null, extra: String = ""): ByteArray {
        val uri = URI(url)
        return Socket(uri.host, uri.port).use { socket ->
            socket.soTimeout = 2000
            socket.getOutputStream().write("$method ${path ?: uri.path} HTTP/1.1\r\nHost: test\r\n${extra}\r\n".toByteArray())
            socket.getInputStream().readBytes()
        }
    }
    private fun fixture(block: (File, ApkHttpServer) -> Unit) {
        val file = File.createTempFile("served-apk", ".apk").apply { writeBytes(ByteArray(12345) { (it % 255).toByte() }) }
        try { ApkHttpServer(loop, loop, file).use { block(file, it) } } finally { file.delete() }
    }
    @Test fun getServesExactlyOneFileAndHeadHasNoBody() = fixture { file, server ->
        val head = request(server.url, "HEAD").toString(Charsets.ISO_8859_1)
        assertTrue(head.startsWith("HTTP/1.1 200")); assertTrue(head.contains("Content-Length: ${file.length()}"))
        assertEquals("", head.substringAfter("\r\n\r\n"))
        assertFalse(server.completed)
        val raw = request(server.url)
        val headerEnd = raw.toString(Charsets.ISO_8859_1).indexOf("\r\n\r\n") + 4
        assertArrayEquals(file.readBytes(), raw.copyOfRange(headerEnd, raw.size))
        assertTrue(server.completed)
        assertTrue(URI(server.url).path.split('/')[1].length >= 32)
    }
    @Test fun rejectsWrongTokenTraversalQueryAndMethods() = fixture { _, server ->
        for (path in listOf("/", "/../secret", "/%2e%2e/secret", "/wrong/package.apk", URI(server.url).path + "?x=1")) {
            assertTrue(request(server.url, path = path).toString(Charsets.UTF_8).startsWith("HTTP/1.1 404"))
        }
        assertTrue(request(server.url, "POST").toString(Charsets.UTF_8).startsWith("HTTP/1.1 405"))
        assertFalse(server.completed)
    }
    @Test fun ignoresRangeWithExplicitFull200() = fixture { file, server ->
        val raw = request(server.url, extra = "Range: bytes=999999-\r\n").toString(Charsets.ISO_8859_1)
        assertTrue(raw.startsWith("HTTP/1.1 200"))
        assertTrue(raw.contains("Accept-Ranges: none"))
        assertEquals(file.length().toInt(), raw.substringAfter("\r\n\r\n").length)
    }
    @Test fun rejectsOtherPeer() {
        val file = File.createTempFile("served-apk", ".apk").apply { writeText("private") }
        try {
            ApkHttpServer(loop, InetAddress.getByName("127.0.0.2"), file).use { server ->
                val result = runCatching { request(server.url) }.getOrDefault(byteArrayOf())
                assertFalse(result.toString(Charsets.UTF_8).contains("private"))
                assertEquals(0, server.requests)
            }
        } finally { file.delete() }
    }
    @Test fun cancelClosesPartialRequestAndReleasesPort() {
        val file = File.createTempFile("served-apk", ".apk").apply { writeText("private") }
        val server = ApkHttpServer(loop, loop, file)
        val uri = URI(server.url)
        try {
            Socket(uri.host, uri.port).use { client ->
                client.soTimeout = 2000
                client.getOutputStream().write("GET ".toByteArray())
                server.close()
                assertTrue(runCatching { client.getInputStream().read() == -1 }.getOrDefault(true))
            }
            assertTrue(runCatching { Socket(uri.host, uri.port).close() }.isFailure)
        } finally { server.close(); file.delete() }
    }
    @Test fun independentRequestDeadlineClosesTricklingClient() {
        val file = File.createTempFile("served-apk", ".apk").apply { writeText("private") }
        try { ApkHttpServer(loop, loop, file, requestTimeoutMs = 100).use { server ->
            val uri = URI(server.url)
            Socket(uri.host, uri.port).use { client ->
                client.soTimeout = 2000
                client.getOutputStream().write("GET ".toByteArray())
                val start = System.nanoTime()
                assertEquals(-1, client.getInputStream().read())
                assertTrue((System.nanoTime() - start) / 1_000_000 < 1500)
            }
        } } finally { file.delete() }
    }
    @Test fun secondSessionUsesNewRoute() = fixture { file, first ->
        ApkHttpServer(loop, loop, file).use { second ->
            assertNotEquals(URI(first.url).path, URI(second.url).path)
            assertTrue(request(second.url, path = URI(first.url).path).toString(Charsets.UTF_8).startsWith("HTTP/1.1 404"))
        }
    }
    @Test fun oversizedHeadersRejected() = fixture { _, server ->
        // Closing with unread oversized input can produce TCP RST instead of a readable 400.
        val rejected = runCatching { request(server.url, extra = "X-Padding: ${"a".repeat(8200)}\r\n") }
        if (rejected.isSuccess) assertTrue(rejected.getOrThrow().toString(Charsets.UTF_8).startsWith("HTTP/1.1 400"))
        else assertTrue(rejected.exceptionOrNull() is java.net.SocketException)
        assertEquals(0, server.requests)
        assertFalse(server.completed)
        assertTrue(request(server.url, "HEAD").toString(Charsets.UTF_8).startsWith("HTTP/1.1 200"))
    }
    @Test fun cancelInterruptsActiveFileTransfer() {
        val file = File.createTempFile("large-served-apk", ".apk")
        java.io.RandomAccessFile(file, "rw").use { it.setLength(32L * 1024 * 1024) }
        val began = CountDownLatch(1)
        val server = ApkHttpServer(loop, loop, file, { bytes, _ -> if (bytes >= 65536) began.countDown() })
        val uri = URI(server.url)
        try {
            Socket().apply { receiveBufferSize = 1024; connect(java.net.InetSocketAddress(loop, uri.port)); soTimeout = 2000 }.use { client ->
                client.getOutputStream().write("GET ${uri.path} HTTP/1.1\r\nHost: test\r\n\r\n".toByteArray())
                assertTrue(began.await(2, TimeUnit.SECONDS))
                server.close()
                var received = 0L
                val buffer = ByteArray(8192)
                runCatching {
                    while (true) {
                        val n = client.getInputStream().read(buffer)
                        if (n < 0) break
                        received += n
                    }
                }
                assertTrue(received < file.length())
                assertFalse(server.completed)
            }
        } finally { server.close(); file.delete() }
    }
    @Test fun boundedRequests() = fixture { _, server ->
        repeat(16) { request(server.url, "HEAD") }
        assertTrue(request(server.url).toString(Charsets.UTF_8).startsWith("HTTP/1.1 429"))
    }
}
