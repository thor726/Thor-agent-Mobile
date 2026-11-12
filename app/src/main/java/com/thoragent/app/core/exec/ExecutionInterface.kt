package com.thoragent.app.core.exec

/**
 * Abstraksi eksekusi perintah shell — diport dari arsitektur (Bab 6).
 * Implementasi: [com.thoragent.app.core.exec.AndroidShellRunner] (on-device, libsu).
 * Menjaga pintu integrasi bila runner lain (desktop/rpc) ditambahkan kelak.
 */
interface ExecutionInterface {
    val runnerName: String
    val isRooted: Boolean

    /** Apakah runner ini siap dipakai saat ini. */
    suspend fun isAvailable(): Boolean

    /** Eksekusi satu perintah, kembalikan hasil lengkap. */
    suspend fun execute(command: String, timeoutSec: Int = 60, cwd: String? = null): ExecResult

    /** Eksekusi dengan streaming baris demi baris (untuk terminal & tool long-running). */
    suspend fun executeStream(
        command: String,
        onLine: (line: String) -> Unit,
        timeoutSec: Int = 60,
        cwd: String? = null
    ): ExecResult
}

data class ExecResult(
    val command: String,
    val exitCode: Int,
    val stdout: String,
    val stderr: String,
    val durationMs: Long,
    val runner: String,
    val timedOut: Boolean = false
) {
    val isSuccess: Boolean get() = exitCode == 0 && !timedOut

    val summary: String
        get() = if (timedOut) "timeout setelah ${durationMs}ms"
        else "exit=$exitCode dalam ${durationMs}ms [${runner}]"
}
