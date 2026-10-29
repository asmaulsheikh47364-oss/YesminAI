@file:OptIn(ExperimentalMaterial3Api::class)

package com.yesmin.ai

import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.sp
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.navigation.NavHostController
import java.text.SimpleDateFormat
import java.util.Locale

// ---------- shared pieces ----------

@Composable
fun NeonAvatar(size: Dp, letter: String = "Y", c1: Color = NeonCyan, c2: Color = NeonViolet) {
    Box(Modifier.size(size).clip(CircleShape).background(Brush.linearGradient(listOf(c1, c2))), contentAlignment = Alignment.Center) {
        Box(
            Modifier.size(size - 10.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surface),
            contentAlignment = Alignment.Center
        ) {
            Text(letter, fontSize = (size.value * 0.45f).sp, fontWeight = FontWeight.Bold, color = c1)
        }
    }
}

@Composable
fun Waveform(level: Float) {
    val mult = listOf(0.5f, 0.8f, 1f, 0.7f, 0.9f, 0.6f, 1f, 0.75f, 0.5f)
    Row(Modifier.height(70.dp), horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
        mult.forEach { m ->
            val h by animateFloatAsState(0.08f + 0.92f * level * m, label = "bar")
            Box(Modifier.width(6.dp).height((70 * h).dp).clip(RoundedCornerShape(3.dp)).background(NeonBrush))
        }
    }
}

@Composable
private fun NavRow(icon: String, title: String, desc: String, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(icon, fontSize = 26.sp)
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Bold)
                Text(desc, style = MaterialTheme.typography.bodySmall)
            }
            Text("›", fontSize = 22.sp)
        }
    }
}

// ---------- Voice tab ----------

@Composable
fun VoiceScreen(vm: AppViewModel) {
    val ctx = LocalContext.current
    var refresh by remember { mutableStateOf(0) }
    val micOk = refresh >= 0 &&
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
    val micLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        refresh++
        if (granted) vm.startSession() else vm.voiceDenied()
    }
    fun onMic() {
        if (!micOk) { micLauncher.launch(Manifest.permission.RECORD_AUDIO); return }
        when {
            !vm.sessionActive -> vm.startSession()
            vm.sessionPaused -> vm.resumeSession()
            else -> vm.pauseSession()
        }
    }
    val pulse by rememberInfiniteTransition(label = "pulse").animateFloat(
        0.3f, 0.9f, infiniteRepeatable(tween(500), RepeatMode.Reverse), label = "pulseValue"
    )
    val level = when (vm.voiceState) {
        VoiceState.Listening -> vm.micLevel
        VoiceState.Speaking -> pulse
        VoiceState.Processing, VoiceState.Connecting -> 0.15f
        else -> 0.03f
    }
    val lastUser = vm.messages.lastOrNull { it.fromUser }
    val lastReply = vm.messages.lastOrNull { !it.fromUser }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Voice", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        NeonAvatar(110.dp, vm.activeProfile().letter, vm.activeProfile().c1, vm.activeProfile().c2)
        Text(vm.statusLabel(), style = MaterialTheme.typography.titleMedium)
        Waveform(level)
        Box(
            Modifier.size(88.dp).clip(CircleShape).background(NeonBrush).clickable { onMic() },
            contentAlignment = Alignment.Center
        ) { Text(if (!vm.sessionActive) "🎤" else if (vm.sessionPaused) "▶" else "⏸", fontSize = 36.sp) }
        Text(
            if (!vm.sessionActive) "Mic dabao — baat-cheet shuru. Yesmin baar-baar sunegi jab tak aap Pause ya Stop na karo."
            else if (vm.sessionPaused) "Paused. Dobara shuru karne ke liye ▶ dabao."
            else "Session chalu hai. Mic band karne ke liye ⏸ ya Stop dabao.",
            style = MaterialTheme.typography.bodySmall
        )
        if (vm.voiceSource.isNotBlank()) Text("Awaaz: " + vm.voiceSource, style = MaterialTheme.typography.labelMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (vm.sessionActive) OutlinedButton(onClick = { vm.stopSession() }) { Text("⏹ Stop session") }
            if (vm.sessionActive) OutlinedButton(onClick = { vm.toggleMicMute() }) { Text(if (vm.micMuted) "🔇 Unmute" else "🎙 Mute") }
            if (vm.voiceState == VoiceState.Speaking) OutlinedButton(onClick = { vm.stopSpeaking() }) { Text("Stop speaking") }
            if (lastReply != null && !lastReply.error) OutlinedButton(onClick = { vm.speakText(lastReply.text) }) { Text("🔊 Replay") }
        }
        if (!micOk) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Microphone ki ijazat nahi hai, isliye voice nahi chalegi. Text se Chat tab mein baat kar sakte ho.", style = MaterialTheme.typography.bodySmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { micLauncher.launch(Manifest.permission.RECORD_AUDIO) }) { Text("Allow karo") }
                        OutlinedButton(onClick = {
                            ctx.startActivity(
                                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + ctx.packageName))
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            )
                            refresh++
                        }) { Text("App Settings") }
                    }
                }
            }
        }
        Text("Bolkar control: \"pause\", \"resume\", \"stop speaking\", \"stop listening\". Pause mein mic band rehta hai, isliye Resume button/notification se dabana padta hai.", style = MaterialTheme.typography.bodySmall)
        if (lastUser != null) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp)) {
                    Text("Aapne kaha", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    Text(lastUser.text)
                }
            }
        }
        if (lastReply != null) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp)) {
                    Text(vm.activeProfile().name, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    RichText(lastReply.text, onSave = { vm.addMemory("snippet", it) })
                }
            }
        }
    }
}

