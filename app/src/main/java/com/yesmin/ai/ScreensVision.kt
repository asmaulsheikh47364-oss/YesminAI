@file:OptIn(ExperimentalMaterial3Api::class)

package com.yesmin.ai

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.io.ByteArrayOutputStream

object ImageUtil {
    fun toJpeg(b: Bitmap): ByteArray {
        val out = ByteArrayOutputStream()
        b.compress(Bitmap.CompressFormat.JPEG, 93, out)
        return out.toByteArray()
    }

    /** Reads an image from a content Uri and shrinks it so uploads stay small. */
    fun fromUri(ctx: Context, uri: Uri): ByteArray? = try {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (bounds.outWidth / sample > 2400 || bounds.outHeight / sample > 2400) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        val bmp = ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
        if (bmp == null) null else toJpeg(bmp)
    } catch (e: Exception) {
        null
    }
}

@Composable
fun VisionScreen(vm: AppViewModel) {
    val ctx = LocalContext.current
    var jpeg by remember { mutableStateOf<ByteArray?>(null) }
    var question by remember { mutableStateOf(VisionPrompts.Q) }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            val b = ImageUtil.fromUri(ctx, uri)
            if (b == null) vm.notice = "Photo padh nahi paayi." else jpeg = b
        }
    }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()) { bmp: Bitmap? ->
        if (bmp != null) jpeg = ImageUtil.toJpeg(bmp)
    }
    LaunchedEffect(Unit) { vm.visionOutput = "" }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("Photo AI", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text("Photo dikhao aur sawaal poochho: yeh kya hai, isme kya likha hai, ise kaise banate hain…", style = MaterialTheme.typography.bodySmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { gallery.launch("image/*") }) { Text("🖼 Gallery") }
            OutlinedButton(onClick = { camera.launch(null) }) { Text("📷 Camera") }
        }
        val bytes = jpeg
        if (bytes != null) {
            val bmp = remember(bytes) { BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap() }
            if (bmp != null) {
                Image(bitmap = bmp, contentDescription = "Chuni hui photo", modifier = Modifier.fillMaxWidth().heightIn(max = 260.dp))
            }
        }
        OutlinedTextField(value = question, onValueChange = { question = it }, label = { Text("Kya poochna hai?") }, minLines = 2, modifier = Modifier.fillMaxWidth())
        Button(onClick = { jpeg?.let { vm.askVision(it, question) } }, enabled = jpeg != null && !vm.toolBusy) {
            Text("Gemini se poochho")
        }
        if (vm.toolBusy) CircularProgressIndicator()
        if (vm.visionOutput.isNotBlank()) RichText(vm.visionOutput)
        Text("Chuni hui photo Google Gemini ko bheji jaati hai. Kuch phone par save nahi hota.", style = MaterialTheme.typography.bodySmall)
    }
}
