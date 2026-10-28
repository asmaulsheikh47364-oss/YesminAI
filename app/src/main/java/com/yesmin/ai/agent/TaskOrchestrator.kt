package com.yesmin.ai.agent

import android.content.Context
import android.util.Log

class TaskOrchestrator(private val context: Context) {
    private val whatsappAgent = WhatsAppAgent(context)
    private val codingAgent = CodingAgent(context)

    fun executeTask(goal: String, target: String, message: String): String {
        return try {
            if (goal.contains("whatsapp", ignoreCase = true)) {
                val success = whatsappAgent.sendMessageDirectly(target, message)
                if (!success) {
                    Log.e("TaskOrchestrator", "Task execution failed for goal: $goal")
                    "Error: WhatsApp task failed."
                } else {
                    "WhatsApp task completed."
                }
            } else {
                codingAgent.runTask(goal)
            }
        } catch (e: Exception) {
            Log.e("TaskOrchestrator", "Error in orchestrator: ${e.message}")
            "Error: ${e.message ?: "Unknown error"}"
        }
    }
}
