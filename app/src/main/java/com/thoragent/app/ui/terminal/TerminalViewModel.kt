package com.thoragent.app.ui.terminal

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.thoragent.app.core.root.RootManager
import com.thoragent.app.core.terminal.TerminalSession
import com.thoragent.app.runtime.ThorRuntime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class TerminalUiState(
    val sessionId: Long? = null,
    val rootMode: Boolean = false,
    val cwd: String = "/sdcard",
    val lines: List<String> = emptyList(),
    val busy: Boolean = false
)

class TerminalViewModel(app: Application) : AndroidViewModel(app) {

    private val rt = ThorRuntime.runtime(app)
    val rootStatus: StateFlow<com.thoragent.app.core.root.RootStatus?> =
        kotlinx.coroutines.flow.flow { emit(rt.root.status()) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(10_000), null)

    private val _ui = MutableStateFlow(TerminalUiState())
    val ui: StateFlow<TerminalUiState> = _ui.asStateFlow()

    private val _historyIndex = MutableStateFlow(-1)
    private var session: TerminalSession? = null
    private var collected = mutableListOf<String>()

    val cwdSetting = rt.settings.cwd.stateIn(viewModelScope, SharingStarted.Eagerly, "/sdcard")

    fun startSession(root: Boolean, cwd: String) {
        closeSession()
        viewModelScope.launch(Dispatchers.IO) {
            val s = rt.terminalManager.create(root, cwd)
            if (s == null) {
                _ui.value = _ui.value.copy(lines = listOf("[gagal] shell ${if (root) "root (su)" else "sh"} tidak tersedia"))
                return@launch
            }
            session = s
            collected = mutableListOf()
            _ui.value = TerminalUiState(sessionId = s.id, rootMode = root, cwd = cwd, lines = emptyList())
            viewModelScope.launch {
                s.output.collect { line ->
                    collected.add(line)
                    if (collected.size > 2000) collected = collected.takeLast(1500).toMutableList()
                    _ui.value = _ui.value.copy(lines = collected.toList())
                }
            }
        }
    }

    fun runCommand(cmd: String) {
        val s = session ?: return
        if (cmd.isBlank()) return
        _ui.value = _ui.value.copy(busy = true)
        viewModelScope.launch(Dispatchers.IO) {
            val code = s.run(cmd)
            s.refreshCwd()
            _ui.value = _ui.value.copy(busy = false, cwd = s.cwd)
            rt.settings.setCwd(s.cwd)
        }
    }

    fun closeSession() {
        session?.let { rt.terminalManager.close(it.id) }
        session = null
        collected = mutableListOf()
    }

    /** Navigasi riwayat perintah (↑/↓). */
    fun historyNav(up: Boolean): String? {
        val s = session ?: return null
        val h = s.historyList
        if (h.isEmpty()) return null
        var idx = _historyIndex.value
        idx = if (up) (idx + 1).coerceAtMost(h.size) else (idx - 1)
        if (idx == 0) { _historyIndex.value = 0; return "" }
        _historyIndex.value = idx
        return h.getOrNull(h.size - idx)
    }

    override fun onCleared() {
        // sesi terminal dibiarkan hidup di manager agar tetap bisa dipakai latar depan
        super.onCleared()
    }
}
