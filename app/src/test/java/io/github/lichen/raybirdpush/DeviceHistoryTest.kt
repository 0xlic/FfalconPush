package io.github.lichen.raybirdpush

import org.junit.Assert.*
import org.junit.Test

class DeviceHistoryTest {
    class Disk : HistoryStorage {
        var text = ""
        override fun load() = text
        override fun save(value: String) { text = value }
    }
    @Test fun restoresAfterRepositoryRecreationAndForgets() {
        val disk = Disk()
        DeviceHistory(disk).connected("192.168.1.2")
        DeviceHistory(disk).connected("192.168.1.3")
        val restored = DeviceHistory(disk)
        assertEquals(listOf("192.168.1.3", "192.168.1.2"), restored.read())
        restored.remove("192.168.1.3")
        assertEquals(listOf("192.168.1.2"), DeviceHistory(disk).read())
    }
    @Test fun deduplicatesAndCapsAtEight() {
        val records = DeviceHistory(Disk())
        for (i in 1..10) records.connected("10.0.0.$i")
        records.connected("10.0.0.5")
        assertEquals(8, records.read().size)
        assertEquals("10.0.0.5", records.read().first())
        assertEquals(1, records.read().count { it == "10.0.0.5" })
    }
    @Test fun readingDoesNotSaveOrTriggerActionsAndIgnoresCorruptRecords() {
        var writes = 0
        val history = DeviceHistory(object : HistoryStorage {
            override fun load() = "bad|8.8.8.8|192.168.1.2|192.168.1.2|10.0.0.3"
            override fun save(value: String) { writes++ }
        })
        assertEquals(listOf("192.168.1.2", "10.0.0.3"), history.read())
        assertEquals(0, writes)
    }
}
