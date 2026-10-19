@file:OptIn(ExperimentalMaterial3Api::class)

package com.yesmin.ai

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavHostController

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            content()
        }
    }
}

@Composable
fun SettingsScreen(vm: AppViewModel, nav: NavHostController) {
    val ctx = LocalContext.current
    var refresh by remember { mutableStateOf(0) }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val obs = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) refresh++ }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs) }
    }
    val micGranted = refresh >= 0 &&
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
    val a11yOn = refresh >= 0 && YesminAccessibilityService.isRunning()
    val micLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { refresh++ }
    val voices = remember(vm.language, refresh) { vm.voiceNames() }
    var confirmWipe by remember { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }
    val version = remember {
        try { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: "?" } catch (e: Exception) { "?" }
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Settings", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)

        Section("Gemini API & Model") {
            Text("Provider: Google Gemini · model: ${vm.model} · key: " + if (vm.hasKey) "saved" else "nahi")
            OutlinedButton(onClick = { nav.navigate("brain") }) { Text("AI Brain kholo") }
        }

        Section("Profile") {
            var nm by remember { mutableStateOf(vm.userName) }
            OutlinedTextField(
                value = nm, onValueChange = { nm = it; vm.changeUserName(it) }, singleLine = true,
                label = { Text("Aapka naam") }, modifier = Modifier.fillMaxWidth()
            )
        }

        Section("Language") {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("hindi" to "हिंदी", "hinglish" to "Hinglish", "english" to "English").forEach { (k, l) ->
                    FilterChip(selected = vm.language == k, onClick = { vm.changeLanguage(k) }, label = { Text(l) })
                }
            }
            Text("Bolne ki bhasha (speech recognition) bhi isi se set hoti hai.", style = MaterialTheme.typography.bodySmall)
        }

        Section("Mode (jawab ka andaaz)") {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Personas.names.forEach { n -> FilterChip(selected = vm.persona == n, onClick = { vm.changePersona(n) }, label = { Text(n) }) }
            }
        }

        Section("Voice") {
            Text(
                "Awaaz aapke phone ke Text-to-Speech engine par nirbhar hai. Female voice har phone par nahi hoti.",
                style = MaterialTheme.typography.bodySmall
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(selected = vm.voiceEngine == "phone", onClick = { vm.changeVoiceEngine("phone") }, label = { Text("Phone voice") })
                FilterChip(selected = vm.voiceEngine == "gemini", onClick = { vm.changeVoiceEngine("gemini") }, label = { Text("Gemini voice (natural)") })
            }
            if (vm.voiceEngine == "gemini") {
                Text(
                    "Gemini awaaz internet aur key use karti hai (quota lagta hai). Na chale to phone ki awaaz se bol degi. Har personality ki apni awaaz hai.",
                    style = MaterialTheme.typography.bodySmall
                )
                vm.models.filter { it.contains("tts") }.take(6).forEach { m ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = vm.ttsModelName == m, onClick = { vm.changeTtsModel(m) })
                        Text(m)
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(selected = vm.voiceName == null, onClick = { vm.setVoice(null) })
                Text("Engine ki default awaaz")
            }
            voices.take(12).forEach { v ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = vm.voiceName == v, onClick = { vm.setVoice(v) })
                    Text(v)
                }
            }
            if (voices.isEmpty()) Text("Is bhasha ki koi awaaz nahi mili — voice data install karna padega.", style = MaterialTheme.typography.bodySmall)
            Text("Awaaz ki unchai (pitch): " + String.format(java.util.Locale.US, "%.1f", vm.voicePitch))
            Slider(value = vm.voicePitch, onValueChange = { vm.changeVoicePitch(it) }, valueRange = 0.7f..1.5f)
            Text("Bolne ki raftaar: " + String.format(java.util.Locale.US, "%.1f", vm.voiceRate))
            Slider(value = vm.voiceRate, onValueChange = { vm.changeVoiceRate(it) }, valueRange = 0.6f..1.4f)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { vm.speakText("Namaste, main Yesmin hoon. Yeh meri awaaz hai.") }) { Text("Awaaz test") }
                OutlinedButton(onClick = {
                    try {
                        ctx.startActivity(Intent("com.android.settings.TTS_SETTINGS").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    } catch (e: Exception) {
                        vm.notice = "TTS settings nahi khul payi. Phone Settings → Language → Text-to-speech dekho."
                    }
                }) { Text("TTS settings") }
            }
        }

        Section("Memory") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Memory on", Modifier.weight(1f))
                Switch(checked = vm.memoryEnabled, onCheckedChange = { vm.changeMemoryEnabled(it) })
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Chat history phone par save karo", Modifier.weight(1f))
                Switch(checked = vm.saveHistory, onCheckedChange = { vm.changeSaveHistory(it) })
            }
            Text("History sirf is phone par rehti hai. Gemini ko har baar sirf haal ke kuch messages bheje jaate hain.", style = MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { nav.navigate("memory") }) { Text("Memory dekho") }
                OutlinedButton(onClick = { vm.clearHistory() }) { Text("History delete") }
            }
            OutlinedButton(onClick = { confirmClear = true }) { Text("Saari memory delete") }
        }

        Section("Theme") {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("system", "dark", "light").forEach { t ->
                    FilterChip(selected = vm.theme == t, onClick = { vm.changeTheme(t) }, label = { Text(t) })
                }
            }
        }

        Section("Permissions") {
            Text("Microphone: " + if (micGranted) "allowed" else "allowed nahi")
            if (!micGranted) {
                Text("Mic sirf tab chalta hai jab aap mic button dabate ho (push-to-talk). Background mein kabhi nahi sunta.", style = MaterialTheme.typography.bodySmall)
                Button(onClick = { micLauncher.launch(Manifest.permission.RECORD_AUDIO) }) { Text("Mic allow karo") }
            }
            Text("Accessibility service: " + if (a11yOn) "ON" else "OFF")
            Text(
                "Yeh optional hai. On karne par Yesmin aapke bolne par hi back, scroll, tap aur text type kar sakti hai (5 second ke delay ke saath, taaki aap target app mein jaa sako). " +
                    "Message bhejna/submit/kharidari jaise kaam se pehle confirm poochti hai. Password fields, security dialogs ya protected screens control nahi hote. Screen record ya content store nahi hota.\n" +
                    "Enable karne ke liye: Settings → Accessibility → Installed apps → Yesmin AI → ON. Band karne ke liye wahin OFF.",
                style = MaterialTheme.typography.bodySmall
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    ctx.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }) { Text("Accessibility settings") }
                OutlinedButton(onClick = {
                    ctx.startActivity(
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + ctx.packageName))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                }) { Text("App permissions") }
            }
        }

        Section("Messages & Contacts access") {
            val contactsLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { refresh++ }
            Text("Contacts: " + if (refresh >= 0 && Contacts.hasPermission(ctx)) "allowed" else "allowed nahi")
            if (!(refresh >= 0 && Contacts.hasPermission(ctx))) {
                Button(onClick = { contactsLauncher.launch(Manifest.permission.READ_CONTACTS) }) { Text("Contacts allow karo") }
            }
            Text("Notification Access (messages padhna/jawab dena): " + if (refresh >= 0 && NotificationManagerCompat.getEnabledListenerPackages(ctx).contains(ctx.packageName)) "ON" else "OFF")
            Text(
                "Yeh sirf WhatsApp/SMS jaisi messaging notifications dekhta hai. Kuch disk par save ya upload nahi hota; messages sirf memory mein rehte hain. Jawab bhejne se pehle hamesha aapse confirm poochha jaata hai.",
                style = MaterialTheme.typography.bodySmall
            )
            OutlinedButton(onClick = {
                ctx.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }) { Text("Notification Access settings") }
        }

        Section("Privacy") {
            Text(
                "• Chat/Tools ka text jawab ke liye Google Gemini ko internet par bheja jaata hai.\n" +
                    "• App kholna, battery, time, calculator jaise commands local hain.\n" +
                    "• API key Android Keystore se encrypted, memory sirf phone par; koi sync nahi.\n" +
                    "• Koi analytics ya chhupa hua data collection nahi.",
                style = MaterialTheme.typography.bodySmall
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Reminder notification mein text chhupao", Modifier.weight(1f))
                Switch(checked = vm.hideReminderText, onCheckedChange = { vm.changeHideReminderText(it) })
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { vm.resetDisclosure() }) { Text("Notice dobara dikhao") }
                OutlinedButton(onClick = { confirmWipe = true }) { Text("Sab kuch delete") }
            }
        }

        Section("About") {
            Text("Yesmin AI v$version · package com.yesmin.ai · compileSdk 34 / minSdk 26")
            Text("Kaunsi feature kaam karti hai aur kaunsi untested hai: FEATURES.md dekho.", style = MaterialTheme.typography.bodySmall)
        }
    }

    if (confirmWipe) {
        AlertDialog(
            onDismissRequest = { confirmWipe = false },
            title = { Text("Sab kuch delete?") },
            text = { Text("API key, memory aur history — sab hat jayega.") },
            confirmButton = { TextButton(onClick = { vm.wipeAll(); confirmWipe = false }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { confirmWipe = false }) { Text("Cancel") } }
        )
    }
    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Saari memory delete?") },
            text = { Text("Yeh wapas nahi aayegi.") },
            confirmButton = { TextButton(onClick = { vm.clearMemory(); confirmClear = false }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancel") } }
        )
    }
}
