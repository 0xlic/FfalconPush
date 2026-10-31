package io.github.lichen.raybirdpush.core

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.io.File
import java.net.InetAddress
import java.net.Socket
import java.net.SocketAddress
import java.net.SocketTimeoutException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class SessionTest {
    private var now = 0L
    private fun machine() = SessionMachine("app.test", "current", now = { now })
    private fun progress(stage: Int, percent: Int = 100) = "223>>PROGRESS>>app.test>>name>>$stage>>$percent>>current"
    @Test fun completeRequiresCurrentReceipt() {
        val m = machine()
        assertNull(m.accept("223>>PROGRESS>>app.test>>name>>5>>100>>old"))
        assertNull(m.accept("223>>INSTALL>>app.test>>name>>installed>>current"))
        assertEquals(Phase.INSTALLED, m.accept(progress(5))!!.phase)
        assertNull(m.accept(progress(2)))
    }
    @Test fun statesDoNotRegress() {
        val m = machine()
        assertEquals(Phase.DOWNLOADING, m.accept(progress(1, 10))!!.phase)
        assertEquals(Phase.INSTALLING, m.accept(progress(3, 40))!!.phase)
        assertNull(m.accept(progress(1, 20)))
        assertEquals(Phase.INSTALLING, m.phase)
    }
    @Test fun rejectionAndFailureAreTerminal() {
        val m = machine()
        assertEquals(Phase.DENIED, m.accept("223>>INSTALL>>app.test>>name>>denied>>current")!!.phase)
        assertNull(m.accept(progress(5)))
        assertEquals(Phase.FAILED, machine().accept(progress(2))!!.phase)
    }
    @Test fun heartbeatAndRepeatedProgressCannotExtendDeadline() {
        val m = machine()
        now = 89_999
        assertFalse(m.expired())
        assertNull(m.accept("150>>YES"))
        now = 90_000
        assertTrue(m.expired())
        val download = machine()
        download.accept(progress(1, 1))
        now += 179_999; download.accept(progress(1, 99))
        assertFalse(download.expired())
        now++; assertTrue(download.expired())
    }
    @Test fun resourcesCloseExactlyOnceAndLateRegistrationCloses() {
        val resources = Resources()
        var closes = 0
        resources.add(Closeable { closes++ })
        resources.close(); resources.close()
        assertThrows(InterruptedException::class.java) { resources.add(Closeable { closes++ }) }
        assertEquals(2, closes)
    }
    @Test fun disconnectAfterRequestIsUnknownAndSocketCloses() {
        val file = File.createTempFile("fake-apk", ".apk").apply { writeBytes(ByteArray(10)) }
        var closed = false
        val socket = object : Socket() {
            override fun connect(endpoint: SocketAddress?, timeout: Int) { }
            override fun setSoTimeout(timeout: Int) { }
            override fun getInputStream() = ByteArrayInputStream(Protocol.encode("253>>1>>0"))
            override fun getOutputStream() = ByteArrayOutputStream()
            override fun close() { closed = true }
        }
        val phases = mutableListOf<Phase>()
        try {
            PushSession(InetAddress.getLoopbackAddress(), InetAddress.getLoopbackAddress(), file,
                "app.test", "name", 1, { socket }, update = { phases.add(it.phase) }, log = {}).run()
            assertEquals(Phase.UNKNOWN, phases.last())
            assertTrue(closed)
        } finally { file.delete() }
    }
    @Test fun cancellationInterruptsWaitingSocket() {
        val file = File.createTempFile("fake-apk", ".apk").apply { writeBytes(ByteArray(10)) }
        val waiting = CountDownLatch(1)
        val closed = CountDownLatch(1)
        val socket = object : Socket() {
            override fun connect(endpoint: SocketAddress?, timeout: Int) { waiting.countDown(); closed.await(2, TimeUnit.SECONDS); throw SocketTimeoutException() }
            override fun close() { closed.countDown() }
        }
        var last = Phase.IDLE
        val session = PushSession(InetAddress.getLoopbackAddress(), InetAddress.getLoopbackAddress(), file,
            "app.test", "name", 1, { socket }, update = { last = it.phase }, log = {})
        val task = Thread { session.run() }
        try {
            task.start(); assertTrue(waiting.await(1, TimeUnit.SECONDS)); session.cancel(); task.join(2000)
            assertFalse(task.isAlive); assertEquals(Phase.CANCELLED, last)
        } finally { session.cancel(); file.delete() }
    }
}
