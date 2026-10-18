package com.yesmin.ai

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Handler
import android.os.Looper

/** Plays raw 16-bit mono PCM (24 kHz) such as the audio Gemini text-to-speech returns. */
class PcmPlayer {
    private var track: AudioTrack? = null

    fun play(pcm: ByteArray, onDone: () -> Unit) {
        stop()
        if (pcm.size < 4) { onDone(); return }
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

    fun stop() {
        try { track?.stop() } catch (e: Exception) {}
        try { track?.release() } catch (e: Exception) {}
        track = null
    }
}
