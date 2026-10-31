package io.github.lichen.raybirdpush.core

import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketAddress
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

class TimeoutTest {
    @Test fun watchdogClosesSlowPartialFrameIndependentlyOfDecoderAndHeartbeat() {
        val loop = InetAddress.getByName("127.0.0.1")
        val file = File.createTempFile("timeout-apk", ".apk").apply { writeText("example") }
        val running = AtomicBoolean(true)
        ServerSocket(0, 1, loop).use { tv ->
            val worker = thread(isDaemon = true) {
                runCatching { tv.accept().use { socket ->
                    Protocol.Decoder().read(socket.getInputStream())
                    // Valid 4096-byte frame, trickle payload: reads keep succeeding but no frame completes.
                    socket.getOutputStream().write(byteArrayOf(0, 0, 16, 0))
                    while (running.get()) { socket.getOutputStream().write(65); Thread.sleep(40) }
                } }
            }
            var last = Phase.IDLE
            val start = System.nanoTime()
            try {
                PushSession(loop, loop, file, "app.test", "label", 1, socketFactory = {
                    object : Socket() {
                        override fun connect(endpoint: SocketAddress?, timeout: Int) {
                            super.connect(InetSocketAddress(loop, tv.localPort), timeout)
                        }
                    }
                }, update = { last = it.phase }, log = {},
                    timeouts = SessionTimeouts(beforeRequest = 80)).run()
                assertEquals(Phase.TIMED_OUT, last)
                assertTrue((System.nanoTime() - start) / 1_000_000 < 2000)
            } finally { running.set(false); worker.join(2000); file.delete() }
        }
    }
    @Test fun heartbeatRunsWhileDecoderReceivesAnIncompleteFrame() {
        val loop = InetAddress.getByName("127.0.0.1")
        val file = File.createTempFile("heartbeat-apk", ".apk").apply { writeText("example") }
        val heardHeartbeat = java.util.concurrent.CountDownLatch(1)
        val running = AtomicBoolean(true)
        ServerSocket(0, 1, loop).use { tv ->
            val worker = thread(isDaemon = true) {
                runCatching { tv.accept().use { socket ->
                    socket.soTimeout = 1500
                    val decoder = Protocol.Decoder()
                    decoder.read(socket.getInputStream())
                    Protocol.write(socket.getOutputStream(), "253>>1>>0")
                    decoder.read(socket.getInputStream()) // installation request
                    socket.getOutputStream().write(byteArrayOf(0, 0, 16, 0))
                    val trickle = thread(isDaemon = true) {
                        runCatching {
                            while (running.get()) { socket.getOutputStream().write(65); Thread.sleep(10) }
                        }
                    }
                    try {
                        if (decoder.read(socket.getInputStream()) == "150>>") heardHeartbeat.countDown()
                    } finally { running.set(false); trickle.join(1000) }
                } }
            }
            val session = PushSession(loop, loop, file, "app.test", "label", 1, socketFactory = {
                object : Socket() {
                    override fun connect(endpoint: SocketAddress?, timeout: Int) {
                        super.connect(InetSocketAddress(loop, tv.localPort), timeout)
                    }
                }
            }, update = {}, log = {}, timeouts = SessionTimeouts(heartbeatInterval = 50))
            val task = thread(isDaemon = true) { session.run() }
            try {
                assertTrue(heardHeartbeat.await(2, java.util.concurrent.TimeUnit.SECONDS))
            } finally {
                running.set(false); session.cancel(); task.join(2000); worker.join(2000); file.delete()
            }
            assertFalse(task.isAlive)
            assertFalse(worker.isAlive)
        }
    }
    @Test fun globalDeadlineIsNotExtendedByPhaseTransitions() {
        var now = 0L
        val m = SessionMachine("pkg", "id", { now }, SessionTimeouts(total = 100))
        now = 30; m.accept("223>>PROGRESS>>pkg>>label>>1>>20>>id")
        now = 60; m.accept("223>>PROGRESS>>pkg>>label>>3>>40>>id")
        now = 100
        assertTrue(m.expired())
    }
}
