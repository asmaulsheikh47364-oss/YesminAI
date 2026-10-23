package com.yesmin.ai

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import java.io.File

object Dbg {
    fun log(ctx: Context, msg: String, toast: Boolean = false) {
        try {
            val dir = ctx.getExternalFilesDir(null) ?: ctx.filesDir
            val f = File(dir, "debug.txt")
            if (f.exists() && f.length() > 60000) f.writeText("")
            val t = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.ENGLISH).format(java.util.Date())
            f.appendText(t + " " + msg + System.lineSeparator())
        } catch (e: Exception) {
        }
        if (toast) {
            try {
                val app = ctx.applicationContext
                Handler(Looper.getMainLooper()).post { Toast.makeText(app, msg, Toast.LENGTH_LONG).show() }
            } catch (e: Exception) {
            }
        }
    }
}
