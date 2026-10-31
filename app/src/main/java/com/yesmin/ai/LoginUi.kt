package com.yesmin.ai

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** First screen after the intro: sign in with Google (one Gmail = one phone). */
@Composable
fun LoginScreen() {
    val ctx = LocalContext.current
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Payal", fontSize = 36.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(8.dp))
        Text("Apne Gmail se login karo", fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(10.dp))
        Text(
            "Free plan mein roz 10 minute chat aur voice milti hai. Plus aur Pro mein app ki taraf se koi time limit nahi. Ek Gmail ek hi phone par chalta hai.",
            textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(22.dp))
        Button(onClick = { Entitlement.startLogin(ctx) }, modifier = Modifier.fillMaxWidth()) {
            Text("Google se login karo")
        }
        if (Entitlement.info.isNotBlank()) {
            Spacer(Modifier.height(12.dp))
            Text(Entitlement.info, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
        }
        Spacer(Modifier.height(18.dp))
        Text(
            "Login browser mein khulega. Login ke baad app apne aap wapas aa jayegi.",
            fontSize = 12.sp, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        TextButton(onClick = { Entitlement.openPlans(ctx) }) { Text("Plan aur daam dekho") }
    }
}

/** Shown when this Gmail is already active on another phone. */
@Composable
fun DeviceLockedScreen() {
    val ctx = LocalContext.current
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Naya phone", fontSize = 26.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(10.dp))
        Text(
            "Yeh Gmail (" + Entitlement.email + ") abhi kisi doosre phone par chal raha hai. Naye phone par 7 din ke baad apne aap chalu ho jayega." +
                (if (Entitlement.unlockAt.length >= 10) " Chalu hone ki date: " + Entitlement.unlockAt.take(10) + "." else ""),
            textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(18.dp))
        Button(onClick = { Entitlement.refresh(ctx) }, modifier = Modifier.fillMaxWidth()) { Text("Dobara check karo") }
        OutlinedButton(onClick = { Entitlement.openWhatsApp(ctx) }, modifier = Modifier.fillMaxWidth()) { Text("Mujhse WhatsApp par baat karo") }
        TextButton(onClick = { Entitlement.logout() }) { Text("Logout") }
    }
}

/** Small bar at the top of the app: minutes ended, minutes running low, or plan ending soon. */
@Composable
fun LicenceBanner() {
    val ctx = LocalContext.current
    if (!Entitlement.loggedIn || Entitlement.deviceLocked) return
    val t = Entitlement.tier
    var text = ""
    var urgent = false
    if (t == "free" && Entitlement.freeLeft <= 0) {
        text = "Aaj ke 10 free minute khatam. Plus Rs 49 / Pro Rs 99 se bina rok-tok chalao. Dekhne ke liye dabao."
        urgent = true
    } else if (t == "free" && Entitlement.freeLeft <= 120) {
        text = "Free minute bache: " + Entitlement.minutesText() + ". Plan dekhne ke liye dabao."
    } else if (t != "free" && Entitlement.expiryText().isNotBlank() && daysLeft() in 0..3) {
        text = Entitlement.tierName() + " plan " + (daysLeft() + 1) + " din mein khatam. Badhane ke liye dabao."
    }
    if (text.isBlank()) return
    val bg = if (urgent) Color(0xFFB3261E) else Color(0xFFB26A00)
    Row(
        Modifier.fillMaxWidth().background(bg).clickable { Entitlement.openPlans(ctx) }.padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text, color = Color.White, fontSize = 13.sp, modifier = Modifier.weight(1f))
    }
}

private fun daysLeft(): Int = try {
    val f = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.US)
    f.timeZone = java.util.TimeZone.getTimeZone("UTC")
    val ms = f.parse(Entitlement.expiresAt.take(19))!!.time - System.currentTimeMillis()
    (ms / 86400000L).toInt()
} catch (e: Exception) { 99 }

/** Replaces a screen that the current plan does not include. */
@Composable
fun FeatureLocked(what: String) {
    val ctx = LocalContext.current
    val title = if (what == "camera") "Camera Plus ya Pro mein" else "Yeh Pro mein milta hai"
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(title, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(10.dp))
        Text(Entitlement.denyUser(what), textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(18.dp))
        Button(onClick = { Entitlement.openPlans(ctx) }) { Text("Plan dekho") }
        TextButton(onClick = { Entitlement.openWhatsApp(ctx) }) { Text("Mujhse WhatsApp par baat karo") }
    }
}

/** Account card for the Settings screen. */
@Composable
fun AccountCard() {
    val ctx = LocalContext.current
    androidx.compose.material3.Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Mera account", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Text("Gmail: " + Entitlement.email)
            Text("Plan: " + Entitlement.tierName() + (if (Entitlement.tier != "free" && Entitlement.expiryText().isNotBlank()) " (" + Entitlement.expiryText() + " tak)" else ""))
            if (Entitlement.tier == "free") Text("Aaj ke free minute bache: " + Entitlement.minutesText())
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { Entitlement.openPlans(ctx) }) { Text("Plan lo") }
                OutlinedButton(onClick = { Entitlement.refresh(ctx) }) { Text("Refresh") }
                TextButton(onClick = { Entitlement.logout() }) { Text("Logout") }
            }
        }
    }
}
