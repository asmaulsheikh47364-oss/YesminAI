package com.yesmin.ai

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

enum class PI { Plus, Up, Stop, Mic, Copy, Speaker, More, Download, Share }

/** Small clean line icons drawn by hand, so every button looks the same. */
@Composable
fun PIcon(kind: PI, tint: Color, box: Dp = 20.dp) {
    Canvas(Modifier.size(box)) {
        val w = box.toPx()
        val h = w
        val sw = w * 0.10f
        val st = Stroke(width = sw, cap = StrokeCap.Round, join = StrokeJoin.Round)
        when (kind) {
            PI.Plus -> {
                drawLine(tint, Offset(w * 0.5f, h * 0.2f), Offset(w * 0.5f, h * 0.8f), sw, StrokeCap.Round)
                drawLine(tint, Offset(w * 0.2f, h * 0.5f), Offset(w * 0.8f, h * 0.5f), sw, StrokeCap.Round)
            }
            PI.Up -> {
                drawLine(tint, Offset(w * 0.5f, h * 0.84f), Offset(w * 0.5f, h * 0.18f), sw * 1.1f, StrokeCap.Round)
                drawLine(tint, Offset(w * 0.5f, h * 0.18f), Offset(w * 0.2f, h * 0.48f), sw * 1.1f, StrokeCap.Round)
                drawLine(tint, Offset(w * 0.5f, h * 0.18f), Offset(w * 0.8f, h * 0.48f), sw * 1.1f, StrokeCap.Round)
            }
            PI.Stop -> drawRoundRect(tint, Offset(w * 0.24f, h * 0.24f), Size(w * 0.52f, h * 0.52f), CornerRadius(w * 0.1f))
            PI.Mic -> {
                drawRoundRect(tint, Offset(w * 0.37f, h * 0.08f), Size(w * 0.26f, h * 0.46f), CornerRadius(w * 0.13f))
                drawArc(tint, 0f, 180f, false, Offset(w * 0.24f, h * 0.22f), Size(w * 0.52f, h * 0.5f), style = st)
                drawLine(tint, Offset(w * 0.5f, h * 0.72f), Offset(w * 0.5f, h * 0.88f), sw, StrokeCap.Round)
                drawLine(tint, Offset(w * 0.34f, h * 0.9f), Offset(w * 0.66f, h * 0.9f), sw, StrokeCap.Round)
            }
            PI.Copy -> {
                drawRoundRect(tint, Offset(w * 0.34f, h * 0.12f), Size(w * 0.5f, h * 0.54f), CornerRadius(w * 0.08f), style = st)
                drawRoundRect(tint, Offset(w * 0.16f, h * 0.32f), Size(w * 0.5f, h * 0.56f), CornerRadius(w * 0.08f), style = st)
            }
            PI.Speaker -> {
                val p = Path()
                p.moveTo(w * 0.1f, h * 0.4f)
                p.lineTo(w * 0.3f, h * 0.4f)
                p.lineTo(w * 0.5f, h * 0.2f)
                p.lineTo(w * 0.5f, h * 0.8f)
                p.lineTo(w * 0.3f, h * 0.6f)
                p.lineTo(w * 0.1f, h * 0.6f)
                p.close()
                drawPath(p, tint)
                drawArc(tint, -45f, 90f, false, Offset(w * 0.34f, h * 0.32f), Size(w * 0.36f, h * 0.36f), style = st)
                drawArc(tint, -50f, 100f, false, Offset(w * 0.3f, h * 0.17f), Size(w * 0.58f, h * 0.66f), style = st)
            }
            PI.More -> {
                drawCircle(tint, w * 0.09f, Offset(w * 0.5f, h * 0.2f))
                drawCircle(tint, w * 0.09f, Offset(w * 0.5f, h * 0.5f))
                drawCircle(tint, w * 0.09f, Offset(w * 0.5f, h * 0.8f))
            }
            PI.Download -> {
                drawLine(tint, Offset(w * 0.5f, h * 0.12f), Offset(w * 0.5f, h * 0.62f), sw, StrokeCap.Round)
                drawLine(tint, Offset(w * 0.5f, h * 0.62f), Offset(w * 0.3f, h * 0.42f), sw, StrokeCap.Round)
                drawLine(tint, Offset(w * 0.5f, h * 0.62f), Offset(w * 0.7f, h * 0.42f), sw, StrokeCap.Round)
                drawLine(tint, Offset(w * 0.2f, h * 0.82f), Offset(w * 0.8f, h * 0.82f), sw, StrokeCap.Round)
            }
            PI.Share -> {
                drawLine(tint, Offset(w * 0.25f, h * 0.5f), Offset(w * 0.75f, h * 0.22f), sw * 0.8f, StrokeCap.Round)
                drawLine(tint, Offset(w * 0.25f, h * 0.5f), Offset(w * 0.75f, h * 0.78f), sw * 0.8f, StrokeCap.Round)
                drawCircle(tint, w * 0.12f, Offset(w * 0.25f, h * 0.5f))
                drawCircle(tint, w * 0.12f, Offset(w * 0.75f, h * 0.22f))
                drawCircle(tint, w * 0.12f, Offset(w * 0.75f, h * 0.78f))
            }
        }
    }
}

/** Round button with an icon inside. */
@Composable
fun RoundIconButton(kind: PI, bg: Color, tint: Color, onClick: () -> Unit, size: Dp = 40.dp, icon: Dp = 20.dp) {
    Box(
        Modifier.size(size).clip(CircleShape).background(bg).clickable { onClick() },
        contentAlignment = Alignment.Center
    ) { PIcon(kind, tint, icon) }
}
