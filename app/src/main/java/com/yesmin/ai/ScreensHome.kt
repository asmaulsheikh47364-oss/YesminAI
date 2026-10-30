@file:OptIn(ExperimentalMaterial3Api::class)

package com.yesmin.ai
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle

import com.yesmin.ai.agent.TaskOrchestrator

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.navigation.NavHostController
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.height
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer


// ---------- shared widgets ----------

@Composable
fun RichText(text: String, onSave: ((String) -> Unit)? = null) {
    val parts = text.split("```")
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        parts.forEachIndexed { i, p ->
            if (i % 2 == 0) {
                if (p.isNotBlank()) MarkdownText(p.trim())
            } else {
                CodeBlock(p, onSave)
            }
        }
    }
}

private fun styleFor(spec: String): SpanStyle {
    var color = Color.Unspecified
    var fam: FontFamily? = null
    var weight: FontWeight? = null
    var ital: androidx.compose.ui.text.font.FontStyle? = null
    var deco: androidx.compose.ui.text.style.TextDecoration? = null
    var size = androidx.compose.ui.unit.TextUnit.Unspecified
    var glow = false
    for (raw in spec.split(",")) {
        when (raw.trim().lowercase()) {
            "serif" -> fam = FontFamily.Serif
            "sans" -> fam = FontFamily.SansSerif
            "mono" -> fam = FontFamily.Monospace
            "cursive" -> fam = FontFamily.Cursive
            "bold" -> weight = FontWeight.Bold
            "italic" -> ital = androidx.compose.ui.text.font.FontStyle.Italic
            "underline" -> deco = androidx.compose.ui.text.style.TextDecoration.Underline
            "strike" -> deco = androidx.compose.ui.text.style.TextDecoration.LineThrough
            "big" -> size = 22.sp
            "huge" -> size = 30.sp
            "small" -> size = 12.sp
            "glow" -> glow = true
            "red" -> color = Color(0xFFEF4444)
            "green" -> color = Color(0xFF22C55E)
            "blue" -> color = Color(0xFF3B82F6)
            "gold" -> color = Color(0xFFFBBF24)
            "pink" -> color = Color(0xFFF472B6)
            "purple" -> color = Color(0xFFA78BFA)
            "cyan" -> color = Color(0xFF22D3EE)
            "orange" -> color = Color(0xFFFB923C)
        }
    }
    val sh = if (glow) androidx.compose.ui.graphics.Shadow(
        if (color == Color.Unspecified) Color.White else color,
        androidx.compose.ui.geometry.Offset.Zero, 18f
    ) else null
    return SpanStyle(color = color, fontSize = size, fontWeight = weight, fontStyle = ital, fontFamily = fam, textDecoration = deco, shadow = sh)
}

private fun AnnotatedString.Builder.appendBold(s: String) {
    var i = 0
    while (i < s.length) {
        val a = s.indexOf("**", i)
        val b = if (a >= 0) s.indexOf("**", a + 2) else -1
        if (a < 0 || b < 0 || b == a + 2) {
            append(s.substring(i))
            return
        }
        append(s.substring(i, a))
        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(s.substring(a + 2, b)) }
        i = b + 2
    }
}

private fun AnnotatedString.Builder.appendRich(s: String) {
    var i = 0
    while (i < s.length) {
        val a = s.indexOf("{{", i)
        val b = if (a >= 0) s.indexOf("}}", a + 2) else -1
        val bar = if (b > 0) s.indexOf('|', a + 2) else -1
        if (a < 0 || b < 0 || bar < 0 || bar > b) {
            appendBold(s.substring(i))
            return
        }
        appendBold(s.substring(i, a))
        withStyle(styleFor(s.substring(a + 2, bar))) { appendBold(s.substring(bar + 1, b)) }
        i = b + 2
    }
}

