package io.github.lichen.raybirdpush.core

import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketAddress
import java.net.URL
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread

/** Loopback-only fake TV, never contacts a user's device. Not Android/TV validation. */
class MockTvTest {
    private val loop = InetAddress.getByName("127.0.0.1")
    private fun runScenario(final: String, download: Boolean): List<SessionStatus> {
        val file = File.createTempFile("mock-tv-apk", ".apk").apply { writeBytes(ByteArray(300_000) { (it % 127).toByte() }) }
        val statuses = mutableListOf<SessionStatus>()
        val failure = AtomicReference<Throwable?>()
        ServerSocket(0, 1, loop).use { tv ->
            val worker = thread(name = "fake-tv", isDaemon = true) {
                try { tv.accept().use { socket ->
                    socket.soTimeout = 3000
                    val decoder = Protocol.Decoder()
                    val input = socket.getInputStream(); val out = socket.getOutputStream()
                    assertEquals("159>>1>>0", decoder.read(input))
                    Protocol.write(out, "253>>1>>0")
                    val fields = decoder.read(input).split(">>")
                    assertEquals("INSTALL", fields[1]); assertEquals("1", fields[7])
                    val id = fields[11]
                    // Neither another package nor a previous session can finish this session.
                    Protocol.write(out, "223>>PROGRESS>>other.pkg>>label>>5>>100>>$id")
                    Protocol.write(out, "223>>PROGRESS>>app.test>>label>>5>>100>>old-session")
                    if (download) {
                        Protocol.write(out, "223>>PROGRESS>>app.test>>label>>1>>10>>$id")
                        val connection = URL(fields[4]).openConnection() as HttpURLConnection
                        try {
                            connection.connectTimeout = 2000; connection.readTimeout = 2000
                            assertArrayEquals(file.readBytes(), connection.inputStream.use { it.readBytes() })
                        } finally { connection.disconnect() }
                        Protocol.write(out, "223>>PROGRESS>>app.test>>label>>3>>50>>$id")
                    }
                    Protocol.write(out, final.replace("{id}", id))
                } } catch (e: Throwable) { failure.set(e) }
            }
            try {
                var connections = 0
                PushSession(loop, loop, file, "app.test", "中文应用", 1,
                    socketFactory = { object : Socket() {
                        override fun connect(endpoint: SocketAddress?, timeout: Int) {
                            super.connect(InetSocketAddress(loop, tv.localPort), timeout)
                        }
                    } }, onConnected = { connections++ }, update = { statuses.add(it) }, log = {}).run()
                worker.join(4000)
                assertFalse(worker.isAlive)
                failure.get()?.let { throw AssertionError("Fake TV failed", it) }
                assertEquals(1, connections)
            } finally { file.delete() }
        }
        return statuses
    }
    @Test fun completeDownloadAndMatchingInstalledReceipt() {
        repeat(2) { // repeated push, fresh token/session
            val states = runScenario("223>>PROGRESS>>app.test>>label>>5>>100>>{id}", true)
            assertEquals(Phase.INSTALLED, states.last().phase)
            assertEquals(1, states.count { it.phase == Phase.INSTALLED })
            assertTrue(states.any { it.phase == Phase.DOWNLOADING })
            assertTrue(states.any { it.phase == Phase.INSTALLING })
        }
    }
    @Test fun tvDenialEndsWithoutDownload() {
        assertEquals(Phase.DENIED, runScenario("223>>INSTALL>>app.test>>label>>denied>>{id}", false).last().phase)
    }
    @Test fun tvReportsInstallationFailure() {
        assertEquals(Phase.FAILED, runScenario("223>>PROGRESS>>app.test>>label>>2>>0>>{id}", true).last().phase)
    }
    @Test fun unverifiedInstalledMessageThenDisconnectIsUnknown() {
        assertEquals(Phase.UNKNOWN, runScenario("223>>INSTALL>>app.test>>label>>installed>>{id}", true).last().phase)
    }
}
