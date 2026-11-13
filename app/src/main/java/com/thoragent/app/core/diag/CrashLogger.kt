package com.thoragent.app.core.diag

import android.content.Context
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Penangkap crash terakhir — supaya force close tidak lagi jadi kejadian bisu.
 *
 * Tanpa ini, satu exception yang lolos (mis. kegagalan baca baris Room, atau
 * `Error` yang tidak tertangkap `catch (Exception)`) langsung mematikan proses
 * tanpa jejak yang bisa dibaca pengguna. Handler ini menulis stack trace utuh
 * ke `filesDir/crash/`, menyimpannya untuk laporan, lalu meneruskan ke handler
 * sistem agar perilaku force close tetap standar.
 */
object CrashLogger {

    private const val DIR = "crash"
    private const val KEEP = 10

    @Volatile
    private var lastTrace: String? = null

    /** Dipanggil sekali dari `Application.onCreate`. */
    fun install(context: Context) {
        val app = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching { record(app, thread, error) }
            previous?.uncaughtException(thread, error)
        }
    }

    /** Stack trace crash terakhir pada proses ini (null bila belum ada). */
    fun last(): String? = lastTrace

    private fun record(app: Context, thread: Thread, error: Throwable) {
        val sw = StringWriter()
        PrintWriter(sw).use { pw ->
            pw.println("waktu : ${stamp()}")
            pw.println("thread: ${thread.name}")
            pw.println("versi : ${app.packageName}")
            pw.println("---")
            error.printStackTrace(pw)
        }
        val trace = sw.toString()
        lastTrace = trace

        val dir = File(app.filesDir, DIR).apply { mkdirs() }
        File(dir, "crash-${System.currentTimeMillis()}.txt").writeText(trace)

        // Simpan hanya KEEP berkas terbaru agar tidak menumpuk.
        dir.listFiles()?.sortedByDescending { it.name }?.drop(KEEP)?.forEach { it.delete() }

        // Salinan tetap agar mudah ditemukan pengguna.
        File(dir, "last-crash.txt").writeText(trace)
    }

    /** Laporan crash terakhir yang tersimpan di disk (bertahan lintas restart). */
    fun lastFromDisk(context: Context): String? {
        val f = File(File(context.filesDir, DIR), "last-crash.txt")
        return if (f.isFile) runCatching { f.readText() }.getOrNull() else null
    }

    fun clear(context: Context) {
        lastTrace = null
        File(context.filesDir, DIR).listFiles()?.forEach { it.delete() }
    }

    private fun stamp(): String =
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
}
