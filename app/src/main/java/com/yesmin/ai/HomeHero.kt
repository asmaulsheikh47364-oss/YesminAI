package com.yesmin.ai

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

private val HomeBg = Color(0xFF1A191B)

private fun gauss(u: Float, v: Float, cu: Float, cv: Float, su: Float, sv: Float): Float {
    val a = (u - cu) / su
    val b = (v - cv) / sv
    return Math.exp((-0.5 * (a * a + b * b)).toDouble()).toFloat()
}

private fun smoothStep(e0: Float, e1: Float, x: Float): Float {
    val t = ((x - e0) / (e1 - e0)).coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}

/** 0 on the face, eyes, ears and neck; up to 1 on free hair. Smooth everywhere, so nothing tears. */
private fun hairWeight(u: Float, v: Float): Float {
    var w = 0f
    w += 0.9f * gauss(u, v, 0.50f, 0.095f, 0.075f, 0.075f)
    w += 0.45f * gauss(u, v, 0.50f, 0.22f, 0.24f, 0.07f)
    w += 0.8f * gauss(u, v, 0.27f, 0.40f, 0.08f, 0.16f)
    w += 1.0f * gauss(u, v, 0.23f, 0.72f, 0.07f, 0.10f)
    w += 0.8f * gauss(u, v, 0.77f, 0.42f, 0.08f, 0.17f)
    w += 1.0f * gauss(u, v, 0.78f, 0.72f, 0.07f, 0.10f)
    w += 0.3f * gauss(u, v, 0.50f, 0.33f, 0.20f, 0.06f)
    if (w > 1.2f) w = 1.2f
    val du = (u - 0.52f) / 0.19f
    val dv = (v - 0.62f) / 0.24f
    val face = smoothStep(0.80f, 1.20f, sqrt(du * du + dv * dv))
    val eu = (u - 0.29f) / 0.05f
    val ev = (v - 0.55f) / 0.07f
    val ear = smoothStep(0.7f, 1.3f, sqrt(eu * eu + ev * ev))
    return w * face * ear
}

private fun buildGrid(gx: Int, gy: Int): FloatArray {
    val out = FloatArray((gx + 1) * (gy + 1) * 3)
    for (j in 0..gy) {
        for (i in 0..gx) {
            val k = (j * (gx + 1) + i) * 3
            val u = i.toFloat() / gx
            val v = j.toFloat() / gy
            out[k] = u
            out[k + 1] = v
            out[k + 2] = hairWeight(u, v)
        }
    }
    return out
}

private fun rot3(x: Float, y: Float, z: Float, yaw: Float, pitch: Float, out: FloatArray) {
    val cy = cos(yaw)
    val sy = sin(yaw)
    val cp = cos(pitch)
    val sp = sin(pitch)
    val x1 = x * cy + z * sy
    val z1 = -x * sy + z * cy
    out[0] = x1
    out[1] = y * cp - z1 * sp
    out[2] = y * sp + z1 * cp
}

private class OrbState {
    var ang = 0f
    var hue = 170f
    var speed = 0.2f
    var level = 0f
}

