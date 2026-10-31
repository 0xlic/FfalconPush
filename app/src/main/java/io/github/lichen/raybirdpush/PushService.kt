package io.github.lichen.raybirdpush

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import io.github.lichen.raybirdpush.core.Phase
import io.github.lichen.raybirdpush.core.PushSession
import io.github.lichen.raybirdpush.core.SessionStatus
import io.github.lichen.raybirdpush.core.privateIpv4
import kotlin.concurrent.thread

class PushService : Service() {
    @Volatile private var session: PushSession? = null
    private var started = false
    private var serviceInput: PushInput? = null
    @Volatile private var workerRunning = false
    @Volatile private var destroyed = false
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "CANCEL") {
            session?.cancel()
            if (!started) stopSelf()
            return START_NOT_STICKY
        }
        if (started) return START_NOT_STICKY
        val input = PushStore.pending
        if (intent?.action != "PUSH" || input == null) { stopSelf(); return START_NOT_STICKY }
        started = true
        serviceInput = input
        try {
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(NotificationChannel("transfer", "用户发起的 APK 推送", NotificationManager.IMPORTANCE_LOW))
            val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
            val cancel = PendingIntent.getService(this, 1, Intent(this, PushService::class.java).setAction("CANCEL"), PendingIntent.FLAG_IMMUTABLE)
            val notification = Notification.Builder(this, "transfer")
                .setSmallIcon(android.R.drawable.stat_sys_upload)
                .setContentTitle("正在向电视推送 APK")
                .setContentText("请在电视按：右 → 同意 → 确认；可返回 App 查看状态")
                .setContentIntent(open).setOngoing(true)
                .addAction(Notification.Action.Builder(null, "取消传输", cancel).build()).build()
            if (Build.VERSION.SDK_INT >= 29) startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
            else startForeground(1, notification)
            val runner = PushSession(privateIpv4(input.ip), input.lan.address, input.apk.file,
                input.apk.pkg, input.apk.label, input.apk.code,
                socketFactory = { input.lan.network.socketFactory.createSocket() },
                onConnected = {
                    runCatching {
                        DeviceHistory(this).connected(input.ip)
                        PushStore.history.value = DeviceHistory(this).read()
                    }.onFailure { PushStore.log("连接成功，但本地连接记录保存失败") }
                }, update = PushStore::update, log = PushStore::log)
            session = runner
            runner.httpProgress = { bytes, total, complete ->
                if (bytes == 0L && !complete) PushStore.log("HTTP 已收到电视下载请求（未完成）")
                PushStore.http.value = "HTTP 已写出 ${bytes * 100 / total}%" +
                    if (complete) "（完整文件；仍需安装回执）" else "（不代表已安装）"
            }
            PushStore.log("版本 ${BuildConfig.VERSION_NAME}；Android ${Build.VERSION.SDK_INT}；targetSdk 36；LAN HTTP 实验模式")
            workerRunning = true
            thread(name = "push-session") {
                try { runner.run() } finally {
                    input.apk.file.delete()
                    if (PushStore.selected.value === input.apk) PushStore.selected.value = null
                    PushStore.log("已清理本会话 APK 缓存；未操作电视文件或设置")
                    workerRunning = false
                    Handler(Looper.getMainLooper()).post {
                        if (destroyed) PushStore.release(input) else stopSelf() // CANCEL also advances startId
                    }
                }
            }
        } catch (_: Exception) {
            session?.cancel()
            PushStore.update(SessionStatus(Phase.FAILED, "无法启动传输前台服务；请保持 App 前台并重试"))
            stopSelf()
        }
        return START_NOT_STICKY
    }
    override fun onTimeout(startId: Int, fgsType: Int) {
        session?.cancel()
        PushStore.log("系统前台服务时限触发，正在关闭传输")
        stopSelf()
    }
    override fun onDestroy() {
        destroyed = true
        session?.cancel()
        stopForeground(STOP_FOREGROUND_REMOVE)
        if (!workerRunning) serviceInput?.let(PushStore::release)
        super.onDestroy()
    }
}
