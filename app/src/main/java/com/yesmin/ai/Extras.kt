package com.yesmin.ai

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.location.Geocoder
import android.location.LocationManager
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.WindowManager
import android.widget.TextView
import androidx.core.content.ContextCompat

/** Live loudness of Yesmin's own voice (0..1); the Home portrait moves its mouth with it. */
object VoiceLevel {
    @Volatile var out = 0f
}

/** Prompts used when Yesmin looks at a photo or at the camera. */
object VisionPrompts {
    const val READ = "You are a very careful photo and document reader. First read EVERY piece of text in the image exactly as written (Hindi, English, handwriting, numbers, dates, stamps, table rows and columns, attendance or hazri cards, bills, forms). Write the text out in order, then explain in simple Hindi what it means and what the user should know. If a part is blurry or unclear, say exactly which part and do not guess names or numbers. If there is no text, describe the objects, people, place and situation clearly and completely."
    const val Q = "Is photo ko dhyan se padho. Jo kuch likha hai wo pura nikalo aur phir aasaan Hindi mein samjhao."
    const val CAMERA = "You are Yesmin looking through the phone camera right now. Say in 2 or 3 short, natural Hindi sentences what this is and one useful thing about it (what it does, how to use it, or what is written on it). If there is text, read the important part. Do not mention that this is a photo."
}

/** Finds the phone's last known position and turns it into words. */
object LocationHelper {
    fun describe(ctx: Context): String {
        val fine = ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!fine && !coarse) return "Location permission nahi mili. Yesmin AI ke Setup mein Location allow karo."
        return try {
            val lm = ctx.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            var best: android.location.Location? = null
            for (p in lm.getProviders(true)) {
                val l = try { lm.getLastKnownLocation(p) } catch (e: SecurityException) { null }
                if (l != null && (best == null || l.time > best.time)) best = l
            }
            if (best == null) return "Location abhi nahi mili. GPS on karke phir bolo."
            var addr = ""
            try {
                val list = Geocoder(ctx, java.util.Locale("hi", "IN")).getFromLocation(best.latitude, best.longitude, 1)
                if (list != null && list.isNotEmpty()) addr = list[0].getAddressLine(0) ?: ""
            } catch (e: Exception) {
            }
            "Latitude " + best.latitude + ", longitude " + best.longitude + (if (addr.isNotBlank()) ". Address: " + addr else "") +
                ". Map link: https://maps.google.com/?q=" + best.latitude + "," + best.longitude
        } catch (e: Exception) {
            "Location nahi mil paayi: " + (e.message ?: "")
        }
    }
}

/** Floating glass panel that shows what the Termux and screen agents are doing. It never takes touches. */
object WorkOverlay {
    private var view: TextView? = null
    private var wm: WindowManager? = null
    private val lines = ArrayList<String>()
    private val main = Handler(Looper.getMainLooper())
    private val hideRun = Runnable { hide() }

    fun enabled(ctx: Context): Boolean =
        ctx.getSharedPreferences("yesmin_ui", Context.MODE_PRIVATE).getBoolean("glass", true)

    fun setEnabled(ctx: Context, on: Boolean) {
        ctx.getSharedPreferences("yesmin_ui", Context.MODE_PRIVATE).edit().putBoolean("glass", on).apply()
        if (!on) main.post { hide() }
    }

    fun log(ctx: Context, line: String) {
        val app = ctx.applicationContext
        if (!enabled(app) || !GlowOverlay.canShow(app)) return
        main.post {
            try {
                lines.add(line.take(90))
                while (lines.size > 6) lines.removeAt(0)
                show(app)
                main.removeCallbacks(hideRun)
                main.postDelayed(hideRun, 9000)
            } catch (e: Exception) {
            }
        }
    }

    private fun show(app: Context) {
        var v = view
        if (v == null) {
            v = TextView(app)
            v.setTextColor(Color.WHITE)
            v.textSize = 13f
            v.setPadding(36, 24, 36, 24)
            val bg = GradientDrawable()
            bg.cornerRadius = 48f
            bg.setColor(Color.argb(190, 18, 24, 40))
            bg.setStroke(2, Color.argb(110, 255, 255, 255))
            v.background = bg
            v.alpha = 0.78f
            val w = app.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            val lp = WindowManager.LayoutParams(
                (app.resources.displayMetrics.widthPixels * 0.92f).toInt(),
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                PixelFormat.TRANSLUCENT
            )
            lp.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            lp.y = 110
            w.addView(v, lp)
            wm = w
            view = v
        }
        v.text = lines.joinToString(System.lineSeparator())
    }

    fun hide() {
        try {
            val v = view
            if (v != null) wm?.removeView(v)
        } catch (e: Exception) {
        }
        view = null
        lines.clear()
    }
}
