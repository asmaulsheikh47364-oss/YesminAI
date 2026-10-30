package com.yesmin.ai

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.location.Geocoder
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat

/** Live loudness of her own voice (0..1); the Home orb reacts to it. */
object VoiceLevel {
    @Volatile var out = 0f
}

/** Prompts used when she looks at a photo or at the camera. */
object VisionPrompts {
    const val READ = "You are a very careful photo and document reader. First read EVERY piece of text in the image exactly as written (Hindi, English, handwriting, numbers, dates, stamps, table rows and columns, attendance or hazri cards, bills, forms). Write the text out in order, then explain in simple Hindi what it means and what the user should know. If a part is blurry or unclear, say exactly which part and do not guess names or numbers. If there is no text, describe the objects, people, place and situation clearly and completely."
    const val Q = "Is photo ko dhyan se padho. Jo kuch likha hai wo pura nikalo aur phir aasaan Hindi mein samjhao."
    const val CAMERA = "You are looking through the phone camera right now. Say in 2 or 3 short, natural Hindi sentences what this is and one useful thing about it (what it does, how to use it, or what is written on it). If there is text, read the important part. Do not mention that this is a photo."
}

/** Gets a fresh position (waits up to about 12 seconds for GPS) and turns it into words. */
object LocationHelper {
    fun describe(ctx: Context): String {
        val fine = ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!fine && !coarse) return "Location ki permission nahi mili. Setup mein Location allow karo."
        return try {
            val lm = ctx.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            val on = lm.getProviders(true)
            if (on.isEmpty()) return "Phone ka Location band hai. Location ON karne ko bolo."
            var best: android.location.Location? = null
            for (p in on) {
                val l = try { lm.getLastKnownLocation(p) } catch (e: SecurityException) { null }
                if (l != null && (best == null || l.time > best.time)) best = l
            }
            val fresh = best != null && System.currentTimeMillis() - best.time < 120000L
            if (!fresh) {
                val latch = java.util.concurrent.CountDownLatch(1)
                val box = arrayOfNulls<android.location.Location>(1)
                val listener = object : LocationListener {
                    override fun onLocationChanged(l: android.location.Location) {
                        box[0] = l
                        latch.countDown()
                    }
                    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
                    override fun onProviderEnabled(provider: String) {}
                    override fun onProviderDisabled(provider: String) {}
                }
                Handler(Looper.getMainLooper()).post {
                    for (p in on) {
                        try {
                            lm.requestSingleUpdate(p, listener, Looper.getMainLooper())
                        } catch (e: Exception) {
                        }
                    }
                }
                latch.await(12, java.util.concurrent.TimeUnit.SECONDS)
                Handler(Looper.getMainLooper()).post { try { lm.removeUpdates(listener) } catch (e: Exception) {} }
                val got = box[0]
                if (got != null) best = got
            }
            val b = best ?: return "Location abhi nahi mili. Khuli jagah par GPS on karke dobara bolo."
            var addr = ""
            try {
                val list = Geocoder(ctx, java.util.Locale("hi", "IN")).getFromLocation(b.latitude, b.longitude, 1)
                if (list != null && list.isNotEmpty()) addr = list[0].getAddressLine(0) ?: ""
            } catch (e: Exception) {
            }
            "Latitude " + b.latitude + ", longitude " + b.longitude + (if (addr.isNotBlank()) ". Address: " + addr else "") +
                ". Map link: https://maps.google.com/?q=" + b.latitude + "," + b.longitude
        } catch (e: Exception) {
            "Location nahi mil paayi: " + (e.message ?: "")
        }
    }
}

