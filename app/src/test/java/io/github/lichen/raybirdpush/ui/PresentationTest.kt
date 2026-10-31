package io.github.lichen.raybirdpush.ui

import io.github.lichen.raybirdpush.core.Phase
import io.github.lichen.raybirdpush.core.SessionStatus
import org.junit.Assert.*
import org.junit.Test

class PresentationTest {
    @Test fun unknownIsNeverSuccess() {
        val text = statusCaption(SessionStatus(Phase.UNKNOWN), false)
        assertTrue(text.contains("未知")); assertFalse(text.contains("完成"))
    }
    @Test fun downloadAt100IsNotInstallationSuccess() {
        assertEquals("下载中 · 100%", statusCaption(SessionStatus(Phase.DOWNLOADING, percent = 100), false))
        assertEquals("安装中 · 100%", statusCaption(SessionStatus(Phase.INSTALLING, percent = 100), false))
        assertEquals("安装完成", statusCaption(SessionStatus(Phase.INSTALLED), false))
    }
    @Test fun idleDoesNotClaimConnectionAndConsentStaysExplicit() {
        assertEquals("", statusCaption(SessionStatus(), false))
        assertEquals("右 → 同意 → 确认", statusCaption(SessionStatus(Phase.CONSENT), false))
    }
    @Test fun preparationIsNotAResumedInstall() {
        assertEquals("正在读取 APK", statusCaption(SessionStatus(Phase.INSTALLED), true))
    }
    @Test fun invalidPercentIsNotShown() {
        assertEquals("下载中", statusCaption(SessionStatus(Phase.DOWNLOADING, percent = 101), false))
    }
    @Test fun compactSizeMatchesActualBytes() {
        assertEquals("8.0 MB", fileSize(8L * 1024 * 1024))
        assertEquals("12 KB", fileSize(12L * 1024))
        assertEquals("25 B", fileSize(25))
    }
}
