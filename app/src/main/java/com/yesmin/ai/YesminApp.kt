package com.yesmin.ai

import android.app.Application
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter

/** The assistant core lives at application level so the voice session survives screen changes and runs with the foreground service. */
class YesminApp : Application() {
    val vm: AppViewModel by lazy { AppViewModel(this) }

    override fun onCreate() {
        super.onCreate()
        try {
            val am = getSystemService(android.content.Context.AUDIO_SERVICE) as android.media.AudioManager
            for (st in intArrayOf(android.media.AudioManager.STREAM_NOTIFICATION, android.media.AudioManager.STREAM_SYSTEM, android.media.AudioManager.STREAM_RING)) {
                try { am.adjustStreamVolume(st, android.media.AudioManager.ADJUST_UNMUTE, 0) } catch (e: Exception) {}
            }
        } catch (e: Exception) {}
        try { Dbg.log(this, "app start; assistant=" + android.provider.Settings.Secure.getString(contentResolver, "assistant") + " | vis=" + android.provider.Settings.Secure.getString(contentResolver, "voice_interaction_service")) } catch (e: Exception) {}
        val old = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            try {
                val sw = StringWriter()
                e.printStackTrace(PrintWriter(sw))
                val text = "Time: " + java.util.Date() + "\nThread: " + t.name + "\n" + sw.toString()
                val dir = getExternalFilesDir(null) ?: filesDir
                File(dir, "crash.txt").writeText(text)
            } catch (x: Exception) {
            }
            old?.uncaughtException(t, e)
        }
    }
}