// ---------- Apps tab (Phone Control) ----------

@Composable
fun AppsScreen(vm: AppViewModel, nav: NavHostController) {
    var appName by remember { mutableStateOf("") }
    var ytQuery by remember { mutableStateOf("") }
    var callTarget by remember { mutableStateOf("") }
    val timeFmt = remember { SimpleDateFormat("hh:mm a", Locale.getDefault()) }
    val actions = listOf(
        Triple("▶", "Open YouTube", "youtube kholo"),
        Triple("🌐", "Open Google", "google kholo"),
        Triple("🟢", "Open WhatsApp", "whatsapp kholo"),
        Triple("🔵", "Open Chrome", "chrome kholo"),
        Triple("📷", "Open Camera", "camera kholo"),
        Triple("⚙", "Open Settings", "settings kholo"),
        Triple("🔋", "Check Battery", "battery kitni hai"),
        Triple("📱", "Device Info", "device model batao"),
        Triple("🔦", "Torch On", "torch on karo"),
        Triple("🔦", "Torch Off", "torch off karo"),
        Triple("🔊", "Volume +", "volume badhao"),
        Triple("🔉", "Volume −", "volume kam karo"),
        Triple("🔇", "Mute", "volume mute"),
        Triple("📶", "Wi-Fi", "wifi on karo"),
        Triple("🔵", "Bluetooth", "bluetooth on karo"),
        Triple("🌙", "DND On", "dnd on karo")
    )
    val lastReply = vm.messages.lastOrNull { !it.fromUser }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("Phone Control", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text("Neeche tap karo ya bolkar bhi bol sakte ho. Har action ke baad asli result yahin dikhega.", style = MaterialTheme.typography.bodySmall)
        if (lastReply != null) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp)) {
                    Text("Last result", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    Text(lastReply.text)
                }
            }
        }
        actions.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { (icon, label, cmd) ->
                    FilledTonalButton(onClick = { vm.send(cmd, false) }, modifier = Modifier.weight(1f)) {
                        Text("$icon $label", maxLines = 1)
                    }
                }
            }
        }
        Text("Koi bhi app kholo", style = MaterialTheme.typography.titleSmall)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedTextField(value = appName, onValueChange = { appName = it }, singleLine = true, modifier = Modifier.weight(1f), label = { Text("App ka naam") })
            Button(onClick = { vm.openApp(appName) }, enabled = appName.isNotBlank()) { Text("Open") }
        }
        Text("YouTube par khojo", style = MaterialTheme.typography.titleSmall)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedTextField(value = ytQuery, onValueChange = { ytQuery = it }, singleLine = true, modifier = Modifier.weight(1f), label = { Text("Gaana / video") })
            Button(onClick = { vm.openYouTubeSearch(ytQuery) }, enabled = ytQuery.isNotBlank()) { Text("Search") }
        }
        Text(
            if (vm.hasYtKey) "Pehla result seedha khulta hai; neeche baaki results se bhi chun sakte ho." else "Sirf YouTube ka search khulta hai. Seedha video ke liye Settings mein YouTube key daalo.",
            style = MaterialTheme.typography.bodySmall
        )
        vm.ytResults.forEach { v -> TextButton(onClick = { vm.openVideo(v) }) { Text("▶ " + v.title.take(50) + " — " + v.channel.take(20)) } }
        Text("Call lagao", style = MaterialTheme.typography.titleSmall)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedTextField(value = callTarget, onValueChange = { callTarget = it }, singleLine = true, modifier = Modifier.weight(1f), label = { Text("Naam ya number") })
            Button(onClick = { vm.send(callTarget + " ko call karo", false) }, enabled = callTarget.isNotBlank()) { Text("📞") }
        }
        Text("Wi-Fi/Bluetooth: Android 10 mein app unhe khud on/off nahi kar sakti, sirf Settings khol deti hai. Call mein dialer khulta hai aur hara button aap dabate ho.", style = MaterialTheme.typography.bodySmall)
        Text("Action history", style = MaterialTheme.typography.titleSmall)
        if (vm.actionLog.isEmpty()) Text("Abhi koi action nahi hua.", style = MaterialTheme.typography.bodySmall)
        vm.actionLog.forEachIndexed { i, a ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(timeFmt.format(a.time) + "  " + a.text, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                if (a.undo != null) TextButton(onClick = { vm.undoAction(i) }) { Text("↩ Undo") }
            }
        }
    }
}

