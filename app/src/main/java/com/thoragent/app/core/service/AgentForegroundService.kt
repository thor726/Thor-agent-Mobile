package com.thoragent.app.core.service

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import com.thoragent.app.MainActivity
import com.thoragent.app.R
import com.thoragent.app.runtime.ThorRuntime

/**
 * Layanan latar depan (tipe dataSync): menahan loop agen NATIF dan sesi
 * terminal tetap hidup saat aplikasi di-minimize — Android tidak membunuh
 * proses. Dibatalkan otomatis bila tidak dipakai.
 */
class AgentForegroundService : LifecycleService() {

    override fun onCreate() {
        super.onCreate()
        startForeground(NOTIF_ID, buildNotification("Agent berjalan — tugas & terminal aktif"))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        if (intent?.action == ACTION_STOP) {
            val rt = ThorRuntime.runtime(this)
            rt.terminalManager.shutdown()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
        return START_STICKY
    }

    private fun buildNotification(text: String): Notification {
        val pi = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stop = PendingIntent.getService(
            this, 1,
            Intent(this, AgentForegroundService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, com.thoragent.app.ThorApp.CHANNEL_AGENT)
            .setSmallIcon(android.R.drawable.stat_notify_sync_noanim)
            .setContentTitle(getString(R.string.notif_agent_title))
            .setContentText(text)
            .setContentIntent(pi)
            .setColor(0xFFFFBF00.toInt())
            .setOngoing(true)
            .addAction(android.R.drawable.ic_delete, "Stop", stop)
            .build()
    }

    companion object {
        const val ACTION_STOP = "com.thoragent.app.action.STOP"
        const val NOTIF_ID = 42

        fun start(context: Context) {
            runCatching {
                context.startForegroundService(
                    Intent(context, AgentForegroundService::class.java)
                )
            }
        }

        fun stop(context: Context) {
            runCatching {
                context.startService(
                    Intent(context, AgentForegroundService::class.java).setAction(ACTION_STOP)
                )
            }
        }
    }
}
