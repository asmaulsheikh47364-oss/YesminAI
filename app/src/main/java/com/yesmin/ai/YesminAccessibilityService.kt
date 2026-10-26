package com.yesmin.ai

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import android.os.Bundle
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/**
 * Optional. Does nothing unless the user enables it manually in Android Settings AND gives an explicit command.
 * It never reads or stores screen content; events are ignored.
 */
class YesminAccessibilityService : AccessibilityService() {

    companion object {
        @Volatile var instance: YesminAccessibilityService? = null
        fun isRunning(): Boolean = instance != null

        fun isEnabledInSettings(context: Context): Boolean {
            return try {
                val enabled = Settings.Secure.getString(
                    context.contentResolver,
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
                ) ?: return false

                val expected = ComponentName(
                    context,
                    YesminAccessibilityService::class.java
                ).flattenToString()

                enabled.split(':').any { it.equals(expected, ignoreCase = true) }
            } catch (_: Exception) {
                false
            }
        }

        fun status(context: Context): String = when {
            isRunning() -> "CONNECTED"
            isEnabledInSettings(context) -> "ENABLED_NOT_CONNECTED"
            else -> "DISABLED"
        }
    }

    override fun onServiceConnected() { instance = this }
    override fun onUnbind(intent: Intent?): Boolean { instance = null; return super.onUnbind(intent) }
    override fun onDestroy() { instance = null; super.onDestroy() }
    private val actLog = ArrayList<String>()

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val e = event ?: return
        try {
            val pkg = e.packageName?.toString() ?: return
            if (pkg == packageName || e.isPassword) return
            val label = e.text.joinToString(" ").ifBlank { e.contentDescription?.toString() ?: "" }
            val what = when (e.eventType) {
                AccessibilityEvent.TYPE_VIEW_CLICKED -> "tap: " + label
                AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> "opened: " + (e.className?.toString()?.substringAfterLast('.') ?: "") + " " + label
                else -> return
            }
            val t = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.ENGLISH).format(java.util.Date())
            synchronized(actLog) {
                actLog.add(t + " " + pkg + " " + what.take(120))
                if (actLog.size > 40) actLog.removeAt(0)
            }
        } catch (x: Exception) {
        }
    }

    fun recentActivity(): String = synchronized(actLog) {
        if (actLog.isEmpty()) "Abhi koi activity record nahi hui" else actLog.takeLast(15).joinToString("\n")
    }

    fun dumpScreen(): String {
        val root = rootInActiveWindow ?: return "Screen abhi padh nahi paaya"
        val sb = StringBuilder()
        sb.append("App: ").append(root.packageName).append("\n")
        var count = 0
        fun walk(n: AccessibilityNodeInfo?) {
            if (n == null || sb.length > 4000 || count > 500) return
            count++
            if (n.isVisibleToUser && !n.isPassword) {
                val t = (n.text ?: n.contentDescription)?.toString()?.trim()
                if (!t.isNullOrEmpty()) {
                    if (n.isClickable) sb.append("[button] ")
                    sb.append(t.take(200)).append("\n")
                }
            }
            for (i in 0 until n.childCount) walk(n.getChild(i))
        }
        walk(root)
        return sb.toString()
    }
    override fun onInterrupt() {}

    fun perform(a: A11yAction): Boolean = when (a) {
        A11yAction.Back -> performGlobalAction(GLOBAL_ACTION_BACK)
        A11yAction.Home -> performGlobalAction(GLOBAL_ACTION_HOME)
        A11yAction.ScrollDown -> scroll(true)
        A11yAction.ScrollUp -> scroll(false)
        is A11yAction.Tap -> tap(a.label)
        is A11yAction.Type -> type(a.text)
    }

    private fun tap(label: String): Boolean {
        if (label.trim().lowercase() in setOf("pehla result", "first result", "pehla video", "first video", "pehla gaana", "pehla")) return tapFirstResult()
        val root = rootInActiveWindow ?: return false
        for (n in root.findAccessibilityNodeInfosByText(label)) {
            var x: AccessibilityNodeInfo? = n
            while (x != null) {
                if (x.isClickable) return x.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                x = x.parent
            }
        }
        return false
    }

    private fun findScrollable(n: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (n == null) return null
        if (n.isScrollable) return n
        for (i in 0 until n.childCount) findScrollable(n.getChild(i))?.let { return it }
        return null
    }

    private fun walkAll(n: AccessibilityNodeInfo?, out: MutableList<AccessibilityNodeInfo>, limit: Int) {
        if (n == null || out.size >= limit) return
        if (n.isVisibleToUser) out.add(n)
        for (i in 0 until n.childCount) walkAll(n.getChild(i), out, limit)
    }

    private fun labelOf(n: AccessibilityNodeInfo): String =
        ((n.text ?: n.contentDescription)?.toString() ?: "").filter { it >= ' ' }.trim().take(80)

    private fun deepLabel(n: AccessibilityNodeInfo, depth: Int = 0): String {
        val own = labelOf(n)
        if (own.isNotEmpty() || depth >= 3) return own
        for (i in 0 until n.childCount) {
            val c = n.getChild(i) ?: continue
            val l = deepLabel(c, depth + 1)
            if (l.isNotEmpty()) return l
        }
        return ""
    }

    private fun isInteractive(n: AccessibilityNodeInfo): Boolean {
        if (n.isPassword) return false
        if (n.isEditable) return true
        return n.isClickable && deepLabel(n).isNotEmpty()
    }

    /** The visible screen as a numbered list: [n] button or input, plus plain text lines. Used by the screen-control agent. */
    fun screenForAgent(): String {
        val root = rootInActiveWindow ?: return "Screen padh nahi paaya"
        val all = ArrayList<AccessibilityNodeInfo>()
        walkAll(root, all, 400)
        val nl = System.lineSeparator()
        val sb = StringBuilder("APP: " + root.packageName + nl)
        var idx = 0
        for (n in all) {
            if (sb.length > 3500) break
            if (n.isPassword) continue
            if (isInteractive(n)) {
                sb.append("[").append(idx).append("] ").append(if (n.isEditable) "input" else "button").append(": ").append(deepLabel(n)).append(nl)
                idx++
            } else if (!n.isClickable) {
                val own = labelOf(n)
                if (own.isNotEmpty()) sb.append("   text: ").append(own).append(nl)
            }
        }
        return sb.toString()
    }

    private fun nodeAt(index: Int): AccessibilityNodeInfo? {
        val root = rootInActiveWindow ?: return null
        val all = ArrayList<AccessibilityNodeInfo>()
        walkAll(root, all, 400)
        var idx = 0
        for (n in all) {
            if (isInteractive(n)) {
                if (idx == index) return n
                idx++
            }
        }
        return null
    }

    fun labelAt(index: Int): String? {
        val n = nodeAt(index) ?: return null
        return deepLabel(n)
    }

    fun tapIndex(index: Int): Boolean {
        val n = nodeAt(index) ?: return false
        if (n.isEditable) {
            n.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
            n.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            return true
        }
        if (n.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true
        val r = android.graphics.Rect()
        n.getBoundsInScreen(r)
        return tapAt(r.centerX().toFloat(), r.centerY().toFloat())
    }

    private fun swipe(up: Boolean): Boolean {
        val dm = resources.displayMetrics
        val x = dm.widthPixels / 2f
        val y1 = dm.heightPixels * (if (up) 0.72f else 0.28f)
        val y2 = dm.heightPixels * (if (up) 0.28f else 0.72f)
        val path = android.graphics.Path()
        path.moveTo(x, y1)
        path.lineTo(x, y2)
        val g = android.accessibilityservice.GestureDescription.Builder()
            .addStroke(android.accessibilityservice.GestureDescription.StrokeDescription(path, 0L, 350L))
            .build()
        return dispatchGesture(g, null, null)
    }

    private fun tapAt(x: Float, y: Float): Boolean {
        val path = android.graphics.Path()
        path.moveTo(x, y)
        val g = android.accessibilityservice.GestureDescription.Builder()
            .addStroke(android.accessibilityservice.GestureDescription.StrokeDescription(path, 0L, 80L))
            .build()
        return dispatchGesture(g, null, null)
    }

    private fun firstResultNode(n: AccessibilityNodeInfo?, minTop: Int): AccessibilityNodeInfo? {
        if (n == null) return null
        if (n.isClickable && n.isVisibleToUser) {
            val r = android.graphics.Rect()
            n.getBoundsInScreen(r)
            val txt = (n.contentDescription ?: n.text)?.toString() ?: ""
            if (r.top >= minTop && txt.length > 15 && r.height() > 150) return n
        }
        for (i in 0 until n.childCount) {
            val x = firstResultNode(n.getChild(i), minTop)
            if (x != null) return x
        }
        return null
    }

    private fun tapFirstResult(): Boolean {
        val root = rootInActiveWindow ?: return false
        val dm = resources.displayMetrics
        val n = firstResultNode(root, (dm.heightPixels * 0.15f).toInt())
        if (n != null && n.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true
        return tapAt(dm.widthPixels / 2f, dm.heightPixels * 0.40f)
    }

    private fun scroll(forward: Boolean): Boolean {
        if (swipe(forward)) return true
        val node = findScrollable(rootInActiveWindow) ?: return false
        return node.performAction(
            if (forward) AccessibilityNodeInfo.ACTION_SCROLL_FORWARD else AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
        )
    }

    private fun findEditable(n: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (n == null) return null
        if (n.isEditable && n.isVisibleToUser) return n
        for (i in 0 until n.childCount) {
            val r = findEditable(n.getChild(i))
            if (r != null) return r
        }
        return null
    }

    private fun type(text: String): Boolean {
        val root = rootInActiveWindow ?: return false
        val f = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT) ?: findEditable(root) ?: return false
        if (f.isPassword) return false
        f.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
        f.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        val args = Bundle()
        args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        if (f.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)) return true
        val cm = getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        cm.setPrimaryClip(android.content.ClipData.newPlainText("yesmin", text))
        return f.performAction(AccessibilityNodeInfo.ACTION_PASTE)
    }
}
