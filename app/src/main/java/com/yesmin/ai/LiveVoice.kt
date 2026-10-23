package com.yesmin.ai

import android.annotation.SuppressLint
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.os.Handler
import android.os.Looper
import android.util.Base64
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

/** Real-time voice chat over the Gemini Live API: 16 kHz mic in, 24 kHz voice out. */
class LiveVoice(
    private val onState: (VoiceState) -> Unit,
    private val onUserText: (String) -> Unit,
    private val onModelText: (String) -> Unit,
    private val onLevel: (Float) -> Unit,
    private val onError: (String) -> Unit,
    private val onCommand: (String) -> Unit = {},
    private val onTool: (String, Boolean) -> String = { _, _ -> "not supported" }
) {
    private val ui = Handler(Looper.getMainLooper())
    private val http = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .pingInterval(20, TimeUnit.SECONDS)
        .build()

    private var ws: WebSocket? = null
    private var rec: AudioRecord? = null
    private var track: AudioTrack? = null
    private val q = LinkedBlockingQueue<ByteArray>()
    private val userBuf = StringBuilder()
    private val modelBuf = StringBuilder()

    @Volatile private var running = false
    @Volatile private var ready = false
    @Volatile var muted = false
    @Volatile private var lastAudioMs = 0L

    val isRunning: Boolean get() = running

    private fun post(f: () -> Unit) { ui.post { f() } }

    fun start(key: String, model: String, system: String, voiceName: String) {
        if (running) return
        params = arrayOf(key, model, system, voiceName)
        running = true
        ready = false
        muted = false
        q.clear()
        userBuf.setLength(0)
        modelBuf.setLength(0)
        post { onState(VoiceState.Connecting) }
        val url = "wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1beta.GenerativeService.BidiGenerateContent?key=$key"
        ws = http.newWebSocket(Request.Builder().url(url).build(), object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                webSocket.send(setupJson(model, system, voiceName))
            }
            override fun onMessage(webSocket: WebSocket, text: String) { handle(text) }
            override fun onMessage(webSocket: WebSocket, bytes: ByteString) { handle(bytes.utf8()) }
            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                webSocket.close(1000, null)
                if (running) { if (!retryWithoutSearch()) fail(closeText(code, reason)) }
            }
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                if (running) fail(failText(response?.code))
            }
        })
    }

    @Volatile private var useSearch = true
    private var params: Array<String>? = null

    private fun retryWithoutSearch(): Boolean {
        val p = params ?: return false
        if (!useSearch || lastAudioMs != 0L) return false
        useSearch = false
        running = false
        ready = false
        try { ws?.cancel() } catch (e: Exception) {}
        ws = null
        try { rec?.stop() } catch (e: Exception) {}
        try { rec?.release() } catch (e: Exception) {}
        rec = null
        try { track?.stop() } catch (e: Exception) {}
        try { track?.release() } catch (e: Exception) {}
        track = null
        q.clear()
        Thread {
            try { Thread.sleep(500) } catch (e: Exception) {}
            start(p[0], p[1], p[2], p[3])
        }.also { it.isDaemon = true }.start()
        return true
    }

    private fun phoneTool(): JSONObject {
        val props = JSONObject()
            .put("command", JSONObject().put("type", "STRING").put("description", "Short Roman Hindi phone command. Open Termux UI: termux kholo. If the user explicitly asks to execute a shell command in Termux, first ask them to confirm the exact command; only after a clear yes, call this tool with command exactly in the form termux:<shell command>, for example termux:pwd, and confirmed=true. Never execute an inferred or unconfirmed shell command. Normal phone commands include torch on karo, volume badhao, youtube kholo."))
            .put("confirmed", JSONObject().put("type", "BOOLEAN").put("description", "true only after the user clearly said yes to a CONFIRMATION REQUIRED question, otherwise false."))
        return JSONObject()
            .put("name", "phone_command")
            .put("description", "Controls the phone and can launch Termux. For Termux shell execution, ask for explicit confirmation of the exact command first, then use command=termux:<exact shell command> and confirmed=true. For opening the Termux app UI use command=termux kholo. Never claim shell output was verified; the bridge currently reports only whether the request was sent. Calls, messages and other consequential actions require confirmation.")
            .put("parameters", JSONObject().put("type", "OBJECT").put("properties", props).put("required", JSONArray().put("command")))
    }

    private fun fail(msg: String) {
        stop()
        post { onError(msg) }
    }

    private fun closeText(code: Int, reason: String): String {
        val r = reason.lowercase()
        return if (r.contains("quota") || r.contains("exhaust") || r.contains("limit") || r.contains("429"))
            "Gemini ki limit khatam ho gayi hai, thodi der baad try karo. [" + code + " " + reason.take(160) + "]"
        else "Live connection band ho gaya ($code). " + reason.take(100)
    }

    private fun failText(code: Int?): String = when (code) {
        429 -> "Gemini ki limit khatam ho gayi hai, thodi der baad try karo."
        400, 401, 403 -> "API key ya Live model ki permission mein dikkat hai (code $code)."
        else -> "Internet ya Live connection mein dikkat hai."
    }

    private fun setupJson(model: String, system: String, voiceName: String): String {
        val voice = JSONObject().put("prebuiltVoiceConfig", JSONObject().put("voiceName", voiceName))
        val gen = JSONObject()
            .put("responseModalities", JSONArray().put("AUDIO"))
            .put("speechConfig", JSONObject().put("voiceConfig", voice))
        if (model.contains("thinking", true)) gen.put("thinkingConfig", JSONObject().put("thinkingLevel", "LOW"))
        val setup = JSONObject()
            .put("model", "models/$model")
            .put("generationConfig", gen)
            .put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", system))))
            .put("inputAudioTranscription", JSONObject())
            .put("outputAudioTranscription", JSONObject())
            .put("tools", JSONArray()
                .also { if (useSearch) it.put(JSONObject().put("googleSearch", JSONObject())) }
                .put(JSONObject().put("functionDeclarations", JSONArray().put(phoneTool()).put(JSONObject().put("name", "read_screen").put("description", "Returns the text currently visible on the user phone screen. Call it whenever the user asks what is on the screen, asks you to read or explain something shown, or says this or here about something on screen.")).put(JSONObject().put("name", "recent_activity").put("description", "Returns what the user recently did on the phone (taps and screens opened). Call it when the user asks what they just did or what happened.")).put(JSONObject().put("name", "stop_assistant").put("description", "Fully switch Yesmin off. Call it only when the user says her name (Yesmin) and tells her to stop, close or switch off. Say one short goodbye line in the same turn.")).put(JSONObject().put("name", "pause_assistant").put("description", "Make Yesmin quiet for a while. Call it only when the user says her name (Yesmin) and asks her to be quiet or pause for some time. Say one short line like okay in the same turn.")).put(
                    JSONObject().put("name", "get_current_time")
                        .put("description", "Returns the exact current date and time in India (IST). Call it every time the user asks the time or date.")))))
        return JSONObject().put("setup", setup).toString()
    }

    private fun handle(text: String) {
        val j = try { JSONObject(text) } catch (e: Exception) { return }
        if (j.has("setupComplete")) {
            ready = true
            startPlayer()
            startMic()
            if (reconnects == 0) {
                try {
                    ws?.send(JSONObject().put("realtimeInput", JSONObject().put("text", "The user just woke you by calling your name. Reply right now with one very short, warm greeting in your own words (different each time, for example telling him to go ahead and speak), then wait.")).toString())
                } catch (e: Exception) {
                }
            }
            post { onState(VoiceState.Listening) }
            return
        }
        j.optJSONObject("toolCall")?.let { tc ->
            val calls = tc.optJSONArray("functionCalls")
            if (calls != null) {
                val resp = JSONArray()
                for (i in 0 until calls.length()) {
                    val c = calls.getJSONObject(i)
                    val f = java.text.SimpleDateFormat("EEEE, d MMMM yyyy, h:mm a", java.util.Locale.ENGLISH)
                    f.timeZone = java.util.TimeZone.getTimeZone("Asia/Kolkata")
                    val nm = c.optString("name")
                    if (nm == "phone_command") {
                        val callId = c.optString("id")
                        val args = c.optJSONObject("args")
                        val cmdText = args?.optString("command") ?: ""
                        val conf = args?.optBoolean("confirmed", false) ?: false
                        post {
                            val res = try { onTool(cmdText, conf) } catch (e: Exception) { "The command failed." }
                            val one = JSONArray().put(JSONObject().put("id", callId).put("name", "phone_command")
                                .put("response", JSONObject().put("result", res)))
                            ws?.send(JSONObject().put("toolResponse", JSONObject().put("functionResponses", one)).toString())
                        }
                        continue
                    }
                    if (nm == "stop_assistant" || nm == "pause_assistant") {
                        closeMode = if (nm == "stop_assistant") "stop" else "pause"
                        if (quiet) {
                            quiet = false
                            for (b in held) q.offer(b)
                            held.clear()
                            lastAudioMs = System.currentTimeMillis()
                        }
                        scheduleClose()
                    }
                    val result = when (nm) {
                        "get_current_time" -> f.format(java.util.Date()) + " IST"
                        "read_screen" -> YesminAccessibilityService.instance?.dumpScreen() ?: "Accessibility service on nahi hai. Settings mein Yesmin AI ko Accessibility mein on karna padega."
                        "recent_activity" -> YesminAccessibilityService.instance?.recentActivity() ?: "Accessibility service on nahi hai."
                        else -> "ok, now say one very short goodbye line"
                    }
                    resp.put(JSONObject().put("id", c.optString("id")).put("name", nm)
                        .put("response", JSONObject().put("result", result)))
                }
                if (resp.length() > 0) ws?.send(JSONObject().put("toolResponse", JSONObject().put("functionResponses", resp)).toString())
            }
            return
        }
        val sc = j.optJSONObject("serverContent") ?: return
        val parts = sc.optJSONObject("modelTurn")?.optJSONArray("parts")
        if (parts != null) {
            for (i in 0 until parts.length()) {
                val d = parts.optJSONObject(i)?.optJSONObject("inlineData")?.optString("data")
                if (!d.isNullOrEmpty()) {
                    val bytes = Base64.decode(d, Base64.DEFAULT)
                    if (quiet) {
                        held.add(bytes)
                    } else {
                        lastAudioMs = System.currentTimeMillis()
                        q.offer(bytes)
                        post { onState(VoiceState.Speaking) }
                    }
                }
            }
        }
        sc.optJSONObject("inputTranscription")?.optString("text")?.let { userBuf.append(it) }
        sc.optJSONObject("outputTranscription")?.optString("text")?.let { modelBuf.append(it) }
        if (sc.optBoolean("interrupted")) {
            q.clear()
            try { track?.pause(); track?.flush(); track?.play() } catch (e: Exception) {}
        }
        if (sc.optBoolean("turnComplete") || sc.optBoolean("interrupted")) {
            val u = userBuf.toString().trim()
            val m = modelBuf.toString().trim()
            userBuf.setLength(0)
            modelBuf.setLength(0)
            if (quiet) {
                if (hasName(u)) {
                    quiet = false
                    for (b in held) q.offer(b)
                    held.clear()
                    lastAudioMs = System.currentTimeMillis()
                } else {
                    held.clear()
                    return
                }
            }
            post {
                if (u.isNotEmpty()) onUserText(u)
                if (m.isNotEmpty()) onModelText(m)
                onState(VoiceState.Listening)
            }
        }
    }

    private fun startPlayer() {
        val min = AudioTrack.getMinBufferSize(24000, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val t = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(24000)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(maxOf(min, 24000))
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
        track = t
        t.play()
        Thread {
            while (running) {
                val b: ByteArray = (try { q.poll(100, TimeUnit.MILLISECONDS) } catch (e: InterruptedException) { null }) ?: continue
                lastAudioMs = System.currentTimeMillis()
                try { t.write(b, 0, b.size) } catch (e: Exception) {}
                lastAudioMs = System.currentTimeMillis()
            }
        }.also { it.isDaemon = true }.start()
    }

    @SuppressLint("MissingPermission")
    private fun startMic() {
        if (rec != null) return
        val min = AudioRecord.getMinBufferSize(16000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val r = AudioRecord(
            MediaRecorder.AudioSource.MIC, 16000,
            AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, maxOf(min, 3200) * 2
        )
        if (r.state != AudioRecord.STATE_INITIALIZED) {
            fail("Mic shuru nahi ho paaya.")
            return
        }
        rec = r
        r.startRecording()
        Thread {
            val buf = ByteArray(1280)
            while (running) {
                val n = r.read(buf, 0, buf.size)
                if (n < 0) break
                if (n == 0) continue
                var sum = 0.0
                var i = 0
                while (i + 1 < n) {
                    val s = ((buf[i + 1].toInt() shl 8) or (buf[i].toInt() and 0xFF)).toShort().toInt()
                    sum += s.toDouble() * s
                    i += 2
                }
                val rms = Math.sqrt(sum / (n / 2).coerceAtLeast(1)).toFloat()
                val lv = (rms / 4000f).coerceIn(0f, 1f)
                post { onLevel(lv) }
                val busy = muted || !ready || !q.isEmpty() || System.currentTimeMillis() - lastAudioMs < 900
                if (busy) continue
                val msg = JSONObject().put(
                    "realtimeInput",
                    JSONObject().put(
                        "audio",
                        JSONObject()
                            .put("data", Base64.encodeToString(buf, 0, n, Base64.NO_WRAP))
                            .put("mimeType", "audio/pcm;rate=16000")
                    )
                )
                ws?.send(msg.toString())
            }
        }.also { it.isDaemon = true }.start()
    }

    @Volatile private var reconnects = 0
    @Volatile var quiet = false
    private val held = java.util.concurrent.CopyOnWriteArrayList<ByteArray>()

    private fun hasName(t: String): Boolean {
        val x = t.lowercase()
        return listOf("yesmin", "yasmin", "yasmeen", "yasmine", "yesmeen", "esmin", "asmin", "jasmin",
            "यास्मिन", "येस्मिन", "यस्मिन", "एस्मिन", "यैस्मिन", "यास्मीन", "एसमिन", "यासमिन", "यस्मीन").any { x.contains(it) }
    }

    @Volatile private var closeMode: String? = null

    private fun scheduleClose() {
        Thread {
            val start = System.currentTimeMillis()
            while (running) {
                val el = System.currentTimeMillis() - start
                val heard = lastAudioMs > start
                val quiet = q.isEmpty() && System.currentTimeMillis() - lastAudioMs > 1500
                if ((heard && quiet) || el > 10000) break
                try { Thread.sleep(200) } catch (e: Exception) {}
            }
            val m = closeMode
            closeMode = null
            if (m != null && running) post { onCommand(m) }
        }.also { it.isDaemon = true }.start()
    }

    fun stop() {
        running = false
        ready = false
        try { ws?.close(1000, "bye") } catch (e: Exception) {}
        ws = null
        try { rec?.stop() } catch (e: Exception) {}
        try { rec?.release() } catch (e: Exception) {}
        rec = null
        try { track?.stop() } catch (e: Exception) {}
        try { track?.release() } catch (e: Exception) {}
        track = null
        q.clear()
        post { onLevel(0f) }
    }
}
