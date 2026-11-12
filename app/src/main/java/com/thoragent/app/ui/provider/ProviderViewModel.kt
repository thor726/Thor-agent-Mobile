package com.thoragent.app.ui.provider

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.thoragent.app.data.db.ProviderProfileEntity
import com.thoragent.app.runtime.ThorRuntime
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class EditState(
    val editingId: Long? = null,   // null = profil baru
    val name: String = "",
    val baseUrl: String = "",
    val apiKey: String = "",       // kosong saat edit = pertahankan yang tersimpan
    val model: String = "",
    val temperature: Double = 0.7,
    val maxTokens: Int = 4096,
    val streaming: Boolean = true,
    val setDefault: Boolean = true
)

class ProviderViewModel(app: Application) : AndroidViewModel(app) {

    private val rt = ThorRuntime.runtime(app)

    val profiles: StateFlow<List<ProviderProfileEntity>> =
        rt.providers.observeProfiles()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _edit = MutableStateFlow(EditState())
    val edit: StateFlow<EditState> = _edit.asStateFlow()

    private val _testResult = MutableStateFlow<String?>(null)
    val testResult: StateFlow<String?> = _testResult.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _liveModels = MutableStateFlow<List<String>>(emptyList())
    val liveModels: StateFlow<List<String>> = _liveModels.asStateFlow()

    fun newProfile() {
        _edit.value = EditState(setDefault = profiles.value.isEmpty())
        _testResult.value = null
    }

    fun editProfile(p: ProviderProfileEntity) {
        _edit.value = EditState(
            editingId = p.id,
            name = p.name,
            baseUrl = p.baseUrl,
            apiKey = "", // jangan tampilkan key tersimpan
            model = p.model,
            temperature = p.temperature,
            maxTokens = p.maxTokens,
            streaming = p.streaming,
            setDefault = p.isDefault
        )
        _testResult.value = null
    }

    fun updateEdit(transform: (EditState) -> EditState) {
        _edit.value = transform(_edit.value)
    }

    /** Terapkan preset cepat (OpenRouter, OpenAI, Groq, lokal…) —
     *  TANPA model default: bidang model dibiarkan kosong supaya user
     *  wajib memilih dari daftar hidup endpoint (hemat saldo, tidak
     *  salah-salah model yang tidak ada di provider). */
    fun applyPreset(preset: com.thoragent.app.core.provider.ModelCatalog.Companion.ProviderPreset) {
        _edit.value = _edit.value.copy(
            baseUrl = preset.baseUrl,
            model = "",
            name = _edit.value.name.ifBlank { preset.label }
        )
        _testResult.value = null
        refreshLiveModels()
    }

    /** Ambil daftar model hidup dari endpoint /models provider pada formulir. */
    fun refreshLiveModels() {
        val e = _edit.value
        viewModelScope.launch {
            val savedKey = e.editingId?.let { rt.providers.get(it)?.apiKey } ?: ""
            val key = e.apiKey.ifBlank { savedKey }
            _liveModels.value = rt.modelCatalog.fetchLiveModels(e.baseUrl, key)
        }
    }

    fun save(onDone: () -> Unit) {
        val e = _edit.value
        if (e.name.isBlank() || e.baseUrl.isBlank() || e.model.isBlank()) return
        viewModelScope.launch {
            rt.providers.save(
                existingId = e.editingId,
                name = e.name,
                baseUrl = e.baseUrl,
                apiKeyPlain = e.apiKey,
                model = e.model,
                temperature = e.temperature,
                maxTokens = e.maxTokens,
                streaming = e.streaming,
                setDefault = e.setDefault
            )
            withContext(kotlinx.coroutines.Dispatchers.Main) { onDone() }
        }
    }

    fun delete(id: Long) {
        viewModelScope.launch { rt.providers.delete(id) }
    }

    fun setDefault(id: Long) {
        viewModelScope.launch { rt.providers.setDefault(id) }
    }

    fun testConnection() {
        val e = _edit.value
        viewModelScope.launch {
            _busy.value = true
            // key yang diuji: field bila diisi, kalau tidak key tersimpan (dekripsi saat itu juga)
            val savedKey = e.editingId?.let { rt.providers.get(it)?.apiKey } ?: ""
            val key = e.apiKey.ifBlank { savedKey }
            val res = rt.llm.testConnection(e.baseUrl, key)
            _testResult.value = res.let { (ok, msg) -> (if (ok) "OK" else "GAGAL: ") + msg }
            _busy.value = false
        }
    }
}
