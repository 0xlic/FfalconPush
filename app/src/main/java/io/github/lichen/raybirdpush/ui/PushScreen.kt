package io.github.lichen.raybirdpush.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.lichen.raybirdpush.SelectedApk
import io.github.lichen.raybirdpush.core.Phase
import io.github.lichen.raybirdpush.core.SessionStatus

val Paper = Color(0xFFF6F7F2)
val Ink = Color(0xFF213D35)
val Quiet = Color(0xFF78837B)
val Lime = Color(0xFFDCEBA6)
val Mist = Color(0xFFEAF0E5)
val Warning = Color(0xFF9A682E)

@Composable fun PushTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = lightColorScheme(primary = Ink, onPrimary = Color.White,
        secondary = Lime, background = Paper, surface = Paper, onSurface = Ink,
        onSurfaceVariant = Quiet, error = Color(0xFFB64D47)), content = content)
}

@Composable
fun PushScreen(
    target: String, apk: SelectedApk?, busy: Boolean, preparing: Boolean,
    status: SessionStatus, onTv: () -> Unit, onApk: () -> Unit,
    onSend: () -> Unit, onCancel: () -> Unit, onStatus: () -> Unit
) {
    var entered by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { entered = true }
    val entrance by animateFloatAsState(if (entered) 1f else 0f, tween(420, easing = FastOutSlowInEasing), label = "entrance")
    val locked = busy || preparing
    val caption = statusCaption(status, preparing)
    val success = status.phase == Phase.INSTALLED && !locked
    val uncertain = status.phase in setOf(Phase.UNKNOWN, Phase.TIMED_OUT, Phase.CANCELLED)
    val failure = status.phase in setOf(Phase.FAILED, Phase.DENIED)
    val tone = if (failure) MaterialTheme.colorScheme.error else if (uncertain) Warning else Ink
    Box(Modifier.fillMaxSize().background(Paper).safeDrawingPadding(), contentAlignment = Alignment.Center) {
        Column(Modifier.widthIn(max = 460.dp).fillMaxWidth().verticalScroll(rememberScrollState())
            .padding(horizontal = 28.dp, vertical = 28.dp)
            .graphicsLayer { alpha = entrance; translationY = (1f - entrance) * 22.dp.toPx() },
            horizontalAlignment = Alignment.CenterHorizontally) {
            PressCard("选择电视", onTv, !locked, Modifier.fillMaxWidth()) {
                Box(Modifier.fillMaxWidth().height(216.dp)) {
                    Box(Modifier.align(Alignment.Center).offset(y = (-8).dp)) { Television() }
                    Box(Modifier.align(Alignment.TopEnd).padding(14.dp).size(36.dp)
                        .background(if (target.isBlank()) Mist else Color(0xFFF0F3EC), CircleShape), contentAlignment = Alignment.Center) {
                        Glyph(if (target.isBlank()) Mark.PLUS else Mark.EDIT, Modifier.size(17.dp), Quiet)
                    }
                    if (target.isNotBlank()) Text(target, Modifier.align(Alignment.BottomCenter).padding(bottom = 19.dp),
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp, letterSpacing = 1.sp), color = Quiet)
                }
            }
            TransferLink(locked)
            PressCard("选择 APK", onApk, !locked, Modifier.fillMaxWidth()) {
                Row(Modifier.fillMaxWidth().heightIn(min = 112.dp).padding(22.dp), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(17.dp)) {
                    val bitmap = apk?.icon
                    if (bitmap != null) Image(bitmap.asImageBitmap(), null, Modifier.size(54.dp).clip(RoundedCornerShape(15.dp)))
                    else Box(Modifier.size(54.dp).background(Mist, RoundedCornerShape(17.dp)), contentAlignment = Alignment.Center) {
                        Glyph(Mark.APK, Modifier.size(28.dp), Ink)
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        if (apk == null) {
                            // Quiet document lines are illustrative, not another title or description block.
                            Box(Modifier.width(76.dp).height(5.dp).background(Color(0xFFDDE4D8), CircleShape))
                            Box(Modifier.width(48.dp).height(4.dp).background(Color(0xFFEDF0E8), CircleShape))
                        } else {
                            Text(apk.label, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Medium), color = Ink)
                            Text("${apk.version.ifBlank { apk.code.toString() }} · ${fileSize(apk.bytes)}", maxLines = 1,
                                overflow = TextOverflow.Ellipsis, color = Quiet, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    Glyph(if (apk == null) Mark.PLUS else Mark.INFO, Modifier.size(20.dp), Quiet)
                }
            }
            Spacer(Modifier.height(34.dp))
            Box(Modifier.size(108.dp), contentAlignment = Alignment.Center) {
                if (locked) {
                    val progress = status.percent?.takeIf { it in 0..100 && status.phase in setOf(Phase.DOWNLOADING, Phase.INSTALLING) }
                    if (progress != null) CircularProgressIndicator(progress = { progress / 100f }, modifier = Modifier.fillMaxSize(),
                        color = Ink, trackColor = Color(0xFFE2E8DB), strokeWidth = 2.dp)
                    else CircularProgressIndicator(Modifier.fillMaxSize(), color = Ink, trackColor = Color(0xFFE2E8DB), strokeWidth = 2.dp)
                }
                val enabled = locked || apk != null && target.isNotBlank()
                val interactions = remember { MutableInteractionSource() }
                val pressed by interactions.collectIsPressedAsState()
                val buttonScale by animateFloatAsState(if (pressed) .92f else 1f,
                    spring(dampingRatio = .55f, stiffness = 450f), label = "send-press")
                val buttonColor by animateColorAsState(if (locked) Ink else if (success) Color(0xFFD3E7D7)
                    else if (enabled) Lime else Color(0xFFE4E9DF), tween(240), label = "send-color")
                FilledIconButton(onClick = if (locked) onCancel else onSend, enabled = enabled,
                    modifier = Modifier.size(84.dp).graphicsLayer { scaleX = buttonScale; scaleY = buttonScale }
                        .shadow(if (enabled) 14.dp else 0.dp, CircleShape, ambientColor = Ink.copy(alpha = .1f), spotColor = Ink.copy(alpha = .12f))
                        .semantics { contentDescription = if (preparing) "取消读取" else if (busy) "取消传输" else "推送" },
                    interactionSource = interactions, colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = buttonColor, contentColor = if (locked) Color.White else Ink,
                        disabledContainerColor = buttonColor, disabledContentColor = if (success) Ink else Quiet.copy(alpha = .5f))) {
                    AnimatedContent(if (locked) Mark.STOP else if (success) Mark.CHECK else Mark.SEND, label = "send-symbol") {
                        Glyph(it, Modifier.size(30.dp))
                    }
                }
            }
            Spacer(Modifier.height(18.dp))
            Box(Modifier.fillMaxWidth().heightIn(min = 58.dp), contentAlignment = Alignment.TopCenter) {
                AnimatedContent(caption, label = "session-caption") { text ->
                    Column(Modifier.animateContentSize(), horizontalAlignment = Alignment.CenterHorizontally) {
                        if (text.isNotEmpty()) Text(text,
                            Modifier.clip(RoundedCornerShape(12.dp)).clickable(enabled = !preparing && !busy, onClick = onStatus)
                                .padding(horizontal = 12.dp, vertical = 8.dp).semantics { liveRegion = LiveRegionMode.Polite },
                            color = tone, style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp))
                        if (status.phase == Phase.CANCELLED && !preparing) Text("电视上的安装可能仍在继续", color = Quiet,
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp))
                    }
                }
            }
        }
    }
}

