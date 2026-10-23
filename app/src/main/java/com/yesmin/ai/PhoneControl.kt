package com.yesmin.ai

import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import android.app.RemoteInput
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.Uri
import android.os.Bundle
import android.provider.ContactsContract
import android.provider.Settings
import androidx.compose.runtime.mutableStateListOf
import androidx.core.content.ContextCompat
import java.net.URLEncoder

object PhoneControl {
    fun torch(ctx: Context, on: Boolean): Boolean = try {
        val cm = ctx.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val id = cm.cameraIdList.firstOrNull { cm.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true }
        if (id == null) false else { cm.setTorchMode(id, on); true }
    } catch (e: Exception) {
        false
    }

    /** action: up | down | mute | max | set. Returns the message and the previous raw level (for undo). */
    fun volume(ctx: Context, action: String, percent: Int?): Pair<String, Int> {
        val am = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val prev = am.getStreamVolume(AudioManager.STREAM_MUSIC)
        val target = when (action) {
            "up" -> (prev + maxOf(1, max / 5)).coerceAtMost(max)
            "down" -> (prev - maxOf(1, max / 5)).coerceAtLeast(0)
            "mute" -> 0
            "max" -> max
            else -> (percent ?: 50).coerceIn(0, 100) * max / 100
        }
        am.setStreamVolume(AudioManager.STREAM_MUSIC, target, AudioManager.FLAG_SHOW_UI)
        return ("Media volume " + (if (max > 0) target * 100 / max else 0) + "% kar diya.") to prev
    }

    fun setVolumeRaw(ctx: Context, level: Int) {
        try {
            val am = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            am.setStreamVolume(AudioManager.STREAM_MUSIC, level, AudioManager.FLAG_SHOW_UI)
        } catch (e: Exception) {
        }
    }

    private fun go(ctx: Context, i: Intent): Boolean = try {
        ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (e: Exception) {
        false
    }

    /** Opens the dialer with the number filled in; the user presses the green call button. */
    /** Sends a media key to whatever player is active. Android gives no confirmation that it worked. */
    fun media(ctx: Context, action: String): Boolean = try {
        val code = when (action) {
            "next" -> android.view.KeyEvent.KEYCODE_MEDIA_NEXT
            "previous" -> android.view.KeyEvent.KEYCODE_MEDIA_PREVIOUS
            "pause" -> android.view.KeyEvent.KEYCODE_MEDIA_PAUSE
            "play" -> android.view.KeyEvent.KEYCODE_MEDIA_PLAY
            else -> android.view.KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
        }
        val am = ctx.getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
        am.dispatchMediaKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, code))
        am.dispatchMediaKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_UP, code))
        true
    } catch (e: Exception) {
        false
    }

    fun dial(ctx: Context, number: String): Boolean = go(ctx, Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode(number))))

    /** Opens the SMS app with the message written; the user presses Send. */
    fun sms(ctx: Context, number: String, body: String): Boolean =
        go(ctx, Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:" + Uri.encode(number))).putExtra("sms_body", body))

    /** Opens the WhatsApp chat with the message written; the user presses Send. */
    fun whatsapp(ctx: Context, number: String, body: String): Boolean {
        val uri = Uri.parse("https://wa.me/" + waNumber(ctx, number) + "?text=" + URLEncoder.encode(body, "UTF-8"))
        val base = Intent(Intent.ACTION_VIEW, uri)
        for (pkg in listOf("com.whatsapp", "com.whatsapp.w4b")) {
            if (go(ctx, Intent(base).setPackage(pkg))) return true
        }
        return go(ctx, base)
    }

    /** wa.me needs digits with country code; a 10 digit number is assumed Indian when the phone region is IN. */
    fun waNumber(ctx: Context, raw: String): String {
        var d = raw.filter { it.isDigit() }
        if (raw.trim().startsWith("+")) return d
        if (d.startsWith("00")) return d.drop(2)
        if (d.startsWith("0")) d = d.drop(1)
        val country = ctx.resources.configuration.locales[0].country
        if (country == "IN" && d.length == 10) d = "91$d"
        return d
    }

    fun openSettings(ctx: Context, page: String): Boolean {
        val action = when (page) {
            "wifi" -> Settings.ACTION_WIFI_SETTINGS
            "bluetooth" -> Settings.ACTION_BLUETOOTH_SETTINGS
            "dnd" -> Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS
            else -> Settings.ACTION_SETTINGS
        }
        return go(ctx, Intent(action))
    }

    fun dnd(ctx: Context, on: Boolean): String {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        if (!nm.isNotificationPolicyAccessGranted) {
            openSettings(ctx, "dnd")
            return "DND badalne ki ijazat chahiye. Settings khol di hai — Yesmin AI ko allow karke dobara bolo."
        }
        nm.setInterruptionFilter(if (on) NotificationManager.INTERRUPTION_FILTER_PRIORITY else NotificationManager.INTERRUPTION_FILTER_ALL)
        return if (on) "Do Not Disturb on kar diya." else "Do Not Disturb band kar diya."
    }
}

object Contacts {
    fun hasPermission(ctx: Context): Boolean =
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED

    /** null = no permission. Otherwise a list of (display name, number). */
    fun find(ctx: Context, query: String): List<Pair<String, String>>? {
        if (!hasPermission(ctx)) return null
        val out = mutableListOf<Pair<String, String>>()
        try {
            ctx.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                arrayOf(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME, ContactsContract.CommonDataKinds.Phone.NUMBER),
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " LIKE ?",
                arrayOf("%" + query.trim() + "%"),
                null
            )?.use { c ->
                while (c.moveToNext() && out.size < 30) out.add(c.getString(0) to c.getString(1))
            }
        } catch (e: Exception) {
        }
        return out
    }

    fun distinctNames(l: List<Pair<String, String>>): List<String> = l.map { it.first }.distinct()
}

data class IncomingMessage(
    val key: String,
    val app: String,
    val sender: String,
    val text: String,
    val time: Long,
    val replyAction: Notification.Action?
)

/** Incoming messages are kept in memory only (never saved to disk) and vanish when the app process ends. */
object MessageStore {
    val messages = mutableStateListOf<IncomingMessage>()

    fun add(m: IncomingMessage) {
        messages.removeAll { it.key == m.key && it.text == m.text }
        messages.add(0, m)
        while (messages.size > 20) messages.removeAt(messages.size - 1)
    }

    fun clear() { messages.clear() }
}

object MessageReply {
    /** Sends [text] through the reply button of the original notification. */
    fun send(ctx: Context, m: IncomingMessage, text: String): Boolean {
        val action = m.replyAction ?: return false
        val inputs = action.remoteInputs ?: return false
        val intent = Intent()
        val bundle = Bundle()
        for (ri in inputs) bundle.putCharSequence(ri.resultKey, text)
        RemoteInput.addResultsToIntent(inputs, intent, bundle)
        return try {
            action.actionIntent.send(ctx, 0, intent)
            true
        } catch (e: Exception) {
            false
        }
    }
}
