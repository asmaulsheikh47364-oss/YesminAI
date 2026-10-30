package com.yesmin.ai

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
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
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

private data class PermItem(val title: String, val why: String, val ok: Boolean, val action: () -> Unit)

private fun granted(ctx: Context, p: String): Boolean =
    ContextCompat.checkSelfPermission(ctx, p) == android.content.pm.PackageManager.PERMISSION_GRANTED

private fun runtimeMissing(ctx: Context): List<String> {
    val l = mutableListOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.READ_CONTACTS, Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.CAMERA)
    if (android.os.Build.VERSION.SDK_INT >= 33) l.add(Manifest.permission.POST_NOTIFICATIONS)
    if (TermuxBridge.installed(ctx)) l.add(TermuxBridge.PERM)
    return l.filter { !granted(ctx, it) }
}

private fun goTo(ctx: Context, i: Intent) {
    try {
        ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (e: Exception) {
        try {
            ctx.startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + ctx.packageName))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (x: Exception) {
        }
    }
}

private fun buildItems(ctx: Context, ok: Boolean, ask: (Array<String>) -> Unit): List<PermItem> {
    val assistant = (Settings.Secure.getString(ctx.contentResolver, "assistant") ?: "") + (Settings.Secure.getString(ctx.contentResolver, "voice_interaction_service") ?: "")
    val items = ArrayList<PermItem>()
    items.add(PermItem("Microphone", "Aapki awaaz sunne ke liye", ok && granted(ctx, Manifest.permission.RECORD_AUDIO)) { ask(arrayOf(Manifest.permission.RECORD_AUDIO)) })
    items.add(PermItem("Contacts", "Naam se call ya message ke liye", ok && granted(ctx, Manifest.permission.READ_CONTACTS)) { ask(arrayOf(Manifest.permission.READ_CONTACTS)) })
    items.add(PermItem("Location", "Aap kahan hain ye batane aur map ke kaam ke liye", ok && granted(ctx, Manifest.permission.ACCESS_FINE_LOCATION)) { ask(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)) })
    items.add(PermItem("Camera", "Camera se cheezein dekhne aur samjhane ke liye", ok && granted(ctx, Manifest.permission.CAMERA)) { ask(arrayOf(Manifest.permission.CAMERA)) })
    if (android.os.Build.VERSION.SDK_INT >= 33) {
        items.add(PermItem("Notifications", "Voice session ka status dikhane ke liye", ok && granted(ctx, Manifest.permission.POST_NOTIFICATIONS)) { ask(arrayOf(Manifest.permission.POST_NOTIFICATIONS)) })
    }
    if (TermuxBridge.installed(ctx)) {
        items.add(PermItem("Termux se kaam", "Bolkar Termux mein code chalane ke liye", ok && TermuxBridge.permitted(ctx)) { ask(arrayOf(TermuxBridge.PERM)) })
    }
    items.add(PermItem("Display over other apps", "Dusre app ke upar kinare ki light aur kaam ka panel dikhane ke liye", ok && Settings.canDrawOverlays(ctx)) {
        goTo(ctx, Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + ctx.packageName)))
    })
    items.add(PermItem("Battery unrestricted", "Background mein band na ho, isliye", ok && (ctx.getSystemService(Context.POWER_SERVICE) as PowerManager).isIgnoringBatteryOptimizations(ctx.packageName)) {
        goTo(ctx, Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:" + ctx.packageName)))
    })
    items.add(PermItem("Accessibility", if (ok && YesminAccessibilityService.status(ctx) == "ENABLED_NOT_CONNECTED") "Settings mein ON hai par service jud nahi payi: Payal ko ek baar OFF karke phir ON karo" else "Screen padhne, tap aur type karne ke liye (Android 13+ mein pehle App info > teen bindu > Allow restricted settings)", ok && YesminAccessibilityService.isRunning()) {
        goTo(ctx, Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
    })
    items.add(PermItem("Notification access", "Aaye message padhne ke liye (optional)", ok && NotificationManagerCompat.getEnabledListenerPackages(ctx).contains(ctx.packageName)) {
        goTo(ctx, Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
    })
    items.add(PermItem("Power button se Payal", "Digital assistant mein Payal chuno", ok && assistant.contains(ctx.packageName)) {
        goTo(ctx, Intent(Settings.ACTION_VOICE_INPUT_SETTINGS))
    })
    return items
}

@Composable
private fun refreshTick(): Int {
    var refresh by remember { mutableStateOf(0) }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val obs = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) refresh++ }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs) }
    }
    return refresh
}

/** Asked only once, the first time the app opens. Anything skipped stays listed in Settings. */
@Composable
fun PermissionSetup() {
    val ctx = LocalContext.current
    val prefs = remember { ctx.getSharedPreferences("yesmin_ui", Context.MODE_PRIVATE) }
    var shown by remember { mutableStateOf(!prefs.getBoolean("perm_asked", false)) }
    val refresh = refreshTick()
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { }
    LaunchedEffect(Unit) {
        if (shown) {
            val m = runtimeMissing(ctx)
            if (m.isNotEmpty()) launcher.launch(m.toTypedArray())
            prefs.edit().putBoolean("perm_asked", true).apply()
        }
    }
    if (!shown) return
    val items = buildItems(ctx, refresh >= 0) { launcher.launch(it) }
    if (items.all { it.ok }) return
    AlertDialog(
        onDismissRequest = { shown = false },
        title = { Text("Setup: sab permissions yahin") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Jo baaki hai uske saamne Do dabao. Jo abhi chhoot jaye wo baad mein Settings mein dikhega.", style = MaterialTheme.typography.bodySmall)
                items.forEach { p ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(if (p.ok) "✅" else "⭕")
                        Column(Modifier.weight(1f)) {
                            Text(p.title, fontWeight = FontWeight.Bold)
                            Text(p.why, style = MaterialTheme.typography.bodySmall)
                        }
                        if (!p.ok) Button(onClick = p.action) { Text("Do") }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { shown = false }) { Text("Baad mein") } },
        dismissButton = {
            TextButton(onClick = {
                goTo(ctx, Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + ctx.packageName)))
            }) { Text("App info") }
        }
    )
}

/** Settings list of permissions that are still missing. Draws nothing when everything is granted. */
@Composable
fun MissingPermissions() {
    val ctx = LocalContext.current
    val refresh = refreshTick()
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { }
    val miss = buildItems(ctx, refresh >= 0) { launcher.launch(it) }.filter { !it.ok }
    if (miss.isEmpty()) return
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Baaki permissions", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            miss.forEach { p ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text(p.title, fontWeight = FontWeight.Bold)
                        Text(p.why, style = MaterialTheme.typography.bodySmall)
                    }
                    Button(onClick = p.action) { Text("Do") }
                }
            }
        }
    }
}
