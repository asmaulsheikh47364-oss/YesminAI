package com.yesmin.ai

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Crop of the photo (fractions of the full image) and glow-ring position inside that crop.
private const val CROP_TOP = 0.195f
private const val CROP_H = 0.40f
private const val RING_CX = 0.50f
private const val RING_CY = 0.675f
private const val RING_R = 0.43f

@Composable
fun HomeHero(vm: AppViewModel, greeting: String, onMic: () -> Unit) {
    val img = ImageBitmap.imageResource(R.drawable.yesmin_portrait)
    val inf = rememberInfiniteTransition(label = "hero")
    val sway by inf.animateFloat(
        initialValue = -1f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(5200, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "sway"
    )
    val wind by inf.animateFloat(
        initialValue = -1f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(3100, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "wind"
    )
    val gazeX by inf.animateFloat(
        initialValue = -1f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2700, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "gx"
    )
    val gazeY by inf.animateFloat(
        initialValue = -1f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(4300, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "gy"
    )
    val talk by inf.animateFloat(
        initialValue = 0.2f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(230), RepeatMode.Reverse), label = "talk"
    )
    val spin by inf.animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(18000, easing = LinearEasing), RepeatMode.Restart), label = "spin"
    )
    val blink = androidx.compose.runtime.remember { androidx.compose.animation.core.Animatable(0f) }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(2000L + (Math.random() * 3500).toLong())
            blink.animateTo(1f, tween(80))
            blink.animateTo(0f, tween(120))
            if (Math.random() < 0.25) {
                kotlinx.coroutines.delay(130)
                blink.animateTo(1f, tween(80))
                blink.animateTo(0f, tween(120))
            }
        }
    }
    val speaking = vm.voiceState == VoiceState.Speaking
    val raw = when (vm.voiceState) {
        VoiceState.Listening -> vm.micLevel
        VoiceState.Speaking -> 0.7f
        VoiceState.Processing, VoiceState.Connecting -> 0.2f
        else -> 0.05f
    }
    val level by animateFloatAsState(raw.coerceIn(0f, 1f), tween(120), label = "lvl")
    val bg = MaterialTheme.colorScheme.background

    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .layout { m, c ->
                    val extra = 32.dp.roundToPx()
                    val p = m.measure(c.copy(minWidth = c.maxWidth + extra, maxWidth = c.maxWidth + extra))
                    layout(c.maxWidth, p.height) { p.place(-extra / 2, 0) }
                }
                .fillMaxWidth()
                .aspectRatio(1f)
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val w = size.width
                val iw = img.width
                val ih = img.height
                val skin = Color(0xFFE8B4A0)
                val lash = Color(0xFF1B1008)

                withTransform({
                    rotate(sway * 0.9f, Offset(w * 0.5f, w * 0.96f))
                    translate(sway * w * 0.004f, level * w * -0.004f)
                }) {
                    drawImage(img, dstSize = IntSize(w.toInt(), w.toInt()), filterQuality = FilterQuality.Medium)

                    // hair locks move a little in the air
                    val locks = listOf(floatArrayOf(0.20f, 0.42f, 0.20f, 0.38f), floatArrayOf(0.72f, 0.52f, 0.10f, 0.30f))
                    for ((i, l) in locks.withIndex()) {
                        drawImage(
                            img,
                            srcOffset = IntOffset((l[0] * iw).toInt(), (l[1] * ih).toInt()),
                            srcSize = IntSize((l[2] * iw).toInt(), (l[3] * ih).toInt()),
                            dstOffset = IntOffset((l[0] * w + wind * w * 0.007f * (if (i == 0) 1f else -1f)).toInt(), (l[1] * w).toInt()),
                            dstSize = IntSize((l[2] * w).toInt(), (l[3] * w).toInt()),
                            alpha = 0.55f, filterQuality = FilterQuality.Medium
                        )
                    }
                    // hair bun and clip sparkle sway
                    drawImage(
                        img,
                        srcOffset = IntOffset((0.34f * iw).toInt(), (0.03f * ih).toInt()),
                        srcSize = IntSize((0.24f * iw).toInt(), (0.16f * ih).toInt()),
                        dstOffset = IntOffset((0.34f * w + wind * w * 0.006f).toInt(), (0.03f * w).toInt()),
                        dstSize = IntSize((0.24f * w).toInt(), (0.16f * w).toInt()),
                        alpha = 0.5f, filterQuality = FilterQuality.Medium
                    )

                    // eyes: iris moves, eyelids blink
                    val eyes = listOf(0.443f to 0.656f, 0.648f to 0.656f)
                    val rx = 0.072f
                    val ry = 0.046f
                    for (e in eyes) {
                        val left = (e.first - rx) * w
                        val top = (e.second - ry) * w
                        val path = Path()
                        path.addOval(androidx.compose.ui.geometry.Rect(left, top, left + 2 * rx * w, top + 2 * ry * w))
                        clipPath(path) {
                            drawImage(
                                img,
                                srcOffset = IntOffset(((e.first - rx) * iw).toInt(), ((e.second - ry) * ih).toInt()),
                                srcSize = IntSize((2 * rx * iw).toInt(), (2 * ry * ih).toInt()),
                                dstOffset = IntOffset((left + gazeX * w * 0.010f).toInt(), (top + gazeY * w * 0.006f).toInt()),
                                dstSize = IntSize((2 * rx * w).toInt(), (2 * ry * w).toInt()),
                                filterQuality = FilterQuality.Medium
                            )
                            val b = blink.value
                            if (b > 0.02f) {
                                drawRect(skin, Offset(left, top), Size(2 * rx * w, 2 * ry * w * b))
                                val ly = top + 2 * ry * w * b
                                drawLine(lash, Offset(left + rx * w * 0.15f, ly), Offset(left + rx * w * 1.85f, ly), strokeWidth = 3.dp.toPx(), cap = StrokeCap.Round)
                            }
                        }
                    }

                    // mouth follows her voice
                    val mo = if (speaking) ((0.12f + 2.6f * VoiceLevel.out) * (0.6f + 0.4f * talk)).coerceIn(0f, 1f) else 0f
                    if (mo > 0.06f) {
                        val mw = w * (0.040f + 0.016f * mo)
                        val mh = w * 0.030f * mo
                        val cx = w * 0.544f
                        val cy = w * 0.868f
                        drawOval(Color(0xFF4A1818), Offset(cx - mw, cy - mh * 0.5f), Size(mw * 2f, mh))
                        drawOval(Color(0xFFB5605A), Offset(cx - mw, cy - mh * 0.5f), Size(mw * 2f, mh), style = Stroke(width = 2.dp.toPx()))
                    }
                }

                // soft golden ring around the head
                val c = Offset(w * 0.5f, w * 0.47f)
                val rr = w * (0.44f + 0.02f * level)
                val brush = Brush.sweepGradient(
                    0.00f to Color.Transparent, 0.25f to Color(0xFFFFD166), 0.50f to Color.Transparent,
                    0.75f to Color(0xFFFFB347), 1.00f to Color.Transparent, center = c
                )
                rotate(spin, c) {
                    drawArc(
                        brush, 0f, 360f, false, Offset(c.x - rr, c.y - rr), Size(rr * 2f, rr * 2f),
                        alpha = 0.10f + 0.40f * level, style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round), blendMode = BlendMode.Plus
                    )
                }
            }
            // soft fade on every edge so the picture melts into the screen
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(0.00f to bg, 0.14f to Color.Transparent, 0.62f to Color.Transparent, 1.00f to bg)
                )
            )
            Box(
                Modifier.fillMaxSize().background(
                    Brush.horizontalGradient(0.00f to bg, 0.16f to Color.Transparent, 0.84f to Color.Transparent, 1.00f to bg)
                )
            )
        }

        Column(
            Modifier
                .layout { m, c ->
                    val p = m.measure(c)
                    val up = 64.dp.roundToPx()
                    layout(p.width, p.height - up) { p.place(0, -up) }
                }
                .padding(horizontal = 4.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(26.dp))
                .background(Color(0xB30B1236))
                .border(1.dp, Color(0x55FFFFFF), RoundedCornerShape(26.dp))
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                greeting + "! 😊", fontWeight = FontWeight.Bold,
                fontSize = 20.sp, color = Color.White, textAlign = TextAlign.Center
            )
            Text(
                "I'm " + vm.activeProfile().name + ". How can I help you today?",
                style = MaterialTheme.typography.bodyMedium, color = Color.White, textAlign = TextAlign.Center
            )
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                MiniWave(level, 0f, Modifier.weight(1f))
                MicOrb(level, onMic)
                MiniWave(level, 1.7f, Modifier.weight(1f))
            }
            Text(
                "● " + vm.statusLabel() + "  ·  Gemini: " + vm.brainStatus.label(),
                style = MaterialTheme.typography.labelMedium, color = Color(0xFFB6C2E2)
            )
        }
    }
}
@Composable
private fun MiniWave(level: Float, shift: Float, modifier: Modifier) {
    val t by rememberInfiniteTransition(label = "wave").animateFloat(
        initialValue = 0f, targetValue = 6.2831855f,
        animationSpec = infiniteRepeatable(tween(1400, easing = LinearEasing), RepeatMode.Restart), label = "waveT"
    )
    Canvas(modifier.height(52.dp)) {
        val n = 13
        val step = size.width / n
        val base = if (shift == 0f) NeonCyan else NeonPink
        for (i in 0 until n) {
            val k = if (shift == 0f) i / (n - 1f) else 1f - i / (n - 1f)
            val wave = 0.5f + 0.5f * kotlin.math.sin(t + i * 0.7f + shift)
            val h = (0.10f + 0.90f * level * wave) * size.height
            val x = step * (i + 0.5f)
            drawLine(
                color = base.copy(alpha = 0.25f + 0.75f * k),
                start = Offset(x, (size.height - h) / 2f),
                end = Offset(x, (size.height + h) / 2f),
                strokeWidth = 3.dp.toPx(),
                cap = StrokeCap.Round
            )
        }
    }
}

@Composable
private fun MicOrb(level: Float, onMic: () -> Unit) {
    val glow = 1f + 0.22f * level
    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(96.dp)) {
        Box(
            Modifier.size(80.dp)
                .graphicsLayer { scaleX = glow; scaleY = glow; alpha = 0.30f + 0.55f * level }
                .clip(CircleShape).background(NeonBrush)
        )
        Box(
            Modifier.size(70.dp).clip(CircleShape).background(NeonBrush).clickable { onMic() },
            contentAlignment = Alignment.Center
        ) { Text("\uD83C\uDFA4", fontSize = 30.sp) }
    }
}