/** Bold with **stars** and styled text with {{font,colour,size|text}}. */
fun boldSpan(s: String): AnnotatedString = buildAnnotatedString { appendRich(s) }
@Composable
fun MarkdownText(text: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        text.lines().forEach { line ->
            val t = line.trim()
            when {
                t.startsWith("### ") -> Text(boldSpan(t.removePrefix("### ")), fontWeight = FontWeight.Bold, fontSize = 17.sp)
                t.startsWith("## ") -> Text(boldSpan(t.removePrefix("## ")), fontWeight = FontWeight.Bold, fontSize = 19.sp)
                t.startsWith("# ") -> Text(boldSpan(t.removePrefix("# ")), fontWeight = FontWeight.Bold, fontSize = 21.sp)
                t.startsWith("> ") -> Text(boldSpan(t.removePrefix("> ")), color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 8.dp))
                t.startsWith("* ") || t.startsWith("- ") || t.startsWith("• ") -> Text("• " + boldSpan(t.drop(2)), modifier = Modifier.padding(start = 8.dp))
                else -> Text(boldSpan(t))
            }
        }
    }
}

@Composable
fun CodeBlock(raw: String, onSave: ((String) -> Unit)?) {
    val nl = raw.indexOf('\n')
    val first = if (nl >= 0) raw.substring(0, nl).trim() else ""
    val hasLang = nl >= 0 && first.length <= 15 && !first.contains(' ')
    val lang = if (hasLang) first.ifBlank { "code" } else "code"
    val code = (if (hasLang) raw.substring(nl + 1) else raw).trimEnd()
    val clip = LocalClipboardManager.current
    Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(12.dp)) {
        Column(Modifier.padding(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(lang, style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
                TextButton(onClick = { clip.setText(AnnotatedString(code)) }) { Text("Copy") }
                if (onSave != null) TextButton(onClick = { onSave(code) }) { Text("Save") }
            }
            Text(
                code, fontFamily = FontFamily.Monospace, fontSize = 13.sp,
                modifier = Modifier.horizontalScroll(rememberScrollState())
            )
        }
    }
}

@Composable
fun GeneratedImage(path: String) {
    val ctx = LocalContext.current
    val bmp = remember(path) { try { android.graphics.BitmapFactory.decodeFile(path)?.asImageBitmap() } catch (e: Exception) { null } }
    var tools by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    if (bmp == null) {
        Text("Photo khul nahi payi", color = MaterialTheme.colorScheme.error)
        return
    }
    val ink = MaterialTheme.colorScheme.onSurface
    Box(Modifier.padding(top = 8.dp).widthIn(max = 340.dp).clip(RoundedCornerShape(18.dp)).clickable { tools = !tools }) {
        androidx.compose.foundation.Image(
            bitmap = bmp, contentDescription = null,
            contentScale = androidx.compose.ui.layout.ContentScale.FillWidth,
            modifier = Modifier.fillMaxWidth()
        )
        if (tools) {
            Box(Modifier.align(Alignment.TopEnd).padding(8.dp)) {
                RoundIconButton(PI.More, Color(0xB3000000), Color.White, { menu = true }, 38.dp, 20.dp)
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(
                        text = { Text("Download") }, leadingIcon = { PIcon(PI.Download, ink) },
                        onClick = { menu = false; tools = false; ImageStore.download(ctx, path) }
                    )
                    DropdownMenuItem(
                        text = { Text("Share") }, leadingIcon = { PIcon(PI.Share, ink) },
                        onClick = { menu = false; tools = false; ImageStore.share(ctx, path) }
                    )
                }
            }
        }
    }
}

@Composable
fun Bubble(m: ChatMsg, vm: AppViewModel) {
    val clipboard = LocalClipboardManager.current
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    if (m.fromUser) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(22.dp),
                modifier = Modifier.widthIn(max = 300.dp)
            ) {
                Text(m.text, Modifier.padding(horizontal = 16.dp, vertical = 11.dp))
            }
        }
    } else {
        Column(Modifier.fillMaxWidth().padding(end = 6.dp)) {
            Text("✦ Payal", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelLarge)
            if (m.imagePath != null) GeneratedImage(m.imagePath)
            if (m.text.isNotBlank()) {
                Column(Modifier.padding(top = 6.dp)) {
                    if (m.error) Text(m.text, color = MaterialTheme.colorScheme.error) else RichText(m.text, onSave = { vm.addMemory("snippet", it) })
                }
                Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    RoundIconButton(PI.Copy, Color.Transparent, muted, { clipboard.setText(AnnotatedString(m.text)) }, 34.dp, 18.dp)
                    if (!m.error) RoundIconButton(PI.Speaker, Color.Transparent, muted, { vm.speakText(m.text) }, 34.dp, 18.dp)
                }
            }
        }
    }
}
// ---------- Home ----------

