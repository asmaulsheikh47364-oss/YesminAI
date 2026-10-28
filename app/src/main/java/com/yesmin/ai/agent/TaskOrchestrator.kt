package com.yesmin.ai.agent

import android.content.Context
import android.util.Log
import com.yesmin.ai.MemoryDb

/**
 * Multi-assistant system ka main dimag.
 * Goal sunta hai, samajhta hai, aur sahi agent ko kaam deta hai.
 */
class TaskOrchestrator(private val context: Context) {

    private val whatsappAgent = WhatsAppAgent(context)
    private val termuxAgent = TermuxAgent(context)
    private val memory = MemoryDb(context)

    /** Purana function - purane calls na toote, isliye waisa hi rakha. */
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

    /**
     * Naya multi-agent function.
     * User sirf goal bole, yeh khud decide karega kaun sa agent kaam karega.
     */
    fun runGoal(goal: String): String {
        val g = goal.lowercase().trim()
        return try {
            when {
                // Samajhne wala agent: galat spelling / broken words theek karega
                g.contains("samajh") || g.contains("kya bola") || g.contains("matlab") ->
                    understandGoal(goal)

                // GitHub agent: push, pull, status, commit
                g.contains("github") || g.contains("git ") || g.contains("push") ||
                g.contains("commit") || g.contains("pull") || g.contains("repo") ->
                    githubTask(goal)

                // Memory agent: yaad rakhna, bhoolna, notes
                g.contains("yaad") || g.contains("remember") || g.contains("bhool") ||
                g.contains("note") || g.contains("memory") ->
                    memoryTask(goal)

                // Termux agent: baaki sab technical kaam
                else -> "Termux agent: " + termuxAgent.executeCommand(goal)
            }
        } catch (e: Exception) {
            Log.e("TaskOrchestrator", "runGoal error: ${e.message}")
            "Kaam mein dikkat aayi: ${e.message ?: "unknown"}"
        }
    }

    /** Samajhne wala agent - galat spelling ko sahi karke goal clear karta hai. */
    private fun understandGoal(goal: String): String {
        val fixed = goal
            .replace(Regex("thermux|termox|tarmux", RegexOption.IGNORE_CASE), "Termux")
            .replace(Regex("gethub|get up|gitup", RegexOption.IGNORE_CASE), "GitHub")
            .replace(Regex("jasmin|yesmin|yasmin", RegexOption.IGNORE_CASE), "Yesmin")
        memory.add("understood", fixed)
        return "Samajh gayi: $fixed"
    }

    /** GitHub agent - Termux ke through git commands chalata hai. */
    private fun githubTask(goal: String): String {
        val cmd = when {
            goal.contains("status") -> "cd ~/YesminAI && git status"
            goal.contains("push") -> "cd ~/YesminAI && git add -A && git commit -m "Yesmin update" && git push"
            goal.contains("pull") || goal.contains("update") -> "cd ~/YesminAI && git pull"
            else -> "cd ~/YesminAI && git status"
        }
        val result = termuxAgent.executeCommand(cmd)
        return "GitHub agent ka result: $result"
    }

    /** Memory agent - baat yaad rakhta hai. */
    private fun memoryTask(goal: String): String {
        val text = goal.substringAfter("yaad rakho", goal).trim().ifEmpty { goal }
        memory.add("auto", text)
        return "Yaad rakh liya: $text"
    }
}
