package com.yesmin.ai

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Creator photo in a circle with a thin light that keeps travelling around it. The photo itself stays still. */
@Composable
fun CreatorPhoto(box: Dp) {
    val inf = rememberInfiniteTransition(label = "creatorRing")
    val a by inf.animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(4200, easing = LinearEasing), RepeatMode.Restart), label = "ringA"
    )
    Box(Modifier.size(box), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val c = Offset(size.width / 2f, size.height / 2f)
            val r = size.minDimension / 2f - 2.dp.toPx()
            drawCircle(Color(0x2638BDF8), radius = r, center = c, style = Stroke(1.dp.toPx()))
            rotate(a, c) {
                drawCircle(
                    Brush.sweepGradient(
                        0f to Color.Transparent, 0.55f to Color.Transparent, 0.88f to Color(0xFF22D3EE),
                        0.98f to Color(0xFFC4B5FD), 1f to Color.Transparent, center = c
                    ),
                    radius = r, center = c, style = Stroke(2.2.dp.toPx(), cap = StrokeCap.Round)
                )
            }
            rotate(-a * 1.6f, c) {
                drawCircle(
                    Brush.sweepGradient(
                        0f to Color.Transparent, 0.75f to Color.Transparent, 0.95f to Color(0xFFF472B6), 1f to Color.Transparent, center = c
                    ),
                    radius = r - 6.dp.toPx(), center = c, style = Stroke(1.2.dp.toPx(), cap = StrokeCap.Round)
                )
            }
        }
        Image(
            painterResource(R.drawable.creator), contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(box - 22.dp).clip(CircleShape)
        )
    }
}
