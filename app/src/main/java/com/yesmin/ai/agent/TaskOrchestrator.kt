package com.yesmin.ai.agent

import android.content.Context
import android.util.Log

class TaskOrchestrator(private val context: Context) {
    private val whatsappAgent = WhatsAppAgent(context)

    fun executeTask(goal: String, target: String, message: String) {
        try {
            if (goal.contains("whatsapp", ignoreCase = true)) {
                val success = whatsappAgent.sendMessageDirectly(target, message)
                if (!success) {
                    Log.e("TaskOrchestrator", "Task execution failed for goal: $goal")
                }
            }
        } catch (e: Exception) {
            Log.e("TaskOrchestrator", "Error in orchestrator: ${e.message}")
        }
    }
}
