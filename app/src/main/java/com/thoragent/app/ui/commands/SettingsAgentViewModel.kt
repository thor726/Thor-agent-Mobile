package com.thoragent.app.ui.commands

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.thoragent.app.runtime.ThorRuntime
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * ViewModel ringan untuk widget pengaturan di SettingsScreen (v0.8.1).
 *
 * Memakai SettingsRuntime yang SAMA dengan CommandCenterViewModel dan
 * dispatch /personality di ChatViewModel — menyetel lewat chip di Setelan
 * mengubah state yang sama dengan mengetik slash command, jadi tidak ada
 * dua sumber kebenaran.
 */
class SettingsAgentViewModel(app: Application) : AndroidViewModel(app) {

    private val rt: ThorRuntime = ThorRuntime.runtime(app)

    val personality: StateFlow<String> =
        rt.settings.personality.stateIn(viewModelScope, SharingStarted.Eagerly, "default")

    private val _toast = MutableStateFlow("")
    val toast: StateFlow<String> = _toast.asStateFlow()

    fun setPersonality(p: String) {
        viewModelScope.launch {
            rt.settings.setPersonality(p)
            _toast.value = "gaya jawaban: $p"
        }
    }
}