/** Floating glass panel that shows what the Termux and screen agents are doing. It never takes touches. */
object WorkOverlay {
    private var root: LinearLayout? = null
    private var body: TextView? = null
    private var dot: View? = null
    private var wm: WindowManager? = null
    private var pulse: android.animation.ValueAnimator? = null
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
                lines.add(line.replace("Step ", "").take(84))
                while (lines.size > 5) lines.removeAt(0)
                show(app)
                render()
                main.removeCallbacks(hideRun)
                main.postDelayed(hideRun, 9000)
            } catch (e: Exception) {
            }
        }
    }

    private fun build(app: Context): LinearLayout {
        val d = app.resources.displayMetrics.density
        val box = LinearLayout(app)
        box.orientation = LinearLayout.VERTICAL
        box.setPadding((16 * d).toInt(), (12 * d).toInt(), (16 * d).toInt(), (14 * d).toInt())
        val bg = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(Color.argb(238, 14, 22, 44), Color.argb(238, 38, 22, 70)))
        bg.cornerRadius = 26 * d
        bg.setStroke((1.2f * d).toInt().coerceAtLeast(1), Color.argb(130, 150, 200, 255))
        box.background = bg

        val head = LinearLayout(app)
        head.orientation = LinearLayout.HORIZONTAL
        head.gravity = Gravity.CENTER_VERTICAL
        val dt = View(app)
        val dg = GradientDrawable()
        dg.shape = GradientDrawable.OVAL
        dg.setColor(Color.rgb(52, 211, 153))
        dt.background = dg
        head.addView(dt, LinearLayout.LayoutParams((8 * d).toInt(), (8 * d).toInt()))
        val title = TextView(app)
        title.text = "  PAYAL  ·  kaam chal raha hai"
        title.setTextColor(Color.rgb(125, 211, 252))
        title.textSize = 11f
        title.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        title.letterSpacing = 0.12f
        head.addView(title)
        box.addView(head)

        val t = TextView(app)
        t.textSize = 12.5f
        t.typeface = Typeface.MONOSPACE
        t.setLineSpacing(2 * d, 1f)
        t.setPadding(0, (8 * d).toInt(), 0, 0)
        box.addView(t)
        body = t
        dot = dt
        val an = android.animation.ValueAnimator.ofFloat(0.25f, 1f)
        an.duration = 700
        an.repeatMode = android.animation.ValueAnimator.REVERSE
        an.repeatCount = android.animation.ValueAnimator.INFINITE
        an.addUpdateListener { dt.alpha = it.animatedValue as Float }
        an.start()
        pulse = an
        return box
    }

    private fun show(app: Context) {
        if (root != null) return
        val d = app.resources.displayMetrics.density
        val v = build(app)
        v.alpha = 0f
        v.translationY = -18 * d
        val w = app.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val lp = WindowManager.LayoutParams(
            (app.resources.displayMetrics.widthPixels * 0.90f).toInt(),
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSLUCENT
        )
        lp.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
        lp.y = (44 * d).toInt()
        lp.alpha = 0.8f
        w.addView(v, lp)
        wm = w
        root = v
        v.animate().alpha(0.8f).translationY(0f).setDuration(220).start()
    }

    private fun render() {
        val t = body ?: return
        val sb = SpannableStringBuilder()
        for ((i, raw) in lines.withIndex()) {
            val last = i == lines.size - 1
            val done = raw.startsWith("Poora")
            val prefix = if (done) "✓ " else if (last) "▸ " else "· "
            val start = sb.length
            sb.append(prefix).append(raw)
            val col = if (done) Color.rgb(110, 231, 183) else if (last) Color.WHITE else Color.argb(150, 200, 212, 235)
            sb.setSpan(ForegroundColorSpan(col), start, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            if (last) sb.setSpan(StyleSpan(Typeface.BOLD), start, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            if (i < lines.size - 1) sb.append(System.lineSeparator())
        }
        t.text = sb
    }

    fun hide() {
        try {
            pulse?.cancel()
            val v = root
            if (v != null) wm?.removeView(v)
        } catch (e: Exception) {
        }
        root = null
        body = null
        dot = null
        pulse = null
        lines.clear()
    }
}
