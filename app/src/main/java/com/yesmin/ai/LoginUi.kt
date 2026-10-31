package com.yesmin.ai

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

private val LoginFonts = listOf(FontFamily.Cursive, FontFamily.Serif, FontFamily.Monospace, FontFamily.SansSerif)

/** Slowly drifting coloured glows on a deep night-blue background. */
@Composable
fun AnimatedBackdrop(content: @Composable () -> Unit) {
    val t = rememberInfiniteTransition(label = "bg")
    val p by t.animateFloat(0f, 1f, infiniteRepeatable(tween(9000, easing = LinearEasing), RepeatMode.Reverse), label = "p")
    val q by t.animateFloat(0f, 1f, infiniteRepeatable(tween(13000, easing = LinearEasing), RepeatMode.Reverse), label = "q")
    Box(Modifier.fillMaxSize()) {
        Canvas(Modifier.fillMaxSize()) {
            drawRect(Brush.verticalGradient(listOf(Color(0xFF050B1A), Color(0xFF0B1B3A), Color(0xFF1B0B3A))))
            val w = size.width
            val h = size.height
            val c1 = Offset(w * (0.15f + 0.6f * p), h * (0.10f + 0.25f * q))
            val c2 = Offset(w * (0.85f - 0.6f * q), h * (0.45f + 0.2f * p))
            val c3 = Offset(w * (0.3f + 0.4f * q), h * (0.85f - 0.2f * p))
            drawCircle(Brush.radialGradient(listOf(Color(0x7722D3EE), Color.Transparent), center = c1, radius = w * 0.8f), radius = w * 0.8f, center = c1)
            drawCircle(Brush.radialGradient(listOf(Color(0x77A855F7), Color.Transparent), center = c2, radius = w * 0.8f), radius = w * 0.8f, center = c2)
            drawCircle(Brush.radialGradient(listOf(Color(0x66F472B6), Color.Transparent), center = c3, radius = w * 0.7f), radius = w * 0.7f, center = c3)
        }
        content()
    }
}

/** Fades in and rises a little, one after another. */
@Composable
private fun Rise(show: Boolean, delayMs: Int, content: @Composable () -> Unit) {
    AnimatedVisibility(
        visible = show,
        enter = fadeIn(tween(700, delayMillis = delayMs)) + slideInVertically(tween(700, delayMillis = delayMs)) { it / 3 }
    ) { content() }
}

