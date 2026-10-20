package com.yesmin.ai

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp
import androidx.compose.animation.core.animateFloat

/** Animated light around the four screen edges, drawn only while a voice session is active. */
@Composable
fun EdgeGlow(vm: AppViewModel) {
    if (!vm.sessionActive) return
    EdgeGlowInner(vm.voiceState)
}

@Composable
private fun EdgeGlowInner(state: VoiceState) {
    val transition = rememberInfiniteTransition(label = "edge")
    val phase = transition.animateFloat(
        0f, 1f, infiniteRepeatable(tween(5000, easing = LinearEasing), RepeatMode.Restart), label = "edgePhase"
    )
    val target = when (state) {
        VoiceState.Listening -> Color(0xFF3B82F6)
        VoiceState.Processing -> Color(0xFFF59E0B)
        VoiceState.Speaking -> Color(0xFFEC4899)
        VoiceState.Paused -> Color(0xFF64748B)
        VoiceState.Error -> Color(0xFFEF4444)
        else -> Color(0xFF3B82F6)
    }
    val base by animateColorAsState(target, label = "edgeColor")
    val alpha by animateFloatAsState(if (state == VoiceState.Paused) 0.25f else 0.95f, label = "edgeAlpha")

    Canvas(Modifier.fillMaxSize()) {
        val cycle = listOf(base, NeonViolet, NeonCyan, NeonPink, Color(0xFF22C55E))
        val n = cycle.size
        val shift = phase.value * n
        val i = shift.toInt()
        val frac = shift - i
        val colors = (0..n).map { k -> lerp(cycle[(k + i) % n], cycle[(k + i + 1) % n], frac) }
        val brush = Brush.sweepGradient(colors, center)
        val w = 5.dp.toPx()
        val r = 38.dp.toPx()
        val topLeft = Offset(w / 2, w / 2)
        val box = Size(size.width - w, size.height - w)
        drawRoundRect(brush = brush, topLeft = topLeft, size = box, cornerRadius = CornerRadius(r, r), alpha = alpha * 0.28f, style = Stroke(width = w * 3.2f))
        drawRoundRect(brush = brush, topLeft = topLeft, size = box, cornerRadius = CornerRadius(r, r), alpha = alpha, style = Stroke(width = w))
    }
}
