package com.yesmin.ai.agent

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Remembers past errors and their fixes so the same mistake is not repeated. */
class AgentMemory(private val context: Context) {
    private val file: File get() = File(context.filesDir, "agent_memory.json")

    private fun load(): JSONArray {
        return try { JSONArray(file.readText()) } catch (e: Exception) { JSONArray() }
    }

    private fun save(arr: JSONArray) {
        try { file.writeText(arr.toString()) } catch (e: Exception) {}
    }

    fun recordError(task: String, error: String, fix: String) {
        val arr = load()
        val item = JSONObject()
            .put("task", task.take(300))
            .put("error", error.take(1000))
            .put("fix", fix.take(1000))
            .put("time", System.currentTimeMillis())
        arr.put(item)
        while (arr.length() > 200) arr.remove(0)
        save(arr)
    }

    fun similarFixes(task: String, error: String): List<String> {
        val arr = load()
        val out = ArrayList<String>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val sameTask = o.optString("task").equals(task, ignoreCase = true)
            val err = o.optString("error")
            val match = sameTask || error.isNotEmpty() && err.contains(error.take(60), ignoreCase = true)
            if (match) out.add(o.optString("fix"))
        }
        return out.distinct().take(5)
    }
}