/** First screen after the intro: sign in with Google (one Gmail = one phone). */
@Composable
fun LoginScreen() {
    val ctx = LocalContext.current
    var show by remember { mutableStateOf(false) }
    var fi by remember { mutableStateOf(0) }
    var li by remember { mutableStateOf(0) }
    var shown by remember { mutableStateOf("") }
    val lines = listOf("Bolo, wo sunegi.", "Likho, wo samjhegi.", "Tumhari apni AI saathi.", "Tumhari bhasha, uski awaaz.")
    LaunchedEffect(Unit) {
        delay(150)
        show = true
        while (true) {
            delay(1700)
            fi = (fi + 1) % LoginFonts.size
        }
    }
    LaunchedEffect(Unit) {
        while (true) {
            val s = lines[li]
            for (i in 1..s.length) { shown = s.take(i); delay(70) }
            delay(1500)
            for (i in s.length downTo 0) { shown = s.take(i); delay(25) }
            li = (li + 1) % lines.size
        }
    }
    val pulse = rememberInfiniteTransition(label = "pulse")
    val sc by pulse.animateFloat(1f, 1.04f, infiniteRepeatable(tween(1100, easing = LinearEasing), RepeatMode.Reverse), label = "sc")
    AnimatedBackdrop {
        Column(
            Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 22.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(36.dp))
            Box(Modifier.height(84.dp), contentAlignment = Alignment.Center) {
                Crossfade(targetState = fi, animationSpec = tween(700), label = "font") { i ->
                    Text(
                        "Payal", fontFamily = LoginFonts[i], fontSize = 58.sp, fontWeight = FontWeight.Bold,
                        fontStyle = if (i == 1) FontStyle.Italic else FontStyle.Normal, color = Color(0xFF22D3EE),
                        style = TextStyle(shadow = Shadow(Color(0xFF22D3EE), Offset(0f, 0f), 28f))
                    )
                }
            }
            Box(Modifier.height(34.dp), contentAlignment = Alignment.Center) {
                Text(shown + "|", fontFamily = LoginFonts[li % LoginFonts.size], fontSize = 21.sp, color = Color(0xFFF1F5F9), textAlign = TextAlign.Center)
            }
            Spacer(Modifier.height(14.dp))
            Rise(show, 200) {
                Text(
                    "Apne Gmail se login karo. Free mein roz 10 minute chat aur voice. Plus aur Pro mein app ki taraf se koi time limit nahi. Ek Gmail ek hi phone par chalta hai.",
                    textAlign = TextAlign.Center, color = Color(0xFFCBD5E1), fontSize = 14.sp
                )
            }
            Spacer(Modifier.height(22.dp))
            Rise(show, 450) {
                Button(
                    onClick = { Entitlement.startLogin(ctx) },
                    modifier = Modifier.fillMaxWidth().height(54.dp).graphicsLayer { scaleX = sc; scaleY = sc },
                    shape = RoundedCornerShape(27.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF22D3EE), contentColor = Color(0xFF04202A))
                ) { Text("Google se login karo", fontWeight = FontWeight.Bold, fontSize = 17.sp) }
            }
            if (Entitlement.info.isNotBlank()) {
                Spacer(Modifier.height(10.dp))
                Text(Entitlement.info, color = Color(0xFFFCA5A5), textAlign = TextAlign.Center)
            }
            Spacer(Modifier.height(6.dp))
            Text("Login browser mein khulega, phir app apne aap wapas aa jayegi.", fontSize = 12.sp, textAlign = TextAlign.Center, color = Color(0xFF94A3B8))
            Spacer(Modifier.height(26.dp))
            Rise(show, 750) { PlansSection(true) }
            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable
private fun PlanCard(dark: Boolean, id: String, name: String, price: String, mrp: String?, accent: Color, tag: String?, items: List<String>) {
    val fg = if (dark) Color.White else MaterialTheme.colorScheme.onSurface
    val sub = if (dark) Color(0xFFCBD5E1) else MaterialTheme.colorScheme.onSurfaceVariant
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = if (dark) Color(0x26FFFFFF) else MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(if (tag != null) 2.dp else 1.dp, accent.copy(alpha = if (tag != null) 1f else 0.55f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(name, fontWeight = FontWeight.Bold, fontSize = 20.sp, color = accent)
                if (tag != null) {
                    Spacer(Modifier.padding(start = 8.dp))
                    Text(tag, fontSize = 11.sp, color = Color(0xFF04202A), modifier = Modifier.background(accent, RoundedCornerShape(10.dp)).padding(horizontal = 8.dp, vertical = 2.dp))
                }
                if (Entitlement.loggedIn && Entitlement.tier == id) {
                    Spacer(Modifier.padding(start = 8.dp))
                    Text("Aapka plan", fontSize = 11.sp, color = sub)
                }
                Spacer(Modifier.weight(1f))
                if (mrp != null) Text(mrp, color = sub, textDecoration = TextDecoration.LineThrough, fontSize = 14.sp)
                Spacer(Modifier.padding(start = 6.dp))
                Text(price, fontWeight = FontWeight.Bold, fontSize = 22.sp, color = fg)
                Text(if (id == "free") "" else " /mahina", fontSize = 12.sp, color = sub)
            }
            for (line in items) Text("• " + line, fontSize = 13.sp, color = fg)
        }
    }
}

