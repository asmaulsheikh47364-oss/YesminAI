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
    val img = ImageBitmap.imageResource(R.drawable.yesmin_hero)
    val ratio = img.width / (CROP_H * img.height)

    val inf = rememberInfiniteTransition(label = "hero")
    val spin by inf.animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(16000, easing = LinearEasing), RepeatMode.Restart), label = "spin"
    )
    val breathe by inf.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(4200, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "breathe"
    )
    val talk by inf.animateFloat(
        initialValue = 0.3f, targetValue = 0.9f,
        animationSpec = infiniteRepeatable(tween(450), RepeatMode.Reverse), label = "talk"
    )
    val raw = when (vm.voiceState) {
        VoiceState.Listening -> vm.micLevel
        VoiceState.Speaking -> talk
        VoiceState.Processing, VoiceState.Connecting -> 0.2f
        else -> 0.05f
    }
    val level by animateFloatAsState(raw.coerceIn(0f, 1f), tween(120), label = "lvl")
    val bg = MaterialTheme.colorScheme.background

    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            "Yesmin AI", style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary
        )
        Text("Your Personal Assistant", style = MaterialTheme.typography.bodyMedium)

        Box(Modifier.fillMaxWidth().aspectRatio(ratio).clipToBounds()) {
            Canvas(
                Modifier.fillMaxSize().graphicsLayer {
                    val s = 1f + 0.035f * breathe + 0.03f * level
                    scaleX = s
                    scaleY = s
                }
            ) {
                drawImage(
                    image = img,
                    srcOffset = IntOffset(0, (CROP_TOP * img.height).toInt()),
                    srcSize = IntSize(img.width, (CROP_H * img.height).toInt()),
                    dstSize = IntSize(size.width.toInt(), size.height.toInt()),
                    filterQuality = FilterQuality.Medium
                )
            }
            Canvas(Modifier.fillMaxSize()) {
                val c = Offset(size.width * RING_CX, size.height * RING_CY)
                val r = size.width * (RING_R - 0.015f + 0.02f * breathe + 0.025f * level)
                val tl = Offset(c.x - r, c.y - r)
                val sz = Size(r * 2f, r * 2f)
                val a = 0.35f + 0.55f * level
                val brush = Brush.sweepGradient(
                    0.00f to Color.Transparent,
                    0.18f to NeonCyan,
                    0.40f to NeonViolet,
                    0.55f to Color.Transparent,
                    0.75f to NeonPink,
                    0.90f to NeonViolet,
                    1.00f to Color.Transparent,
                    center = c
                )
                clipRect(0f, 0f, size.width, size.height * 0.80f) {
                    rotate(spin, c) {
                        drawArc(
                            brush, 0f, 360f, false, tl, sz, alpha = a * 0.35f,
                            style = Stroke(width = 26.dp.toPx(), cap = StrokeCap.Round), blendMode = BlendMode.Plus
                        )
                        drawArc(
                            brush, 0f, 360f, false, tl, sz, alpha = a,
                            style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round), blendMode = BlendMode.Plus
                        )
                    }
                }
            }
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(
                        0.00f to bg, 0.12f to Color.Transparent,
                        0.65f to Color.Transparent, 1.00f to bg
                    )
                )
            )
        }

        Column(
            Modifier
                .layout { m, c ->
                    val p = m.measure(c)
                    val up = 44.dp.roundToPx()
                    layout(p.width, p.height - up) { p.place(0, -up) }
                }
                .padding(horizontal = 4.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(26.dp))
                .background(Color(0xE60B1236))
                .border(1.5.dp, NeonBrush, RoundedCornerShape(26.dp))
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                greeting + "! \uD83D\uDE0A", fontWeight = FontWeight.Bold,
                fontSize = 20.sp, color = Color.White, textAlign = TextAlign.Center
            )
            Text(
                "I'm " + vm.activeProfile().name + ", your AI assistant. How can I help you today?",
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
                "\u25CF " + vm.statusLabel() + "  \u00B7  Gemini: " + vm.brainStatus.label(),
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
