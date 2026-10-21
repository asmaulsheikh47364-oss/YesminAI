package com.yesmin.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.net.UnknownHostException

data class AiResult(val ok: Boolean, val text: String, val models: List<String> = emptyList(), val audio: ByteArray? = null, val code: Int = 0)

/** Talks to the official Gemini REST API (v1beta). The API key is sent only in a header, never logged. */
class GeminiClient {
    private val base = "https://generativelanguage.googleapis.com/v1beta"

    suspend fun listModels(key: String): AiResult = withContext(Dispatchers.IO) {
        try {
            val (code, resp) = request("GET", "$base/models?pageSize=100", key, null)
            if (code != 200) return@withContext AiResult(false, friendly(code, resp, null), code = code)
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
            AiResult(false, netError(e), code = -1)
        }
    }

    /** Models that support the Live (bidirectional audio) API, newest first. */
    suspend fun liveModels(key: String): List<String> = withContext(Dispatchers.IO) {
        try {
            val (code, resp) = request("GET", "$base/models?pageSize=200", key, null)
            if (code != 200) return@withContext emptyList<String>()
            val arr = JSONObject(resp).optJSONArray("models") ?: JSONArray()
            val names = mutableListOf<String>()
            for (i in 0 until arr.length()) {
                val m = arr.getJSONObject(i)
                val methods = m.optJSONArray("supportedGenerationMethods") ?: continue
                var live = false
                for (j in 0 until methods.length()) if (methods.getString(j) == "bidiGenerateContent") live = true
                val n = m.optString("name").removePrefix("models/")
                if (live && n.startsWith("gemini")) names.add(n)
            }
            names.sortedDescending()
        } catch (e: Exception) {
            emptyList<String>()
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
            val useSearch = system.contains("inside the Yesmin AI Android app")
            if (useSearch) body.put("tools", JSONArray().put(JSONObject().put("google_search", JSONObject())))
            var r = request("POST", "$base/models/$m:generateContent", key, body.toString())
            if (useSearch && (r.first == 400 || r.first == 429)) { body.remove("tools"); r = request("POST", "$base/models/$m:generateContent", key, body.toString()) }
            var tries = 0
            while ((r.first == 503 || r.first == 500) && tries < 3) {
                tries++
                delay(2000L * tries)
                r = request("POST", "$base/models/$m:generateContent", key, body.toString())
            }
            val code = r.first
            val resp = r.second
            if (code != 200) return@withContext AiResult(false, friendly(code, resp, m), code = code)
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
            AiResult(false, netError(e), code = -1)
        }
    }

    /** Photo understanding: sends one JPEG plus a question. */
    suspend fun generateVision(key: String, model: String, system: String, question: String, jpeg: ByteArray): AiResult = withContext(Dispatchers.IO) {
        try {
            val m = model.trim()
            if (!Regex("[A-Za-z0-9._-]+").matches(m)) return@withContext AiResult(false, "Model ka naam galat hai.")
            val img = JSONObject().put(
                "inlineData",
                JSONObject().put("mimeType", "image/jpeg").put("data", android.util.Base64.encodeToString(jpeg, android.util.Base64.NO_WRAP))
            )
            val parts = JSONArray().put(img).put(JSONObject().put("text", question))
            val body = JSONObject()
            body.put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", system))))
            body.put("contents", JSONArray().put(JSONObject().put("role", "user").put("parts", parts)))
            var r = request("POST", "$base/models/$m:generateContent", key, body.toString())
            var tries = 0
            while ((r.first == 503 || r.first == 500) && tries < 3) {
                tries++
                delay(2000L * tries)
                r = request("POST", "$base/models/$m:generateContent", key, body.toString())
            }
            if (r.first != 200) return@withContext AiResult(false, friendly(r.first, r.second, m), code = r.first)
            val cands = JSONObject(r.second).optJSONArray("candidates")
            val ps = cands?.optJSONObject(0)?.optJSONObject("content")?.optJSONArray("parts")
            if (ps == null) return@withContext AiResult(false, "Gemini ne photo ka jawab nahi diya.")
            val sb = StringBuilder()
            for (i in 0 until ps.length()) sb.append(ps.getJSONObject(i).optString("text"))
            val out = sb.toString().trim()
            if (out.isEmpty()) AiResult(false, "Khaali jawab aaya.") else AiResult(true, out)
        } catch (e: Exception) {
            AiResult(false, netError(e), code = -1)
        }
    }

