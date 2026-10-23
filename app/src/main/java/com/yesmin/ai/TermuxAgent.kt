package com.yesmin.ai

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import java.util.concurrent.atomic.AtomicInteger

object TermuxAgent {
    private val requestIds = AtomicInteger(1000)

    fun isAvailable(context: Context): Boolean {
        return try {
            context.packageManager.getPackageInfo("com.termux", 0)
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        } catch (_: Exception) {
            false
        }
    }

    fun execute(
        context: Context,
        command: String,
        workingDirectory: String =
            "/data/data/com.termux/files/home/YesminAI"
    ): Boolean {
        if (command.isBlank() || !isAvailable(context)) return false

        val id = requestIds.incrementAndGet()
        val callbackIntent = Intent(context, TermuxResultService::class.java)
            .putExtra("execution_id", id)

        val flags = PendingIntent.FLAG_ONE_SHOT or
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                PendingIntent.FLAG_MUTABLE
            } else {
                0
            }

        val callback = PendingIntent.getService(
            context, id, callbackIntent, flags
        )

        val intent = Intent("com.termux.RUN_COMMAND").apply {
            setClassName(
                "com.termux",
                "com.termux.app.RunCommandService"
            )
            putExtra(
                "com.termux.RUN_COMMAND_PATH",
                "/data/data/com.termux/files/usr/bin/bash"
            )
            putExtra(
                "com.termux.RUN_COMMAND_ARGUMENTS",
                arrayOf("-lc", command)
            )
            putExtra("com.termux.RUN_COMMAND_WORKDIR", workingDirectory)
            putExtra("com.termux.RUN_COMMAND_BACKGROUND", true)
            putExtra("com.termux.RUN_COMMAND_PENDING_INTENT", callback)
        }

        return try {
            context.startService(intent)
            true
        } catch (_: Exception) {
            callback.cancel()
            false
        }
    }

    fun openTerminal(
        context: Context,
        command: String = "clear; exec bash -l"
    ): Boolean {
        if (!isAvailable(context)) return false

        val intent = Intent("com.termux.RUN_COMMAND").apply {
            setClassName("com.termux", "com.termux.app.RunCommandService")
            putExtra(
                "com.termux.RUN_COMMAND_PATH",
                "/data/data/com.termux/files/usr/bin/bash"
            )
            putExtra(
                "com.termux.RUN_COMMAND_ARGUMENTS",
                arrayOf("-lc", command)
            )
            putExtra(
                "com.termux.RUN_COMMAND_WORKDIR",
                "/data/data/com.termux/files/home/YesminAI"
            )
            putExtra("com.termux.RUN_COMMAND_BACKGROUND", false)
        }

        return try {
            context.startService(intent)
            true
        } catch (_: Exception) {
            false
        }
    }
}
