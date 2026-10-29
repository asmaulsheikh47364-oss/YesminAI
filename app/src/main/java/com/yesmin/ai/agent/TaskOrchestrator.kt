package com.yesmin.ai.agent

import android.content.Context
import android.util.Log
import com.yesmin.ai.MemoryDb

class TaskOrchestrator(private val context: Context) {

    private val whatsappAgent = WhatsAppAgent(context)
    private val termuxAgent = TermuxAgent(context)
    private val memory = MemoryDb(context)

    fun executeTask(goal: String, target: String, message: String) {
        try {
            if (goal.contains("whatsapp", ignoreCase = true)) {
                val success = whatsappAgent.sendMessageDirectly(target, message)
                if (!success) Log.e("TaskOrchestrator", "WhatsApp task failed: $goal")
            }
        } catch (e: Exception) {
            Log.e("TaskOrchestrator", "Error: ${e.message}")
        }
    }

    fun runGoal(goal: String, planner: (suspend (goal: String, lastError: String?) -> String)? = null): String {
        val g = goal.lowercase().trim()
        return try {
            when {
                g.contains("samajh") || g.contains("kya bola") || g.contains("matlab") ->
                    understandGoal(goal)

                g.contains("yaad") || g.contains("remember") || g.contains("bhool") ||
                g.contains("note") || g.contains("memory") ->
                    memoryTask(goal)

                else -> termuxSmartTask(goal, planner)
            }
        } catch (e: Exception) {
            Log.e("TaskOrchestrator", "runGoal error: ${e.message}")
            "Kaam mein dikkat aayi: ${e.message ?: "unknown"}"
        }
    }

    /** Goal ko Gemini se shell command banwata hai; error par khud fix karke 3 baar try karta hai. */
    private suspend fun termuxSmartTask(goal: String, planner: (suspend (String, String?) -> String)?): String {
        if (planner == null) return "Termux agent: " + termuxAgent.executeCommand(goal)
        var lastError: String? = null
        repeat(3) {
            val cmd = planner(goal, lastError).trim()
            if (cmd.isBlank()) return "Yeh kaam main samajh nahi paayi. Thoda detail mein bolo."
            if (cmd.startsWith("DONE:")) return cmd.removePrefix("DONE:").trim().ifBlank { "Ho gaya." }
            if (isDangerous(cmd)) return "Yeh command risky hai, isliye main ise nahi chalaungi."
            val out = termuxAgent.executeCommand(cmd)
            if (!out.startsWith("Error:")) return out.ifBlank { "Ho gaya." }
            lastError = out
        }
        return "Kaam poora nahi ho paya. Last error: ${lastError ?: "unknown"}"
    }

    private fun isDangerous(cmd: String): Boolean {
        val c = cmd.lowercase()
        return listOf("rm -rf", "mkfs", "format", "shutdown", "reboot", ":(){:|:&};:").any { c.contains(it) }
    }

    private fun understandGoal(goal: String): String {
        val fixed = goal
            .replace(Regex("thermux|termox|tarmux", RegexOption.IGNORE_CASE), "Termux")
            .replace(Regex("gethub|get up|gitup", RegexOption.IGNORE_CASE), "GitHub")
            .replace(Regex("jasmin|yesmin|yasmin", RegexOption.IGNORE_CASE), "Yesmin")
        memory.add("understood", fixed)
        return "Samajh gayi: $fixed"
    }

    private fun memoryTask(goal: String): String {
        val text = goal.substringAfter("yaad rakho", goal).trim().ifEmpty { goal }
        memory.add("auto", text)
        return "Yaad rakh liya: $text"
    }
}
