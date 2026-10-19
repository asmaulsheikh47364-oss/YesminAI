package com.yesmin.ai

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object AppLauncher {
    sealed class R {
        data class Opened(val label: String) : R()
        object NotFound : R()
        data class Ambiguous(val labels: List<String>) : R()
        data class Failed(val msg: String) : R()
    }

    private val alias = mapOf(
        "maps" to "google maps", "map" to "google maps", "insta" to "instagram", "yt" to "youtube",
        "playstore" to "play store", "setting" to "settings"
    )

    fun open(ctx: Context, name: String): R {
        val pm = ctx.packageManager
        val q = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val apps = try {
            pm.queryIntentActivities(q, 0).map { it.loadLabel(pm).toString() to it.activityInfo.packageName }.distinctBy { it.second }
        } catch (e: Exception) {
            return R.Failed("App list nahi mil payi")
        }
        val n = (alias[name.lowercase()] ?: name).lowercase()
        val exact = apps.filter { it.first.lowercase() == n }
        val matches = if (exact.isNotEmpty()) exact else apps.filter {
            val l = it.first.lowercase()
            l.contains(n) || (l.length >= 3 && n.contains(l))
        }
        if (matches.isEmpty()) return R.NotFound
        if (matches.size > 1) return R.Ambiguous(matches.map { it.first }.distinct().take(5))
        val (label, pkg) = matches[0]
        val intent = pm.getLaunchIntentForPackage(pkg) ?: return R.Failed("Launch intent nahi mila")
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            ctx.startActivity(intent)
            R.Opened(label)
        } catch (e: Exception) {
            R.Failed(e.javaClass.simpleName)
        }
    }

    /** Opens one specific video in the YouTube app (or browser). YouTube decides whether it starts playing by itself. */
    fun openYouTubeVideo(ctx: Context, id: String): Boolean {
        val base = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/watch?v=" + id)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            ctx.startActivity(Intent(base).setPackage("com.google.android.youtube"))
            return true
        } catch (e: Exception) {
        }
        return try {
            ctx.startActivity(base)
            true
        } catch (e: Exception) {
            false
        }
    }

    /** Opens a YouTube search page. Does not (and cannot reliably) auto-play anything. */
    fun openYouTube(ctx: Context, query: String): Boolean {
        val uri = Uri.parse("https://www.youtube.com/results?search_query=" + URLEncoder.encode(query, "UTF-8"))
        val base = Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            ctx.startActivity(Intent(base).setPackage("com.google.android.youtube"))
            return true
        } catch (e: Exception) {
        }
        return try {
            ctx.startActivity(base)
            true
        } catch (e: Exception) {
            false
        }
    }
}

object PhoneInfo {
    fun battery(ctx: Context): String {
        val i = ctx.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            ?: return "Battery ki jaankari nahi mili."
        val lvl = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = i.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        val st = i.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        if (lvl < 0 || scale <= 0) return "Battery ki jaankari nahi mili."
        val charging = st == BatteryManager.BATTERY_STATUS_CHARGING || st == BatteryManager.BATTERY_STATUS_FULL
        return "Battery ${lvl * 100 / scale}% hai, " + if (charging) "charge ho rahi hai." else "charging nahi ho rahi."
    }

    fun network(ctx: Context): String {
        val cm = ctx.getSystemService(ConnectivityManager::class.java) ?: return "Network status nahi mila."
        val caps = cm.activeNetwork?.let { cm.getNetworkCapabilities(it) }
        if (caps == null || !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) return "Internet connected nahi hai."
        val type = when {
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "mobile data"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "ethernet"
            else -> "network"
        }
        val ok = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        return "Internet $type se connected hai" + if (ok) "." else ", par abhi internet access verify nahi hua."
    }

    fun time(): String =
        "Abhi " + SimpleDateFormat("EEEE, d MMMM yyyy, hh:mm a", Locale.getDefault()).format(Date()) + " hai."

    fun device(): String = "Device: ${Build.MANUFACTURER} ${Build.MODEL}, Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})."

    private fun gb(b: Long): String = String.format(Locale.US, "%.1f GB", b / 1073741824.0)

    fun storage(): String = try {
        val s = android.os.StatFs(android.os.Environment.getDataDirectory().path)
        "Storage: ${gb(s.totalBytes - s.availableBytes)} used / ${gb(s.totalBytes)} total (${gb(s.availableBytes)} khaali)"
    } catch (e: Exception) {
        "Storage ki jaankari nahi mili."
    }

    fun ram(ctx: Context): String = try {
        val am = ctx.getSystemService(android.app.ActivityManager::class.java)
        val mi = android.app.ActivityManager.MemoryInfo()
        am.getMemoryInfo(mi)
        "RAM: ${gb(mi.totalMem - mi.availMem)} used / ${gb(mi.totalMem)} total"
    } catch (e: Exception) {
        "RAM ki jaankari nahi mili."
    }

    fun cpu(): String = "CPU: ${Runtime.getRuntime().availableProcessors()} cores (${Build.SUPPORTED_ABIS.firstOrNull() ?: "?"})"
}
