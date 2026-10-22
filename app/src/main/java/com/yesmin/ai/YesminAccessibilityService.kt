package com.yesmin.ai

import android.accessibilityservice.AccessibilityService
import android.content.Intent
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

    private fun scroll(forward: Boolean): Boolean {
        val node = findScrollable(rootInActiveWindow) ?: return false
        return node.performAction(
            if (forward) AccessibilityNodeInfo.ACTION_SCROLL_FORWARD else AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
        )
    }

    private fun type(text: String): Boolean {
        val f = rootInActiveWindow?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT) ?: return false
        if (f.isPassword) return false
        val args = Bundle()
        args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        return f.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
    }
}
