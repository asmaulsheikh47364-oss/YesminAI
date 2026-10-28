package com.yesmin.ai.agent

import android.content.Context

/**
 * CodingAgent: plans a coding task, runs steps through TermuxAgent,
 * learns from errors using AgentMemory, and reports the real result.
 */
class CodingAgent(private val context: Context) {
    private val termux = TermuxAgent(context)
    private val memory = AgentMemory(context)

    fun runTask(goal: String): String {
        val steps = plan(goal)
        val report = StringBuilder()
        for (step in steps) {
            val result = termux.executeCommand(step)
            report.append("STEP: ").append(step).append("\nRESULT: ").append(result).append("\n\n")
            if (result.startsWith("Error:")) {
                val fixes = memory.similarFixes(goal, result)
                for (fix in fixes) {
                    if (fix.isBlank() || fix == step) continue
                    val retry = termux.executeCommand(fix)
                    report.append("RETRY WITH MEMORY FIX: ").append(fix).append("\nRESULT: ").append(retry).append("\n\n")
                    if (!retry.startsWith("Error:")) break
                }
            }
        }
        return report.toString().trim()
    }

    /** Basic planner: prepares safe project steps. A Gemini-based planner can replace this later. */
    private fun plan(goal: String): List<String> {
        val steps = ArrayList<String>()
        steps.add("cd ~/YesminAI && git status --short && git branch --show-current")
        steps.add("cd ~/YesminAI && git pull --ff-only")
        if (goal.contains("build", ignoreCase = true) || goal.contains("apk", ignoreCase = true)) {
            steps.add("cd ~/YesminAI && git push")
        }
        return steps
    }
}
