package com.thoragent.app.core.terminal

import com.topjohnwu.superuser.CallbackList
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

/** Satu sesi terminal ala Termux: streaming baris, riwayat perintah, cwd nyata. */
class TerminalSession(
    val id: Long,
    val root: Boolean,
    private val shell: Shell,
    initialCwd: String
) {
    private val _output = MutableSharedFlow<String>(
        replay = 400, extraBufferCapacity = 512, onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val output: SharedFlow<String> = _output

    private val history = mutableListOf<String>()
    val historyList: List<String> get() = history

    @Volatile var cwd: String = initialCwd
    @Volatile var busy: Boolean = false

    private val outCb = object : CallbackList<String>() {
        override fun onAddElement(line: String?) {
            line?.let { _output.tryEmit(it) }
        }
    }
    private val errCb = object : CallbackList<String>() {
        override fun onAddElement(line: String?) {
            line?.let { _output.tryEmit("[E] $it") }
        }
    }

    init {
        _output.tryEmit("Thor Terminal — mode: ${if (root) "root (su)" else "non-root (sh)"}")
        _output.tryEmit("Sesi #$id siap. cwd awal: $cwd")
    }

    fun prompt(): String = "${if (root) "# " else "$ "}"

    /** Jalankan satu perintah; kembalikan exit code. */
    fun run(command: String, timeoutSec: Int = 30): Int {
        if (busy) {
            _output.tryEmit("[W] perintah sebelumnya masih berjalan")
            return -1
        }
        busy = true
        try {
            history.add(command)
            _output.tryEmit("${prompt()}$command")
            val wrapped = "cd \"$cwd\" 2>/dev/null || true; $command"
            val future = shell.newJob().add(wrapped).to(outCb, errCb).enqueue()
            val res = try {
                future.get(timeoutSec.toLong() + 10, TimeUnit.SECONDS)
            } catch (e: Exception) {
                _output.tryEmit("[timeout] $command tidak selesai dalam ${timeoutSec}s")
                return 124
            }
            _output.tryEmit("[exit ${res.code}]")
            return res.code
        } finally {
            busy = false
        }
    }

    /** Ambil cwd nyata terakhir (best-effort). */
    fun refreshCwd() {
        try {
            val out = mutableListOf<String>()
            shell.newJob().add("cd \"$cwd\" 2>/dev/null || true; pwd").to(out, null).enqueue().get(5, TimeUnit.SECONDS)
            out.lastOrNull()?.let { cwd = it.trim() }
        } catch (_: Exception) {
        }
    }

    fun close() {
        try {
            shell.close()
        } catch (_: Exception) {
        }
    }

    companion object {
        private val seq = AtomicLong(0)
        fun nextId(): Long = seq.incrementAndGet()
    }
}