// ---------- More tab ----------

@Composable
fun MoreScreen(nav: NavHostController) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("More", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        NavRow("📱", "Apps & Phone", "App kholo, phone control, YouTube") { nav.navigate("apps") { launchSingleTop = true } }
        NavRow("📷", "Photo AI", "Photo dikhao aur sawaal poochho") { nav.navigate("vision") { launchSingleTop = true } }
        NavRow("🧮", "Calculator", "Jodna, ghatana, percent aur aur bhi") { nav.navigate("calc") { launchSingleTop = true } }
        NavRow("💬", "Help & Support", "Creator ki jaankari aur sampark") { nav.navigate("support") { launchSingleTop = true } }
    }
}

// ---------- Device info ----------

@Composable
fun DeviceScreen() {
    val ctx = LocalContext.current
    var tick by remember { mutableStateOf(0) }
    val rows = remember(tick) {
        listOf(
            "🔋" to PhoneInfo.battery(ctx), "📶" to PhoneInfo.network(ctx), "💾" to PhoneInfo.storage(),
            "🧠" to PhoneInfo.ram(ctx), "⚙" to PhoneInfo.cpu(), "📱" to PhoneInfo.device(), "🕒" to PhoneInfo.time()
        )
    }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("Device Information", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        rows.forEach { (icon, text) ->
            Card(Modifier.fillMaxWidth()) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(icon, fontSize = 24.sp)
                    Text(text)
                }
            }
        }
        Button(onClick = { tick++ }) { Text("Refresh") }
        Text("Sirf wahi jaankari dikhti hai jo Android khud deta hai. IMEI/serial jaisi cheezein collect nahi hoti.", style = MaterialTheme.typography.bodySmall)
    }
}

// ---------- Help & Support ----------

@Composable
fun SupportScreen(vm: AppViewModel) {
    val ctx = LocalContext.current
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("Help & Support", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Box(
            Modifier.fillMaxWidth()
                .background(
                    Brush.linearGradient(listOf(Color(0xFF0B1026), Color(0xFF1B1B4B), Color(0xFF2E1B66))),
                    RoundedCornerShape(26.dp)
                )
                .padding(24.dp)
        ) {
            Column(
                Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                NeonAvatar(76.dp, "A")
                Text("CREATOR", color = NeonCyan, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 3.sp)
                Text("Md Asmaul", color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Bold)
                Text("Founder & Developer, Yesmin AI", color = Color(0xFFB8C0E0), fontSize = 14.sp)
                Text("📍 Bhawanipur, Pakur", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                Text("Jharkhand, India", color = Color(0xFFB8C0E0), fontSize = 14.sp)
                Button(
                    onClick = { PhoneControl.whatsapp(ctx, "9321109411", "") },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF25D366), contentColor = Color.Black
                    )
                ) {
                    Text("💬  Contact on WhatsApp", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
