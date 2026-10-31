package io.github.lichen.raybirdpush

import android.content.Context
import android.net.Uri
import io.github.lichen.raybirdpush.core.ApkFiles
import io.github.lichen.raybirdpush.core.Protocol
import io.github.lichen.raybirdpush.core.Resources
import java.io.File
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

data class SelectedApk(val file: File, val pkg: String, val label: String, val version: String,
    val code: Long, val bytes: Long, val sha256: String, val icon: android.graphics.Bitmap? = null)

object ApkRepository {
    fun prepare(context: Context, uri: Uri, resources: Resources): SelectedApk {
        val dir = File(context.cacheDir, "selected-apk").apply { mkdirs() }
        val dest = File(dir, "${UUID.randomUUID()}.apk")
        val watchdog = Executors.newSingleThreadScheduledExecutor()
        val signal = android.os.CancellationSignal()
        resources.add(java.io.Closeable { signal.cancel() })
        val limit = watchdog.schedule({ resources.close() }, 120, TimeUnit.SECONDS)
        try {
            val descriptor = resources.add(requireNotNull(
                context.contentResolver.openAssetFileDescriptor(uri, "r", signal)) { "无法读取文件" })
            val input = descriptor.createInputStream()
            val (size, sha) = ApkFiles.copy(input, dest, resources, {})
            ApkFiles.verifyZip(dest, resources, {})
            resources.check()
            @Suppress("DEPRECATION")
            val info = context.packageManager.getPackageArchiveInfo(dest.path, 0)
                ?: error("无法解析 APK；不支持 APKS/XAPK/AAB 或 split APK")
            require(info.splitNames.isNullOrEmpty()) { "不支持 split APK" }
            val app = requireNotNull(info.applicationInfo)
            app.sourceDir = dest.path; app.publicSourceDir = dest.path
            val label = Protocol.safeField(app.loadLabel(context.packageManager).toString())
            val pkg = Protocol.safeField(info.packageName)
            val code = if (android.os.Build.VERSION.SDK_INT >= 28) info.longVersionCode else {
                @Suppress("DEPRECATION")
                info.versionCode.toLong()
            }
            resources.check()
            // Fixed-size rendering on the preparation IO thread; failure only falls back to the package glyph.
            val icon = runCatching {
                val drawable = app.loadIcon(context.packageManager)
                androidx.core.graphics.createBitmap(160, 160).also { bitmap ->
                    drawable.setBounds(0, 0, bitmap.width, bitmap.height)
                    drawable.draw(android.graphics.Canvas(bitmap))
                }
            }.getOrNull()
            resources.check()
            return SelectedApk(dest, pkg, label, info.versionName.orEmpty(), code, size, sha, icon)
        } catch (e: Exception) { dest.delete(); throw e }
        finally { limit.cancel(false); watchdog.shutdownNow() }
    }
}
