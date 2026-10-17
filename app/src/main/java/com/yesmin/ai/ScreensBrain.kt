@file:OptIn(ExperimentalMaterial3Api::class)

package com.yesmin.ai

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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import java.util.Locale

@Composable
fun BrainScreen(vm: AppViewModel) {
    var key by remember { mutableStateOf("") }
    var custom by remember { mutableStateOf(vm.model) }
    var sample by remember { mutableStateOf("Namaste Yesmin, ek line mein apna intro do.") }
    LaunchedEffect(vm.brainStatus) { if (vm.brainStatus == BrainStatus.Connected) key = "" }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("YESMIN AI BRAIN", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text("Powered by Gemini. Built for your everyday life.")

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Active provider: Google Gemini", fontWeight = FontWeight.Bold)
                val (label, color) = when (vm.brainStatus) {
                    BrainStatus.Connected -> "● Connected" to Color(0xFF2E7D32)
                    BrainStatus.Error -> "● Error" to Color(0xFFC62828)
                    BrainStatus.NotConnected -> "● Not Connected" to Color(0xFF757575)
                }
                Text(label, color = color, fontWeight = FontWeight.Bold)
                if (vm.brainMessage.isNotBlank()) Text(vm.brainMessage)
                Text("Saved key: " + if (vm.hasKey) "haan (Keystore se encrypted)" else "nahi")
                Text("Model: ${vm.model}")
                if (vm.busy) CircularProgressIndicator()
            }
        }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Gemini API key", fontWeight = FontWeight.Bold)
                OutlinedTextField(
                    value = key, onValueChange = { key = it }, modifier = Modifier.fillMaxWidth(),
                    singleLine = true, label = { Text(if (vm.hasKey) "Nayi key (replace karne ke liye)" else "API key") },
                    visualTransformation = PasswordVisualTransformation()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { vm.saveAndTest(key.trim()) }, enabled = !vm.busy) { Text("Save & Test") }
                    OutlinedButton(onClick = { vm.testSaved() }, enabled = !vm.busy && vm.hasKey) { Text("Test") }
                    OutlinedButton(onClick = { vm.removeKey() }, enabled = !vm.busy && vm.hasKey) { Text("Remove") }
                }
                Text(
                    "Key Google AI Studio se milti hai. Yeh sirf is phone par encrypted rehti hai aur kabhi log nahi hoti. " +
                        "Dhyan rahe: agar aap key ko kisi APK ke andar daal kar baantte ho to nikaali ja sakti hai — production app ke liye apna secure backend use karo.",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Model", fontWeight = FontWeight.Bold)
                if (vm.models.isEmpty()) {
                    Text("Key test karne ke baad yahan Google ki asli supported models list aayegi. Tab tak neeche naam khud likh sakte ho.", style = MaterialTheme.typography.bodySmall)
                }
                vm.models.take(15).forEach { m ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = vm.model == m, onClick = { vm.setModelName(m); custom = m })
                        Text(m)
                    }
                }
                OutlinedTextField(
                    value = custom, onValueChange = { custom = it }, modifier = Modifier.fillMaxWidth(),
                    singleLine = true, label = { Text("Model ka naam") }
                )
                OutlinedButton(onClick = { vm.setModelName(custom) }) { Text("Yeh model use karo") }
                Text("Model badalne ke baad 'Test' dobara chalao.", style = MaterialTheme.typography.bodySmall)
            }
        }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text("Temperature: " + String.format(Locale.US, "%.1f", vm.temperature), fontWeight = FontWeight.Bold)
                Slider(value = vm.temperature, onValueChange = { vm.changeTemperature(it) }, valueRange = 0f..2f)
                Text("Kam = seedha/pakka jawab, zyada = creative.", style = MaterialTheme.typography.bodySmall)
            }
        }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Sample message se test", fontWeight = FontWeight.Bold)
                OutlinedTextField(value = sample, onValueChange = { sample = it }, modifier = Modifier.fillMaxWidth(), minLines = 2)
                Button(onClick = { vm.sampleTest(sample) }, enabled = !vm.busy && vm.hasKey) { Text("Gemini ko bhejo") }
                if (vm.sampleReply.isNotBlank()) RichText(vm.sampleReply)
                Text("Yeh message Google Gemini ko bheja jaata hai.", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