/** Home: full-screen portrait where only the hair moves, with a 3D hacker-style orb as the voice button. */
@Composable
fun HomeHero(vm: AppViewModel, onSettings: () -> Unit, onMic: () -> Unit) {
    val img = ImageBitmap.imageResource(R.drawable.yesmin_portrait)
    val bmp = remember(img) { img.asAndroidBitmap() }
    val inf = rememberInfiniteTransition(label = "hair")
    val phase by inf.animateFloat(
        initialValue = 0f, targetValue = 6.2831855f,
        animationSpec = infiniteRepeatable(tween(12000, easing = LinearEasing), RepeatMode.Restart), label = "hairPhase"
    )
    val gx = 24
    val gy = 24
    val grid = remember { buildGrid(gx, gy) }
    val verts = remember { FloatArray((gx + 1) * (gy + 1) * 2) }
    val paint = remember { android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG or android.graphics.Paint.FILTER_BITMAP_FLAG) }

    Box(Modifier.fillMaxSize().background(HomeBg)) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val s = minOf(w * 1.18f, h * 0.80f)
            val ox = (w - s) / 2f
            val oy = h * 0.02f
            val amp = s * 0.011f
            val ph = phase
            for (j in 0..gy) {
                for (i in 0..gx) {
                    val k = j * (gx + 1) + i
                    val u = grid[k * 3]
                    val v = grid[k * 3 + 1]
                    val wt = grid[k * 3 + 2]
                    val a = ph + v * 4.2f + u * 2.6f
                    verts[k * 2] = ox + u * s + amp * wt * sin(a)
                    verts[k * 2 + 1] = oy + v * s + amp * 0.45f * wt * sin(a * 0.8f + 1.7f)
                }
            }
            drawContext.canvas.nativeCanvas.drawBitmapMesh(bmp, gx, gy, verts, 0, null, 0, paint)

            val y0 = oy + s * 0.66f
            drawRect(
                Brush.verticalGradient(listOf(Color.Transparent, HomeBg), startY = y0, endY = oy + s * 0.98f),
                Offset(0f, y0), Size(w, h - y0)
            )
            drawRect(
                Brush.verticalGradient(listOf(HomeBg, Color.Transparent), startY = 0f, endY = oy + s * 0.12f),
                Offset(0f, 0f), Size(w, oy + s * 0.12f)
            )
            val fw = s * 0.18f
            drawRect(Brush.horizontalGradient(listOf(HomeBg, Color.Transparent), startX = ox, endX = ox + fw), Offset(ox, 0f), Size(fw, h))
            drawRect(Brush.horizontalGradient(listOf(Color.Transparent, HomeBg), startX = ox + s - fw, endX = ox + s), Offset(ox + s - fw, 0f), Size(fw, h))
            if (ox > 0f) {
                drawRect(HomeBg, Offset(0f, 0f), Size(ox, h))
                drawRect(HomeBg, Offset(ox + s, 0f), Size(w - ox - s, h))
            }
        }

        Text(
            "⚙️", fontSize = 22.sp,
            modifier = Modifier.align(Alignment.TopEnd).padding(12.dp).clickable { onSettings() }.padding(6.dp)
        )

        HackOrb(vm, Modifier.align(Alignment.BottomCenter).padding(bottom = 30.dp).size(190.dp), onMic)

        val cap = if (!vm.sessionActive) "dabao aur bolo" else when (vm.voiceState) {
            VoiceState.Listening -> "sun rahi hoon"
            VoiceState.Speaking -> "bol rahi hoon"
            VoiceState.Processing, VoiceState.Connecting -> "soch rahi hoon"
            VoiceState.Paused -> "ruki hui"
            else -> "taiyaar"
        }
        Text(
            cap, fontFamily = FontFamily.Monospace, fontSize = 11.sp,
            color = Color(0xFF7DD3FC).copy(alpha = 0.8f),
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp)
        )
    }
}

