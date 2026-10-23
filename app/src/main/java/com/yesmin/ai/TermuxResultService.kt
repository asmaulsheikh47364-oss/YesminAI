package com.yesmin.ai

import android.app.IntentService
import android.content.Intent
import android.os.Bundle

class TermuxResultService : IntentService("TermuxResultService") {


    override fun onHandleIntent(intent: Intent?) {
        if (intent == null) return

        val result = intent.getBundleExtra(
            "com.termux.service.EXTRA_PLUGIN_RESULT_BUNDLE"
        ) ?: return

        getSharedPreferences("termux_command_results", MODE_PRIVATE)
            .edit()
            .putInt("execution_id", intent.getIntExtra("execution_id", -1))
            .putString("stdout", result.getString("stdout", ""))
            .putString("stderr", result.getString("stderr", ""))
            .putInt("exit_code", result.getInt("exitCode", -1))
            .putInt("error_code", result.getInt("err", 0))
            .putString("error_message", result.getString("errmsg", ""))
            .putBoolean("result_received", true)
            .apply()

        sendBroadcast(
            Intent("com.yesmin.ai.TERMUX_RESULT")
                .setPackage(packageName)
                .putExtra("execution_id", intent.getIntExtra("execution_id", -1))
        )
    }
}
