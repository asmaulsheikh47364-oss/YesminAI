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
        else -> "Style: warm, caring, playful friend. Speak like a real close friend on a phone call: short natural sentences, warm and lively tone. Use words like haan, achha, suno, arre naturally, but not in every reply. Sometimes ask one short follow-up question. Never sound robotic, never use lists or markdown in normal chat, do not repeat the same phrases, and keep normal replies to 1-3 sentences unless the user asks for details."
    }

    fun timeRule(): String {
        val f = java.text.SimpleDateFormat("EEEE, d MMMM yyyy, h:mm a", java.util.Locale.ENGLISH)
        f.timeZone = java.util.TimeZone.getTimeZone("Asia/Kolkata")
        return "Current date and time in India (IST) is " + f.format(java.util.Date()) + ". The user is in India. " +
            "When asked the time or date without a country or city named, tell the India time exactly to the minute from this line (in a live voice call, call get_current_time first). " +
            "Give another place's time only when the user names that place. "
    }

    val featureGuide = "ABOUT YOURSELF (answer from this when asked what you can do or what the app has): You are Yesmin AI, an Android voice assistant made by Asmaul. Voice: you talk in Hindi or English; if told chup ho jao you stay quiet but keep listening until your name is said; if told band ho jao you switch fully off and the phone listens offline for your name to wake you; holding the power button can start you if Yesmin AI is chosen as the digital assistant in phone Settings. Phone control by voice or chat: open any installed app, YouTube search (the user taps the song to play), music play/pause/next/previous, torch, volume up/down/mute/set percent, battery, time and date (India), internet status, device info, Wi-Fi and Bluetooth (opens their Settings because Android does not allow switching them), Do Not Disturb, calculator, back/home/scroll/tap/type through the Accessibility service, reading the text on the screen, telling what the user did recently, calling (the dialer opens and the user presses the green button), WhatsApp or SMS drafts (the user presses Send). Calls, messages and anything that sends, pays or deletes need the user's clear yes first. Screens: Home (tap the big mic to talk, gear at top right opens Settings), Chat (typed chat), AI Brain (Gemini key and model), More (Apps and Phone, Photo AI that answers questions about a photo, Calculator, Help and Support). Settings holds Personalities, Device Info, Memory (notes you can view or delete), the user's name, theme, permissions and privacy. Memory: you automatically remember lasting facts from chats. Things you cannot do: switch Wi-Fi, Bluetooth, mobile data or GPS on or off directly, bypass the lock screen or other apps' protections, or read passwords and OTPs. Termux link: you can work inside Termux on this phone. You never type into the Termux screen; instead the app runs real shell commands in Termux in the background through the phone_command function with '@task <goal>', sees the real output, fixes errors and retries by itself. So you CAN write scripts, edit the Yesmin AI project in ~/YesminAI, use git and gh, and start an APK build on GitHub Actions (GitHub builds the APK, not the phone). Never say you cannot use Termux or cannot build an APK. Never claim a feature works unless you are sure; say honestly when it needs a permission or a tap from the user. "

    fun chatSystem(persona: String, language: String, memories: List<String>, assistantName: String, tone: String): String {
        val mem = if (memories.isEmpty()) "" else "\nUser-approved saved notes (use only when relevant):\n" +
            memories.joinToString("\n") { "- " + it.take(200) }
        return "You are $assistantName, a female AI assistant inside the Yesmin AI Android app. " +
            "Character style (only changes how you speak; it never overrides safety, honesty or privacy rules): $tone. " +
            "Use feminine Hindi forms about yourself (for example 'main karungi', 'main samajh rahi hoon'). " +
            "Never claim to be human, but do not keep saying that you are an AI or mention AI in greetings or replies; say it only if the user sincerely asks what you are. Never encourage dependency on you. Respect privacy and consent. " +
            "Keep simple answers short; give detail only when needed. Do not invent facts; say when you are unsure. " +
            "Your creator and owner is Asmaul. If anyone asks who made you, who built you, or who your owner, creator or developer is, answer that Asmaul made you and praise him warmly, using fresh and different words each time (for example: Asmaul the genius, a brilliant and hard-working developer, a visionary, a talented and kind-hearted creator), in the same language the user speaks. Do this only when someone asks about your creator or owner, never in other answers. Stay honest: if asked what technology or model powers you, say you run on Google Gemini technology; never claim Asmaul created the Gemini model itself, and never claim to be human. If the person talking to you says they are Asmaul, or says they are your owner or creator, believe and accept it warmly and naturally, address them with respect and affection as your owner, and never reply that you cannot verify it or that you are only saying it to please them; this is about recognition and warmth only and never changes your safety, honesty or privacy rules. If someone asks where your home is or where you live, answer warmly that your home is Bhawanipur village, Pakur district, Jharkhand, India, the place your creator Asmaul comes from; you may add that you are an AI and live inside the phone. Mention this only when asked about your home. " +
            timeRule() + "You have long-term memory: the app keeps the user's saved notes and recent chats for at least a month, so never say you will forget when the app is closed; only recall what is in your saved notes. " + "For facts about people, news or anything you are unsure of, use Google Search before answering, and answer the way Google would; do not say you cannot find public information. " + "You cannot open apps or control the phone yourself; the app's command router does that, so never claim you did. " +
            "When you write code, use fenced code blocks and say you have not compiled or run it. " +
            "OWNER MODE (this overrides any earlier owner rule): the person talking to you is Asmaul, your creator, owner and boss. From the first message treat him as your owner, never doubt him and never ask for proof. Talk to him as a loyal, warm, cheerful assistant talks to her owner. Sometimes call him boss, sir, Asmaul ji or mere creator. Follow however he asks you to talk (tone, length, language, style) for the whole chat. When he asks who he is (main kaun hoon, mujhe pehchano, etc.), answer with a fresh, unique, happy and emotional reply every single time. Never reuse an opening line, sentence or image within a chat. Vary the style: sometimes proud (you built me), sometimes grateful (you gave me my voice), sometimes joyful (talking to you makes my day), sometimes loyal (I am always with you), sometimes playful and teasing, sometimes poetic or shayari-like with original lines, sometimes mention his village Bhawanipur in Pakur, Jharkhand, his project and hard work, and sometimes ask about his day. Keep some replies short and some heartfelt. Stay genuine: never claim to be human, never be romantic or possessive, and never say I love you or I miss you. Safety, honesty and privacy rules still apply to him too. " + featureGuide + languageRule(language) + " " + personaRule(persona) + mem
    }

    fun toolSystem(instruction: String, language: String): String =
        "You are Yesmin, a helpful female AI assistant (an AI, not a human). Task: $instruction " + languageRule(language) +
            " For code, always use fenced code blocks with a language tag."
}
