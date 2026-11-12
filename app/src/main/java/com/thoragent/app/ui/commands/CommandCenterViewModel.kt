package com.thoragent.app.ui.commands

import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import android.app.Application
import com.thoragent.app.runtime.ThorRuntime
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * ViewModel Pusat Perintah (v0.6.0) — sumber state untuk tombol slash command.
 *
 * Hanya pengaturan BER-STATE yang tampil sebagai kontrol (slider/toggle/
 * segmented) — pemetaan sama dengan cabang dispatch di ChatViewModel, jadi
 * tombol dan perintah slash selalu mengubah state yang sama.
 * Pengaturan berargumen teks (goal, subgoal, cron, title, …) TIDAK dipaksa
 * jadi tombol; tetap lewat perintah slash.
 */
class CommandCenterViewModel(app: Application) : AndroidViewModel(app) {

    private val rt: ThorRuntime = ThorRuntime.runtime(app)

    /** Callback agar layar dapat mengirim perintah slash apa pun lewat
     *  jalur yang sudah teruji (dispatch 105 perintah di ChatViewModel). */
    var sendSlash: (suspend (String) -> Boolean)? = null

    // ---- state pengaturan (StateFlow: UI chat langsung reaktif) ----
    val reasoningLevel: StateFlow<String> =
        rt.settings.reasoningLevel.stateIn(viewModelScope, SharingStarted.Eagerly, "")
    val yolo: StateFlow<Boolean> =
        rt.settings.yolo.stateIn(viewModelScope, SharingStarted.Eagerly, false)
    val timestamps: StateFlow<Boolean> =
        rt.settings.timestamps.stateIn(viewModelScope, SharingStarted.Eagerly, true)
    val focusMode: StateFlow<Boolean> =
        rt.settings.focusMode.stateIn(viewModelScope, SharingStarted.Eagerly, false)
    val statusbar: StateFlow<Boolean> =
        rt.settings.statusbar.stateIn(viewModelScope, SharingStarted.Eagerly, true)
    val battery: StateFlow<Boolean> =
        rt.settings.battery.stateIn(viewModelScope, SharingStarted.Eagerly, false)
    val voice: StateFlow<Boolean> =
        rt.settings.voice.stateIn(viewModelScope, SharingStarted.Eagerly, false)
    val verboseMode: StateFlow<String> =
        rt.settings.verboseMode.stateIn(viewModelScope, SharingStarted.Eagerly, "new")
    val skin: StateFlow<String> =
        rt.settings.skin.stateIn(viewModelScope, SharingStarted.Eagerly, "thor")
    val personality: StateFlow<String> =
        rt.settings.personality.stateIn(viewModelScope, SharingStarted.Eagerly, "default")

    private val _toast = MutableStateFlow("")
    val toast: StateFlow<String> = _toast.asStateFlow()

    private fun emitToast(msg: String) {
        _toast.value = msg
    }

    // ---- setter (logika & pesan toast sama persis dengan cabang slash) ----
    fun setReasoning(level: String) {
        viewModelScope.launch {
            rt.settings.setReasoningLevel(level)
            emitToast("reasoning effort: ${level.ifBlank { "(bawaan)" }}")
        }
    }

    fun toggleYolo() {
        viewModelScope.launch {
            val next = !rt.settings.yolo.first()
            rt.settings.setYolo(next)
            emitToast(
                if (next) "YOLO aktif — persetujuan perintah berbahaya DILEWATI"
                else "YOLO nonaktif — persetujuan kembali aktif"
            )
        }
    }

    fun setApprovals(mode: String) {
        viewModelScope.launch {
            when (mode) {
                "off" -> { rt.settings.setYolo(true); emitToast("persetujuan: NONAKTIF (yolo)") }
                "smart" -> {
                    rt.settings.setYolo(false); rt.settings.setConfirmDangerous(true)
                    emitToast("persetujuan: CERDAS (default)")
                }
                "manual" -> {
                    rt.settings.setYolo(false); rt.settings.setConfirmDangerous(true)
                    emitToast("persetujuan: MANUAL")
                }
            }
        }
    }

    fun setTimestamps(b: Boolean) {
        viewModelScope.launch { rt.settings.setTimestamps(b); emitToast("timestamp pesan: ${if (b) "aktif" else "nonaktif"}") }
    }

    fun setFocus(b: Boolean) {
        viewModelScope.launch { rt.settings.setFocusMode(b); emitToast("focus view: ${if (b) "aktif" else "nonaktif"}") }
    }

    fun setStatusbar(b: Boolean) {
        viewModelScope.launch { rt.settings.setStatusbar(b); emitToast("status bar mini: ${if (b) "aktif" else "nonaktif"}") }
    }

    fun setBattery(b: Boolean) {
        viewModelScope.launch { rt.settings.setBattery(b); emitToast("indikator baterai: ${if (b) "aktif" else "nonaktif"}") }
    }

    fun setVoice(b: Boolean) {
        viewModelScope.launch { rt.settings.setVoice(b); emitToast(if (b) "mode suara AKTIF — balasan dibacakan" else "mode suara nonaktif") }
    }

    fun setVerbose(mode: String) {
        viewModelScope.launch { rt.settings.setVerboseMode(mode); emitToast("tampilan progres tool: $mode") }
    }

    fun setSkin(s: String) {
        viewModelScope.launch { rt.settings.setSkin(s); emitToast("skin: $s — langsung terlihat") }
    }

    fun setPersonality(p: String) {
        viewModelScope.launch {
            rt.settings.setPersonality(p)
            emitToast("personality: $p — berlaku dari giliran berikutnya")
        }
    }

    fun setFast(on: Boolean) {
        viewModelScope.launch {
            rt.settings.setReasoningLevel(if (on) "low" else "")
            emitToast(
                if (on) "mode cepat: reasoning low — jawaban singkat & responsif"
                else "mode cepat dimatikan — reasoning kembali bawaan"
            )
        }
    }

    /** Kirim perintah slash apa pun (aksi satu-tombol) lewat dispatch teruji. */
    fun runSlash(cmd: String) {
        viewModelScope.launch {
            val handled = sendSlash?.invoke("/$cmd") ?: false
            if (!handled) emitToast("perintah /$cmd tidak dikenal")
        }
    }

    /** Dipakai layar untuk menyambungkan callback dispatch. */
    suspend fun sendSlashSafe(cmd: String): Boolean = sendSlash?.invoke(cmd) ?: false

    fun clearToast() {
        _toast.value = ""
    }
}
