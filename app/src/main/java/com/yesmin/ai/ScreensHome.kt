@file:OptIn(ExperimentalMaterial3Api::class)

package com.yesmin.ai

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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.navigation.NavHostController

// ---------- shared widgets ----------

@Composable
fun RichText(text: String, onSave: ((String) -> Unit)? = null) {
    val parts = text.split("```")
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        parts.forEachIndexed { i, p ->
            if (i % 2 == 0) {
                if (p.isNotBlank()) Text(p.trim().replace("**", "").replace(Regex("(?m)^\\* "), "• "))
            } else {
                CodeBlock(p, onSave)
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
fun Bubble(m: ChatMsg, vm: AppViewModel) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = if (m.fromUser) Arrangement.End else Arrangement.Start
    ) {
        Surface(
            color = when {
                m.fromUser -> MaterialTheme.colorScheme.primaryContainer
                m.error -> MaterialTheme.colorScheme.errorContainer
                else -> MaterialTheme.colorScheme.surfaceVariant
            },
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier.widthIn(max = 320.dp)
        ) {
            Column(Modifier.padding(12.dp)) {
                if (m.fromUser) Text(m.text) else RichText(m.text, onSave = { vm.addMemory("snippet", it) })
                if (!m.fromUser && !m.error) TextButton(onClick = { vm.speakText(m.text) }) { Text("🔊 Suno") }
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
    val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
    val greet = when {
        hour < 12 -> "Good morning"
        hour < 17 -> "Good afternoon"
        else -> "Good evening"
    }
    val title = if (vm.userName.isBlank()) greet else greet + ", " + vm.userName
    val quick = listOf(
        "▶ YouTube music" to "youtube par music kholo",
        "🔋 Battery" to "battery kitni hai",
        "🕒 Time" to "abhi time kya hai",
        "📶 Internet" to "internet status"
    )
    val recent = vm.messages.filter { it.fromUser }.takeLast(3).reversed()
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        HomeHero(vm, title) { onMic() }

        Text("Quick commands", style = MaterialTheme.typography.titleMedium, modifier = Modifier.fillMaxWidth())
        quick.chunked(2).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { (label, cmd) ->
                    FilledTonalButton(
                        onClick = { vm.send(cmd, false); nav.navigate("chat") { launchSingleTop = true } },
                        modifier = Modifier.weight(1f)
                    ) { Text(label, maxLines = 1) }
                }
            }
        }
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Recent", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                if (recent.isEmpty()) Text("Abhi koi baat nahi hui.", style = MaterialTheme.typography.bodySmall)
                recent.forEach { Text("• " + it.text.take(60), style = MaterialTheme.typography.bodySmall) }
            }
        }
    }
}

// ---------- Chat ----------

@Composable
fun ChatScreen(vm: AppViewModel) {
    val ctx = LocalContext.current
    var input by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    LaunchedEffect(vm.messages.size) {
        if (vm.messages.isNotEmpty()) listState.animateScrollToItem(vm.messages.size - 1)
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
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            FilterChip(selected = vm.tempMode, onClick = { vm.tempMode = !vm.tempMode }, label = { Text("🕶 Temporary chat") })
            Personas.names.forEach { n ->
                FilterChip(selected = vm.persona == n, onClick = { vm.changePersona(n) }, label = { Text(n) })
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(vm.statusLabel(), style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
            if (vm.voiceState == VoiceState.Listening) TextButton(onClick = { vm.cancelListening() }) { Text("Cancel listening") }
            if (vm.voiceState == VoiceState.Speaking) TextButton(onClick = { vm.stopSpeaking() }) { Text("Stop speaking") }
            if (vm.messages.isNotEmpty()) TextButton(onClick = { vm.clearChat() }) { Text("Clear") }
        }
        if (vm.messages.isEmpty()) {
            Column(Modifier.weight(1f).padding(24.dp), verticalArrangement = Arrangement.Center) {
                Text("Namaste! 👋", style = MaterialTheme.typography.headlineSmall)
                Text("Mic dabao ya type karo. Try karo:\n• \"YouTube kholo\"\n• \"Arijit Singh ke songs YouTube par\"\n• \"battery kitni hai\"\n• \"12 + 8 x 3\"\n• \"Kotlin mein calculator ka code likho\"")
                if (!vm.hasKey) Text("\n⚠ AI jawab ke liye AI Brain tab mein Gemini key daalo. Local commands bina key ke chalte hain.")
            }
        } else {
            LazyColumn(
                Modifier.weight(1f).padding(horizontal = 12.dp),
                state = listState,
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                items(vm.messages.size) { i -> Bubble(vm.messages[i], vm) }
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
                    Text("\"" + p.text + "\"")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { vm.confirmReply() }) { Text("Bhejo") }
                        TextButton(onClick = { vm.cancelReply() }) { Text("Cancel") }
                    }
                }
            }
        }
        if (vm.busy) Text("Gemini jawab likh raha hai…", Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.labelMedium)
        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedTextField(
                value = input, onValueChange = { input = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Yesmin se kuch bhi poocho…") },
                maxLines = 4,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { vm.send(input, false); input = "" })
            )
            Button(onClick = { onMic() }, contentPadding = PaddingValues(14.dp)) {
                Text(if (vm.voiceState == VoiceState.Listening) "⏹" else "🎤")
            }
            Button(onClick = { vm.send(input, false); input = "" }, contentPadding = PaddingValues(14.dp)) { Text("➤") }
        }
    }
}
