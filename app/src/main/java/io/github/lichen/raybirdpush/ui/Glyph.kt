package io.github.lichen.raybirdpush.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.unit.dp

// Small, purpose-drawn vector symbols. No icon font or bitmap upscaling.
enum class Mark { TV, APK, SEND, PLUS, CLOSE, CHECK, EDIT, TRASH, WIFI, INFO, REFRESH, SHARE, FOLDER, STOP, LICENSE }

@Composable
fun Glyph(mark: Mark, modifier: Modifier = Modifier, color: Color = LocalContentColor.current) {
    Canvas(modifier.size(24.dp)) {
        scale(size.width / 24f, size.height / 24f, pivot = Offset.Zero) {
            val stroke = Stroke(1.7f, cap = StrokeCap.Round)
            fun line(x: Float, y: Float, xx: Float, yy: Float) =
                drawLine(color, Offset(x, y), Offset(xx, yy), 1.7f, StrokeCap.Round)
            fun path(vararg points: Float) {
                val p = Path().apply { moveTo(points[0], points[1]); for (i in 2 until points.size step 2) lineTo(points[i], points[i + 1]) }
                drawPath(p, color, style = stroke)
            }
            when (mark) {
                Mark.TV -> { drawRoundRect(color, Offset(3f, 4f), Size(18f, 13f), CornerRadius(2f), style = stroke); line(8f, 21f, 16f, 21f); line(12f, 17f, 12f, 21f) }
                Mark.APK -> { path(5f, 7f, 12f, 3f, 19f, 7f, 19f, 17f, 12f, 21f, 5f, 17f, 5f, 7f, 12f, 11f, 19f, 7f); line(12f, 11f, 12f, 21f); line(8.5f, 5f, 15.5f, 9f) }
                Mark.SEND -> { path(6f, 10f, 12f, 4f, 18f, 10f); line(12f, 4f, 12f, 19f) }
                Mark.PLUS -> { line(5f, 12f, 19f, 12f); line(12f, 5f, 12f, 19f) }
                Mark.CLOSE -> { line(6f, 6f, 18f, 18f); line(18f, 6f, 6f, 18f) }
                Mark.CHECK -> path(5f, 12f, 10f, 17f, 19f, 7f)
                Mark.EDIT -> { path(5f, 15f, 15f, 5f, 19f, 9f, 9f, 19f, 4f, 20f, 5f, 15f); line(13f, 7f, 17f, 11f) }
                Mark.TRASH -> { path(6f, 7f, 7f, 20f, 17f, 20f, 18f, 7f); line(4f, 7f, 20f, 7f); path(9f, 7f, 9f, 4f, 15f, 4f, 15f, 7f); line(10f, 10f, 10f, 16f); line(14f, 10f, 14f, 16f) }
                Mark.WIFI -> { val p = Path().apply { moveTo(3f, 8f); quadraticTo(12f, 1f, 21f, 8f); moveTo(6f, 12f); quadraticTo(12f, 7f, 18f, 12f); moveTo(9f, 16f); quadraticTo(12f, 13f, 15f, 16f) }; drawPath(p, color, style = stroke); drawCircle(color, 1.1f, Offset(12f, 20f)) }
                Mark.INFO -> { drawCircle(color, 9f, Offset(12f, 12f), style = stroke); drawCircle(color, 1f, Offset(12f, 8f)); line(12f, 11f, 12f, 17f) }
                Mark.REFRESH -> { drawArc(color, 35f, 285f, false, Offset(4f, 4f), Size(16f, 16f), style = stroke); path(19f, 3f, 19f, 8f, 14f, 8f) }
                Mark.SHARE -> { path(8f, 9f, 16f, 5f); path(8f, 15f, 16f, 19f); for (o in listOf(Offset(5f, 12f), Offset(18f, 4f), Offset(18f, 20f))) drawCircle(color, 2.5f, o, style = stroke) }
                Mark.FOLDER -> { path(3f, 7f, 3f, 19f, 21f, 19f, 21f, 7f, 12f, 7f, 10f, 4f, 3f, 4f, 3f, 7f); line(4f, 10f, 20f, 10f) }
                Mark.STOP -> drawRoundRect(color, Offset(6f, 6f), Size(12f, 12f), CornerRadius(3f))
                Mark.LICENSE -> { drawRoundRect(color, Offset(5f, 3f), Size(14f, 18f), CornerRadius(2f), style = stroke); line(9f, 8f, 15f, 8f); line(9f, 12f, 15f, 12f); line(9f, 16f, 13f, 16f) }
            }
        }
    }
}
