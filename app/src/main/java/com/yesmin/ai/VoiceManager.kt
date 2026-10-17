package com.yesmin.ai

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale

class VoiceManager(private val ctx: Context, private val onState: (VoiceState) -> Unit) {
    private val main = Handler(Looper.getMainLooper())
    private var recognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    @Volatile private var ttsReady = false

    init {
        tts = TextToSpeech(ctx) { status -> ttsReady = status == TextToSpeech.SUCCESS }
    }

    val recognitionAvailable: Boolean get() = SpeechRecognizer.isRecognitionAvailable(ctx)

    /** Must be called on the main thread. */
    fun listen(localeTag: String, onText: (String) -> Unit, onFail: (String) -> Unit) {
        stopSpeaking()
        if (!recognitionAvailable) {
            onFail("Is phone par speech recognition service nahi mili. Text se type karo.")
            return
        }
        destroyRecognizer()
        val r = SpeechRecognizer.createSpeechRecognizer(ctx)
        recognizer = r
        r.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
            override fun onError(error: Int) {
                destroyRecognizer()
                onFail(errorText(error))
            }
            override fun onResults(results: Bundle?) {
                val t = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                destroyRecognizer()
                if (t.isNullOrBlank()) onFail("Kuch sunai nahi diya.") else onText(t)
            }
        })
        val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, localeTag)
            .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
        onState(VoiceState.Listening)
        r.startListening(i)
    }

    fun cancelListening() {
        destroyRecognizer()
    }

    private fun destroyRecognizer() {
        try { recognizer?.cancel() } catch (e: Exception) {}
        try { recognizer?.destroy() } catch (e: Exception) {}
        recognizer = null
    }

    private fun errorText(code: Int): String = when (code) {
        SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Kuch sunai nahi diya. Dobara mic dabao."
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Mic permission chahiye."
        SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Speech service ko network chahiye."
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Speech service busy hai. Thodi der baad try karo."
        else -> "Speech error ($code). Text se type kar sakte ho."
    }

    /** Returns false if speech could not start (engine missing or language unsupported). */
    fun speak(raw: String, language: String, voiceName: String?, finished: () -> Unit): Boolean {
        val t = tts
        if (t == null || !ttsReady) { finished(); return false }
        val clean = speakable(raw)
        if (clean.isBlank()) { finished(); return true }
        val loc = if (language == "english") Locale("en", "IN") else Locale("hi", "IN")
        val res = t.setLanguage(loc)
        if (res == TextToSpeech.LANG_MISSING_DATA || res == TextToSpeech.LANG_NOT_SUPPORTED) { finished(); return false }
        if (voiceName != null) t.voices?.firstOrNull { it.name == voiceName }?.let { t.voice = it }
        t.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) { main.post { onState(VoiceState.Speaking) } }
            override fun onDone(utteranceId: String?) { main.post { finished() } }
            @Suppress("OVERRIDE_DEPRECATION")
            override fun onError(utteranceId: String?) { main.post { finished() } }
        })
        onState(VoiceState.Speaking)
        t.speak(clean, TextToSpeech.QUEUE_FLUSH, null, "yesmin")
        return true
    }

    fun stopSpeaking() { tts?.stop() }

    fun voices(language: String): List<String> {
        val lang = if (language == "english") "en" else "hi"
        return try {
            tts?.voices?.filter { it.locale.language == lang }?.map { it.name }?.sorted() ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun shutdown() {
        destroyRecognizer()
        tts?.stop()
        tts?.shutdown()
        tts = null
    }

    private fun speakable(s: String): String =
        s.replace(Regex("```[\\s\\S]*?```"), " ").replace(Regex("[*#_`>]"), "").trim()
}
