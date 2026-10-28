package com.yesmin.ai

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

val GoldenYellow = Color(0xFFFFD700)
val GoldenAmber = Color(0xFFFFB300)
val GoldenLight = Color(0xFFFFF8E1)
val VipPurple = Color(0xFF4A148C)
val VipPink = Color(0xFFE91E63)

val GoldenBrush = Brush.linearGradient(
    colors = listOf(GoldenLight, GoldenYellow, GoldenAmber, GoldenYellow, GoldenLight)
)

val VipGradient = Brush.linearGradient(
    colors = listOf(VipPurple, VipPink, GoldenYellow, VipPurple)
)

@Composable
fun GoldenSparkleOverlay() {
    val inf = rememberInfiniteTransition(label = "sparkle")
    val shimmer by inf.animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(3000, easing = LinearEasing), RepeatMode.Restart),
        label = "shimmer"
    )

    Canvas(Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val rnd = Random(42)
        for (i in 0..18) {
            val cx = (rnd.nextFloat() * 0.9f + 0.05f) * w
            val cy = (rnd.nextFloat() * 0.9f + 0.05f) * h
            val alpha = (0.3f + 0.7f * kotlin.math.sin(Math.toRadians((shimmer + i * 20).toDouble())).toFloat()).coerceIn(0.1f, 1f)
            val radius = (2f + rnd.nextFloat() * 5f)
            drawCircle(color = GoldenYellow.copy(alpha = alpha), radius = radius, center = Offset(cx, cy))
            drawCircle(color = Color.White.copy(alpha = alpha * 0.8f), radius = radius * 0.4f, center = Offset(cx, cy))
        }
    }
}

@Composable
fun VipBadgeAnimation(text: String = "VIP GOLDEN EDITION") {
    val inf = rememberInfiniteTransition(label = "vip")
    val scaleAnim by inf.animateFloat(
        initialValue = 0.95f, targetValue = 1.05f,
        animationSpec = infiniteRepeatable(tween(1200, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "vipScale"
    );
    val rotateAnim by inf.animateFloat(
        initialValue = -3f, targetValue = 3f,
        animationSpec = infiniteRepeatable(tween(2000, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "vipRotate"
    )

    Box(
        modifier = Modifier
            .scale(scaleAnim)
            .rotate(rotateAnim)
            .clip(RoundedCornerShape(24.dp))
            .background(VipGradient)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("👑", fontSize = 20.sp)
            Text(
                text = text,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.labelLarge
            )
            Text("✨", fontSize = 20.sp)
        }
    }
}

@Composable
fun GoldenButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val inf = rememberInfiniteTransition(label = "goldBtn")
    val pulse by inf.animateFloat(
        initialValue = 1f, targetValue = 1.03f,
        animationSpec = infiniteRepeatable(tween(1000, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "pulse"
    )

    Button(
        onClick = onClick,
        modifier = modifier.scale(pulse),
        colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
        contentPadding = PaddingValues(0.dp)
    ) {
        Box(
            modifier = Modifier
                .background(GoldenBrush, RoundedCornerShape(12.dp))
                .padding(horizontal = 24.dp, vertical = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = text,
                color = Color(0xFF3E2723),
                fontWeight = FontWeight.Bold
            )
        }
    }
}
