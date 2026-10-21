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