@Composable
fun HomeScreen(vm: AppViewModel, nav: NavHostController) {
    val ctx = LocalContext.current
    val micLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) vm.startSession() else vm.voiceDenied()
    }
    fun onMic() {
        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            if (vm.sessionActive) vm.stopSession() else vm.startSession()
        } else {
            micLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }
    Box(Modifier.fillMaxSize()) {
        HomeHero(vm, { nav.navigate("settings") { launchSingleTop = true } }) { onMic() }
        PermissionSetup()
    }
}
// ---------- Chat ----------

@Composable
fun ChatScreen(vm: AppViewModel) {
    val ctx = LocalContext.current
    var input by remember { mutableStateOf("") }
    var showAttach by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    val fileLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val name = ctx.contentResolver.query(uri, null, null, null, null)?.use { c ->
                val i = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (i >= 0 && c.moveToFirst()) c.getString(i) else "file"
            } ?: "file"
            val text = ctx.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } ?: ""
            vm.sendFileToChat(name, text)
        }
    }
    val imageLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            val b = ImageUtil.fromUri(ctx, uri)
            if (b != null) vm.sendImageToChat(b, VisionPrompts.Q)
        }
    }
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()) { bmp ->
        if (bmp != null) vm.sendImageToChat(ImageUtil.toJpeg(bmp), VisionPrompts.Q)
    }
    val listState = rememberLazyListState()
    val shown = vm.messages.filter { !it.voice }
    LaunchedEffect(shown.size) {
        if (shown.isNotEmpty()) listState.animateScrollToItem(shown.size - 1)
    }
    val micLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) vm.startListening() else vm.voiceDenied()
    }
    fun onMic() {
        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            vm.startListening()
        } else {
            micLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }
    fun doSend() {
        if (input.isNotBlank()) {
            vm.send(input, false)
            input = ""
        }
    }
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Column(Modifier.fillMaxSize().imePadding()) {
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (vm.tempMode) "Temporary chat" else "",
                style = MaterialTheme.typography.labelMedium, color = muted, modifier = Modifier.weight(1f)
            )
            if (vm.voiceState == VoiceState.Listening) TextButton(onClick = { vm.cancelListening() }) { Text("Sunna band") }
            if (vm.voiceState == VoiceState.Speaking) TextButton(onClick = { vm.stopSpeaking() }) { Text("Bolna band") }
            Box {
                TextButton(onClick = { showMenu = true }) { Text("⋯", fontSize = 22.sp, color = muted) }
                DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                    DropdownMenuItem(
                        text = { Text(if (vm.tempMode) "Temporary chat band karo" else "Temporary chat") },
                        onClick = { vm.tempMode = !vm.tempMode; showMenu = false }
                    )
                    Personas.names.forEach { n ->
                        DropdownMenuItem(
                            text = { Text((if (vm.persona == n) "✓ " else "") + n) },
                            onClick = { vm.changePersona(n); showMenu = false }
                        )
                    }
                    if (shown.isNotEmpty()) {
                        DropdownMenuItem(text = { Text("Nayi chat (clear)") }, onClick = { vm.clearChat(); showMenu = false })
                    }
                }
            }
        }
        if (shown.isEmpty()) {
            Column(
                Modifier.weight(1f).fillMaxWidth().padding(horizontal = 28.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("✦", fontSize = 40.sp, color = MaterialTheme.colorScheme.primary)
                Text(
                    "How can I help you today?",
                    fontFamily = FontFamily.Serif, fontSize = 28.sp,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.padding(top = 12.dp)
                )
                if (!vm.hasKey) {
                    Text(
                        "AI jawab ke liye AI Brain tab mein API key daalo.",
                        style = MaterialTheme.typography.bodySmall, color = muted,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier.padding(top = 14.dp)
                    )
                }
            }
        } else {
            LazyColumn(
                Modifier.weight(1f).fillMaxWidth(),
                state = listState,
                verticalArrangement = Arrangement.spacedBy(20.dp),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
            ) {
                items(shown.size) { i -> Bubble(shown[i], vm) }
                if (vm.busy) {
                    item { Text("✦ Payal kaam kar rahi hai…", style = MaterialTheme.typography.bodySmall, color = muted) }
                }
            }
        }
        vm.pending?.let { p ->
            Card(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
                Column(Modifier.padding(12.dp)) {
                    Text("Confirm karo", fontWeight = FontWeight.Bold)
                    Text(p.description)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { vm.confirmPending() }) { Text("Haan, karo") }
                        TextButton(onClick = { vm.cancelPending() }) { Text("Cancel") }
                    }
                }
            }
        }
        vm.pendingReply?.let { p ->
            Card(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
                Column(Modifier.padding(12.dp)) {
                    Text("Jawab bhejna hai?", fontWeight = FontWeight.Bold)
                    Text(p.msg.sender + " (" + p.msg.app + ") ko jayega:")
                    Text('"' + p.text + '"')
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { vm.confirmReply() }) { Text("Bhejo") }
                        TextButton(onClick = { vm.cancelReply() }) { Text("Cancel") }
                    }
                }
            }
        }
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp)
        ) {
            Column(Modifier.padding(start = 6.dp, end = 10.dp, top = 4.dp, bottom = 10.dp)) {
                OutlinedTextField(
                    value = input, onValueChange = { input = it },
                    modifier = Modifier.fillMaxWidth(),
                    colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color.Transparent,
                        unfocusedBorderColor = Color.Transparent
                    ),
                    placeholder = { Text("Payal se baat karo", color = muted) },
                    maxLines = 6,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { doSend() })
                )
                Row(Modifier.fillMaxWidth().padding(start = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box {
                        RoundIconButton(PI.Plus, MaterialTheme.colorScheme.surface, MaterialTheme.colorScheme.onSurface, { showAttach = !showAttach }, 42.dp, 22.dp)
                        DropdownMenu(expanded = showAttach, onDismissRequest = { showAttach = false }) {
                            DropdownMenuItem(text = { Text("File bhejo") }, onClick = { showAttach = false; fileLauncher.launch(arrayOf("*/*")) })
                            DropdownMenuItem(text = { Text("Gallery se photo") }, onClick = { showAttach = false; imageLauncher.launch("image/*") })
                            DropdownMenuItem(text = { Text("Camera se photo") }, onClick = { showAttach = false; cameraLauncher.launch(null) })
                        }
                    }
                    Box(Modifier.weight(1f))
                    val listening = vm.voiceState == VoiceState.Listening
                    RoundIconButton(
                        if (listening) PI.Stop else PI.Mic,
                        if (listening) Color(0xFFE5484D) else MaterialTheme.colorScheme.surface,
                        if (listening) Color.White else MaterialTheme.colorScheme.onSurface,
                        { onMic() }, 42.dp, 22.dp
                    )
                    Box(Modifier.padding(start = 8.dp)) {
                        RoundIconButton(
                            PI.Up,
                            if (input.isBlank()) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.primary,
                            if (input.isBlank()) muted else MaterialTheme.colorScheme.onPrimary,
                            { doSend() }, 42.dp, 22.dp
                        )
                    }
                }
            }
        }
    }
}
@Composable
fun AgentCard() {
    val ctx = LocalContext.current
    var goal by remember { mutableStateOf("") }
    var result by remember { mutableStateOf("") }
    val agent = remember { TaskOrchestrator(ctx) }
    val scope = rememberCoroutineScope()
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("🤖 Agent", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            OutlinedTextField(
                value = goal,
                onValueChange = { goal = it },
                label = { Text("Kaam bolo (jaise: github status)") },
                modifier = Modifier.fillMaxWidth()
            )
            Button(
                onClick = { scope.launch { result = agent.runGoal(goal) } },
                enabled = goal.isNotBlank()
            ) {
                Text("Agent ko kaam do")
            }
            if (result.isNotBlank()) {
                Text(result, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
