package com.thoragent.app.ui.root

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.thoragent.app.core.root.RootStatus
import com.thoragent.app.runtime.ThorRuntime
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class RootViewModel(app: Application) : AndroidViewModel(app) {

    private val rt = ThorRuntime.runtime(app)

    private val _status = MutableStateFlow<RootStatus?>(null)
    val status: StateFlow<RootStatus?> = _status.asStateFlow()

    private val _suTest = MutableStateFlow<String?>(null)
    val suTest: StateFlow<String?> = _suTest.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    /** Toggle: gunakan root hanya saat eksekusi filesystem butuh (bawaan: ya). */
    val useRoot = rt.settings.useRoot
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _busy.value = true
            _status.value = rt.root.status(force = true)
            _busy.value = false
        }
    }

    fun setUseRoot(v: Boolean) {
        viewModelScope.launch { rt.settings.setUseRoot(v) }
    }

    /** Uji shell biasa (non-root) — jalur default aplikasi. */
    fun testSh() {
        viewModelScope.launch {
            _busy.value = true
            val r = rt.androidRunner.executeNonRoot("id; echo ---; uname -a", 10, null)
            _suTest.value = "exit=${r.exitCode}\n${r.stdout.take(800)}${r.stderr.take(200)}"
            _busy.value = false
        }
    }

    /** Uji shell su — hanya relevan bila root terdeteksi. */
    fun testSu() {
        viewModelScope.launch {
            _busy.value = true
            val st = rt.root.status()
            if (!st.appGrantedRoot) {
                _suTest.value = "root tidak tersedia — shell biasa tetap berfungsi untuk pekerjaan harian."
            } else {
                val r = runCatching { rt.androidRunner.executeRoot("id; whoami", 10, null) }
                    .getOrNull()
                _suTest.value = r?.let { "exit=${it.exitCode}\n${it.stdout.take(400)}${it.stderr.take(200)}" }
                    ?: "shell su gagal dibangun"
            }
            _busy.value = false
        }
    }
}
