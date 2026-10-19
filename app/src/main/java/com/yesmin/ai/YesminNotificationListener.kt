package com.yesmin.ai

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

/**
 * Optional. Only active after the user enables Notification Access in Android Settings.
 * It looks at messaging notifications only, keeps the last few in memory, and never uploads or stores them.
 */
class YesminNotificationListener : NotificationListenerService() {

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val pkg = sbn.packageName
        val appName = MESSAGING[pkg] ?: return
        val n = sbn.notification
        if (n.flags and Notification.FLAG_GROUP_SUMMARY != 0) return
        val extras = n.extras
        val sender = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: return
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: return
        val reply = n.actions?.firstOrNull { a -> a.remoteInputs?.isNotEmpty() == true }
        MessageStore.add(IncomingMessage(sbn.key, appName, sender, text, sbn.postTime, reply))
    }

    companion object {
        val MESSAGING = mapOf(
            "com.whatsapp" to "WhatsApp",
            "com.whatsapp.w4b" to "WhatsApp Business",
            "com.google.android.apps.messaging" to "Messages",
            "com.android.mms" to "SMS",
            "com.samsung.android.messaging" to "SMS",
            "org.telegram.messenger" to "Telegram"
        )
    }
}
