package io.github.lichen.raybirdpush

import android.content.Context
import io.github.lichen.raybirdpush.core.privateIpv4

interface HistoryStorage {
    fun load(): String
    fun save(value: String)
}

/** Only successful handshakes enter history. Reading history never opens a socket. */
class DeviceHistory(private val storage: HistoryStorage) {
    constructor(context: Context) : this(object : HistoryStorage {
        private val prefs = context.getSharedPreferences("devices_private", Context.MODE_PRIVATE)
        override fun load() = prefs.getString("recent", "").orEmpty()
        override fun save(value: String) {
            check(prefs.edit().putString("recent", value).commit()) { "连接记录保存失败" }
        }
    })
    @Synchronized fun read(): List<String> = decode(storage.load())
    @Synchronized fun connected(ip: String) {
        privateIpv4(ip)
        storage.save(((listOf(ip) + read()).distinct().take(8)).joinToString("|"))
    }
    @Synchronized fun remove(ip: String) { storage.save(read().filterNot { it == ip }.joinToString("|")) }
    companion object {
        fun decode(raw: String): List<String> = raw.split('|').filter {
            runCatching { privateIpv4(it) }.isSuccess
        }.distinct().take(8)
    }
}
