package io.github.lichen.raybirdpush.core

import java.io.File
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException
import java.util.UUID
import java.util.concurrent.TimeUnit

enum class Phase(val title: String) {
    IDLE("尚未开始"), CONNECTING("连接电视"), PREPARING("准备下载服务"),
    CONSENT("等待电视同意：右 → 同意 → 确认"), DOWNLOADING("下载中"), INSTALLING("安装中"),
    INSTALLED("安装成功"), DENIED("电视已拒绝"), CANCELLED("已取消手机侧传输"),
    FAILED("失败"), TIMED_OUT("等待超时"), UNKNOWN("结果未知")
}
data class SessionStatus(val phase: Phase = Phase.IDLE, val detail: String = "", val percent: Int? = null) {
    val active get() = phase in setOf(Phase.CONNECTING, Phase.PREPARING, Phase.CONSENT, Phase.DOWNLOADING, Phase.INSTALLING)
}

data class SessionTimeouts(val beforeRequest: Long = 20_000, val consent: Long = 90_000,
    val download: Long = 180_000, val install: Long = 180_000, val total: Long = 600_000,
    val heartbeatInterval: Long = 5000)

/** Pure state transitions. Progress can't move backwards, old sessions can't finish this one. */
class SessionMachine(private val pkg: String, private val id: String,
    private val now: () -> Long, private val timeouts: SessionTimeouts = SessionTimeouts()) {
    @Volatile var phase = Phase.CONSENT
        private set
    @Volatile var since = now()
        private set
    private val start = since
    fun accept(text: String): SessionStatus? {
        if (phase !in setOf(Phase.CONSENT, Phase.DOWNLOADING, Phase.INSTALLING)) return null
        val r = receipt(text, pkg, id) ?: return null
        val next = when {
            r.kind == "INSTALL" && r.stage == "denied" -> Phase.DENIED
            r.kind == "PROGRESS" && r.stage == "2" -> Phase.FAILED
            r.kind == "PROGRESS" && r.stage == "5" -> Phase.INSTALLED
            (r.kind == "PROGRESS" && r.stage == "3") || (r.kind == "INSTALL" && r.stage == "installing") -> Phase.INSTALLING
            r.kind == "PROGRESS" && r.stage == "1" && phase != Phase.INSTALLING -> Phase.DOWNLOADING
            else -> return null // INSTALL installed is not yet a validated success signal.
        }
        if (next != phase) { phase = next; since = now() }
        return SessionStatus(next, percent = r.percent)
    }
    fun expired(): Boolean = now() - start >= timeouts.total || now() - since >= when (phase) {
        Phase.CONSENT -> timeouts.consent
        Phase.DOWNLOADING -> timeouts.download
        Phase.INSTALLING -> timeouts.install
        else -> Long.MAX_VALUE
    }
}

class PushSession(
    private val tv: InetAddress,
    private val bind: InetAddress,
    private val file: File,
    private val pkg: String,
    private val label: String,
    private val version: Long,
    private val socketFactory: () -> Socket = { Socket() },
    private val onConnected: () -> Unit = {},
    private val update: (SessionStatus) -> Unit,
    private val log: (String) -> Unit,
    private val timeouts: SessionTimeouts = SessionTimeouts()
) : AutoCloseable {
    private val resources = Resources()
    private val clock = { TimeUnit.NANOSECONDS.toMillis(System.nanoTime()) }
    private var requested = false
    private var installed = false
    @Volatile private var machine: SessionMachine? = null
    @Volatile private var timedOut = false
    private var http: ApkHttpServer? = null
    val sessionId: String = UUID.randomUUID().toString()
    @Volatile private var cancelled = false
    fun cancel() { cancelled = true; close() }
    fun run() {
        val began = clock()
        val watchdog = java.util.concurrent.Executors.newSingleThreadScheduledExecutor()
        val heartbeat = java.util.concurrent.Executors.newSingleThreadScheduledExecutor()
        watchdog.scheduleWithFixedDelay({
            val m = machine
            if ((m == null && clock() - began >= timeouts.beforeRequest) || m?.expired() == true) {
                timedOut = true
                resources.close() // interrupts slow/trickling frames and blocked writes, independently of heartbeat
            }
        }, 250, 250, TimeUnit.MILLISECONDS)
        try {
            update(SessionStatus(Phase.CONNECTING))
            val socket = resources.add(socketFactory())
            socket.connect(InetSocketAddress(tv, 6553), 5000)
            socket.soTimeout = 500
            val input = socket.getInputStream()
            val out = socket.getOutputStream()
            val decoder = Protocol.Decoder()
            Protocol.write(out, "159>>1>>0")
            val handshakeEnd = clock() + 10_000
            var ready = false
            while (!ready) {
                resources.check()
                check(clock() < handshakeEnd) { "握手超时，TCP 连接不等于多屏互动可用" }
                try { ready = decoder.read(input) == "253>>1>>0" }
                catch (_: SocketTimeoutException) { }
            }
            onConnected()
            log("多屏互动握手完成")
            update(SessionStatus(Phase.PREPARING))
            val server = resources.add(ApkHttpServer(bind, tv, file, { bytes, complete ->
                if (complete) log("HTTP 完整文件已写出；不等同于电视安装完成")
                // Protocol stage and HTTP transport are independently reported.
                httpProgress(bytes, file.length(), complete)
            }))
            http = server
            val m = SessionMachine(pkg, Protocol.pathId(server.url), clock, timeouts)
            machine = m
            val request = Protocol.request(pkg, label, server.url, version, file.length())
            resources.check()
            // Mark before writing: even a partial/failed write can have uncertain effects.
            requested = true
            Protocol.write(out, request)
            update(SessionStatus(Phase.CONSENT, "手机局域网直连模式；请在电视操作遥控器"))
            log("安装请求已发送（不代表下载或安装成功）")
            heartbeat.scheduleWithFixedDelay({
                try { Protocol.write(out, "150>>") }
                catch (_: Exception) { resources.close() }
            }, timeouts.heartbeatInterval, timeouts.heartbeatInterval, TimeUnit.MILLISECONDS)
            while (true) {
                resources.check()
                if (m.expired()) {
                    reportTimeout()
                    return
                }
                val text = try { decoder.read(input) } catch (_: SocketTimeoutException) { continue }
                val status = m.accept(text) ?: continue
                update(status)
                if (!status.active) {
                    installed = status.phase == Phase.INSTALLED
                    return
                }
            }
        } catch (_: Exception) {
            when {
                installed -> Unit
                cancelled -> update(SessionStatus(Phase.CANCELLED, "已关闭传输；不能撤销电视可能已开始的安装"))
                timedOut -> reportTimeout()
                requested -> update(SessionStatus(Phase.UNKNOWN, "连接中断或协议异常，未能确认电视最终结果"))
                else -> update(SessionStatus(Phase.FAILED, "连接/握手/下载服务准备失败；请检查 IP、同一局域网及多屏互动"))
            }
        } finally { close(); heartbeat.shutdownNow(); watchdog.shutdownNow(); log("本会话 HTTP、TCP 和计时资源已关闭") }
    }
    private fun reportTimeout() {
        val detail = if (http?.requests == 0) "电视未发起 HTTP 请求；可能未同意、URL 被固件拒绝或网络不可达"
            else "等待超时，未收到匹配当前任务的安装完成回执"
        update(SessionStatus(if (machine?.phase == Phase.INSTALLING || http?.completed == true) Phase.UNKNOWN else Phase.TIMED_OUT, detail))
    }
    var httpProgress: (Long, Long, Boolean) -> Unit = { _, _, _ -> }
    override fun close() { resources.close() }
}