@Composable
private fun HackOrb(vm: AppViewModel, modifier: Modifier, onClick: () -> Unit) {
    val st = remember { OrbState() }
    var frame by remember { mutableStateOf(0L) }
    LaunchedEffect(Unit) {
        var last = 0L
        while (true) {
            withFrameNanos { n ->
                if (last != 0L) {
                    val dt = ((n - last) / 1.0e9f).coerceAtMost(0.05f)
                    val talking = vm.sessionActive && (vm.voiceState == VoiceState.Speaking || vm.voiceState == VoiceState.Listening)
                    val lvl = when {
                        !talking -> 0f
                        vm.voiceState == VoiceState.Speaking -> VoiceLevel.out
                        else -> vm.micLevel
                    }.coerceIn(0f, 1f)
                    st.level += (lvl - st.level) * minOf(1f, dt * 8f)
                    val target = when {
                        !vm.sessionActive -> 0.16f
                        talking -> 1.7f + 3.2f * st.level
                        else -> 0.7f
                    }
                    st.speed += (target - st.speed) * minOf(1f, dt * 3f)
                    st.ang += dt * st.speed
                    val hs = when {
                        !vm.sessionActive -> 5f
                        talking -> 150f
                        else -> 45f
                    }
                    st.hue = (st.hue + dt * hs) % 360f
                }
                last = n
                frame = n
            }
        }
    }
    Canvas(modifier.pointerInput(Unit) { detectTapGestures { onClick() } }) {
        if (frame < 0L) return@Canvas
        val cx = size.width / 2f
        val cy = size.height / 2f
        val rr = size.minDimension * 0.36f * (1f + 0.07f * st.level)
        val f = rr * 3.4f
        val ang = st.ang
        val yaw = ang * 0.7f
        val pitch = 0.55f + 0.35f * sin(ang * 0.4f)
        val sw = 1.6.dp.toPx()
        val p1 = FloatArray(3)
        val p2 = FloatArray(3)
        val q1 = FloatArray(3)
        val q2 = FloatArray(3)

        // soft glow behind everything
        val glow = Color.hsv(st.hue, 0.7f, 1f, 1f)
        drawCircle(
            Brush.radialGradient(listOf(glow.copy(alpha = 0.30f + 0.25f * st.level), Color.Transparent), center = Offset(cx, cy), radius = rr * 1.7f),
            radius = rr * 1.7f, center = Offset(cx, cy)
        )

        // three tilted rings, dashed, each spinning on its own
        val tilts = floatArrayOf(0f, 1.05f, 2.1f)
        val radii = floatArrayOf(1.0f, 0.82f, 0.64f)
        val n = 72
        for (k in 0..2) {
            val r = rr * radii[k]
            val dir = if (k == 1) -1f else 1f
            val off = ang * (1.3f + 0.5f * k) * dir
            val col = k
            for (i in 0 until n) {
                if (i % 4 == 3) continue
                val t1 = off + i * 6.2831855f / n
                val t2 = off + (i + 1) * 6.2831855f / n
                rot3(r * cos(t1), r * sin(t1), 0f, 0f, tilts[k], p1)
                rot3(r * cos(t2), r * sin(t2), 0f, 0f, tilts[k], p2)
                rot3(p1[0], p1[1], p1[2], yaw, pitch, q1)
                rot3(p2[0], p2[1], p2[2], yaw, pitch, q2)
                val s1 = f / (f - q1[2])
                val s2 = f / (f - q2[2])
                val d = ((q1[2] / rr) + 1f) / 2f
                val c = Color.hsv((st.hue + col * 60f) % 360f, 0.75f, 1f, (0.18f + 0.82f * d.coerceIn(0f, 1f)))
                drawLine(c, Offset(cx + q1[0] * s1, cy + q1[1] * s1), Offset(cx + q2[0] * s2, cy + q2[1] * s2), strokeWidth = sw, cap = StrokeCap.Round)
            }
        }

        // data dots on a sphere
        val dots = 54
        for (i in 0 until dots) {
            val y = 1f - 2f * (i + 0.5f) / dots
            val rad = sqrt(1f - y * y)
            val th = i * 2.3999632f
            rot3(rad * cos(th) * rr * 1.08f, y * rr * 1.08f, rad * sin(th) * rr * 1.08f, ang * 1.2f, pitch * 0.8f, q1)
            val sc = f / (f - q1[2])
            val d = ((q1[2] / (rr * 1.08f)) + 1f) / 2f
            val c = Color.hsv((st.hue + i * 7f) % 360f, 0.65f, 1f, (0.15f + 0.85f * d.coerceIn(0f, 1f)))
            drawCircle(c, radius = (1.0.dp.toPx() + 2.2.dp.toPx() * d.coerceIn(0f, 1f)) * sc, center = Offset(cx + q1[0] * sc, cy + q1[1] * sc))
        }

        // flat scanner arcs
        val ar = rr * 1.2f
        val deg = ang * 57.29578f
        for (a in 0..1) {
            drawArc(
                Color.hsv((st.hue + 120f * a) % 360f, 0.8f, 1f, 0.9f),
                startAngle = deg * (2.2f + a) + a * 180f, sweepAngle = 62f, useCenter = false,
                topLeft = Offset(cx - ar, cy - ar), size = Size(ar * 2f, ar * 2f),
                style = Stroke(width = 2.2.dp.toPx(), cap = StrokeCap.Round)
            )
        }

        // pulsing core
        val core = rr * (0.20f + 0.10f * st.level + 0.02f * sin(ang * 6f))
        drawCircle(
            Brush.radialGradient(listOf(Color.White, glow, Color.Transparent), center = Offset(cx, cy), radius = core * 2.2f),
            radius = core * 2.2f, center = Offset(cx, cy)
        )
    }
}
