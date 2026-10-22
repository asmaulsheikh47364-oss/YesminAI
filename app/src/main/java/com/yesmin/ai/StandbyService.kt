package com.yesmin.ai

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import java.io.File

/** Offline standby: no Gemini, no internet. Only listens on the phone for her name, then starts the voice session. */
class StandbyService : Service() {
    private val main = Handler(Looper.getMainLooper())
    @Volatile private var alive = false
    @Volatile private var woke = false
    @Volatile private var gen = 0
    private var worker: Thread? = null

    private val nameRegex = Regex("yesmin|yasmin|yasmeen|yasmine|jasmin|jasmine|yes mean|yes men|yes min|yes mind|yaz min|us mean|as mean|yes mine|यास्मिन|येस्मिन|यस्मिन|एस्मिन")

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_OFF) {
            alive = false
            stopSelf()
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
            Dbg.log(this, "standby shuru nahi hua: " + e.message)
            stopSelf()
            return START_NOT_STICKY
        }
        if (!alive) {
            alive = true
            woke = false
            gen++
            worker = Thread { listenLoop() }.also { it.isDaemon = true; it.start() }
        }
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
            .setContentText("Bolo: Yesmin")
            .setContentIntent(open)
            .addAction(0, "Standby band", off)
            .setOngoing(true)
            .build()
    }

    private fun copyAssets(src: String, dst: File) {
        val list = assets.list(src) ?: return
        if (list.isEmpty()) {
            dst.parentFile?.mkdirs()
            assets.open(src).use { i -> dst.outputStream().use { o -> i.copyTo(o) } }
        } else {
            dst.mkdirs()
            for (n in list) copyAssets(src + "/" + n, File(dst, n))
        }
    }

    private fun listenLoop() {
        var model: Model? = null
        var rec: Recognizer? = null
        var ar: AudioRecord? = null
        try {
            val dir = File(filesDir, "vosk-model")
            if (!File(dir, "am/final.mdl").exists()) copyAssets("vosk-model", dir)
            model = Model(dir.absolutePath)
            rec = Recognizer(model, 16000.0f)
            val minBuf = AudioRecord.getMinBufferSize(16000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
            ar = AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION, 16000,
                AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, maxOf(minBuf, 8000) * 2
            )
            if (ar.state != AudioRecord.STATE_INITIALIZED) {
                Dbg.log(this, "standby: mic nahi mila")
                return
            }
            ar.startRecording()
            Dbg.log(this, "standby chalu: Yesmin bolo")
            val buf = ByteArray(4096)
            while (alive && !sessionBusy()) {
                val n = ar.read(buf, 0, buf.size)
                if (n <= 0) { Thread.sleep(50); continue }
                val done = rec.acceptWaveForm(buf, n)
                val txt = if (done) JSONObject(rec.result).optString("text") else JSONObject(rec.partialResult).optString("partial")
                if (txt.isNotBlank()) {
                    if (done) Dbg.log(this, "suna: " + txt, false)
                    if (nameRegex.containsMatchIn(txt.lowercase())) {
                        woke = true
                        alive = false
                    }
                }
            }
        } catch (e: Throwable) {
            Dbg.log(this, "standby error: " + e.message)
        } finally {
            try { ar?.stop() } catch (e: Exception) {}
            try { ar?.release() } catch (e: Exception) {}
            try { rec?.close() } catch (e: Throwable) {}
            try { model?.close() } catch (e: Throwable) {}
        }
        if (woke) {
            main.postDelayed({
                try {
                    Dbg.log(this, "wake! session shuru")
                    val vm = (application as YesminApp).vm
                    if (!vm.sessionActive) vm.startSession() else if (vm.sessionPaused) vm.resumeSession()
                    Dbg.log(this, "wake ke baad: active=" + vm.sessionActive + " state=" + vm.voiceState + " notice=" + vm.notice)
                } catch (e: Exception) {
                    Dbg.log(this, "wake error: " + e.message)
                }
                watchSession()
            }, 500L)
        }
    }

    private fun sessionBusy(): Boolean = try {
        (application as YesminApp).vm.sessionActive
    } catch (e: Exception) {
        false
    }

    private fun watchSession() {
        val g = gen
        val r = object : Runnable {
            override fun run() {
                if (g != gen) return
                if (sessionBusy()) main.postDelayed(this, 1500L) else stopSelf()
            }
        }
        main.postDelayed(r, 3000L)
    }

    override fun onDestroy() {
        alive = false
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
