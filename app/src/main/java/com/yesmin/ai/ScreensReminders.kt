@file:OptIn(ExperimentalMaterial3Api::class)

package com.yesmin.ai

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.horizontalScroll
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
import java.util.Calendar
import java.util.Locale

@Composable
private fun ReminderItem(r: Reminder, fmt: SimpleDateFormat, vm: AppViewModel, onEdit: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(r.title, fontWeight = FontWeight.Bold)
            if (r.note.isNotBlank()) Text(r.note, style = MaterialTheme.typography.bodySmall)
            Text(
                fmt.format(r.timeMillis) + when (r.repeat) {
                    "daily" -> "  ·  roz"
                    "weekly" -> "  ·  har hafte"
                    else -> ""
                },
                style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary
            )
            Row(Modifier.horizontalScroll(rememberScrollState())) {
                if (!r.done) {
                    TextButton(onClick = { vm.completeReminder(r.id) }) { Text("✓ Done") }
                    TextButton(onClick = { vm.snoozeReminder(r.id) }) { Text("⏰ +10 min") }
                    TextButton(onClick = onEdit) { Text("✎ Edit") }
                }
                TextButton(onClick = { vm.deleteReminder(r.id) }) { Text("🗑 Delete") }
            }
        }
    }
}

@Composable
fun RemindersScreen(vm: AppViewModel) {
    val ctx = LocalContext.current
    var refresh by remember { mutableStateOf(0) }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val obs = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) refresh++ }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs) }
    }
    val notifOn = refresh >= 0 && NotificationManagerCompat.from(ctx).areNotificationsEnabled()
    val exactOk = refresh >= 0 && ReminderScheduler.canExact(ctx)

    var editingId by remember { mutableStateOf<Long?>(null) }
    var title by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var whenMillis by remember { mutableStateOf(System.currentTimeMillis() + 3_600_000L) }
    var repeat by remember { mutableStateOf("none") }
    val fmt = remember { SimpleDateFormat("EEE, d MMM yyyy · hh:mm a", Locale.getDefault()) }

    fun pickDate() {
        val c = Calendar.getInstance().apply { timeInMillis = whenMillis }
        DatePickerDialog(ctx, { _, y, m, d ->
            val n = Calendar.getInstance().apply {
                timeInMillis = whenMillis
                set(Calendar.YEAR, y); set(Calendar.MONTH, m); set(Calendar.DAY_OF_MONTH, d)
            }
            whenMillis = n.timeInMillis
        }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).show()
    }

    fun pickTime() {
        val c = Calendar.getInstance().apply { timeInMillis = whenMillis }
        TimePickerDialog(ctx, { _, h, mi ->
            val n = Calendar.getInstance().apply {
                timeInMillis = whenMillis
                set(Calendar.HOUR_OF_DAY, h); set(Calendar.MINUTE, mi); set(Calendar.SECOND, 0)
            }
            whenMillis = n.timeInMillis
        }, c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE), false).show()
    }

    val now = System.currentTimeMillis()
    val upcoming = vm.reminders.filter { !it.done && (it.timeMillis >= now || it.repeat != "none") }
    val overdue = vm.reminders.filter { !it.done && it.timeMillis < now && it.repeat == "none" }
    val completed = vm.reminders.filter { it.done }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("Reminders", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        if (!notifOn) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp)) {
                    Text("Notifications band hain, isliye reminder dikhega nahi.", fontWeight = FontWeight.Bold)
                    Button(onClick = {
                        ctx.startActivity(
                            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                .putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    }) { Text("Notifications allow karo") }
                }
            }
        }
        if (!exactOk && Build.VERSION.SDK_INT >= 31) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp)) {
                    Text("Sahi waqt par alarm ke liye ijazat chahiye. Bina iske reminder thoda der se aa sakta hai.", style = MaterialTheme.typography.bodySmall)
                    OutlinedButton(onClick = {
                        ctx.startActivity(
                            Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:" + ctx.packageName))
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    }) { Text("Exact alarm allow karo") }
                }
            }
        }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(if (editingId == null) "Naya reminder" else "Reminder edit karo", fontWeight = FontWeight.Bold)
                OutlinedTextField(value = title, onValueChange = { title = it.take(80) }, singleLine = true, label = { Text("Kya yaad dilana hai?") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = note, onValueChange = { note = it.take(200) }, label = { Text("Note (zaroori nahi)") }, modifier = Modifier.fillMaxWidth())
                Text(fmt.format(whenMillis), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { pickDate() }) { Text("📅 Date") }
                    OutlinedButton(onClick = { pickTime() }) { Text("🕒 Time") }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    listOf("none" to "Ek baar", "daily" to "Roz", "weekly" to "Har hafte").forEach { (k, l) ->
                        FilterChip(selected = repeat == k, onClick = { repeat = k }, label = { Text(l) })
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = {
                        if (vm.saveReminder(editingId, title, note, whenMillis, repeat)) {
                            editingId = null; title = ""; note = ""; repeat = "none"
                            whenMillis = System.currentTimeMillis() + 3_600_000L
                        }
                    }) { Text(if (editingId == null) "Reminder lagao" else "Update karo") }
                    if (editingId != null) {
                        TextButton(onClick = { editingId = null; title = ""; note = ""; repeat = "none" }) { Text("Cancel") }
                    }
                }
                Text("Bolkar bhi laga sakte ho: \"10 minute baad chai ke liye yaad dilao\".", style = MaterialTheme.typography.bodySmall)
            }
        }

        Text("Upcoming", style = MaterialTheme.typography.titleMedium)
        if (upcoming.isEmpty()) Text("Koi upcoming reminder nahi.", style = MaterialTheme.typography.bodySmall)
        upcoming.forEach { r ->
            ReminderItem(r, fmt, vm) {
                editingId = r.id; title = r.title; note = r.note; whenMillis = r.timeMillis; repeat = r.repeat
            }
        }
        if (overdue.isNotEmpty()) {
            Text("Overdue", style = MaterialTheme.typography.titleMedium)
            overdue.forEach { r ->
                ReminderItem(r, fmt, vm) {
                    editingId = r.id; title = r.title; note = r.note; whenMillis = System.currentTimeMillis() + 3_600_000L; repeat = r.repeat
                }
            }
        }
        if (completed.isNotEmpty()) {
            Text("Completed", style = MaterialTheme.typography.titleMedium)
            completed.forEach { r -> ReminderItem(r, fmt, vm) {} }
        }
    }
}
