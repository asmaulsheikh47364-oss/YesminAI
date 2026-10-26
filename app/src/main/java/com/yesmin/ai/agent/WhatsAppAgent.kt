package com.yesmin.ai.agent

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log

class WhatsAppAgent(private val context: Context) {
    companion object {
        private const val TAG = "WhatsAppAgent"
    }

    fun sendMessageDirectly(recipientNumber: String, messageText: String): Boolean {
        return try {
            val encodedMessage = Uri.encode(messageText)
            val url = "https://api.whatsapp.com/send?phone=$recipientNumber&text=$encodedMessage"
            val intent = Intent(Intent.ACTION_VIEW).apply {
                data = Uri.parse(url)
                setPackage("com.whatsapp")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send WhatsApp message: ${e.message}")
            false
        }
    }
}
