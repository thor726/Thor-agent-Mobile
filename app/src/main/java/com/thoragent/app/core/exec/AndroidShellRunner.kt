package com.thoragent.app.core.exec

import com.thoragent.app.core.root.RootManager
import com.topjohnwu.superuser.CallbackList
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/**
 * Runner on-device via libsu — mematuhi prinsip "root hanya saat dibutuhkan":
 * - executeNonRoot: shell sh/toybox biasa (tanpa su) — jalur default semua tool.
 * - executeRoot: shell su eksplisit — dipanggil hanya untuk eskalasi setelah
 *   eksekusi non-root ditolak izinnya (lihat NativeToolRegistry).
 * - execute (legacy): shell utama libsu (root bila granted, sh bila tidak).
 */
class AndroidShellRunner(private val rootManager: RootManager) : ExecutionInterface {

    override val runnerName: String = "AndroidShellRunner"
    override val isRooted: Boolean get() = Shell.isAppGrantedRoot() == true

    override suspend fun isAvailable(): Boolean = withContext(Dispatchers.IO) {
        try {
            rootManager.mainShell().isAlive
        } catch (e: Exception) {
            false
        }
    }

    /** Eksekusi shell TANPA root (sh/toybox) — jalur default. */
    suspend fun executeNonRoot(command: String, timeoutSec: Int, cwd: String?): ExecResult =
        run(command, timeoutSec, cwd, onLine = { }, root = false)

    /** Eksekusi via su — eskalasi root eksplisit (dipakai seperlunya). */
    suspend fun executeRoot(command: String, timeoutSec: Int, cwd: String?): ExecResult =
        run(command, timeoutSec, cwd, onLine = { }, root = true)

    /** Eksekusi dengan streaming baris (shell utama libsu). */
    override suspend fun executeStream(
        command: String,
        onLine: (line: String) -> Unit,
        timeoutSec: Int,
        cwd: String?
    ): ExecResult = run(command, timeoutSec, cwd, onLine)

    override suspend fun execute(
        command: String,
        timeoutSec: Int,
        cwd: String?
    ): ExecResult = run(command, timeoutSec, cwd, onLine = { })

    private fun wrap(command: String, cwd: String?): String =
        if (cwd.isNullOrBlank()) command
        else "cd \"$cwd\" 2>/dev/null || true; $command"

    private suspend fun run(
        command: String,
        timeoutSec: Int,
        cwd: String?,
        onLine: (line: String) -> Unit = { },
        root: Boolean = false
    ): ExecResult = withContext(Dispatchers.IO) {
        val t0 = System.currentTimeMillis()
        val shell = try {
            if (root) rootManager.rootShell() else rootManager.nonRootShell()
        } catch (e: Exception) {
            return@withContext ExecResult(
                command, -1, "", "shell ${if (root) "su" else "sh"} tidak tersedia: ${e.message}",
                0, "$runnerName${if (root) "(root)" else ""}"
            )
        }
        val out = object : CallbackList<String>() {
            override fun onAddElement(line: String?) {
                line?.let { onLine(it) }
            }
        }
        val err = object : CallbackList<String>() {
            override fun onAddElement(line: String?) {
                line?.let { onLine("[stderr] $it") }
            }
        }
        val future = shell.newJob()
            .add(wrap(command, cwd))
            .to(out, err)
            .enqueue()
        val res = try {
            future.get(timeoutSec.toLong() + 5, TimeUnit.SECONDS)
        } catch (e: Exception) {
            return@withContext ExecResult(
                command, 124, "", "timeout eksekusi: ${e.message}",
                System.currentTimeMillis() - t0, runnerName, timedOut = true
            )
        }
        val sbOut = StringBuilder()
        for (l in res.out) sbOut.appendLine(l)
        val sbErr = StringBuilder()
        for (l in res.err) sbErr.appendLine(l)
        ExecResult(
            command = command,
            exitCode = res.code,
            stdout = sbOut.toString().trim(),
            stderr = sbErr.toString().trim(),
            durationMs = System.currentTimeMillis() - t0,
            runner = "$runnerName${if (root) "(root)" else ""}"
        )
    }
}
