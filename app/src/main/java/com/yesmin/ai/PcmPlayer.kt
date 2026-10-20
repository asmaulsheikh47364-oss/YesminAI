package com.yesmin.ai

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Handler
import android.os.Looper

/** Plays raw 16-bit mono PCM (24 kHz) such as the audio Gemini text-to-speech returns. */
class PcmPlayer {
    private var track: AudioTrack? = null

    fun play(raw: ByteArray, onDone: () -> Unit) {
        stop()
        if (raw.size < 4) { onDone(); return }
        val pcm = clean(raw)
        val frames = pcm.size / 2
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
            .setBufferSizeInBytes(pcm.size)
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()
        t.write(pcm, 0, pcm.size)
        t.setNotificationMarkerPosition((frames - 1).coerceAtLeast(1))
        t.setPlaybackPositionUpdateListener(object : AudioTrack.OnPlaybackPositionUpdateListener {
            override fun onMarkerReached(track: AudioTrack?) { onDone() }
            override fun onPeriodicNotification(track: AudioTrack?) {}
        }, Handler(Looper.getMainLooper()))
        track = t
        t.play()
    }

    /** Removes DC/rumble and fades the start and end to avoid clicks and hiss. */
    private fun clean(src: ByteArray): ByteArray {
        val n = src.size / 2
        val y = FloatArray(n)
        var px = 0f
        var py = 0f
        for (i in 0 until n) {
            val v = ((src[2 * i + 1].toInt() shl 8) or (src[2 * i].toInt() and 0xFF)).toShort().toFloat()
            py = v - px + 0.995f * py
            px = v
            y[i] = py
        }
        val fi = minOf(240, n / 2)
        val fo = minOf(720, n / 2)
        for (i in 0 until fi) y[i] *= i.toFloat() / fi
        for (i in 0 until fo) y[n - 1 - i] *= i.toFloat() / fo
        val out = ByteArray(n * 2)
        for (i in 0 until n) {
            val v = y[i].toInt().coerceIn(-32768, 32767)
            out[2 * i] = (v and 0xFF).toByte()
            out[2 * i + 1] = ((v shr 8) and 0xFF).toByte()
        }
        return out
    }

    fun stop() {
        try { track?.stop() } catch (e: Exception) {}
        try { track?.release() } catch (e: Exception) {}
        track = null
    }
}
