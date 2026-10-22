package com.yesmin.ai

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.compose.runtime.snapshotFlow
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Foreground service (microphone type) that keeps the voice session alive while the app is in the background.
 * The notification buttons talk to the same session that the screen uses.
 */
class AssistantService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var watcher: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val vm = (application as YesminApp).vm
        when (intent?.action) {
            ACTION_PAUSE -> vm.pauseSession()
            ACTION_RESUME -> vm.resumeSession()
            ACTION_STOP -> {
                vm.stopSession()
                return START_NOT_STICKY
            }
        }
        try {
            val n = build(vm)
            if (Build.VERSION.SDK_INT >= 29) {
                startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
            } else {
                startForeground(NOTIF_ID, n)
            }
        } catch (e: Exception) {
            vm.stopSession()
            vm.notice = "Background service nahi chal payi. Mic aur notification ki ijazat dekho."
            stopSelf()
            return START_NOT_STICKY
        }
        if (watcher == null) {
            watcher = scope.launch {
                snapshotFlow { listOf<Any>(vm.sessionActive, vm.sessionPaused, vm.voiceState, AppVisibility.inForeground.value) }.collect {
                    glowUpdate(vm)
                    if (!vm.sessionActive) {
                        stopSelf()
                    } else {
                        getSystemService(NotificationManager::class.java).notify(NOTIF_ID, build(vm))
                    }
                }
            }
        }
        return START_NOT_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        try { (application as YesminApp).vm.stopSession() } catch (e: Exception) {}
        GlowOverlay.hide(this)
        stopSelf()
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        GlowOverlay.hide(this)
        scope.cancel()
        super.onDestroy()
    }

    private fun glowUpdate(vm: AppViewModel) {
        val show = vm.sessionActive && !AppVisibility.inForeground.value
        val color = when (vm.voiceState) {
            VoiceState.Listening -> 0xFF3B82F6.toInt()
            VoiceState.Processing -> 0xFFF59E0B.toInt()
            VoiceState.Speaking -> 0xFFEC4899.toInt()
            VoiceState.Paused -> 0xFF64748B.toInt()
            VoiceState.Error -> 0xFFEF4444.toInt()
            else -> 0xFF3B82F6.toInt()
        }
        GlowOverlay.update(this, show, color, if (vm.voiceState == VoiceState.Paused) 0.25f else 0.95f)
    }

    private fun build(vm: AppViewModel): Notification {
        getSystemService(NotificationManager::class.java)
            .createNotificationChannel(NotificationChannel(CHANNEL, "Voice session", NotificationManager.IMPORTANCE_LOW))
        val p = vm.activeProfile()
        val status = if (vm.sessionPaused) "Paused" else vm.statusLabel()
        val open = PendingIntent.getActivity(
            this, 1,
            Intent(this, MainActivity::class.java).putExtra("route", "home")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val b = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle(p.name + " AI")
            .setContentText(status)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(open)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
        if (vm.sessionPaused) b.addAction(0, "Resume", action(ACTION_RESUME, 2)) else b.addAction(0, "Pause", action(ACTION_PAUSE, 3))
        b.addAction(0, "Stop", action(ACTION_STOP, 4))
        return b.build()
    }

    private fun action(a: String, code: Int): PendingIntent = PendingIntent.getService(
        this, code, Intent(this, AssistantService::class.java).setAction(a),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )

    companion object {
        const val CHANNEL = "yesmin_session"
        const val NOTIF_ID = 4242
        const val ACTION_START = "com.yesmin.ai.SESSION_START"
        const val ACTION_PAUSE = "com.yesmin.ai.SESSION_PAUSE"
        const val ACTION_RESUME = "com.yesmin.ai.SESSION_RESUME"
        const val ACTION_STOP = "com.yesmin.ai.SESSION_STOP"

        fun start(ctx: Context) {
            ContextCompat.startForegroundService(ctx, Intent(ctx, AssistantService::class.java).setAction(ACTION_START))
        }

        fun stop(ctx: Context) {
            ctx.stopService(Intent(ctx, AssistantService::class.java))
        }
    }
}

/** Quick Settings tile: tap to start (opens the app and begins listening) or stop the voice session. */
class YesminTile : TileService() {
    override fun onStartListening() { refresh() }

    override fun onClick() {
        val vm = (application as YesminApp).vm
        if (vm.sessionActive) {
            vm.stopSession()
            refresh()
            return
        }
        val i = Intent(this, MainActivity::class.java).putExtra("start_session", true).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivityAndCollapse(i)
    }

    private fun refresh() {
        val t = qsTile ?: return
        val vm = (application as YesminApp).vm
        t.state = if (vm.sessionActive) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        t.label = "Yesmin AI"
        t.updateTile()
    }
}
