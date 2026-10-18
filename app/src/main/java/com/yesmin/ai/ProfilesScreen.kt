@file:OptIn(ExperimentalMaterial3Api::class)

package com.yesmin.ai

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
private fun ProfileCard(p: Profile, selected: Boolean, modifier: Modifier, onSelect: () -> Unit, onPreview: () -> Unit) {
    Card(
        onClick = onSelect,
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(Modifier.fillMaxWidth().padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier.size(68.dp).clip(CircleShape).background(Brush.linearGradient(listOf(p.c1, p.c2))),
                contentAlignment = Alignment.Center
            ) { Text(p.letter, fontSize = 30.sp, fontWeight = FontWeight.Bold, color = androidx.compose.ui.graphics.Color.White) }
            Text(p.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
            Text("(" + p.tag + ")", style = MaterialTheme.typography.bodySmall)
            if (selected) Text("✓ Active", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
            TextButton(onClick = onPreview) { Text("🔊 Sunao") }
        }
    }
}

@Composable
fun ProfilesScreen(vm: AppViewModel) {
    var cn by remember { mutableStateOf(vm.customName) }
    var ct by remember { mutableStateOf(vm.customTone) }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("AI Personalities", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text("Apni pasand ki saathi chuno. Naam, bolne ka andaaz aur awaaz sab badal jaate hain. Sunne ke liye 🔊 dabao.", style = MaterialTheme.typography.bodySmall)
        Profiles.all.chunked(2).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { p ->
                    ProfileCard(p, vm.profileId == p.id, Modifier.weight(1f), { vm.changeProfile(p.id) }, { vm.previewProfile(p) })
                }
            }
        }
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Apni khud ki personality", fontWeight = FontWeight.Bold)
                OutlinedTextField(
                    value = cn, onValueChange = { cn = it.take(20) }, singleLine = true,
                    label = { Text("Naam") }, modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = ct, onValueChange = { ct = it.take(200) },
                    label = { Text("Bolne ka andaaz (jaise: mazaakiya aur dost jaisi)") }, minLines = 2, modifier = Modifier.fillMaxWidth()
                )
                Button(onClick = { vm.saveCustom(cn, ct) }, enabled = cn.isNotBlank() && ct.isNotBlank()) {
                    Text(if (vm.profileId == "custom") "Update karo (Active)" else "Yeh personality use karo")
                }
                Text(
                    "Custom andaaz sirf bolne ka tarika badalta hai. Sachchai, privacy aur safety ke niyam har personality mein same rehte hain, aur Yesmin kabhi insaan hone ka dawa nahi karti.",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}
