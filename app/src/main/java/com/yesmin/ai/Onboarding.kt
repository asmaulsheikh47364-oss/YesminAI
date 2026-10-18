@file:OptIn(ExperimentalMaterial3Api::class)

package com.yesmin.ai

import android.Manifest
import android.os.Build
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
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
private fun PermRow(icon: String, title: String, why: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(icon, fontSize = 26.sp)
        Column {
            Text(title, fontWeight = FontWeight.Bold)
            Text(why, style = MaterialTheme.typography.bodySmall)
        }
    }
}

/** Shown once on first launch: asks for a name and the permissions the app needs right now. */
@Composable
fun OnboardingScreen(vm: AppViewModel) {
    var name by remember { mutableStateOf(vm.userName) }
    val perms = remember {
        buildList {
            add(Manifest.permission.RECORD_AUDIO)
            if (Build.VERSION.SDK_INT >= 33) add("android.permission.POST_NOTIFICATIONS")
        }
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        vm.finishOnboarding(name)
    }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        NeonAvatar(120.dp)
        Text("Yesmin AI", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text("Aapki personal AI companion — smart, friendly, hamesha saath.")
        OutlinedTextField(
            value = name, onValueChange = { name = it }, singleLine = true,
            label = { Text("Aapka naam (greeting ke liye)") }, modifier = Modifier.fillMaxWidth()
        )
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text("Ijazat (permissions)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                PermRow("🎤", "Microphone", "Aapki awaaz sunne ke liye. Sirf tab chalta hai jab aap mic dabate ho.")
                if (Build.VERSION.SDK_INT >= 33) PermRow("🔔", "Notifications", "Reminders aur alerts dikhane ke liye.")
            }
        }
        Text(
            "Aapka data aapke phone par rehta hai. Sirf chat ka text jawab ke liye Google Gemini ko jaata hai. Aap kabhi bhi Settings mein permissions badal sakte ho.",
            style = MaterialTheme.typography.bodySmall
        )
        Button(onClick = { launcher.launch(perms.toTypedArray()) }, modifier = Modifier.fillMaxWidth()) {
            Text("Allow karo aur shuru karo")
        }
        TextButton(onClick = { vm.finishOnboarding(name) }) { Text("Abhi nahi, baad mein") }
    }
}
