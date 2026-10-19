@file:OptIn(ExperimentalMaterial3Api::class)

package com.yesmin.ai

import android.Manifest
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.text.SimpleDateFormat
import java.util.Locale

@Composable
fun MessagesScreen(vm: AppViewModel) {
    val ctx = LocalContext.current
    var refresh by remember { mutableStateOf(0) }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val obs = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) refresh++ }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs) }
    }
    val hasContacts = refresh >= 0 && Contacts.hasPermission(ctx)
    val listenerOn = refresh >= 0 && NotificationManagerCompat.getEnabledListenerPackages(ctx).contains(ctx.packageName)
    val contactsLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { refresh++ }

    var to by remember { mutableStateOf("") }
    var body by remember { mutableStateOf("") }
    var idea by remember { mutableStateOf("") }
    var channel by remember { mutableStateOf("whatsapp") }
    var resolved by remember { mutableStateOf<Pair<String, String>?>(null) }
    var matches by remember { mutableStateOf(listOf<Pair<String, String>>()) }
    var info by remember { mutableStateOf("") }
    var replyTo by remember { mutableStateOf<IncomingMessage?>(null) }
    var replyText by remember { mutableStateOf("") }
    val fmt = remember { SimpleDateFormat("hh:mm a", Locale.getDefault()) }
    val numberRe = remember { Regex("^[+0-9 \\-]{6,}$") }

    fun resolve() {
        val t = to.trim()
        matches = emptyList()
        resolved = null
        if (t.isEmpty()) { info = "Naam ya number likho."; return }
        if (numberRe.matches(t)) { resolved = t to t; info = ""; return }
        val f = Contacts.find(ctx, t)
        when {
            f == null -> info = "Naam se dhundhne ke liye Contacts ki ijazat chahiye."
            f.isEmpty() -> info = "'$t' naam ka contact nahi mila."
            Contacts.distinctNames(f).size == 1 -> { resolved = f[0]; info = "" }
            else -> { matches = f.distinctBy { it.first }.take(8); info = "Kaunsa contact? Neeche se chuno." }
        }
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("Messages & Call", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(
            "Message ya call aapki aankhon ke saamne taiyaar hota hai. WhatsApp/SMS khulta hai aur message likha hua milta hai; bhejne ke liye aap khud Send dabate ho.",
            style = MaterialTheme.typography.bodySmall
        )
        if (!hasContacts) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp)) {
                    Text("Naam se number dhundhne ke liye Contacts allow karo (number seedha likhna ho to zaroorat nahi).", style = MaterialTheme.typography.bodySmall)
                    Button(onClick = { contactsLauncher.launch(Manifest.permission.READ_CONTACTS) }) { Text("Contacts allow karo") }
                }
            }
        }
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Naya message", fontWeight = FontWeight.Bold)
                OutlinedTextField(
                    value = to, onValueChange = { to = it; resolved = null },
                    singleLine = true, label = { Text("Kisko? (naam ya number)") }, modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(onClick = { resolve() }) { Text("Dhundo") }
                    OutlinedButton(onClick = { if (resolved == null) resolve(); resolved?.let { vm.dialNumber(it.first, it.second) } }) { Text("📞 Call") }
                }
                if (info.isNotBlank()) Text(info, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                matches.forEach { m ->
                    TextButton(onClick = { resolved = m; matches = emptyList(); info = "" }) { Text(m.first + "  " + m.second) }
                }
                OutlinedTextField(
                    value = body, onValueChange = { body = it }, label = { Text("Message") },
                    minLines = 2, modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(selected = channel == "whatsapp", onClick = { channel = "whatsapp" }, label = { Text("WhatsApp") })
                    FilterChip(selected = channel == "sms", onClick = { channel = "sms" }, label = { Text("SMS") })
                }
                OutlinedTextField(
                    value = idea, onValueChange = { idea = it }, singleLine = true,
                    label = { Text("AI se likhwao: kya kehna hai?") }, modifier = Modifier.fillMaxWidth()
                )
                OutlinedButton(onClick = { vm.draftMessage(idea) { body = it } }, enabled = idea.isNotBlank() && !vm.busy) {
                    Text("✨ AI se draft")
                }
                resolved?.let { r ->
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp)) {
                            Text("Preview", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                            Text("Kisko: " + r.first + " (" + r.second + ")")
                            Text("Message: " + body.ifBlank { "(abhi khaali)" })
                            Text("Kahan: " + if (channel == "whatsapp") "WhatsApp" else "SMS", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    Button(onClick = { vm.openMessage(r.first, r.second, channel, body, false) }, enabled = body.isNotBlank()) {
                        Text(if (channel == "whatsapp") "WhatsApp mein kholo" else "SMS mein kholo")
                    }
                }
            }
        }

        Text("Aaye hue messages", style = MaterialTheme.typography.titleMedium)
        if (!listenerOn) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        "Aaye hue WhatsApp/SMS padhne aur unka jawab dene ke liye Notification Access on karna padta hai. Yeh sirf messaging notifications dekhta hai, kuch save ya upload nahi karta; messages sirf memory mein rehte hain.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Button(onClick = {
                        ctx.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    }) { Text("Notification Access kholo") }
                }
            }
        } else {
            Text("Notification Access: ON. Naye messages yahan dikhenge. Bolkar bhi: \"mere message padho\".", style = MaterialTheme.typography.bodySmall)
        }
        if (listenerOn && MessageStore.messages.isEmpty()) Text("Abhi koi naya message nahi.", style = MaterialTheme.typography.bodySmall)
        MessageStore.messages.forEach { m ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(m.sender + "  ·  " + m.app + "  ·  " + fmt.format(m.time), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
                    Text(m.text)
                    Row {
                        if (m.replyAction != null) TextButton(onClick = { replyTo = m; replyText = "" }) { Text("↩ Reply") }
                        TextButton(onClick = { vm.speakText(m.sender + " ka message: " + m.text) }) { Text("🔊 Padho") }
                    }
                    if (replyTo?.key == m.key && replyTo?.text == m.text) {
                        OutlinedTextField(value = replyText, onValueChange = { replyText = it }, label = { Text("Jawab") }, modifier = Modifier.fillMaxWidth())
                        Text("Preview: " + m.sender + " ko jayega: \"" + replyText + "\"", style = MaterialTheme.typography.bodySmall)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { vm.sendReplyDirect(m, replyText); replyTo = null; replyText = "" }, enabled = replyText.isNotBlank()) { Text("Bhejo") }
                            TextButton(onClick = { replyTo = null }) { Text("Cancel") }
                        }
                    }
                }
            }
        }
        if (MessageStore.messages.isNotEmpty()) {
            OutlinedButton(onClick = { MessageStore.clear() }) { Text("Messages saaf karo") }
        }
    }
}
