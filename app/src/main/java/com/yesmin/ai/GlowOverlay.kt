package com.yesmin.ai

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.SweepGradient
import android.os.Build
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.animation.LinearInterpolator

/** Animated light around the whole screen edge, drawn above other apps. */
class GlowView(ctx: Context) : View(ctx) {
    var baseColor: Int = 0xFF3B82F6.toInt()
        set(v) { field = v; invalidate() }
    var strength: Float = 0.95f
        set(v) { field = v; invalidate() }

    private var phase = 0f
    private val dp = resources.displayMetrics.density
    private val core = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val halo = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val rect = RectF()
    private val matrix = Matrix()
    private val anim = ValueAnimator.ofFloat(0f, 360f).apply {
        duration = 5000
        repeatCount = ValueAnimator.INFINITE
        interpolator = LinearInterpolator()
        addUpdateListener { phase = it.animatedValue as Float; invalidate() }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        anim.start()
    }

    override fun onDetachedFromWindow() {
        anim.cancel()
        super.onDetachedFromWindow()
    }

    override fun onDraw(c: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return
        val cols = intArrayOf(
            baseColor, 0xFF8B5CF6.toInt(), 0xFF22D3EE.toInt(),
            0xFFF472B6.toInt(), 0xFF22C55E.toInt(), baseColor
        )
        val shader = SweepGradient(w / 2f, h / 2f, cols, null)
        matrix.setRotate(phase, w / 2f, h / 2f)
        shader.setLocalMatrix(matrix)
        val s = 5f * dp
        rect.set(s / 2f, s / 2f, w - s / 2f, h - s / 2f)
        val r = 38f * dp
        halo.shader = shader
        halo.strokeWidth = s * 3.2f
        halo.alpha = (strength * 0.28f * 255f).toInt()
        core.shader = shader
        core.strokeWidth = s
        core.alpha = (strength * 255f).toInt()
        c.drawRoundRect(rect, r, r, halo)
        c.drawRoundRect(rect, r, r, core)
    }
}

object GlowOverlay {
    private var view: GlowView? = null

    fun canShow(ctx: Context): Boolean =
        Build.VERSION.SDK_INT < 23 || Settings.canDrawOverlays(ctx)

    fun update(ctx: Context, visible: Boolean, color: Int, strength: Float) {
        if (!visible || !canShow(ctx)) {
            hide(ctx)
            return
        }
        val wm = ctx.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        if (view == null) {
            val v = GlowView(ctx.applicationContext)
            val dm = android.util.DisplayMetrics()
            @Suppress("DEPRECATION") wm.defaultDisplay.getRealMetrics(dm)
            val lp = WindowManager.LayoutParams(
                dm.widthPixels,
                dm.heightPixels,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT
            )
            lp.gravity = Gravity.TOP or Gravity.START
            if (Build.VERSION.SDK_INT >= 28) {
                lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
            try {
                wm.addView(v, lp)
                view = v
            } catch (e: Exception) {
            }
        }
        view?.baseColor = color
        view?.strength = strength
    }

    fun hide(ctx: Context) {
        val v = view ?: return
        try {
            (ctx.getSystemService(Context.WINDOW_SERVICE) as WindowManager).removeView(v)
        } catch (e: Exception) {
        }
        view = null
    }
}

/** True while the Yesmin screen is visible; the overlay glow shows only when it is not. */
object AppVisibility {
    val inForeground: androidx.compose.runtime.MutableState<Boolean> = androidx.compose.runtime.mutableStateOf(false)
}
