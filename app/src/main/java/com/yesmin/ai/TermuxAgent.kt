package com.yesmin.ai

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager

/**
 * Termux execution bridge for Jasmine.
 * Termux must be installed and external command execution enabled.
 */
object TermuxAgent {

    fun isAvailable(context: Context): Boolean {
        return try {
            context.packageManager.getPackageInfo("com.termux", 0)
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        }
    }

    fun execute(
        context: Context,
        command: String,
        workingDirectory: String = context.filesDir.absolutePath
    ): Boolean {
        if (command.isBlank() || !isAvailable(context)) return false

        val intent = Intent("com.termux.RUN_COMMAND").apply {
            setClassName(
                "com.termux",
                "com.termux.app.RunCommandService"
            )
            putExtra("com.termux.RUN_COMMAND_PATH", "/data/data/com.termux/files/usr/bin/bash")
            putExtra(
                "com.termux.RUN_COMMAND_ARGUMENTS",
                arrayOf("-lc", command)
            )
            putExtra("com.termux.RUN_COMMAND_WORKDIR", workingDirectory)
            putExtra("com.termux.RUN_COMMAND_BACKGROUND", true)
        }

        return try {
            context.startService(intent)
            true
        } catch (_: Exception) {
            false
        }
    }
}
