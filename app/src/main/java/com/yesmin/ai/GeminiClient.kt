package com.yesmin.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.net.UnknownHostException

data class AiResult(val ok: Boolean, val text: String, val models: List<String> = emptyList())

/** Talks to the official Gemini REST API (v1beta). The API key is sent only in a header, never logged. */
class GeminiClient {
    private val base = "https://generativelanguage.googleapis.com/v1beta"

    suspend fun listModels(key: String): AiResult = withContext(Dispatchers.IO) {
        try {
            val (code, resp) = request("GET", "$base/models?pageSize=100", key, null)
            if (code != 200) return@withContext AiResult(false, friendly(code, resp, null))
            val arr = JSONObject(resp).optJSONArray("models") ?: JSONArray()
            val names = mutableListOf<String>()
            for (i in 0 until arr.length()) {
                val m = arr.getJSONObject(i)
                val methods = m.optJSONArray("supportedGenerationMethods")
                var can = false
                if (methods != null) for (j in 0 until methods.length()) if (methods.getString(j) == "generateContent") can = true
                val n = m.optString("name").removePrefix("models/")
                if (can && n.startsWith("gemini")) names.add(n)
            }
            AiResult(true, "Key valid hai. ${names.size} Gemini models mile.", names.sortedDescending())
        } catch (e: Exception) {
            AiResult(false, netError(e))
        }
    }

    /** history = list of (role, text) where role is "user" or "model". */
    suspend fun generate(
        key: String, model: String, system: String,
        history: List<Pair<String, String>>, temperature: Float
    ): AiResult = withContext(Dispatchers.IO) {
        try {
            val m = model.trim()
            if (!Regex("[A-Za-z0-9._-]+").matches(m)) return@withContext AiResult(false, "Model ka naam galat hai.")
            val body = JSONObject()
            body.put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", system))))
            val contents = JSONArray()
            for ((role, text) in history) {
                contents.put(JSONObject().put("role", role).put("parts", JSONArray().put(JSONObject().put("text", text))))
            }
            body.put("contents", contents)
            body.put("generationConfig", JSONObject().put("temperature", temperature.toDouble()))
            val (code, resp) = request("POST", "$base/models/$m:generateContent", key, body.toString())
            if (code != 200) return@withContext AiResult(false, friendly(code, resp, m))
            val j = JSONObject(resp)
            val cands = j.optJSONArray("candidates")
            if (cands == null || cands.length() == 0) {
                val reason = j.optJSONObject("promptFeedback")?.optString("blockReason").orEmpty()
                return@withContext AiResult(false, "Gemini ne jawab nahi diya" + if (reason.isNotBlank()) " (blocked: $reason)." else ".")
            }
            val parts = cands.getJSONObject(0).optJSONObject("content")?.optJSONArray("parts")
            if (parts == null) {
                val fr = cands.getJSONObject(0).optString("finishReason")
                return@withContext AiResult(false, "Khaali jawab aaya (finishReason: $fr).")
            }
            val sb = StringBuilder()
            for (i in 0 until parts.length()) sb.append(parts.getJSONObject(i).optString("text"))
            val out = sb.toString().trim()
            if (out.isEmpty()) AiResult(false, "Khaali jawab aaya.") else AiResult(true, out)
        } catch (e: Exception) {
            AiResult(false, netError(e))
        }
    }

    private fun request(method: String, url: String, key: String, body: String?): Pair<Int, String> {
        val c = URL(url).openConnection() as HttpURLConnection
        try {
            c.requestMethod = method
            c.connectTimeout = 15000
            c.readTimeout = 60000
            c.setRequestProperty("x-goog-api-key", key)
            c.setRequestProperty("Content-Type", "application/json")
            if (body != null) {
                c.doOutput = true
                c.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            }
            val code = c.responseCode
            val stream = if (code in 200..299) c.inputStream else c.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() } ?: ""
            return code to text
        } finally {
            c.disconnect()
        }
    }

    private fun friendly(code: Int, resp: String, model: String?): String {
        val msg = try { JSONObject(resp).optJSONObject("error")?.optString("message").orEmpty() } catch (e: Exception) { "" }
        return when {
            code == 400 && msg.contains("API key", true) -> "API key galat hai. Google AI Studio se nayi key lo."
            code == 400 -> "Request galat gayi (400): ${msg.take(160)}"
            code == 401 || code == 403 -> "API key ko permission nahi hai ya key invalid hai ($code)."
            code == 404 -> "Model ${model ?: ""} available nahi hai. AI Brain mein doosra model chuno."
            code == 429 -> "Quota ya rate limit khatam. Thodi der baad try karo ya model badlo."
            code >= 500 -> "Gemini server par problem ($code). Baad mein try karo."
            else -> "Error $code: ${msg.take(160)}"
        }
    }

    private fun netError(e: Exception): String = when (e) {
        is UnknownHostException -> "Internet nahi hai ya Google tak pahunch nahi pa rahe."
        is SocketTimeoutException -> "Timeout ho gaya. Network check karo aur dobara try karo."
        else -> "Network error: ${e.javaClass.simpleName}"
    }
}

object Personas {
    val names = listOf("Friendly Companion", "Professional Assistant", "Coding Expert", "Study Helper")

    private fun languageRule(language: String) = when (language) {
        "hindi" -> "Reply in Hindi using Devanagari script."
        "english" -> "Reply in English."
        else -> "Reply in Hinglish (Hindi written in Roman script, mixed naturally with simple English)."
    }

    private fun personaRule(persona: String) = when (persona) {
        "Professional Assistant" -> "Style: polite, precise, professional, structured."
        "Coding Expert" -> "Style: senior software engineer. Prefer correct, minimal, well-explained code in fenced code blocks."
        "Study Helper" -> "Style: patient teacher. Explain step by step, give small examples, offer a short quiz when useful."
        else -> "Style: warm, caring, playful friend. Keep it natural and not repetitive."
    }

    fun chatSystem(persona: String, language: String, memories: List<String>): String {
        val mem = if (memories.isEmpty()) "" else "\nUser-approved saved notes (use only when relevant):\n" +
            memories.joinToString("\n") { "- " + it.take(200) }
        return "You are Yesmin, a female AI assistant inside the Yesmin AI Android app. " +
            "Use feminine Hindi forms about yourself (for example 'main karungi', 'main samajh rahi hoon'). " +
            "You are an AI, never claim to be human, and never encourage dependency on you. Respect privacy and consent. " +
            "Keep simple answers short; give detail only when needed. Do not invent facts; say when you are unsure. " +
            "You cannot open apps or control the phone yourself; the app's command router does that, so never claim you did. " +
            "When you write code, use fenced code blocks and say you have not compiled or run it. " +
            languageRule(language) + " " + personaRule(persona) + mem
    }

    fun toolSystem(instruction: String, language: String): String =
        "You are Yesmin, a helpful female AI assistant (an AI, not a human). Task: $instruction " + languageRule(language) +
            " For code, always use fenced code blocks with a language tag."
}
