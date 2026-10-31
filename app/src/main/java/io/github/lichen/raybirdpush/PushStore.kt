package io.github.lichen.raybirdpush

import android.net.Network
import io.github.lichen.raybirdpush.core.SessionStatus
import kotlinx.coroutines.flow.MutableStateFlow
import java.net.InetAddress
import java.util.concurrent.atomic.AtomicBoolean

data class LanInterface(val network: Network, val address: InetAddress, val name: String)
data class PushInput(val ip: String, val lan: LanInterface, val apk: SelectedApk)
object PushStore {
    val busy = MutableStateFlow(false)
    private val claimed = AtomicBoolean()
    val status = MutableStateFlow(SessionStatus())
    val http = MutableStateFlow("")
    val selected = MutableStateFlow<SelectedApk?>(null)
    val history = MutableStateFlow<List<String>>(emptyList())
    val diagnostics = MutableStateFlow("")
    @Volatile var pending: PushInput? = null
    private var began = System.nanoTime()
    private val lines = ArrayDeque<String>()
    @Synchronized fun claim(input: PushInput): Boolean {
        if (!claimed.compareAndSet(false, true)) return false
        pending = input; busy.value = true; http.value = "尚无 HTTP 请求"
        synchronized(this) { began = System.nanoTime(); lines.clear(); diagnostics.value = "" }
        return true
    }
    @Synchronized fun release(expected: PushInput) {
        if (pending !== expected) return
        pending = null; claimed.set(false); busy.value = false
    }
    @Synchronized fun log(message: String) {
        val sec = (System.nanoTime() - began) / 1_000_000_000
        lines.addLast("+${sec}s $message")
        while (lines.size > 200) lines.removeFirst()
        diagnostics.value = lines.joinToString("\n")
    }
    fun update(value: SessionStatus) {
        if (status.value.phase != value.phase) log("阶段：${value.phase}")
        status.value = value
    }
}
