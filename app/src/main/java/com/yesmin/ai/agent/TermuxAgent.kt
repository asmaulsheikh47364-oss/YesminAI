package com.yesmin.ai.agent

import android.content.Context
import android.util.Log
import com.yesmin.ai.ui.LiveTaskMonitor
import java.io.BufferedReader
import java.io.InputStreamReader

class TermuxAgent(private val context: Context) {
    private val memory = AgentMemory(context)

    companion object {
        private const val TAG = "TermuxAgent"
    }

    fun executeCommand(command: String, retryCount: Int = 2): String {
        LiveTaskMonitor.logStatus(TAG, "RUNNING", "Executing: $command")
        var currentAttempt = 0
        var lastError = ""
        var hadError = false

        while (currentAttempt <= retryCount) {
            try {
                val process = Runtime.getRuntime().exec(arrayOf("sh", "-c", command))
                val reader = BufferedReader(InputStreamReader(process.inputStream))
                val errorReader = BufferedReader(InputStreamReader(process.errorStream))

                val output = StringBuilder()
                var line: String?
                while (reader.readLine().also { line = it } != null) output.append(line).append("\n")

                val errorOutput = StringBuilder()
                while (errorReader.readLine().also { line = it } != null) errorOutput.append(line).append("\n")

                val exitCode = process.waitFor()

                if (exitCode == 0) {
                    if (hadError) memory.recordError(command, lastError, command)
                    LiveTaskMonitor.logStatus(TAG, "SUCCESS", "Command executed successfully.")
                    return output.toString().trim()
                }

                lastError = errorOutput.toString().trim()
                hadError = true
                currentAttempt++
                LiveTaskMonitor.logStatus(TAG, "WARNING", "Attempt $currentAttempt failed. Error: $lastError")

                if (currentAttempt <= retryCount) {
                    val hints = memory.similarFixes(command, lastError)
                    if (hints.isNotEmpty()) {
                        LiveTaskMonitor.logStatus(TAG, "MEMORY", "Using past fixes: ${hints.joinToString(" | ")}")
                    }
                    LiveTaskMonitor.logStatus(TAG, "SELF-CORRECT", "Analyzing error and retrying...")
                }
            } catch (e: Exception) {
                lastError = e.message ?: "Unknown exception"
                hadError = true
                currentAttempt++
                LiveTaskMonitor.logStatus(TAG, "ERROR", "Exception occurred: $lastError")
            }
        }

        memory.recordError(command, lastError, "")
        LiveTaskMonitor.logStatus(TAG, "FAILED", "Task failed after $retryCount retries. Last error: $lastError")
        return "Error: $lastError"
    }
}
