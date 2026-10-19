@file:OptIn(ExperimentalMaterial3Api::class)

package com.yesmin.ai

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp

@Composable
fun LockScreen(vm: AppViewModel) {
    val ctx = LocalContext.current
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    val bioOk = vm.biometricEnabled && Biometric.available(ctx)

    fun tryBio() {
        ctx.findActivity()?.let { Biometric.prompt(it, { vm.unlock() }, {}) }
    }
    LaunchedEffect(Unit) { if (bioOk) tryBio() }

    Column(
        Modifier.fillMaxSize().padding(28.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        val p = vm.activeProfile()
        NeonAvatar(110.dp, p.letter, p.c1, p.c2)
        Text("Yesmin AI locked hai", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 12.dp))
        Text("Apna PIN daalo", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(bottom = 12.dp))
        OutlinedTextField(
            value = pin,
            onValueChange = { if (it.length <= 8 && it.all { c -> c.isDigit() }) pin = it },
            singleLine = true,
            label = { Text("PIN") },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            modifier = Modifier.fillMaxWidth()
        )
        if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        Button(
            onClick = { error = vm.tryUnlock(pin) ?: ""; if (error.isNotBlank()) pin = "" },
            enabled = pin.length >= 4,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
        ) { Text("Unlock") }
        if (bioOk) TextButton(onClick = { tryBio() }) { Text("🖐 Fingerprint se kholo") }
    }
}