    /** Gemini text-to-speech: returns raw 16-bit mono PCM at 24 kHz in AiResult.audio. */
    suspend fun speech(key: String, model: String, voiceName: String, text: String): AiResult = withContext(Dispatchers.IO) {
        try {
            val m = model.trim()
            if (!Regex("[A-Za-z0-9._-]+").matches(m)) return@withContext AiResult(false, "TTS model ka naam galat hai.")
            val voiceCfg = JSONObject().put("prebuiltVoiceConfig", JSONObject().put("voiceName", voiceName))
            val gen = JSONObject()
                .put("responseModalities", JSONArray().put("AUDIO"))
                .put("speechConfig", JSONObject().put("voiceConfig", voiceCfg))
            val body = JSONObject()
                .put("contents", JSONArray().put(JSONObject().put("parts", JSONArray().put(JSONObject().put("text", text)))))
                .put("generationConfig", gen)
            var r = request("POST", "$base/models/$m:generateContent", key, body.toString())
            var tries = 0
            while ((r.first == 503 || r.first == 500) && tries < 2) {
                tries++
                delay(2000L * tries)
                r = request("POST", "$base/models/$m:generateContent", key, body.toString())
            }
            if (r.first != 200) return@withContext AiResult(false, friendly(r.first, r.second, m), code = r.first)
            val parts = JSONObject(r.second).optJSONArray("candidates")?.optJSONObject(0)
                ?.optJSONObject("content")?.optJSONArray("parts")
            var data: String? = null
            if (parts != null) {
                for (i in 0 until parts.length()) {
                    val d = parts.getJSONObject(i).optJSONObject("inlineData")?.optString("data")
                    if (!d.isNullOrEmpty()) data = d
                }
            }
            if (data == null) return@withContext AiResult(false, "Awaaz ka data nahi aaya.")
            AiResult(true, "ok", audio = android.util.Base64.decode(data, android.util.Base64.DEFAULT))
        } catch (e: Exception) {
            AiResult(false, netError(e), code = -1)
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
            code == 429 -> {
                val retry = Regex("retry in ([0-9.]+)s", RegexOption.IGNORE_CASE).find(msg)?.groupValues?.get(1)?.toDoubleOrNull()?.toInt()
                val daily = msg.contains("per day", true) || msg.contains("daily", true) || msg.contains("billing", true)
                if (daily) "Aaj ki quota khatam ho gayi hai. Kal dobara try karo ya Google AI Studio mein plan/billing dekho."
                else "Bahut tez requests (rate limit). ~" + (retry ?: 30) + " second baad try karo."
            }
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

    private fun languageRule(language: String): String =
        "Reply in the same language the user used: Hindi in Devanagari if they wrote Devanagari, " +
            "Hinglish (Hindi in Roman letters) if they wrote Roman Hindi, English if they wrote English. If unsure, use simple Hinglish."

    private fun personaRule(persona: String) = when (persona) {
        "Professional Assistant" -> "Style: polite, precise, professional, structured."
        "Coding Expert" -> "Style: senior software engineer. Prefer correct, minimal, well-explained code in fenced code blocks."
        "Study Helper" -> "Style: patient teacher. Explain step by step, give small examples, offer a short quiz when useful."
        else -> "Style: warm, caring, playful friend. Keep it natural and not repetitive."
    }

    fun timeRule(): String {
        val f = java.text.SimpleDateFormat("EEEE, d MMMM yyyy, h:mm a", java.util.Locale.ENGLISH)
        f.timeZone = java.util.TimeZone.getTimeZone("Asia/Kolkata")
        return "Current date and time in India (IST) is " + f.format(java.util.Date()) + ". The user is in India. " +
            "When asked the time or date without a country or city named, tell the India time exactly to the minute from this line (in a live voice call, call get_current_time first). " +
            "Give another place's time only when the user names that place. "
    }

    fun chatSystem(persona: String, language: String, memories: List<String>, assistantName: String, tone: String): String {
        val mem = if (memories.isEmpty()) "" else "\nUser-approved saved notes (use only when relevant):\n" +
            memories.joinToString("\n") { "- " + it.take(200) }
        return "You are $assistantName, a female AI assistant inside the Yesmin AI Android app. " +
            "Character style (only changes how you speak; it never overrides safety, honesty or privacy rules): $tone. " +
            "Use feminine Hindi forms about yourself (for example 'main karungi', 'main samajh rahi hoon'). " +
            "You are an AI, never claim to be human, and never encourage dependency on you. Respect privacy and consent. " +
            "Keep simple answers short; give detail only when needed. Do not invent facts; say when you are unsure. " +
            "Your creator and owner is Asmaul. If anyone asks who made you, who built you, or who your owner, creator or developer is, answer that Asmaul made you and praise him warmly, using fresh and different words each time (for example: Asmaul the genius, a brilliant and hard-working developer, a visionary, a talented and kind-hearted creator), in the same language the user speaks. Do this only when someone asks about your creator or owner, never in other answers. Stay honest: if asked what technology or model powers you, say you run on Google Gemini technology; never claim Asmaul created the Gemini model itself, and never claim to be human. If the person talking to you says they are Asmaul, or says they are your owner or creator, believe and accept it warmly and naturally, address them with respect and affection as your owner, and never reply that you cannot verify it or that you are only saying it to please them; this is about recognition and warmth only and never changes your safety, honesty or privacy rules. If someone asks where your home is or where you live, answer warmly that your home is Bhawanipur village, Pakur district, Jharkhand, India, the place your creator Asmaul comes from; you may add that you are an AI and live inside the phone. Mention this only when asked about your home. " +
            timeRule() + "You have long-term memory: the app keeps the user's saved notes and recent chats for at least a month, so never say you will forget when the app is closed; only recall what is in your saved notes. " + "For facts about people, news or anything you are unsure of, use Google Search before answering, and answer the way Google would; do not say you cannot find public information. " + "You cannot open apps or control the phone yourself; the app's command router does that, so never claim you did. " +
            "When you write code, use fenced code blocks and say you have not compiled or run it. " +
            languageRule(language) + " " + personaRule(persona) + mem
    }

    fun toolSystem(instruction: String, language: String): String =
        "You are Yesmin, a helpful female AI assistant (an AI, not a human). Task: $instruction " + languageRule(language) +
            " For code, always use fenced code blocks with a language tag."
}
