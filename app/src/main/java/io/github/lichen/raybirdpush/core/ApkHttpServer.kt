package io.github.lichen.raybirdpush.core

import java.io.Closeable
import java.io.File
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.nio.charset.StandardCharsets
import java.security.SecureRandom
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/** One file, one route, selected interface, selected peer. No third-party server. */
class ApkHttpServer(
    bind: InetAddress, private val peer: InetAddress, private val file: File,
    private val onBytes: (Long, Boolean) -> Unit = { _, _ -> },
    private val requestTimeoutMs: Long = 120_000
) : Closeable {
    private val resources = Resources()
    private val timer = Executors.newSingleThreadScheduledExecutor()
    private val server = resources.add(ServerSocket().apply { reuseAddress = false })
    private val token = ByteArray(24).also { SecureRandom().nextBytes(it) }.joinToString("") { "%02x".format(it) }
    private val route = "/$token/package.apk"
    val url: String
    @Volatile var requests = 0
        private set
    @Volatile var completed = false
        private set
    init {
        try {
            server.bind(java.net.InetSocketAddress(bind, 0), 2)
            url = "http://${bind.hostAddress}:${server.localPort}$route"
            thread(name = "single-apk-http", isDaemon = true) {
                try {
                    var attempts = 0
                    while (!resources.isClosed && attempts++ < 64) {
                        val client = resources.add(server.accept())
                        try { serve(client) } catch (_: Exception) { /* request aborted; no sensitive logging */ }
                        finally { client.close(); resources.remove(client) }
                    }
                } catch (_: Exception) { /* close interrupts accept */ }
            }
        } catch (e: Exception) { close(); throw e }
    }
    private fun serve(client: Socket) {
        client.soTimeout = 3000
        if (client.inetAddress != peer) return
        val deadline = timer.schedule({ runCatching { client.close() } }, requestTimeoutMs, TimeUnit.MILLISECONDS)
        try {
            val input = client.getInputStream()
            val header = java.io.ByteArrayOutputStream()
            var tail = ""
            while (!tail.endsWith("\r\n\r\n")) {
                val b = input.read()
                if (b < 0) return
                if ((b !in 32..126 && b !in listOf(9, 10, 13)) || header.size() >= 8192) { respond(client, 400); return }
                header.write(b)
                tail = (tail + b.toChar()).takeLast(4)
            }
            val lines = header.toString(StandardCharsets.US_ASCII.name()).split("\r\n")
            val request = lines[0].split(' ')
            if (request.size != 3 || request[2] !in listOf("HTTP/1.0", "HTTP/1.1")) { respond(client, 400); return }
            if (request[0] !in listOf("GET", "HEAD")) { respond(client, 405); return }
            if (request[1] != route) { respond(client, 404); return }
            // Range is intentionally ignored: valid HTTP behavior, always full 200 representation.
            if (lines.drop(1).any { it.isNotEmpty() && !it.contains(':') }) { respond(client, 400); return }
            if (++requests > 16) { respond(client, 429); return }
            val out = client.getOutputStream()
            val size = file.length()
            out.write(("HTTP/1.1 200 OK\r\nContent-Length: $size\r\n" +
                "Content-Type: application/vnd.android.package-archive\r\n" +
                "Accept-Ranges: none\r\nCache-Control: no-store\r\nConnection: close\r\n\r\n").toByteArray())
            if (request[0] == "HEAD") { out.flush(); return }
            var sent = 0L
            onBytes(0, false)
            val source = resources.add(file.inputStream())
            try {
                source.use {
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        resources.check()
                        val n = it.read(buffer)
                        if (n < 0) break
                        out.write(buffer, 0, n)
                        sent += n
                        onBytes(sent, false)
                    }
                }
            } finally { resources.remove(source) }
            out.flush()
            if (sent == size) { completed = true; onBytes(sent, true) }
        } finally { deadline.cancel(false) }
    }
    private fun respond(client: Socket, status: Int) {
        client.getOutputStream().write("HTTP/1.1 $status Rejected\r\nContent-Length: 0\r\nConnection: close\r\n\r\n".toByteArray())
    }
    override fun close() { resources.close(); timer.shutdownNow() }
}
