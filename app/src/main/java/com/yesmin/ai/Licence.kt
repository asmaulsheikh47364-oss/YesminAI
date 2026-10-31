package com.yesmin.ai

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Login + plan (Free / Plus / Pro) + free daily minutes. The server is the source of truth. */
object Entitlement {
    const val SUPA = "https://hwfifqyssabwwdjlbqty.supabase.co"
    const val KEY = "sb_publishable_p_qIHrnCIUqRK7l-rkDhRw_r-O0yIWW"
    const val SITE = "https://asmaulsheikh47364-oss.github.io/payal/"
    const val WA = "919321109411"

    var loggedIn by mutableStateOf(false)
    var email by mutableStateOf("")
    var tier by mutableStateOf("free")
    var freeLeft by mutableStateOf(600)
    var expiresAt by mutableStateOf("")
    var deviceLocked by mutableStateOf(false)
    var unlockAt by mutableStateOf("")
    var busy by mutableStateOf(false)
    var info by mutableStateOf("")
    var checked by mutableStateOf(false)

    private var access = ""
    private var refreshTok = ""
    private var accessExp = 0L
    private var sp: android.content.SharedPreferences? = null
    private var appCtx: Context? = null

    fun init(ctx: Context) {
        if (sp != null) return
        appCtx = ctx.applicationContext
        sp = ctx.applicationContext.getSharedPreferences("payal_licence", Context.MODE_PRIVATE)
        val s = sp!!
        refreshTok = s.getString("rt", null)?.let { SecureStore.decrypt(it) } ?: ""
        access = s.getString("at", null)?.let { SecureStore.decrypt(it) } ?: ""
        accessExp = s.getLong("exp", 0L)
        email = s.getString("email", "") ?: ""
        tier = s.getString("tier", "free") ?: "free"
        expiresAt = s.getString("tier_exp", "") ?: ""
        freeLeft = s.getInt("free_left", 600)
        loggedIn = refreshTok.isNotEmpty()
        // A cached paid plan is trusted only until its own expiry date.
        if (tier != "free" && expiredLocally()) tier = "free"
    }