@Composable private fun PressCard(description: String, onClick: () -> Unit, enabled: Boolean,
    modifier: Modifier, content: @Composable () -> Unit) {
    val interactions = remember { MutableInteractionSource() }
    val pressed by interactions.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) .975f else 1f, spring(dampingRatio = .65f, stiffness = 500f), label = "card-press")
    Surface(onClick = onClick, enabled = enabled, interactionSource = interactions,
        modifier = modifier.graphicsLayer { scaleX = scale; scaleY = scale }.semantics { contentDescription = description },
        shape = RoundedCornerShape(30.dp), color = Color.White, shadowElevation = 2.dp,
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE9EEE3))) { content() }
}

@Composable private fun TransferLink(active: Boolean) {
    val t = if (active) {
        val transition = rememberInfiniteTransition(label = "packet")
        transition.animateFloat(0f, 1f, infiniteRepeatable(tween(1300, easing = LinearEasing)), label = "packet-position").value
    } else 0f
    Canvas(Modifier.width(32.dp).height(46.dp)) {
        repeat(3) { index ->
            val p = if (active) ((index / 3f + t) % 1f) else (index + 1) / 4f
            drawCircle(if (active) Ink.copy(alpha = .15f + .4f * (1f - p)) else Color(0xFFD5DECF),
                if (active) 1.8.dp.toPx() else 1.5.dp.toPx(), Offset(size.width / 2, size.height * (1 - p)))
        }
    }
}

@Composable private fun Television() {
    Canvas(Modifier.size(206.dp, 137.dp)) {
        scale(size.width / 206f, size.height / 137f, pivot = Offset.Zero) {
            drawOval(Color(0xFFEEF2E8), Offset(16f, 113f), Size(174f, 10f))
            val outline = Path().apply { addRoundRect(androidx.compose.ui.geometry.RoundRect(15f, 10f, 191f, 111f, CornerRadius(12f))) }
            drawPath(outline, Ink)
            val screen = Path().apply { addRoundRect(androidx.compose.ui.geometry.RoundRect(19f, 14f, 187f, 105f, CornerRadius(9f))) }
            clipPath(screen) {
                drawRect(Brush.linearGradient(listOf(Color(0xFFDBE9DF), Color(0xFFF0F2DD)), Offset(20f, 20f), Offset(185f, 110f)))
                drawCircle(Color(0xFFC5D9C9), 84f, Offset(160f, 112f))
                drawCircle(Color(0xFFE7EED4), 64f, Offset(153f, 112f))
                val slope = Path().apply { moveTo(19f, 94f); cubicTo(62f, 47f, 92f, 56f, 139f, 105f); lineTo(19f, 105f); close() }
                drawPath(slope, Color(0xFF9DBFAF))
                drawCircle(Color.White.copy(alpha = .7f), 15f, Offset(63f, 39f))
            }
            drawLine(Ink, Offset(59f, 109f), Offset(51f, 120f), 3f, StrokeCap.Round)
            drawLine(Ink, Offset(147f, 109f), Offset(155f, 120f), 3f, StrokeCap.Round)
            drawCircle(Color(0xFFB5C6B6), 1f, Offset(103f, 108f))
        }
    }
}
