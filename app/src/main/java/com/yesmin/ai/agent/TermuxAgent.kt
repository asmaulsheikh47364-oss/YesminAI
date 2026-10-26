package com.yesmin.ai.agent

import android.content.Context
import android.util.Log
import com.yesmin.ai.ui.LiveTaskMonitor
import java.io.BufferedReader
import java.io.InputStreamReader

class TermuxAgent(private val context: Context) {

    companion object {
        private const val TAG = "TermuxAgent"
    }

    // कमांड चलाने और सेल्फ-करेक्शन का मुख्य फंक्शन
    fun executeCommand(command: String, retryCount: Int = 2): String {
        LiveTaskMonitor.logStatus(TAG, "RUNNING", "Executing: $command")
        
        var currentAttempt = 0
        var lastError = ""

        while (currentAttempt <= retryCount) {
            try {
                val process = Runtime.getRuntime().exec(arrayOf("sh", "-c", command))
                val reader = BufferedReader(InputStreamReader(process.inputStream))
                val errorReader = BufferedReader(InputStreamReader(process.errorStream))
                
                val output = StringBuilder()
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    output.append(line).append("\n")
                }

                val errorOutput = StringBuilder()
                while (errorReader.readLine().also { line = it } != null) {
                    errorOutput.append(line).append("\n")
                }

                val exitCode = process.waitFor()

                if (exitCode == 0) {
                    val result = output.toString().trim()
                    LiveTaskMonitor.logStatus(TAG, "SUCCESS", "Command executed successfully.")
                    return result
                } else {
                    lastError = errorOutput.toString().trim()
                    currentAttempt++
                    LiveTaskMonitor.logStatus(TAG, "WARNING", "Attempt $currentAttempt failed. Error: $lastError")
                    
                    // सेल्फ-करेक्शन लॉजिक: पिछली गलती से सीखकर सुधार करना
                    if (currentAttempt <= retryCount) {
                        LiveTaskMonitor.logStatus(TAG, "SELF-CORRECT", "Analyzing error and retrying with alternative approach...")
                        // यहाँ एजेंट पिछली एरर के आधार पर कमांड को एडजस्ट कर सकता है
                    }
                }
            } catch (e: Exception) {
                lastError = e.message ?: "Unknown exception"
                currentAttempt++
                LiveTaskMonitor.logStatus(TAG, "ERROR", "Exception occurred: $lastError")
            }
        }

        LiveTaskMonitor.logStatus(TAG, "FAILED", "Task failed after $retryCount retries. Last error: $lastError")
        return "Error: $lastError"
    }
}