    private fun expiredLocally(): Boolean {
        if (expiresAt.isBlank()) return true
        return try {
            val f = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.US)
            f.timeZone = java.util.TimeZone.getTimeZone("UTC")
            f.parse(expiresAt.take(19))!!.time < System.currentTimeMillis()
        } catch (e: Exception) { true }
    }

    fun deviceId(): String {
        val c = appCtx ?: return "unknown"
        return android.provider.Settings.Secure.getString(c.contentResolver, android.provider.Settings.Secure.ANDROID_ID) ?: "unknown"
    }

    // ---------- rules ----------
    fun usable(): Boolean = loggedIn && !deviceLocked && (tier != "free" || freeLeft > 0)
    fun canCamera(): Boolean = loggedIn && !deviceLocked && (tier == "plus" || tier == "pro")
    fun canLocation(): Boolean = canCamera()
    fun canPhone(): Boolean = loggedIn && !deviceLocked && tier == "pro"
    fun canScreen(): Boolean = canPhone()
    /** Gmail addresses Payal treats as her owner. Change this list later if needed. */
    private val OWNERS = listOf("asmaulsheikh47364@gmail.com")
    fun isOwner(): Boolean = loggedIn && email.trim().lowercase() in OWNERS

    fun tierName(): String = when (tier) { "pro" -> "Pro"; "plus" -> "Plus"; else -> "Free" }

    fun deny(what: String): String = when (what) {
        "phone" -> "This needs the Pro plan. Tell the user kindly in one short sentence that phone control is part of Payal Pro and they can upgrade from the Payal website."
        "screen" -> "Reading the screen needs the Pro plan. Tell the user kindly in one short sentence that it is part of Payal Pro."
        "location" -> "Location needs the Plus plan or Pro. Tell the user kindly in one short sentence that it is part of Payal Plus."
        "camera" -> "The camera needs the Plus plan or Pro. Tell the user kindly in one short sentence."
        else -> "Aaj ke free minute khatam ho gaye. Plan website par milta hai."
    }

    fun denyUser(what: String): String = when (what) {
        "phone" -> "Phone control (app kholna, call, WhatsApp, torch, volume...) Pro plan mein milta hai. Website par plan le sakte ho."
        "screen" -> "Screen padhna Pro plan mein milta hai."
        "location" -> "Location Plus ya Pro plan mein milti hai."
        "camera" -> "Camera Plus ya Pro plan mein chalta hai."
        else -> "Aaj ke 10 free minute khatam ho gaye. Kal fir milenge, ya Plus/Pro lekar bina rok-tok chalao."
    }

    fun promptNote(): String = when (tier) {
        "pro" -> " PLAN: the user has Payal Pro, everything is unlocked."
        "plus" -> " PLAN: the user has Payal Plus (no time limit, camera and location). Phone control, screen reading and Termux/coding tasks are only in Pro; if he asks for them, say so kindly and mention Pro once, without pushing."
        else -> " PLAN: the user is on the Free plan (chat and voice, 10 minutes a day). Phone control, camera, location, screen reading and coding tasks are in the paid plans: Plus Rs 49 a month (no time limit, camera, location) and Pro Rs 99 a month (everything including phone control). If a free user asks for such a thing, or the minutes are about to end, mention the plan once, kindly and honestly, with no pressure and no false claims. Never say you can do these things on the Free plan. Never say any photo creation is possible."
    }

    // ---------- login ----------
    fun loginUrl(): String = SUPA + "/auth/v1/authorize?provider=google&redirect_to=" + Uri.encode("payal://auth")

    fun startLogin(ctx: Context) {
        info = ""
        try {
            ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(loginUrl())).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: Exception) { info = "Browser nahi khul paaya." }
    }

    fun handleRedirect(ctx: Context, uri: Uri) {
        init(ctx)
        val frag = uri.fragment ?: uri.encodedQuery ?: ""
        val m = HashMap<String, String>()
        for (part in frag.split("&")) {
            val i = part.indexOf("=")
            if (i > 0) m[part.substring(0, i)] = Uri.decode(part.substring(i + 1).replace("+", " "))
        }
        val err = m["error_description"] ?: m["error"]
        if (err != null) { info = "Login nahi hua: " + err; return }
        val at = m["access_token"]
        val rt = m["refresh_token"]
        if (at.isNullOrEmpty() || rt.isNullOrEmpty()) { info = "Login ka jawab adhura aaya. Dobara try karo."; return }
        val exp = (m["expires_in"]?.toLongOrNull() ?: 3600L)
        saveTokens(at, rt, exp)
        email = emailFromJwt(at)
        sp?.edit()?.putString("email", email)?.apply()
        loggedIn = true
        refresh(ctx)
    }

    private fun emailFromJwt(jwt: String): String = try {
        val p = jwt.split(".")[1]
        val raw = android.util.Base64.decode(p, android.util.Base64.URL_SAFE or android.util.Base64.NO_PADDING or android.util.Base64.NO_WRAP)
        JSONObject(String(raw, Charsets.UTF_8)).optString("email", "")
    } catch (e: Exception) { "" }

    private fun saveTokens(at: String, rt: String, expIn: Long) {
        access = at
        refreshTok = rt
        accessExp = System.currentTimeMillis() + expIn * 1000L
        sp?.edit()?.putString("at", SecureStore.encrypt(at))?.putString("rt", SecureStore.encrypt(rt))?.putLong("exp", accessExp)?.apply()
    }

    fun logout() {
        access = ""; refreshTok = ""; accessExp = 0L
        loggedIn = false; email = ""; tier = "free"; freeLeft = 600; expiresAt = ""; deviceLocked = false; checked = false
        sp?.edit()?.clear()?.apply()
    }

    // ---------- network ----------
    private fun http(method: String, url: String, body: String?, bearer: String?): Pair<Int, String> {
        val c = URL(url).openConnection() as HttpURLConnection
        c.requestMethod = method
        c.connectTimeout = 15000
        c.readTimeout = 20000
        c.setRequestProperty("apikey", KEY)
        c.setRequestProperty("Content-Type", "application/json")
        if (bearer != null) c.setRequestProperty("Authorization", "Bearer " + bearer)
        if (body != null) {
            c.doOutput = true
            c.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
        }
        val code = c.responseCode
        val st = if (code >= 400) c.errorStream else c.inputStream
        val txt = st?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""
        return code to txt
    }

    private fun ensureToken(): Boolean {
        if (refreshTok.isEmpty()) return false
        if (access.isNotEmpty() && System.currentTimeMillis() < accessExp - 60000L) return true
        return try {
            val (code, txt) = http("POST", SUPA + "/auth/v1/token?grant_type=refresh_token", JSONObject().put("refresh_token", refreshTok).toString(), null)
            if (code == 200) {
                val j = JSONObject(txt)
                saveTokens(j.getString("access_token"), j.getString("refresh_token"), j.optLong("expires_in", 3600L))
                true
            } else {
                if (code == 400 || code == 401 || code == 403) {
                    // refresh token is dead: ask for login again
                    android.os.Handler(android.os.Looper.getMainLooper()).post { logout(); info = "Dobara login karo." }
                }
                false
            }
        } catch (e: Exception) { false }
    }

    private fun rpc(fn: String, args: JSONObject): JSONObject? {
        if (!ensureToken()) return null
        return try {
            var (code, txt) = http("POST", SUPA + "/rest/v1/rpc/" + fn, args.toString(), access)
            if (code == 401) {
                accessExp = 0L
                if (!ensureToken()) return null
                val r = http("POST", SUPA + "/rest/v1/rpc/" + fn, args.toString(), access)
                code = r.first; txt = r.second
            }
            if (code in 200..299) JSONObject(txt) else null
        } catch (e: Exception) { null }
    }

    /** Asks the server for plan, device state and free minutes left. */
    fun refresh(ctx: Context) {
        init(ctx)
        if (!loggedIn) return
        Thread {
            busy = true
            val j = rpc("app_status", JSONObject().put("p_device", deviceId()))
            busy = false
            if (j == null) return@Thread
            if (j.has("error")) { return@Thread }
            checked = true
            val dev = j.optJSONObject("device")
            if (dev != null && dev.optString("state") == "locked") {
                deviceLocked = true
                unlockAt = dev.optString("unlock_at", "")
            } else {
                deviceLocked = false
                unlockAt = ""
            }
            tier = j.optString("tier", "free")
            expiresAt = if (j.isNull("expires_at")) "" else j.optString("expires_at", "")
            freeLeft = j.optInt("free_seconds_left", 0)
            sp?.edit()?.putString("tier", tier)?.putString("tier_exp", expiresAt)?.putInt("free_left", freeLeft)?.apply()
        }.also { it.isDaemon = true }.start()
    }

    /** Tells the server how many seconds were used (free plan only). */
    fun reportUsed(seconds: Int) {
        if (seconds <= 0 || !loggedIn || tier != "free") return
        Thread {
            val j = rpc("use_time", JSONObject().put("p_device", deviceId()).put("p_seconds", seconds))
            if (j != null) {
                if (j.optString("error") == "device") { deviceLocked = true; return@Thread }
                if (!j.isNull("left")) {
                    freeLeft = j.optInt("left", freeLeft)
                    sp?.edit()?.putInt("free_left", freeLeft)?.apply()
                }
            }
        }.also { it.isDaemon = true }.start()
    }

    // ---------- help / payment problem messages ----------
    fun sendSupport(kind: String, plan: String, ref: String, msg: String, cb: (String) -> Unit) {
        Thread {
            val a = JSONObject().put("p_kind", kind).put("p_ref", ref).put("p_message", msg)
            if (kind == "payment") a.put("p_plan", plan) else a.put("p_plan", JSONObject.NULL)
            val j = rpc("support_send", a)
            val t = if (j == null) "Bhej nahi paaye. Internet dekho aur dobara try karo." else if (j.has("error")) j.optString("error") else "Bhej diya. Jaldi dekha jayega."
            cb(t)
        }.also { it.isDaemon = true }.start()
    }

    fun supportMine(cb: (List<String>) -> Unit) {
        Thread {
            val j = rpc("support_mine", JSONObject())
            val out = ArrayList<String>()
            val arr = j?.optJSONArray("items")
            if (arr != null) {
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    val done = o.optString("status") == "done"
                    val note = if (o.isNull("admin_note")) "" else o.optString("admin_note")
                    out.add((if (done) "Ho gaya: " else "Intezaar: ") + (if (note.isNotBlank()) note else if (o.optString("kind") == "payment") "payment ka masla" else "madad"))
                }
            }
            cb(out)
        }.also { it.isDaemon = true }.start()
    }
    // ---------- helpers for the UI ----------
    fun openPlans(ctx: Context) {
        try { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(SITE + "#plans")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (e: Exception) {}
    }

    fun openAccountPage(ctx: Context) {
        try { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(SITE + "account.html")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (e: Exception) {}
    }

    fun openWhatsApp(ctx: Context) {
        val msg = "Hi, mujhe Payal ke plan ke baare mein baat karni hai. Mera Gmail: " + email
        try { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/" + WA + "?text=" + Uri.encode(msg))).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (e: Exception) {}
    }

    fun minutesText(): String {
        val m = freeLeft / 60
        val s = freeLeft % 60
        return m.toString() + ":" + (if (s < 10) "0" else "") + s
    }

    fun expiryText(): String = if (expiresAt.length >= 10) expiresAt.take(10) else ""
}