/** The three plans, shown inside the app. Buying happens on the website. */
@Composable
fun PlansSection(dark: Boolean) {
    val ctx = LocalContext.current
    val fg = if (dark) Color.White else MaterialTheme.colorScheme.onSurface
    val sub = if (dark) Color(0xFF94A3B8) else MaterialTheme.colorScheme.onSurfaceVariant
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Plan aur daam", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = fg)
        PlanCard(dark, "free", "Free", "₹0", null, Color(0xFF94A3B8), null, listOf("Chat aur voice baat-cheet", "Roz 10 minute (raat 12 baje naye)", "Chaar kirdaar aur awaazein"))
        PlanCard(dark, "plus", "Plus", "₹49", "₹199", Color(0xFF22D3EE), "Sabse pasand", listOf("Chat aur voice, app ki taraf se koi time limit nahi", "Camera se samajh aur photo mein likha padhna", "Location aur raasta"))
        PlanCard(dark, "pro", "Pro", "₹99", "₹399", Color(0xFFFBBF24), "Sab kuch", listOf("Plus ki sab cheezein", "Phone control: YouTube, WhatsApp, call, torch, volume", "Screen padhna, Termux aur coding ka kaam"))
        Button(
            onClick = { Entitlement.openPlans(ctx) },
            modifier = Modifier.fillMaxWidth().height(50.dp),
            shape = RoundedCornerShape(25.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFBBF24), contentColor = Color(0xFF2A1B00))
        ) { Text("Website par plan kharido", fontWeight = FontWeight.Bold) }
        Text("30 din ka plan, auto-pay nahi. Payment website par hota hai, phir yahi Gmail se ek tap mein chalu. Time limit sirf app ki taraf se hata hai; AI ka kharcha aapki apni API key se hota hai.", fontSize = 11.sp, color = sub, textAlign = TextAlign.Center)
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
        text = "Aaj ke 10 free minute khatam. Plus ₹49 / Pro ₹99 se bina rok-tok chalao. Dekhne ke liye dabao."
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
            Text(if (Entitlement.isOwner()) "Pehchaan: Owner (Asmaul) - Payal aapko malik ki tarah pehchanti hai" else "Pehchaan: aam user")
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

/** Send a help or payment-problem message to the owner. */
@Composable
fun SupportCard() {
    val ctx = LocalContext.current
    var kind by remember { mutableStateOf("payment") }
    var plan by remember { mutableStateOf("plus") }
    var ref by remember { mutableStateOf("") }
    var msg by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("") }
    var mine by remember { mutableStateOf(listOf<String>()) }
    LaunchedEffect(Unit) { Entitlement.supportMine { mine = it } }
    androidx.compose.material3.Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Madad ya payment ka masla", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Text("Paisa kat gaya par plan nahi mila, ya koi aur dikkat? Yahan likho, dekh kar plan chalu kar diya jayega.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (kind == "payment") Button(onClick = { kind = "payment" }) { Text("Paisa kata") } else OutlinedButton(onClick = { kind = "payment" }) { Text("Paisa kata") }
                if (kind == "help") Button(onClick = { kind = "help" }) { Text("Aur madad") } else OutlinedButton(onClick = { kind = "help" }) { Text("Aur madad") }
            }
            if (kind == "payment") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Plan:")
                    if (plan == "plus") Button(onClick = { plan = "plus" }) { Text("Plus") } else OutlinedButton(onClick = { plan = "plus" }) { Text("Plus") }
                    if (plan == "pro") Button(onClick = { plan = "pro" }) { Text("Pro") } else OutlinedButton(onClick = { plan = "pro" }) { Text("Pro") }
                }
                androidx.compose.material3.OutlinedTextField(value = ref, onValueChange = { ref = it.take(100) }, label = { Text("Payment ID ya UTR") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            }
            androidx.compose.material3.OutlinedTextField(value = msg, onValueChange = { msg = it.take(1000) }, label = { Text("Aapka sandesh") }, minLines = 2, modifier = Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    status = "Bhej rahe hain..."
                    val send = { extra: String ->
                        Entitlement.sendSupport(kind, plan, ref, msg) { s ->
                            status = (if (extra.isNotEmpty()) extra + " " else "") + s
                            if (s.startsWith("Bhej diya")) { ref = ""; msg = ""; Entitlement.supportMine { mine = it } }
                        }
                    }
                    if (kind == "payment" && ref.trim().startsWith("pay_")) {
                        status = "Payment jaanch rahe hain..."
                        Entitlement.claimPayment(ctx, ref.trim()) { ok, t2 ->
                            if (ok) { status = t2; ref = ""; msg = "" } else send(t2)
                        }
                    } else send("")
                }) { Text("Bhejo") }
                OutlinedButton(onClick = { Entitlement.openWhatsApp(ctx) }) { Text("WhatsApp") }
            }
            if (status.isNotBlank()) Text(status, fontSize = 13.sp)
            for (line in mine) Text("• " + line, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
