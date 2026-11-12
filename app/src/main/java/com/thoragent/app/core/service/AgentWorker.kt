package com.thoragent.app.core.service

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.thoragent.app.runtime.ThorRuntime

/**
 * Worker berkala ringan: menjaga sesi terminal tetap hangat dan memverifikasi
 * shell dasar masih hidup setelah reboot/doze. Loop agen sendiri berjalan
 * on-demand saat pengguna mengirim pesan (dengan foreground service).
 */
class AgentWorker(context: Context, params: WorkerParameters) :
    CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            val rt = ThorRuntime.runtime(applicationContext)
            // health check shell non-root (tidak menyentuh root)
            val r = rt.androidRunner.executeNonRoot("true", 5, null)
            if (r.isSuccess) Result.success() else Result.retry()
        } catch (e: Exception) {
            Result.success() // tidak fatal — hanya keep-alive
        }
    }
}
