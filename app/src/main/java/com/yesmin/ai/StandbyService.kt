package com.yesmin.ai

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat

/** Light standby: Yesmin is off (no Gemini), only the phone's own recognizer waits for her name plus an "on" word. */
class StandbyService : Service() {
    private val main = Handler(Looper.getMainLooper())
    private var rec: SpeechRecognizer? = null
    private var alive = false
    private var muted = false
    private lateinit var audio: AudioManager
    private val streams = intArrayOf(AudioManager.STREAM_NOTIFICATION, AudioManager.STREAM_SYSTEM)

    private val names = listOf("yesmin", "yasmin", "yasmeen", "yasmine", "yesmeen", "jasmin", "esmin", "asmin",
        "यास्मिन", "येस्मिन", "यस्मिन", "एस्मिन", "यैस्मिन", "यास्मीन", "एसमिन", "यासमिन", "यस्मीन")
    private val onWords = listOf("chalu", "chaalu", "on", "shuru", "jago", "jaag", "uth", "start", "active",
        "चालू", "ऑन", "शुरू", "जागो", "जाग", "उठ", "स्टार्ट", "एक्टिव")
    private val nameRegex = Regex(names.joinToString("|"))

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        audio = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        if (intent?.action == ACTION_OFF) {
            shutdown()
            return START_NOT_STICKY
        }
        try {
            val vm = (application as YesminApp).vm
            if (vm.sessionActive) { stopSelf(); return START_NOT_STICKY }
        } catch (e: Exception) {}
        try {
            val n = notification()
            if (Build.VERSION.SDK_INT >= 29) startForeground(NOTIF, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
            else startForeground(NOTIF, n)
        } catch (e: Exception) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (!alive) { alive = true; listen() }
        return START_STICKY
    }

    private fun notification(): Notification {
        getSystemService(NotificationManager::class.java)
            .createNotificationChannel(NotificationChannel(CHANNEL, "Yesmin standby", NotificationManager.IMPORTANCE_LOW))
        val open = PendingIntent.getActivity(
            this, 7,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val off = PendingIntent.getService(
            this, 8, Intent(this, StandbyService::class.java).setAction(ACTION_OFF),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle("Yesmin standby")
            .setContentText("Bolo: Yesmin, chalu ho jao")
            .setContentIntent(open)
            .addAction(0, "Standby band", off)
            .setOngoing(true)
            .build()
    }

    private fun listen() {
        if (!alive) return
        if (!SpeechRecognizer.isRecognitionAvailable(this)) { shutdown(); return }
        release()
        val r = SpeechRecognizer.createSpeechRecognizer(this)
        rec = r
        r.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
            override fun onPartialResults(partialResults: Bundle?) { check(partialResults) }
            override fun onResults(results: Bundle?) { if (!check(results)) again(300) }
            override fun onError(error: Int) {
                if (error == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS) { shutdown(); return }
                again(if (error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY) 1500L else 400L)
            }
        })
        val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "hi-IN")
            .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            .putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, packageName)
        mute()
        try { r.startListening(i) } catch (e: Exception) { again(1000L) }
    }

    private fun again(ms: Long) { main.postDelayed({ listen() }, ms) }

    private fun check(b: Bundle?): Boolean {
        val l = b?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION) ?: return false
        for (t in l) if (isWake(t)) { wake(); return true }
        return false
    }

    private fun isWake(t: String): Boolean {
        val x = t.lowercase()
        val n = nameRegex.findAll(x).count()
        if (n == 0) return false
        return n >= 2 || onWords.any { x.contains(it) }
    }

    private fun wake() {
        if (!alive) return
        alive = false
        main.post {
            release()
            unmute()
            try {
                val vm = (application as YesminApp).vm
                if (!vm.sessionActive) vm.startSession() else if (vm.sessionPaused) vm.resumeSession()
            } catch (e: Exception) {}
            main.postDelayed({ stopSelf() }, 4000L)
        }
    }

    private fun shutdown() {
        alive = false
        release()
        unmute()
        stopSelf()
    }

    private fun release() {
        try { rec?.cancel() } catch (e: Exception) {}
        try { rec?.destroy() } catch (e: Exception) {}
        rec = null
    }

    private fun mute() {
        if (muted) return
        muted = true
        for (s in streams) { try { audio.adjustStreamVolume(s, AudioManager.ADJUST_MUTE, 0) } catch (e: Exception) {} }
    }

    private fun unmute() {
        if (!muted) return
        muted = false
        for (s in streams) { try { audio.adjustStreamVolume(s, AudioManager.ADJUST_UNMUTE, 0) } catch (e: Exception) {} }
    }

    override fun onDestroy() {
        alive = false
        main.removeCallbacksAndMessages(null)
        release()
        try { if (::audio.isInitialized) unmute() } catch (e: Exception) {}
        super.onDestroy()
    }

    companion object {
        const val CHANNEL = "yesmin_standby"
        const val NOTIF = 4243
        const val ACTION_OFF = "com.yesmin.ai.STANDBY_OFF"

        fun start(ctx: Context) {
            ContextCompat.startForegroundService(ctx, Intent(ctx, StandbyService::class.java))
        }

        fun stop(ctx: Context) {
            ctx.stopService(Intent(ctx, StandbyService::class.java))
        }
    }
}
