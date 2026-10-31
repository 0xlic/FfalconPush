package io.github.lichen.raybirdpush.ui

import android.graphics.Bitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import io.github.lichen.raybirdpush.MainActivity
import io.github.lichen.raybirdpush.PushStore
import io.github.lichen.raybirdpush.SelectedApk
import io.github.lichen.raybirdpush.core.Phase
import io.github.lichen.raybirdpush.core.SessionStatus
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.LooperMode
import java.io.File

/** Android resources + Compose semantics/rendering only. No TV/ADB/network actions. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w412dp-h892dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
class ScreenTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    @After fun reset() {
        PushStore.selected.value = null
        PushStore.status.value = SessionStatus()
        PushStore.http.value = ""
    }
    private fun chooseTv() {
        compose.onNodeWithContentDescription("选择电视").performClick()
        compose.onNode(hasSetTextAction()).performTextReplacement("192.168.8.8")
        compose.onNodeWithContentDescription("选择此电视").performClick()
        compose.waitForIdle()
    }
    private fun selectFakeApk() {
        compose.runOnIdle {
            PushStore.selected.value = SelectedApk(File("not-read-by-ui.apk"), "example.demo", "示例应用", "1.0", 1, 8L * 1024 * 1024, "")
        }
    }
    private fun screenshot(name: String) {
        lateinit var bitmap: Bitmap
        compose.runOnIdle {
            val view = compose.activity.window.decorView
            bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(android.graphics.Canvas(bitmap))
        }
        val dir = File(requireNotNull(System.getProperty("screenshots.dir"))).apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        assertTrue(bitmap.width > 0 && bitmap.height > 0)
    }
    @Test
    @android.annotation.TargetApi(35)
    fun launcherUsesVectorAndMonochromeEmblem() {
        assertEquals("FfalconPush", compose.activity.getString(io.github.lichen.raybirdpush.R.string.app_name))
        val icon = compose.activity.applicationInfo.loadIcon(compose.activity.packageManager)
        assertTrue(icon is android.graphics.drawable.AdaptiveIconDrawable)
        val adaptive = icon as android.graphics.drawable.AdaptiveIconDrawable
        assertTrue(adaptive.foreground is android.graphics.drawable.VectorDrawable)
        assertNotNull(adaptive.monochrome)
        val bitmap = Bitmap.createBitmap(1024, 1024, Bitmap.Config.ARGB_8888)
        icon.setBounds(0, 0, 1024, 1024)
        icon.draw(android.graphics.Canvas(bitmap))
        val dir = File(requireNotNull(System.getProperty("screenshots.dir"))).apply { mkdirs() }
        File(dir, "icon.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
    @Test
    @Config(sdk = [26])
    @GraphicsMode(GraphicsMode.Mode.LEGACY)
    fun android8CanInflateLauncherAndShowControls() {
        assertTrue(compose.activity.applicationInfo.loadIcon(compose.activity.packageManager) is android.graphics.drawable.AdaptiveIconDrawable)
        compose.onNodeWithContentDescription("选择电视").assertExists()
        compose.onNodeWithContentDescription("选择 APK").assertExists()
    }
    @Test fun homeIsMinimalAndAccessible() {
        compose.onNodeWithContentDescription("选择电视").assertExists()
        compose.onNodeWithContentDescription("选择 APK").assertExists()
        compose.onNodeWithContentDescription("推送").assertIsNotEnabled()
        compose.onNodeWithText("FfalconPush").assertDoesNotExist()
        compose.onNodeWithText("APK 下载使用的手机接口").assertDoesNotExist()
        compose.onNodeWithText("分享脱敏诊断").assertDoesNotExist()
        compose.onNodeWithText("第三方许可").assertDoesNotExist()
        screenshot("home")
    }
    @Test
    @Config(qualifiers = "w320dp-h640dp-xhdpi")
    fun compactScreenKeepsControlsReachable() {
        compose.onNodeWithContentDescription("推送").performScrollTo().assertIsDisplayed()
        screenshot("compact")
    }
    @Test fun cancellationDoesNotClaimToUndoTvInstallation() {
        compose.runOnIdle { PushStore.status.value = SessionStatus(Phase.CANCELLED) }
        compose.onNodeWithText("传输已取消").assertExists()
        compose.onNodeWithText("电视上的安装可能仍在继续").assertExists()
        compose.onNodeWithText("安装完成").assertDoesNotExist()
    }
    @Test fun androidPreferencesSurviveRepositoryRecreation() {
        val history = io.github.lichen.raybirdpush.DeviceHistory(compose.activity)
        history.connected("192.168.8.8")
        assertEquals(listOf("192.168.8.8"), io.github.lichen.raybirdpush.DeviceHistory(compose.activity).read())
        assertFalse(PushStore.busy.value)
        history.remove("192.168.8.8")
    }
    @Test fun selectionDoesNotStartATransferAndMetadataIsAvailable() {
        chooseTv(); selectFakeApk()
        compose.onNodeWithText("192.168.8.8").assertExists()
        compose.onNodeWithText("示例应用").assertExists()
        compose.onNodeWithContentDescription("推送").assertIsEnabled()
        assertFalse(PushStore.busy.value)
        assertEquals(Phase.IDLE, PushStore.status.value.phase)
        screenshot("ready")
        compose.onNodeWithContentDescription("选择 APK").performClick()
        compose.onNodeWithText("example.demo").assertExists()
        compose.onNodeWithContentDescription("更换 APK").assertExists()
    }
    @Test fun unknownIsNotDrawnAsCompleted() {
        chooseTv()
        compose.runOnIdle { PushStore.status.value = SessionStatus(Phase.UNKNOWN, "未收到最终回执") }
        compose.onNodeWithText("结果未知 · 请到电视核对").assertExists()
        compose.onNodeWithText("安装完成").assertDoesNotExist()
        screenshot("unknown")
    }
    @Test fun consentAndCancelAreAvailableWithoutDebugText() {
        chooseTv(); selectFakeApk()
        // Render-only injection: never create a real PushSession or foreground service.
        compose.runOnIdle {
            PushStore.busy.value = true
            PushStore.status.value = SessionStatus(Phase.CONSENT)
        }
        try {
            compose.onNodeWithText("右 → 同意 → 确认").assertExists()
            compose.onNodeWithContentDescription("取消传输").assertIsEnabled()
            compose.onNodeWithContentDescription("选择电视").assertIsNotEnabled()
            compose.onNodeWithContentDescription("选择 APK").assertIsNotEnabled()
            screenshot("consent")
        } finally { PushStore.busy.value = false }
    }
}
