package io.github.lichen.raybirdpush.ui

import io.github.lichen.raybirdpush.core.Phase
import io.github.lichen.raybirdpush.core.SessionStatus
import java.util.Locale

fun fileSize(bytes: Long): String = when {
    bytes >= 1024 * 1024 -> String.format(Locale.ROOT, "%.1f MB", bytes / 1048576.0)
    bytes >= 1024 -> String.format(Locale.ROOT, "%.0f KB", bytes / 1024.0)
    else -> "$bytes B"
}

// Presentation never infers installation success from transport bytes, a chosen TV or an animation.
fun statusCaption(status: SessionStatus, preparing: Boolean): String {
    if (preparing) return "正在读取 APK"
    val percent = status.percent?.takeIf { it in 0..100 }?.let { " · $it%" }.orEmpty()
    return when (status.phase) {
        Phase.IDLE -> ""
        Phase.CONNECTING -> "连接中"
        Phase.PREPARING -> "准备中"
        Phase.CONSENT -> "右 → 同意 → 确认"
        Phase.DOWNLOADING -> "下载中$percent"
        Phase.INSTALLING -> "安装中$percent"
        Phase.INSTALLED -> "安装完成"
        Phase.DENIED -> "电视已拒绝"
        Phase.CANCELLED -> "传输已取消"
        Phase.FAILED -> "未能完成"
        Phase.TIMED_OUT -> "等待超时"
        Phase.UNKNOWN -> "结果未知 · 请到电视核对"
    }
}
