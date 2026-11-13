package com.thoragent.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.thoragent.app.core.diag.CrashLogger
import com.thoragent.app.core.service.AgentWorker
import com.thoragent.app.runtime.ThorRuntime
import java.util.concurrent.TimeUnit

class ThorApp : Application() {

    lateinit var runtime: ThorRuntime
        private set

    override fun onCreate() {
        super.onCreate()
        // Pasang paling awal: apa pun yang meledak setelah ini meninggalkan jejak.
        CrashLogger.install(this)
        runtime = ThorRuntime(this)
        createNotificationChannel()
        scheduleKeepAlive()
    }

    private fun createNotificationChannel() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            CHANNEL_AGENT,
            getString(R.string.notif_channel_agent),
            NotificationManager.IMPORTANCE_LOW
        ).apply { description = getString(R.string.notif_channel_agent_desc) }
        nm.createNotificationChannel(channel)
    }

    private fun scheduleKeepAlive() {
        val wm = WorkManager.getInstance(this)
        val req = PeriodicWorkRequestBuilder<AgentWorker>(25, TimeUnit.MINUTES).build()
        wm.enqueueUniquePeriodicWork("thor_agent_keepalive", ExistingPeriodicWorkPolicy.KEEP, req)
    }

    companion object {
        const val CHANNEL_AGENT = "thor_agent_channel"

        fun runtime(context: Context): ThorRuntime =
            (context.applicationContext as ThorApp).runtime
    }
}
