package com.yesmin.ai

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private fun camSig(b: Bitmap): IntArray {
    val s = Bitmap.createScaledBitmap(b, 8, 8, true)
    val a = IntArray(64)
    for (i in 0 until 64) {
        val p = s.getPixel(i % 8, i / 8)
        a[i] = (android.graphics.Color.red(p) * 3 + android.graphics.Color.green(p) * 6 + android.graphics.Color.blue(p)) / 10
    }
    return a
}

private fun camDiff(a: IntArray, b: IntArray): Int {
    var t = 0
    for (i in 0 until 64) t += Math.abs(a[i] - b[i])
    return t / 64
}

private fun camJpeg(b: Bitmap): ByteArray {
    val w = b.width
    val h = b.height
    val k = 1280f / maxOf(w, h)
    val s = if (k < 1f) Bitmap.createScaledBitmap(b, (w * k).toInt(), (h * k).toInt(), true) else b
    return ImageUtil.toJpeg(s)
}

/** Live camera. When she sees something new and it stays steady, she explains it by herself. */
@Composable
fun CameraScreen(vm: AppViewModel) {
    val ctx = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    var granted by remember { mutableStateOf(ContextCompat.checkSelfPermission(ctx, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    LaunchedEffect(Unit) { if (!granted) launcher.launch(Manifest.permission.CAMERA) }

    if (!granted) {
        Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Camera ki permission chahiye.")
            Button(onClick = { launcher.launch(Manifest.permission.CAMERA) }) { Text("Allow karo") }
        }
        return
    }

    val previewView = remember {
        PreviewView(ctx).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }
    LaunchedEffect(granted) {
        val fut = ProcessCameraProvider.getInstance(ctx)
        fut.addListener({
            try {
                val p = fut.get()
                val prev = Preview.Builder().build()
                prev.setSurfaceProvider(previewView.surfaceProvider)
                p.unbindAll()
                p.bindToLifecycle(owner, CameraSelector.DEFAULT_BACK_CAMERA, prev)
            } catch (e: Exception) {
                vm.notice = "Camera shuru nahi ho paaya."
            }
        }, ContextCompat.getMainExecutor(ctx))
    }
    DisposableEffect(Unit) {
        onDispose { try { ProcessCameraProvider.getInstance(ctx).get().unbindAll() } catch (e: Exception) {} }
    }

    var text by remember { mutableStateOf("Camera ko kisi cheez par rakho. Main khud bata dungi ki ye kya hai.") }
    var auto by remember { mutableStateOf(true) }
    var working by remember { mutableStateOf(false) }
    var lastSig by remember { mutableStateOf<IntArray?>(null) }

    suspend fun look(bmp: Bitmap) {
        working = true
        val r = vm.describeFrame(camJpeg(bmp))
        text = r
        working = false
        if (!vm.sessionActive) vm.speakText(r)
    }

    LaunchedEffect(auto) {
        while (auto) {
            delay(2500)
            if (working || vm.toolBusy) continue
            val b1 = previewView.bitmap ?: continue
            val s1 = camSig(b1)
            val prev = lastSig
            if (prev != null && camDiff(prev, s1) < 16) continue
            delay(1200)
            val b2 = previewView.bitmap ?: continue
            val s2 = camSig(b2)
            if (camDiff(s1, s2) > 12) continue
            lastSig = s2
            look(b2)
        }
    }

    val inf = rememberInfiniteTransition(label = "cam")
    val spin by inf.animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(if (working) 1500 else 6000, easing = LinearEasing), RepeatMode.Restart), label = "spin"
    )

    Box(Modifier.fillMaxSize()) {
        AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
        Canvas(Modifier.fillMaxSize()) {
            val c = Offset(size.width / 2f, size.height * 0.40f)
            val r = size.width * 0.34f
            val tl = Offset(c.x - r, c.y - r)
            val sz = Size(r * 2f, r * 2f)
            val brush = Brush.sweepGradient(
                0.00f to Color.Transparent,
                0.20f to Color(0xFF22D3EE),
                0.45f to Color(0xFF8B5CF6),
                0.60f to Color.Transparent,
                0.80f to Color(0xFFF472B6),
                1.00f to Color.Transparent,
                center = c
            )
            rotate(spin, c) {
                drawArc(brush, 0f, 360f, false, tl, sz, alpha = 0.30f, style = Stroke(width = 22.dp.toPx(), cap = StrokeCap.Round))
                drawArc(brush, 0f, 360f, false, tl, sz, alpha = 0.95f, style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round))
            }
            rotate(-spin * 0.6f, c) {
                drawArc(brush, 0f, 360f, false, Offset(c.x - r * 0.86f, c.y - r * 0.86f), Size(r * 1.72f, r * 1.72f), alpha = 0.55f, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round))
            }
        }
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Surface(shape = RoundedCornerShape(22.dp), color = Color(0xCC0B1026), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp).verticalScroll(rememberScrollState())) {
                    Text(if (working) "Dekh rahi hoon…" else "Payal ne dekha", color = Color(0xFF22D3EE), style = androidx.compose.material3.MaterialTheme.typography.labelMedium)
                    Text(text, color = Color.White)
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(
                    onClick = { auto = !auto },
                    shape = RoundedCornerShape(16.dp), modifier = Modifier.weight(1f)
                ) { Text(if (auto) "Auto: ON" else "Auto: OFF") }
                Button(
                    onClick = { val b = previewView.bitmap; if (b != null) scope.launch { look(b) } },
                    shape = RoundedCornerShape(16.dp), modifier = Modifier.weight(1f)
                ) { Text("Samjhao") }
                FilledTonalButton(
                    onClick = {
                        val b = previewView.bitmap
                        if (b != null) {
                            vm.sendImageToChat(camJpeg(b), VisionPrompts.Q)
                            vm.notice = "Photo chat mein bhej di."
                        }
                    },
                    shape = RoundedCornerShape(16.dp), modifier = Modifier.weight(1f)
                ) { Text("Photo lo") }
            }
        }
    }
}
