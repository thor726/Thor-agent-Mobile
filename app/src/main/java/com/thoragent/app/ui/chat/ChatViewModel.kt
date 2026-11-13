package com.thoragent.app.ui.chat

import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.BatteryManager
import android.speech.tts.TextToSpeech
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.thoragent.app.core.agent.AgentEvent
import com.thoragent.app.core.agent.AgentTools
import com.thoragent.app.core.diag.CrashLogger
import com.thoragent.app.core.llm.LlmMessage
import com.thoragent.app.core.llm.LlmUsage
import com.thoragent.app.core.provider.ProviderProfile
import com.thoragent.app.core.service.AgentForegroundService
import com.thoragent.app.data.db.MessageEntity
import com.thoragent.app.data.db.SessionEntity
import com.thoragent.app.runtime.ThorRuntime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlin.coroutines.resume

/** Permintaan persetujuan perintah berbahaya (approval floor ala Thor-agent). */
data class ApprovalRequest(
    val toolName: String,
    val command: String,
    val reason: String?,
    val onResponse: (Boolean) -> Unit
)

/** Statistik pemakaian token sesi — ala /usage /status repo sumber. */
data class UsageStats(
    val promptTokens: Long = 0,
    val completionTokens: Long = 0,
    val totalTokens: Long = 0,
    val llmCalls: Int = 0,
    val toolCalls: Int = 0
) {
    fun add(u: LlmUsage): UsageStats = UsageStats(
        promptTokens + u.promptTokens,
        completionTokens + u.completionTokens,
        totalTokens + u.totalTokens,
        llmCalls + 1,
        toolCalls
    )
}

/** Aksi UI yang dipicu slash command (buka palet, bagikan berkas, dll). */
sealed interface ChatUiAction {
    data object OpenModelPicker : ChatUiAction
    data object OpenSessions : ChatUiAction
    data object OpenSkillHub : ChatUiAction
    data object OpenTools : ChatUiAction
    data object OpenHelp : ChatUiAction
    data object FocusComposer : ChatUiAction
    data object PickImage : ChatUiAction
    data object PickProfileImport : ChatUiAction
    data object RestartApp : ChatUiAction
    data object QuitApp : ChatUiAction
    data class OpenUrl(val url: String) : ChatUiAction
    data class ShareFile(val path: String, val mime: String) : ChatUiAction
}

/** Entri cron sederhana — /cron (berjalan selama aplikasi terbuka). */
@Serializable
data class CronEntry(
    val id: Int,
    val prompt: String,
    val everyMin: Long,
    val lastRun: Long = 0L
)

class ChatViewModel(app: Application) : AndroidViewModel(app) {

    private val rt: ThorRuntime = ThorRuntime.runtime(app)
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

    val sessions: StateFlow<List<SessionEntity>> =
        rt.db.sessionDao().observeSessions()
            // Error baca DB (mis. baris terlalu besar / DB korup) TIDAK boleh
            // mematikan proses: tanpa catch, exception lahir di dalam flow dan
            // langsung jadi force close tanpa pesan.
            .catch { _toast.tryEmit("gagal membaca sesi: ${it.message}") ; emit(emptyList()) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _sessionId = MutableStateFlow<Long?>(null)
    val sessionId: StateFlow<Long?> = _sessionId.asStateFlow()

    val messages: StateFlow<List<MessageEntity>> =
        _sessionId.flatMapLatest { id ->
            if (id == null) {
                flowOf(emptyList())
            } else {
                // catch di dalam: satu sesi yang datanya bermasalah tidak
                // menghentikan seluruh flow, dan aplikasi tetap hidup.
                rt.db.messageDao().observeMessages(id)
                    .catch { _toast.tryEmit("gagal membaca pesan: ${it.message}"); emit(emptyList()) }
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val streamingText = MutableStateFlow("")
    val busy = MutableStateFlow(false)
    val waitingFirstToken = MutableStateFlow(false)

    val providerInfo = rt.providers.observeProfiles()
        .map { list ->
            val p = list.firstOrNull { it.isDefault } ?: list.firstOrNull()
            p?.let { "${it.name} · ${it.model}" } ?: "belum ada provider"
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "belum ada provider")

    /** Model efektif (bukan "auto") setelah resolusi otomatis — untuk chip UI. */
    private val _effectiveModel = MutableStateFlow("")
    val effectiveModel: StateFlow<String> = _effectiveModel.asStateFlow()

    /** Info singkat kenapa model otomatis dipilih (untuk /status). */
    private val _autoModelNote = MutableStateFlow("")
    val autoModelNote: StateFlow<String> = _autoModelNote.asStateFlow()

    val cwd = rt.settings.cwd.stateIn(viewModelScope, SharingStarted.Eagerly, "/sdcard")

    /** Model khusus sesi (override) — "" = pakai model profil. */
    val sessionModel = rt.settings.sessionModel
        .stateIn(viewModelScope, SharingStarted.Eagerly, "")

    /** Tujuan berdiri (/goal). */
    val goal = rt.settings.goal
        .stateIn(viewModelScope, SharingStarted.Eagerly, "")

    /** Statistik token sesi. */
    private val _usage = MutableStateFlow(UsageStats())
    val usage: StateFlow<UsageStats> = _usage.asStateFlow()

    private val _approval = MutableStateFlow<ApprovalRequest?>(null)
    val approval: StateFlow<ApprovalRequest?> = _approval.asStateFlow()

    private val _toast = MutableSharedFlow<String>(extraBufferCapacity = 16, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val toast = _toast

    /** Aksi UI dari slash command. */
    private val _uiAction = MutableSharedFlow<ChatUiAction>(extraBufferCapacity = 8, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val uiAction = _uiAction

    /** Prefill composer (dipakai /undo, /title, dll). */
    private val _prefill = MutableStateFlow("")
    val prefill: StateFlow<String> = _prefill.asStateFlow()

    /** Sisip teks ke posisi kursor composer (/paste) — tidak menimpa. */
    private val _insert = MutableStateFlow("")
    val insertText: StateFlow<String> = _insert.asStateFlow()

    // ==== v0.4.0: antrean pesan (perilaku queue repo sumber, kini beneran) ====
    private val _queue = MutableStateFlow<List<String>>(emptyList())
    val queue: StateFlow<List<String>> = _queue.asStateFlow()

    /** Lampiran gambar menunggu dikirim (/image) — path berkas. */
    private val _attachment = MutableStateFlow<String?>(null)
    val attachment: StateFlow<String?> = _attachment.asStateFlow()

    /** Tugas background yang berjalan (/bg). */
    private val _bgJobs = MutableStateFlow<List<String>>(emptyList())
    val bgJobs: StateFlow<List<String>> = _bgJobs.asStateFlow()

    // ==== v0.4.0: preferensi UI hidup (dipakai ChatScreen) ====
    val timestamps: StateFlow<Boolean> = rt.settings.timestamps.stateIn(viewModelScope, SharingStarted.Eagerly, true)
    val focusMode: StateFlow<Boolean> = rt.settings.focusMode.stateIn(viewModelScope, SharingStarted.Eagerly, false)
    val verboseMode: StateFlow<String> = rt.settings.verboseMode.stateIn(viewModelScope, SharingStarted.Eagerly, "new")
    val statusbarOn: StateFlow<Boolean> = rt.settings.statusbar.stateIn(viewModelScope, SharingStarted.Eagerly, true)
    val batteryOn: StateFlow<Boolean> = rt.settings.battery.stateIn(viewModelScope, SharingStarted.Eagerly, false)
    val petName: StateFlow<String> = rt.settings.pet.stateIn(viewModelScope, SharingStarted.Eagerly, "")
    val voiceOn: StateFlow<Boolean> = rt.settings.voice.stateIn(viewModelScope, SharingStarted.Eagerly, false)
    val pausedNow: StateFlow<Boolean> = rt.settings.paused.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    /** Sesi “rumah” (/sethome) — dibuka otomatis saat aplikasi jalan. */
    val homeSessionId: StateFlow<Long> = rt.settings.homeSession.stateIn(viewModelScope, SharingStarted.Eagerly, -1L)

    /** Registry slash command repo sumber (untuk palet interaktif). */
    val slashCommands: StateFlow<List<com.thoragent.app.core.agent.SlashCommandDef>> =
        kotlinx.coroutines.flow.flow {
            emit(runCatching { rt.slash.all() }.getOrDefault(emptyList()))
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Daftar model hidup provider aktif (untuk ModelPicker). */
    private val _liveModels = MutableStateFlow<List<String>>(emptyList())
    val liveModels: StateFlow<List<String>> = _liveModels.asStateFlow()

    private val _modelsLoading = MutableStateFlow(false)
    val modelsLoading: StateFlow<Boolean> = _modelsLoading.asStateFlow()

    fun refreshLiveModels() {
        if (_modelsLoading.value) return
        viewModelScope.launch {
            val p = rt.providers.current() ?: return@launch
            _modelsLoading.value = true
            _liveModels.value = rt.modelCatalog.fetchLiveModels(p.baseUrl, p.apiKey)
            _modelsLoading.value = false
        }
    }

    private var agentJob: Job? = null

    /** System prompt di-cache per sesi (stabil demi cache prefix provider). */
    private var cachedPromptKey: String? = null
    private var cachedPrompt: String? = null

    // ==== v0.4.0: TTS on-device (/voice) ====
    private var tts: TextToSpeech? = null
    private var ttsReady = false

    // ==== v0.4.0: /heartbeat & /loop ====
    private var repeatJob: Job? = null
    private var repeatKind = ""
    private var repeatIntervalMs = 0L
    private var repeatPrompt = ""

    // ==== v0.4.0: cron ticker ====
    private val cronFile: File
        get() = File(getApplication<Application>().filesDir, "thor-cron.json")

    init {
        // Ticker cron — memeriksa jadwal tiap menit selama aplikasi hidup.
        viewModelScope.launch {
            while (true) {
                delay(60_000)
                runCatching { checkCron() }
            }
        }
    }

    fun newSession(): Long? {
        val key = _sessionId.value
        if (key != null) return key
        viewModelScope.launch {
            val id = rt.db.sessionDao().upsert(
                SessionEntity(title = "Sesi baru", mode = "agent", providerId = 0, cwd = cwd.value)
            )
            _sessionId.value = id
        }
        return null
    }

    fun selectSession(id: Long?) {
        _sessionId.value = id
        _usage.value = UsageStats() // statistik per sesi
    }

    fun deleteSession(id: Long) {
        viewModelScope.launch {
            rt.db.messageDao().clearSession(id)
            rt.db.sessionDao().delete(id)
            if (_sessionId.value == id) _sessionId.value = sessions.value.firstOrNull()?.id
        }
    }

    fun renameSession(id: Long, title: String) {
        viewModelScope.launch { rt.db.sessionDao().rename(id, title.ifBlank { "Sesi" }) }
    }

    fun respondApproval(ok: Boolean) {
        _approval.value?.let { it.onResponse(ok) }
        _approval.value = null
    }

    fun clearPrefill() { _prefill.value = "" }
    fun consumeInsert() { _insert.value = "" }

    /** Simpan model khusus sesi (dipakai ModelPicker & /model). */
    fun setSessionModel(model: String) {
        viewModelScope.launch {
            rt.settings.setSessionModel(model)
            _toast.tryEmit(
                if (model.isBlank()) "model sesi dikembalikan ke profil provider"
                else "model sesi: $model (berlaku sesi ini — /model --global untuk permanen)"
            )
        }
    }

    /** Simpan model permanen ke profil provider (/model --global). */
    fun setGlobalModel(model: String) {
        viewModelScope.launch {
            val p = rt.providers.current()
            if (p == null) {
                _toast.tryEmit("belum ada profil provider")
            } else {
                rt.providers.setModel(p.id, model)
                rt.settings.setSessionModel("")
                _toast.tryEmit("model bawaan provider: $model")
            }
        }
    }

    fun stopAgent() {
        agentJob?.cancel()
        busy.value = false
        streamingText.value = ""
        waitingFirstToken.value = false
    }

    /** Edit & kirim ulang dari pesan user tertentu (menu tekan-lama). */
    fun editFrom(messageId: Long) {
        viewModelScope.launch {
            val sid = _sessionId.value ?: return@launch
            val m = rt.db.messageDao().listSession(sid).firstOrNull { it.id == messageId } ?: return@launch
            rt.db.messageDao().deleteFrom(sid, messageId)
            _prefill.value = m.content
            _toast.tryEmit("pesan dikembalikan ke kotak ketik — edit lalu kirim")
        }
    }

    /** Status baterai perangkat: (persen, mengisi?) — untuk status bar /battery. */
    fun batteryStatus(): Pair<Int, Boolean> = runCatching {
        val intent = getApplication<Application>()
            .registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
        val status = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val pct = if (level >= 0 && scale > 0) level * 100 / scale else -1
        pct to (status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL)
    }.getOrDefault(-1 to false)

    // ===================== KIRIM PESAN / SLASH COMMAND =====================

    fun send(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            val paused = rt.settings.paused.first()
            if (paused && !trimmed.startsWith("/pause")) {
                _toast.tryEmit("Dijeda global (/pause) — ketik /pause off untuk melanjutkan")
                return@launch
            }
            // Slash command SELALU dieksekusi seketika (lokal & murah);
            // agent-tier yang dikirim saat sibuk otomatis mengantre di sendToAgent.
            if (trimmed.startsWith("/")) {
                val handled = runSlashCommand(trimmed)
                if (!handled) sendToAgent(trimmed)
                return@launch
            }
            if (busy.value) {
                enqueue(trimmed)
                return@launch
            }
            sendToAgent(trimmed)
        }
    }

    private fun enqueue(text: String) {
        _queue.value = _queue.value + text
        _toast.tryEmit("Masuk antrean (#${_queue.value.size}) — terkirim otomatis saat agent selesai")
    }

    fun removeFromQueue(index: Int) {
        _queue.value = _queue.value.filterIndexed { i, _ -> i != index }
    }

    fun clearQueue() {
        _queue.value = emptyList()
        _toast.tryEmit("antrean dikosongkan")
    }

    private fun drainQueue() {
        if (_queue.value.isEmpty()) return
        val next = _queue.value.first()
        _queue.value = _queue.value.drop(1)
        send(next)
    }

    /** Lampirkan gambar dari photo picker Android (/image). */
    fun attachImage(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val app = getApplication<Application>()
                val mime = app.contentResolver.getType(uri) ?: "image/png"
                val ext = when {
                    mime.contains("jpeg") || mime.contains("jpg") -> "jpg"
                    mime.contains("webp") -> "webp"
                    mime.contains("gif") -> "gif"
                    else -> "png"
                }
                val dir = File(cwd.value, ".thor/attachments").apply { mkdirs() }
                val f = File(dir, "img-${System.currentTimeMillis()}.$ext")
                app.contentResolver.openInputStream(uri)!!.use { input ->
                    FileOutputStream(f).use { input.copyTo(it) }
                }
                _attachment.value = f.absolutePath
                _toast.tryEmit("gambar dilampirkan: ${f.name} — kirim pesanmu")
            }.onFailure { _toast.tryEmit("gagal melampirkan: ${it.message}") }
        }
    }

    fun clearAttachment() { _attachment.value = null }

    /** Impor profil (skill) dari arsip zip (/import). */
    fun importProfile(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            var imported = 0
            runCatching {
                val app = getApplication<Application>()
                val tmp = File(app.cacheDir, "import-${System.currentTimeMillis()}.zip")
                app.contentResolver.openInputStream(uri)!!.use { input ->
                    FileOutputStream(tmp).use { input.copyTo(it) }
                }
                ZipInputStream(FileInputStream(tmp)).use { zis ->
                    while (true) {
                        val e = zis.nextEntry ?: break
                        if (!e.isDirectory && e.name.endsWith("SKILL.md")) {
                            val parts = e.name.trim('/').split('/')
                            val skillDirName = parts.dropLast(1).lastOrNull()
                            if (skillDirName != null) {
                                val dest = File(rt.skills.userSkillsDir, skillDirName).apply { mkdirs() }
                                FileOutputStream(File(dest, "SKILL.md")).use { zis.copyTo(it) }
                                imported++
                            }
                        }
                        zis.closeEntry()
                    }
                }
                tmp.delete()
                rt.skills.invalidateCache()
                cachedPromptKey = null
            }.onFailure { _toast.tryEmit("impor gagal: ${it.message}") }
            _toast.tryEmit(
                if (imported > 0) "$imported skill diimpor — lihat di Skill Hub"
                else "tidak ada SKILL.md di dalam arsip"
            )
        }
    }

    // ==== arsip profil (dipakai /snapshot /export /handoff) ====

    private suspend fun exportProfile(prefix: String = "thor-profile"): File? =
        withContext(Dispatchers.IO) {
            runCatching {
                val dir = File(
                    android.os.Environment.getExternalStoragePublicDirectory(
                        android.os.Environment.DIRECTORY_DOWNLOADS
                    ), "thor-sesi"
                ).apply { mkdirs() }
                val out = File(dir, "$prefix-${System.currentTimeMillis()}.zip")
                ZipOutputStream(FileOutputStream(out)).use { zos ->
                    val dump = buildString {
                        appendLine("# Thor Agent Mobile — profil (${prefix})")
                        appendLine("waktu: ${java.text.SimpleDateFormat("dd MMM yyyy HH:mm", Locale("id")).format(java.util.Date())}")
                        appendLine("cwd: ${rt.settings.cwd.first()}")
                        appendLine("goal: ${rt.settings.goal.first()}")
                        appendLine("personality: ${rt.settings.personality.first()}")
                        appendLine("skin: ${rt.settings.skin.first()}")
                        appendLine("reasoning: ${rt.settings.reasoningLevel.first().ifBlank { "(bawaan)" }}")
                        appendLine("yolo: ${rt.settings.yolo.first()}")
                        appendLine("model sesi: ${rt.settings.sessionModel.first().ifBlank { "(profil)" }}")
                    }
                    zos.putNextEntry(ZipEntry("settings.txt"))
                    zos.write(dump.toByteArray())
                    zos.closeEntry()
                    val skillsDir = rt.skills.userSkillsDir
                    if (skillsDir.exists()) {
                        skillsDir.walkTopDown().filter { it.isFile }.forEach { f ->
                            val rel = f.relativeTo(skillsDir).path
                            zos.putNextEntry(ZipEntry("skills/$rel"))
                            FileInputStream(f).use { it.copyTo(zos) }
                            zos.closeEntry()
                        }
                    }
                }
                out
            }.getOrNull()
        }

    // ==== cron ====

    private fun loadCron(): MutableList<CronEntry> = runCatching {
        json.decodeFromString(ListSerializer(CronEntry.serializer()), cronFile.readText()).toMutableList()
    }.getOrDefault(mutableListOf())

    private fun saveCron(list: List<CronEntry>) {
        runCatching { cronFile.writeText(json.encodeToString(ListSerializer(CronEntry.serializer()), list)) }
    }

    private suspend fun checkCron() {
        val list = loadCron()
        val now = System.currentTimeMillis()
        var changed = false
        for (e in list) {
            if (e.everyMin > 0 && now - e.lastRun >= e.everyMin * 60_000) {
                if (!busy.value && !rt.settings.paused.first()) {
                    val i = list.indexOf(e)
                    list[i] = e.copy(lastRun = now)
                    changed = true
                    _toast.tryEmit("cron #${e.id} berjalan: ${e.prompt.take(40)}")
                    send(e.prompt)
                }
            }
        }
        if (changed) saveCron(list)
    }

    // ==== /heartbeat & /loop ====

    private fun startRepeat(kind: String, intervalMs: Long, prompt: String) {
        repeatKind = kind
        repeatIntervalMs = intervalMs
        repeatPrompt = prompt
        repeatJob?.cancel()
        repeatJob = viewModelScope.launch {
            while (true) {
                delay(intervalMs)
                if (busy.value || rt.settings.paused.first()) continue
                send(prompt)
            }
        }
    }

    private fun stopRepeat() {
        repeatJob?.cancel()
        repeatJob = null
        repeatKind = ""
        repeatIntervalMs = 0
        repeatPrompt = ""
    }

    // ==== TTS ====

    private fun speak(text: String) {
        val clean = text
            .replace(Regex("```[\\s\\S]*?```"), " (blok kode dilewati) ")
            .replace(Regex("[*_#>`\\[\\]|()~]"), " ")
            .take(1200)
        if (clean.isBlank()) return
        if (!ttsReady) {
            tts = TextToSpeech(getApplication()) { status ->
                ttsReady = status == TextToSpeech.SUCCESS
                if (ttsReady) {
                    runCatching { tts?.language = Locale("id", "ID") }
                    tts?.speak(clean, TextToSpeech.QUEUE_FLUSH, null, "thor-reply")
                }
            }
        } else {
            tts?.speak(clean, TextToSpeech.QUEUE_FLUSH, null, "thor-reply")
        }
    }

    // ==== pertanyaan sampingan (/btw) ====

    private suspend fun sideAsk(question: String) {
        val profile = rt.providers.current() ?: run {
            _toast.tryEmit("Atur provider dulu (Pengaturan Provider)")
            return
        }
        val model = rt.modelCatalog.resolveAuto(
            sessionModel.value.ifBlank { profile.model }, profile.baseUrl, profile.apiKey
        ).model
        if (model.isBlank()) {
            insertAssistantNotice(
                "**[sampingan dibatalkan]** ${question.take(80)}…\n\nModel belum dipilih — " +
                    "pilih model dulu lewat chip Model agar tidak salah model (memotong saldo)."
            )
            return
        }
        val sb = StringBuilder()
        val ctx = messages.first()
            .filter { it.kind == "text" && (it.role == "user" || it.role == "assistant") }
            .takeLast(6)
            .map { LlmMessage(role = it.role, content = it.content) }
        runCatching {
            rt.agent.run(
                baseUrl = profile.baseUrl,
                apiKey = profile.apiKey,
                model = model,
                systemPrompt = "Kamu Thor Agent. Jawab PERTANYAAN SAMPINGAN berikut secara singkat dan padat " +
                    "memakai konteks percakapan. Ini bukan tugas baru — jangan memanggil tool kecuali benar-benar perlu.",
                history = ctx,
                userText = question,
                temperature = profile.temperature,
                maxTokens = profile.maxTokens,
                stream = profile.streaming,
                cwd = cwd.value,
                reasoningEffort = rt.settings.reasoningLevel.first(),
                approver = ::approveTool
            ) { ev -> if (ev is AgentEvent.TextDelta) sb.append(ev.text) }
        }
        insertAssistantNotice(
            if (sb.isBlank()) "**[sampingan]** $question\n\n_(tidak ada jawaban)_"
            else "**[sampingan]** $question\n\n${sb}"
        )
    }

    // ==== tugas background (/bg) ====

    fun runBackground(prompt: String) {
        viewModelScope.launch {
            val name = prompt.take(34)
            _bgJobs.value = _bgJobs.value + name
            try {
                val profile = rt.providers.current() ?: run {
                    _toast.tryEmit("Atur provider dulu (Pengaturan Provider)")
                    return@launch
                }
                val sid = rt.db.sessionDao().upsert(
                    SessionEntity(title = "[bg] $name", mode = "agent", providerId = 0, cwd = cwd.value)
                )
                val model = rt.modelCatalog.resolveAuto(
                    sessionModel.value.ifBlank { profile.model }, profile.baseUrl, profile.apiKey
                ).model
                if (model.isBlank()) {
                    _toast.tryEmit("Tugas bg dibatalkan: model belum dipilih (pilih lewat chip Model)")
                    return@launch
                }
                val sb = StringBuilder()
                runCatching {
                    rt.agent.run(
                        baseUrl = profile.baseUrl,
                        apiKey = profile.apiKey,
                        model = model,
                        systemPrompt = systemPrompt(),
                        history = emptyList(),
                        userText = prompt,
                        temperature = profile.temperature,
                        maxTokens = profile.maxTokens,
                        stream = profile.streaming,
                        cwd = cwd.value,
                        reasoningEffort = rt.settings.reasoningLevel.first(),
                        approver = ::approveTool
                    ) { ev ->
                        when (ev) {
                            is AgentEvent.TextDelta -> sb.append(ev.text)
                            is AgentEvent.ToolCallStart -> rt.db.messageDao().insert(
                                MessageEntity(
                                    sessionId = sid, role = "assistant", kind = "tool_call",
                                    content = "", toolCallId = ev.id, toolName = ev.name,
                                    toolArgs = capStore(prettyJson(ev.argsJson), MAX_STORED_TOOL_ARGS)
                                )
                            )
                            is AgentEvent.ToolResult -> rt.db.messageDao().insert(
                                MessageEntity(
                                    sessionId = sid, role = "tool", kind = "tool_result",
                                    content = ev.resultJson.take(4000), toolCallId = ev.id, toolName = ev.name
                                )
                            )
                            else -> {}
                        }
                    }
                }
                if (sb.isNotBlank()) {
                    rt.db.messageDao().insert(
                        MessageEntity(sessionId = sid, role = "assistant", kind = "text", content = capStore(sb.toString(), MAX_STORED_TEXT))
                    )
                }
                insertAssistantNotice(
                    "**Tugas background selesai** — _${prompt.take(70)}_\n\nHasil tersimpan di sesi `[bg] $name` (Riwayat Sesi)."
                )
                _toast.tryEmit("tugas background selesai")
            } finally {
                _bgJobs.value = _bgJobs.value - name
            }
        }
    }

    override fun onCleared() {
        stopRepeat()
        runCatching { tts?.shutdown() }
        super.onCleared()
    }

    // ===================== RUNSLASH =====================

    /** Jalankan slash command. true = terconsumsi (jangan kirim ke LLM). */
    /**
     * Dipanggil Pusat Perintah (CommandCenterScreen): tombol di layar itu
     * mengirim perintah lewat dispatch 105 perintah ini — jalur sama
     * dengan mengetik slash di kotak chat, jadi perilaku identik.
     */
    fun setSlashSender(sender: suspend (String) -> Boolean) {
        slashSender = sender
    }

    private var slashSender: (suspend (String) -> Boolean)? = null

    /** Dipakai CommandCenterViewModel: jalankan perintah lewat dispatch. */
    suspend fun dispatchFromCommandCenter(cmd: String): Boolean =
        slashSender?.invoke(cmd) ?: runSlashCommand(cmd)

    private suspend fun runSlashCommand(input: String): Boolean {        val parsed = runCatching { rt.slash.parse(input) }.getOrNull()
        if (parsed == null) {
            if (input.trim() == "/") return false
            _toast.tryEmit("perintah tidak dikenal — ketik / untuk lihat semua perintah")
            return true
        }
        val name = parsed.command.name
        val args = parsed.args.trim()

        return when (name) {
            // ==================== Sesi ====================
            "new", "reset", "clear" -> {
                val id = rt.db.sessionDao().upsert(
                    SessionEntity(
                        title = args.ifBlank { "Sesi baru" }.take(40),
                        mode = "agent", providerId = 0, cwd = cwd.value
                    )
                )
                _sessionId.value = id
                _usage.value = UsageStats()
                _toast.tryEmit(if (name == "clear") "layar bersih — sesi baru dimulai" else "sesi baru dimulai")
                true
            }
            "title", "topic" -> {
                val sid = _sessionId.value
                if (sid == null) { _toast.tryEmit("belum ada sesi aktif"); return true }
                if (args.isBlank()) {
                    val s = sessions.value.firstOrNull { it.id == sid }
                    _toast.tryEmit("judul sesi: ${s?.title ?: "?"}")
                } else {
                    rt.db.sessionDao().rename(sid, args.take(60))
                    _toast.tryEmit("judul sesi diubah: $args")
                }
                true
            }
            // Laporan crash terakhir: satu-satunya cara pengguna mengirim jejak
            // force close tanpa adb. Ditampilkan sebagai pesan agar bisa disalin.
            "crash" -> {
                val trace = CrashLogger.lastFromDisk(getApplication()) ?: CrashLogger.last()
                if (trace == null) {
                    _toast.tryEmit("tidak ada crash tercatat di perangkat ini")
                } else {
                    insertAssistantNotice(
                        "**[laporan crash terakhir]** — salin seluruh teks di bawah:\n\n```\n" +
                            trace.take(12_000) + "\n```"
                    )
                    _toast.tryEmit("laporan ditampilkan — tekan lama untuk menyalin")
                }
                true
            }
            "sessions", "resume" -> { _uiAction.tryEmit(ChatUiAction.OpenSessions); true }
            "retry" -> {
                val sid = _sessionId.value ?: return true
                val list = rt.db.messageDao().listSession(sid)
                val lastUser = list.lastOrNull { it.role == "user" && it.kind == "text" } ?: run {
                    _toast.tryEmit("tidak ada pesan untuk dicoba ulang"); return true
                }
                rt.db.messageDao().deleteFrom(sid, lastUser.id)
                sendToAgent(lastUser.content)
                true
            }
            "undo", "rewind" -> {
                val sid = _sessionId.value ?: return true
                val n = args.toIntOrNull() ?: 1
                val list = rt.db.messageDao().listSession(sid)
                val userIds = list.filter { it.role == "user" && it.kind == "text" }.map { it.id }
                if (userIds.isEmpty()) { _toast.tryEmit("tidak ada giliran untuk dibatalkan"); return true }
                val target = userIds.takeLast(n.coerceIn(1, userIds.size)).first()
                val removedUser = list.first { it.id == target }
                rt.db.messageDao().deleteFrom(sid, target)
                _prefill.value = removedUser.content
                _toast.tryEmit("prompt dikembalikan ke kotak ketik — edit lalu kirim ulang")
                true
            }
            "stop" -> { stopAgent(); _toast.tryEmit("agent dihentikan"); true }
            "history" -> {
                val sid = _sessionId.value ?: return true
                val list = rt.db.messageDao().listSession(sid)
                val sb = StringBuilder("**Riwayat sesi** (${list.size} baris)\n\n")
                list.filter { it.kind == "text" }.takeLast(20).forEach {
                    val who = if (it.role == "user") "Anda" else "Thor"
                    sb.append("- **$who**: ").append(it.content.replace('\n', ' ').take(80)).append('\n')
                }
                insertAssistantNotice(sb.toString())
                true
            }
            "save" -> {
                val sid = _sessionId.value ?: return true
                val fmt = args.split(" ").firstOrNull()?.lowercase() ?: "md"
                val list = rt.db.messageDao().listSession(sid)
                val sb = StringBuilder("# Thor Agent — Ekspor Sesi\n\n")
                list.filter { it.kind == "text" || it.kind == "tool_call" }.forEach {
                    when {
                        it.kind == "text" && it.role == "user" -> sb.append("## Anda\n\n").append(it.content).append("\n\n")
                        it.kind == "text" -> sb.append("## Thor\n\n").append(it.content).append("\n\n")
                        it.kind == "tool_call" -> sb.append("> tool: ").append(it.toolName).append("\n\n")
                    }
                }
                val dir = File(
                    android.os.Environment.getExternalStoragePublicDirectory(
                        android.os.Environment.DIRECTORY_DOWNLOADS
                    ), "thor-sesi"
                ).apply { mkdirs() }
                val file = File(dir, "sesi-${System.currentTimeMillis()}.$fmt")
                file.writeText(sb.toString())
                insertAssistantNotice("Sesi diekspor ke:\n`${file.absolutePath}`")
                true
            }
            "copy" -> {
                val last = messages.value.lastOrNull { it.role == "assistant" && it.kind == "text" }
                if (last == null) { _toast.tryEmit("belum ada balasan untuk disalin"); return true }
                val cm = getApplication<Application>().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("Thor", last.content))
                _toast.tryEmit("balasan terakhir disalin ke clipboard")
                true
            }
            "paste" -> {
                val cm = getApplication<Application>().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val text = cm.primaryClip?.getItemAt(0)?.coerceToText(getApplication())?.toString().orEmpty()
                if (text.isBlank()) _toast.tryEmit("clipboard kosong")
                else { _insert.value = text; _toast.tryEmit("ditempel ke kotak ketik") }
                true
            }
            "prompt" -> {
                _uiAction.tryEmit(ChatUiAction.FocusComposer)
                _toast.tryEmit("composer adalah editor prompt Anda — tulis lalu kirim")
                true
            }
            "queue" -> handleQueue(args)
            "discard" -> {
                val n = _queue.value.size
                _queue.value = emptyList()
                _toast.tryEmit(if (n > 0) "$n pesan antrean dibuang" else "antrean memang kosong")
                true
            }
            "approve" -> {
                val ap = _approval.value
                if (ap == null) _toast.tryEmit("tidak ada perintah yang menunggu persetujuan")
                else { respondApproval(true); _toast.tryEmit("disetujui — perintah dijalankan") }
                true
            }
            "deny" -> {
                val ap = _approval.value
                if (ap == null) _toast.tryEmit("tidak ada perintah yang menunggu persetujuan")
                else {
                    respondApproval(false)
                    _toast.tryEmit("ditolak${if (args.isNotBlank()) " — ${args.take(50)}" else ""}")
                }
                true
            }
            "pause" -> {
                if (args.equals("off", true) || args.equals("resume", true)) {
                    rt.settings.setPaused(false)
                    _toast.tryEmit("dilanjutkan — antrean diproses kembali")
                    drainQueue()
                } else {
                    stopAgent()
                    rt.settings.setPaused(true)
                    _toast.tryEmit("DIJEDA global — pesan baru ditahan sampai /pause off")
                }
                true
            }
            "btw" -> {
                if (args.isBlank()) { _prefill.value = "/btw "; _toast.tryEmit("apa pertanyaan sampingannya?") }
                else sideAsk(args)
                true
            }
            "steer" -> {
                if (args.isBlank()) { _prefill.value = "/steer "; _toast.tryEmit("apa arahan yang disuntikkan?") }
                else {
                    _queue.value = listOf(args) + _queue.value
                    _toast.tryEmit("arahan disuntik ke depan antrean — diproses setelah tool call berikutnya")
                }
                true
            }
            "bg" -> {
                if (args.isBlank()) { _prefill.value = "/bg "; _toast.tryEmit("tugas apa yang dijalankan di belakang?") }
                else runBackground(args)
                true
            }
            "agents" -> {
                val cron = loadCron()
                insertAssistantNotice(
                    buildString {
                        append("**Agent & tugas berjalan**\n\n")
                        append("- Loop utama: **${if (busy.value) "BEKERJA" else "idle"}**")
                        if (streamingText.value.isNotBlank()) append(" (sedang menulis balasan)")
                        append("\n")
                        append("- Antrean pesan: ${_queue.value.size}")
                        if (_queue.value.isNotEmpty()) append(" — " + _queue.value.take(3).joinToString("; ") { "\"${it.take(30)}\"" })
                        append("\n")
                        append("- Tugas background: ${_bgJobs.value.size}")
                        _bgJobs.value.take(3).forEach { append("\n  - $it") }
                        append("\n")
                        append("- Heartbeat/loop: ${if (repeatJob?.isActive == true) "$repeatKind tiap ${repeatIntervalMs / 60000} mnt" else "nonaktif"}\n")
                        append("- Cron: ${cron.size} entri")
                        if (cron.isNotEmpty()) cron.take(3).forEach { append("\n  - #${it.id} tiap ${it.everyMin} mnt: ${it.prompt.take(40)}") }
                        append("\n- Jeda global: **${if (rt.settings.paused.first()) "AKTIF (/pause)" else "tidak"}**")
                    }
                )
                true
            }
            "branch" -> {
                val sid = _sessionId.value
                if (sid == null) { _toast.tryEmit("belum ada sesi untuk dicabang"); return true }
                val list = rt.db.messageDao().listSession(sid)
                val src = sessions.value.firstOrNull { it.id == sid }
                val nid = rt.db.sessionDao().upsert(
                    SessionEntity(title = "[cabang] ${src?.title ?: "Sesi"}".take(60), mode = "agent", providerId = 0, cwd = cwd.value)
                )
                list.filter { it.kind == "text" }.takeLast(12).forEach {
                    rt.db.messageDao().insert(it.copy(id = 0, sessionId = nid))
                }
                _sessionId.value = nid
                _toast.tryEmit("cabangan sesi dibuat — 12 pesan terakhir disalin")
                true
            }
            "start" -> {
                insertAssistantNotice(
                    "**Thor Agent siap.**\n\nSemua 105 perintah repo sumber jalan di Android. " +
                        "Ketik `/` untuk membuka palet perintah, atau langsung bicara — saya bisa " +
                        "mengerjakan berkas, terminal, GitHub, web, dan skill."
                )
                true
            }
            "sethome" -> {
                val sid = _sessionId.value
                rt.settings.setHomeSession(sid ?: -1L)
                _toast.tryEmit(if (sid != null) "sesi ini jadi rumah — dibuka otomatis saat aplikasi jalan" else "sesi rumah dihapus")
                true
            }
            "snapshot" -> {
                val f = exportProfile("thor-snapshot")
                if (f == null) _toast.tryEmit("gagal membuat snapshot")
                else insertAssistantNotice("**Snapshot profil tersimpan**\n\n`${f.absolutePath}`\n\nBerisi pengaturan + skill Anda — pulihkan lewat `/import`.")
                true
            }
            "export" -> {
                val f = exportProfile("thor-profile")
                if (f == null) _toast.tryEmit("gagal mengekspor profil")
                else {
                    _uiAction.tryEmit(ChatUiAction.ShareFile(f.absolutePath, "application/zip"))
                    insertAssistantNotice("**Profil diekspor** — ${f.name}\n\n`${f.absolutePath}`")
                }
                true
            }
            "import" -> { _uiAction.tryEmit(ChatUiAction.PickProfileImport); true }
            "handoff" -> {
                val f = exportProfile("thor-handoff")
                if (f != null) {
                    _uiAction.tryEmit(ChatUiAction.ShareFile(f.absolutePath, "application/zip"))
                    insertAssistantNotice(
                        "**Handoff versi mobile.** Alihkan sesi antar platform messaging adalah fitur " +
                            "gateway desktop; padanan Android: profil + skill Anda dikemas jadi arsip " +
                            "untuk dibagikan (share sheet terbuka)."
                    )
                } else _toast.tryEmit("gagal mengekspor untuk handoff")
                true
            }
            "compress" -> {
                val sid = _sessionId.value ?: return true
                val list = rt.db.messageDao().listSession(sid)
                val keep = Regex("here\\s+(\\d+)").find(args)?.groupValues?.get(1)?.toIntOrNull() ?: 12
                val textMsgs = list.filter { it.kind == "text" }
                if (textMsgs.size <= keep) { _toast.tryEmit("context masih ringkas — tidak perlu dipadatkan"); return true }
                val keepFrom = textMsgs.takeLast(keep).first().id
                val toDelete = list.filter { it.id < keepFrom }.map { it.id }
                rt.db.messageDao().deleteByIds(toDelete)
                insertAssistantNotice("**Context dipadatkan** — ${toDelete.size} baris lama diarsipkan, $keep giliran terakhir dipertahankan.")
                true
            }
            "heartbeat" -> handleHeartbeat(args)
            "loop" -> handleLoop(args)

            // ==================== Konfigurasi ====================
            "model" -> {
                if (args.isBlank()) { _uiAction.tryEmit(ChatUiAction.OpenModelPicker); return true }
                var rest = args
                val global = rest.contains("--global")
                rest = rest.replace("--global", "").replace("--session", "").trim()
                if (rest.startsWith("--provider")) rest = rest.split(" ", limit = 3).getOrNull(2) ?: ""
                if (rest.isBlank()) { _uiAction.tryEmit(ChatUiAction.OpenModelPicker); return true }
                if (global) setGlobalModelNow(rest) else {
                    rt.settings.setSessionModel(rest)
                    _toast.tryEmit("model sesi: $rest")
                }
                true
            }
            "yolo" -> {
                val cur = rt.settings.yolo.first()
                rt.settings.setYolo(!cur)
                _toast.tryEmit(if (!cur) "YOLO aktif — persetujuan perintah berbahaya DILEWATI" else "YOLO nonaktif — persetujuan kembali aktif")
                true
            }
            "approvals" -> {
                when (args.split(" ").firstOrNull()?.lowercase()) {
                    "off" -> { rt.settings.setYolo(true); _toast.tryEmit("persetujuan: NONAKTIF (yolo)") }
                    "smart" -> { rt.settings.setYolo(false); rt.settings.setConfirmDangerous(true); _toast.tryEmit("persetujuan: CERDAS (default)") }
                    "manual" -> { rt.settings.setYolo(false); rt.settings.setConfirmDangerous(true); _toast.tryEmit("persetujuan: MANUAL") }
                    else -> {
                        val y = rt.settings.yolo.first()
                        insertAssistantNotice("**Mode persetujuan**: ${if (y) "NONAKTIF (yolo)" else "AKTIF"}\n\n`/approvals off|smart|manual` untuk mengubah.")
                    }
                }
                true
            }
            "reasoning" -> {
                val lvl = args.split(" ").firstOrNull()?.lowercase() ?: ""
                // sinkron dengan sumber: VALID_REASONING_EFFORTS (thor_constants.py)
                // + "none" (nonaktif) — 'ultra' = level tertinggi (ADAPTIVE_EFFORT_MAP:
                // ultra→max di Anthropic; dikirim verbatim ke provider).
                val valid = listOf("none", "minimal", "low", "medium", "high", "xhigh", "max", "ultra")
                if (lvl in valid) {
                    rt.settings.setReasoningLevel(lvl)
                    _toast.tryEmit("reasoning effort: $lvl")
                } else {
                    val cur = rt.settings.reasoningLevel.first()
                    insertAssistantNotice("**Reasoning effort**: ${cur.ifBlank { "(bawaan)" }}\n\nPilihan: none · minimal · low · medium · high · xhigh · max · ultra")
                }
                true
            }
            "busy" -> {
                insertAssistantNotice(
                    "Pesan baru saat agent bekerja otomatis **mengantre** (perilaku `queue` repo sumber) " +
                        "dan terkirim begitu agent selesai. Kelola antrean lewat `/queue` — " +
                        "hentikan agent dengan `/stop`, suntik arahan dengan `/steer`."
                )
                true
            }
            "config" -> {
                insertAssistantNotice(
                    buildString {
                        append("**Konfigurasi saat ini**\n\n")
                        append("- CWD: `${cwd.value}`\n")
                        append("- YOLO: ${if (rt.settings.yolo.first()) "aktif" else "nonaktif"}\n")
                        append("- Reasoning: ${rt.settings.reasoningLevel.first().ifBlank { "(bawaan)" }}\n")
                        append("- Personality: ${rt.settings.personality.first().ifBlank { "default" }}\n")
                        append("- Skin: ${rt.settings.skin.first()}\n")
                        append("- Timestamps: ${if (rt.settings.timestamps.first()) "on" else "off"}\n")
                        append("- Focus view: ${if (rt.settings.focusMode.first()) "on" else "off"}\n")
                        append("- Verbose tool: ${rt.settings.verboseMode.first()}\n")
                        append("- Status bar: ${if (rt.settings.statusbar.first()) "on" else "off"}\n")
                        append("- Baterai di status bar: ${if (rt.settings.battery.first()) "on" else "off"}\n")
                        append("- Suara (TTS): ${if (rt.settings.voice.first()) "on" else "off"}\n")
                        append("- Pet: ${rt.settings.pet.first().ifBlank { "belum diadopsi" }}\n")
                        val g = rt.settings.goal.first()
                        if (g.isNotBlank()) append("- Goal: ${g.take(80)}\n")
                    }
                )
                true
            }
            "timestamps" -> handleBoolSetting(args, rt.settings.timestamps, rt.settings::setTimestamps, "Timestamp pesan")
            "focus" -> handleBoolSetting(args, rt.settings.focusMode, rt.settings::setFocusMode, "Focus view (kartu tool disembunyikan)")
            "statusbar" -> handleBoolSetting(args, rt.settings.statusbar, rt.settings::setStatusbar, "Status bar mini")
            "battery" -> handleBoolSetting(args, rt.settings.battery, rt.settings::setBattery, "Indikator baterai")
            "verbose" -> {
                val cur = rt.settings.verboseMode.first()
                val next = when (args.lowercase()) {
                    "off", "none" -> "off"
                    "new" -> "new"
                    "all" -> "all"
                    else -> when (cur) { "off" -> "new"; "new" -> "all"; else -> "off" }
                }
                rt.settings.setVerboseMode(next)
                _toast.tryEmit("tampilan progres tool: $next")
                true
            }
            "skin" -> {
                val opts = listOf("thor", "midnight", "ember", "mono")
                val pick = args.lowercase()
                if (pick in opts) {
                    rt.settings.setSkin(pick)
                    _toast.tryEmit("skin: $pick — langsung terlihat")
                } else {
                    insertAssistantNotice(
                        "**Skin aktif**: ${rt.settings.skin.first()}\n\nPilihan: " +
                            opts.joinToString(" · ") + "\n\n(thor = emas klasik, midnight = biru malam, ember = merah bara, mono = abu netral)"
                    )
                }
                true
            }
            "personality" -> {
                val presets = com.thoragent.app.core.agent.SystemPromptBuilder.PERSONALITIES.keys.toList()
                val pick = args.lowercase()
                if (pick in presets) {
                    rt.settings.setPersonality(pick)
                    cachedPromptKey = null
                    _toast.tryEmit("personality: $pick — berlaku dari giliran berikutnya")
                } else {
                    insertAssistantNotice(
                        "**Personality aktif**: ${rt.settings.personality.first().ifBlank { "default" }}\n\nPilihan: " +
                            presets.joinToString(" · ")
                    )
                }
                true
            }
            "indicator" -> {
                insertAssistantNotice(
                    "Indikator sibuk Thor Mobile: **titik emas melompat** (animasi native Compose). " +
                        "Di TUI desktop ada beberapa gaya; di mobile satu gaya hidup sudah diterapkan."
                )
                true
            }
            "voice" -> {
                val cur = rt.settings.voice.first()
                val next = when (args.lowercase()) {
                    "on" -> true
                    "off" -> false
                    else -> !cur
                }
                rt.settings.setVoice(next)
                _toast.tryEmit(if (next) "mode suara AKTIF — balasan dibacakan (TTS perangkat)" else "mode suara nonaktif")
                true
            }
            "fast" -> {
                when (args.lowercase()) {
                    "on", "fast", "ultrafast" -> {
                        rt.settings.setReasoningLevel("low")
                        _toast.tryEmit("mode cepat: reasoning low — jawaban lebih singkat & responsif")
                    }
                    "off", "normal" -> {
                        rt.settings.setReasoningLevel("")
                        _toast.tryEmit("mode cepat dimatikan — reasoning kembali bawaan")
                    }
                    else -> insertAssistantNotice(
                        "**Mode cepat**: ${if (rt.settings.reasoningLevel.first() == "low") "AKTIF (reasoning low)" else "nonaktif"}\n\n" +
                            "Di mobile, prioritas pemrosesan server tidak berlaku — padanannya: reasoning rendah + jawaban ringkas. `/fast on|off`."
                    )
                }
                true
            }
            "palette" -> { _uiAction.tryEmit(ChatUiAction.OpenHelp); true }

            // ==================== Info ====================
            "status" -> { insertAssistantNotice(statusReport()); true }
            "usage" -> {
                val u = _usage.value
                insertAssistantNotice(
                    "**Pemakaian token sesi**\n\n" +
                        "- Prompt: ${u.promptTokens}\n- Completion: ${u.completionTokens}\n" +
                        "- Total: **${u.totalTokens}**\n- Panggilan LLM: ${u.llmCalls}\n- Panggilan tool: ${u.toolCalls}"
                )
                true
            }
            "context" -> {
                val list = messages.value
                val chars = list.sumOf { it.content.length }
                val estTokens = chars / 4
                val textTurns = list.count { it.kind == "text" }
                insertAssistantNotice(
                    "**Jendela context (perkiraan)**\n\n" +
                        "- Baris riwayat: ${list.size} ($textTurns teks + ${list.size - textTurns} tool)\n" +
                        "- Estimasi token: ~$estTokens\n" +
                        "- Dikirim ke LLM: ${minOf(24, textTurns)} giliran teks terakhir\n\n" +
                        "Gunakan `/compress` untuk memadatkan bila penuh."
                )
                true
            }
            "version" -> {
                insertAssistantNotice(
                    "**Thor Agent Mobile** v${com.thoragent.app.BuildConfig.VERSION_NAME}\n\n" +
                        "Kecerdasan/skill/katalog: repo sumber ${com.thoragent.app.data.prefs.SettingsRepository.REPO_URL}"
                )
                true
            }
            "profile" -> {
                val p = rt.providers.current()
                insertAssistantNotice(
                    if (p == null) "Belum ada profil provider — atur di Pengaturan Provider."
                    else "**Profil aktif**: ${p.name}\n\n- Base URL: `${p.baseUrl}`\n- Model: ${p.model}\n- Sesi ini: ${sessionModel.value.ifBlank { "(mengikuti profil)" }}"
                )
                true
            }
            "whoami" -> { insertAssistantNotice("Akses: **admin** (pemilik perangkat — semua 105 perintah tersedia)"); true }
            "help", "commands" -> {
                if (args.isBlank()) { _uiAction.tryEmit(ChatUiAction.OpenHelp); return true }
                val found = rt.slash.search(args).take(12)
                if (found.isEmpty()) { _toast.tryEmit("tidak ada perintah cocok: $args"); return true }
                insertAssistantNotice(
                    "**Perintah cocok \"$args\"**\n\n" + found.joinToString("\n") {
                        "- `/${it.name}` — ${it.description}"
                    }
                )
                true
            }
            "insights" -> {
                val sess = rt.db.messageDao().countSessions()
                val msgs = rt.db.messageDao().countMessages()
                val tools = rt.db.messageDao().topTools()
                insertAssistantNotice(
                    buildString {
                        append("**Insight pemakaian**\n\n")
                        append("- Total sesi: $sess\n")
                        append("- Total pesan: $msgs\n")
                        append("- Token sesi ini: ${_usage.value.totalTokens}\n")
                        if (tools.isNotEmpty()) {
                            append("\n**Tool paling sering dipakai**\n\n")
                            tools.forEach { append("- `${it.toolName ?: "?"}` — ${it.n}x\n") }
                        }
                        append("\nTip: `/compress` merapikan context panjang, `/save` mengarsipkan sesi penting.")
                    }
                )
                true
            }
            "debug" -> {
                val f = writeDebugReport()
                if (f == null) _toast.tryEmit("gagal menulis laporan debug")
                else {
                    _uiAction.tryEmit(ChatUiAction.ShareFile(f.absolutePath, "text/markdown"))
                    insertAssistantNotice("**Laporan debug** tertulis: `${f.absolutePath}`\n\nTanpa API key — aman dibagikan.")
                }
                true
            }
            "update" -> {
                insertAssistantNotice(
                    "**Versi terpasang**: v${com.thoragent.app.BuildConfig.VERSION_NAME}\n\n" +
                        "Pembaruan mobile dirilis lewat GitHub — tombol/rowser terbuka di halaman rilis repo."
                )
                _uiAction.tryEmit(ChatUiAction.OpenUrl("https://github.com/dwisetyabudi15581/Thor-agent/releases"))
                true
            }
            "image" -> { _uiAction.tryEmit(ChatUiAction.PickImage); true }
            "platforms", "platform" -> {
                insertAssistantNotice(
                    "**Platform gateway** (Telegram/Discord/Slack) berjalan di gateway desktop — " +
                        "mobile adalah konektor langsung tanpa perantara: sesi ini 100% on-device.\n\n" +
                        "Padanan terdekat: `/handoff` (bagikan profil) atau `/branch` (cabangkan sesi)."
                )
                true
            }
            "subscription", "topup", "login" -> {
                insertAssistantNotice(
                    "Akun/paket Thor dikelola lewat portal web gateway desktop. **Mobile memakai API key " +
                        "provider Anda langsung** (Pengaturan Provider) — tanpa langganan, tanpa saldo.\n\n" +
                        "Portal repo terbuka di browser."
                )
                _uiAction.tryEmit(ChatUiAction.OpenUrl("https://github.com/dwisetyabudi15581/Thor-agent"))
                true
            }
            "quit" -> { _uiAction.tryEmit(ChatUiAction.QuitApp); true }
            "restart" -> { _uiAction.tryEmit(ChatUiAction.RestartApp); true }
            "redraw" -> {
                _toast.tryEmit("UI Compose menyegarkan diri otomatis — paksa mulai ulang via /restart bila aneh")
                true
            }

            // ==================== Tool & skill ====================
            "skills" -> { _uiAction.tryEmit(ChatUiAction.OpenSkillHub); true }
            "tools", "toolsets" -> { _uiAction.tryEmit(ChatUiAction.OpenTools); true }
            "goal" -> {
                when {
                    args == "show" || args.isBlank() -> {
                        val g = rt.settings.goal.first()
                        if (g.isBlank()) insertAssistantNotice("**Goal**: belum ada.\n\nSetel dengan `/goal <tujuan>` — Thor bekerja mencapainya lintas giliran.")
                        else insertAssistantNotice("**Goal aktif**:\n\n$g")
                    }
                    args == "clear" || args == "off" -> { rt.settings.setGoal(""); cachedPromptKey = null; _toast.tryEmit("goal dihapus") }
                    args.startsWith("draft ") -> { _prefill.value = "/goal ${args.removePrefix("draft ")}"; _toast.tryEmit("draf goal di kotak ketik — kirim untuk menyetujui") }
                    else -> { rt.settings.setGoal(args); cachedPromptKey = null; _toast.tryEmit("goal disetel: ${args.take(60)}") }
                }
                true
            }
            "subgoal" -> {
                val cur = rt.settings.subgoals.first()
                when {
                    args.isBlank() || args == "show" -> {
                        if (cur.isBlank()) insertAssistantNotice("**Subgoal**: belum ada.\n\nTambahkan dengan `/subgoal <kriteria>` — dipaketkan ke goal aktif.")
                        else insertAssistantNotice("**Subgoal aktif**:\n\n" + cur.lines().filter { it.isNotBlank() }.joinToString("\n") { "- $it" } + "\n\n`/subgoal rm <nomor>` hapus, `/subgoal clear` semua.")
                    }
                    args == "clear" -> { rt.settings.setSubgoals(""); cachedPromptKey = null; _toast.tryEmit("subgoal dikosongkan") }
                    args.startsWith("rm ") -> {
                        val idx = args.removePrefix("rm ").trim().toIntOrNull()
                        val lines = cur.lines().filter { it.isNotBlank() }.toMutableList()
                        if (idx == null || idx !in 1..lines.size) _toast.tryEmit("nomor tidak valid — lihat /subgoal show")
                        else {
                            lines.removeAt(idx - 1)
                            rt.settings.setSubgoals(lines.joinToString("\n"))
                            cachedPromptKey = null
                            _toast.tryEmit("subgoal #$idx dihapus")
                        }
                    }
                    else -> {
                        val lines = cur.lines().filter { it.isNotBlank() } + args
                        rt.settings.setSubgoals(lines.joinToString("\n"))
                        cachedPromptKey = null
                        _toast.tryEmit("subgoal ditambahkan (${lines.size} total)")
                    }
                }
                true
            }
            "journey" -> {
                val dir = rt.skills.userSkillsDir
                val learned = dir.listFiles()?.filter { it.isDirectory }
                    ?.mapNotNull { d -> File(d, "SKILL.md").takeIf { it.exists() }?.let { d.name to it.lastModified() } }
                    ?.sortedBy { it.second } ?: emptyList()
                val idx = rt.skills.index()
                insertAssistantNotice(
                    if (learned.isEmpty()) "**Learning journey** masih kosong.\n\nMulai dengan `/learn <hal>` — skill yang dipelajari tercatat di sini."
                    else buildString {
                        append("**Learning journey** (${learned.size} skill dipelajari)\n\n")
                        java.text.SimpleDateFormat("dd MMM yy", Locale("id")).let { fmt ->
                            learned.forEach { (n, t) -> append("- ${fmt.format(java.util.Date(t))} — **$n**\n") }
                        }
                        append("\nDitambah ${idx.count - learned.size} skill bawaan repo.")
                    }
                )
                true
            }
            "reload", "reload-skills" -> {
                rt.skills.invalidateCache()
                cachedPromptKey = null
                val n = rt.skills.index().count
                _toast.tryEmit("skill dimuat ulang — $n skill terindeks")
                true
            }
            "reload-mcp" -> {
                rt.skills.invalidateCache()
                _toast.tryEmit("MCP adalah ekstensi desktop — padanan mobile: skill dimuat ulang")
                true
            }
            "memory" -> {
                val memDir = File(cwd.value, ".thor/memory")
                val files = memDir.listFiles()?.filter { it.isFile }?.sortedBy { it.lastModified() } ?: emptyList()
                insertAssistantNotice(
                    if (files.isEmpty()) "**Memory** belum ada.\n\nAgent menulis pelajaran ke `.thor/memory/` di CWD — " +
                        "picu dengan `/refine` setelah menyelesaikan tugas besar."
                    else buildString {
                        append("**Memory tersimpan** (${files.size} berkas di `.thor/memory/`)\n\n")
                        files.take(10).forEach { append("- `${it.name}` (${it.length()} B)\n") }
                    }
                )
                true
            }
            "pet" -> handlePet(args)
            "hatch" -> {
                if (args.isBlank()) { _prefill.value = "/hatch "; _toast.tryEmit("deskripsikan pet impiannya: /hatch <deskripsi>") }
                else {
                    val nm = args.split(" ").first().take(12).lowercase().replace("[^a-z0-9]".toRegex(), "").ifBlank { "pet" }
                    val sp = PET_SPECIES[(nm.hashCode().let { if (it < 0) -it else it }) % PET_SPECIES.size]
                    rt.settings.setPet("$nm#$sp#m")
                    insertAssistantNotice("**Pet baru menetas!**\n\n_${nm}_ si $sp bergabung — sapa ia di layar kosong Chat. `/pet off` bila ingin tidur.")
                }
                true
            }
            "cron" -> handleCron(args)

            // ==================== AGENT-TIER (dibungkus instruksi ala /plan /learn) ====================
            "plan" -> {
                if (args.isBlank()) { _prefill.value = "/plan "; _toast.tryEmit("tulis tugasnya: /plan <tugas>"); return true }
                sendToAgent(
                    "[MODE PLAN — meniru /plan repo Thor-agent] Tulis rencana implementasi markdown yang rinci untuk tugas berikut, " +
                        "lalu SIMPAN ke berkas \"${cwd.value.trimEnd('/')}/.thor/plans/plan-${System.currentTimeMillis()}.md\" dengan tool write_file. " +
                        "JANGAN mengeksekusi tugasnya — hanya rencana (tujuan, langkah-langkah, berkas yang disentuh, risiko).\n\n$args",
                    display = "/plan $args"
                )
                true
            }
            "learn" -> {
                if (args.isBlank()) { _prefill.value = "/learn "; _toast.tryEmit("tentukan sumbernya: /learn <hal yang dipelajari>"); return true }
                val skillsDir = rt.skills.userSkillsDir.absolutePath
                sendToAgent(
                    "[MODE LEARN — loop pembelajaran tertutup repo Thor-agent] Pelajari: $args\n\n" +
                        "Baca sumber yang relevan (berkas lokal via read_file/search_files, web via terminal+curl bila tersedia), lalu TULIS skill baru yang bisa dipakai ulang " +
                        "ke \"$skillsDir/<nama-skill>/SKILL.md\" dengan tool write_file. Format SKILL.md: baris pertama `# <Judul>`, " +
                        "satu paragraf deskripsi kapan skill dipakai, lalu `## Langkah` berisi prosedur langkah-demi-langkah yang konkret. " +
                        "Nama skill singkat-kecil (kebab-case). Setelah menulis, sebutkan nama skill yang dibuat.",
                    display = "/learn $args",
                    after = {
                        rt.skills.invalidateCache()
                        _toast.tryEmit("skill baru diindeks — lihat di Skill Hub")
                    }
                )
                true
            }
            "worktree" -> { agentCommand(name, args) {
                "[MODE WORKTREE] Kelola git worktree terisolasi di CWD `${cwd.value}`.\n\n" +
                    "Args: $args\n- list: `git worktree list` via shell\n- new: `git worktree add <path> [branch]`\n- prune: `git worktree prune`\n" +
                    "Laporkan hasil perintah apa adanya."
            }; true }
            "diff" -> { agentCommand(name, args) {
                "[MODE DIFF] Tampilkan perubahan git di working directory `${cwd.value}`.\n\n" +
                    "Args: ${args.ifBlank { "(working tree)" }}\n" +
                    "staged → `git diff --staged`; all/session → `git status --porcelain` + `git diff HEAD`; default → `git diff`.\n" +
                    "Jalankan via shell lalu RINGKAS: berkas berubah, tambahan/hapus, temuan penting."
            }; true }
            "init" -> { agentCommand(name, args) {
                "[MODE INIT — meniru /init repo] Scan proyek di `${cwd.value}` (search_files target=files + read_file: README, struktur direktori, " +
                    "berkas konfigurasi/dependensi) lalu TULIS `AGENTS.md` berisi instruksi proyek untuk agent: " +
                    "overview, cara build/jalankan, struktur penting, konvensi, hal yang harus dihindari. Args tambahan: $args"
            }; true }
            "refine" -> { agentCommand(name, args) {
                "[MODE REFINE] Tinjau percakapan dan pekerjaan yang baru dilakukan" +
                    "${if (args.isNotBlank()) " (fokus: $args)" else ""}. " +
                    "Ekstrak pelajaran yang bisa dipakai ulang, lalu TULIS/tambahkan ke " +
                    "\"${cwd.value.trimEnd('/')}/.thor/memory/lessons.md\" via write_file (tanggal + pelajaran + konteks singkat). " +
                    "Sebutkan 3 pelajaran utama di balasan."
            }; true }
            "review" -> { agentCommand(name, args) {
                "[MODE REVIEW — subagent independen] Periksa hasil kerja terbaru di `${cwd.value}` " +
                    "(git diff bila ada repo; kalau tidak, baca berkas yang terakhir diedit). " +
                    "Tulis review kritis: bug, risiko, pelanggaran konvensi, saran perbaikan — " +
                    "SIMPAN ke \"${cwd.value.trimEnd('/')}/.thor/reviews/review-${System.currentTimeMillis()}.md\" " +
                    "lalu ringkas 3 temuan terpenting.${if (args.isNotBlank()) "\n\nFokus review: $args" else ""}"
            }; true }
            "moa" -> { agentCommand(name, args) {
                "[MODE MoA — Mixture of Agents] Untuk permintaan berikut, susun TIGA draf jawaban independen " +
                    "dari sudut pandang berbeda (mis. praktis, kritis, kreatif), lalu SINTESISKAN satu jawaban final " +
                    "terbaik yang menggabungkan kekuatan ketiganya. Tandai bagian sintesis dengan heading **Sintesis**.\n\n$args"
            }; true }
            "kanban" -> { agentCommand(name, args) {
                "[MODE KANBAN — papan berbasis berkas] Kelola papan di \"${cwd.value.trimEnd('/')}/.thor/kanban/board.md\" " +
                    "(kolom: Backlog | Doing | Review | Done; format `- [ ] kartu`).\n\nArgs: ${args.ifBlank { "show" }}\n" +
                    "show/list → baca & tampilkan papan; create/add → tambah kartu; complete → pindah ke Done; " +
                    "assign/comment → catat di kartu. Gunakan read_file/write_file/patch. Balas dengan isi papan setelah diubah."
            }; true }
            "suggestions" -> { agentCommand(name, args) {
                "[MODE SUGGESTIONS] Analisis riwayat percakapan dan isi `${cwd.value}`; usulkan 3 otomasi/skill " +
                    "yang paling menghemat waktu pengguna. TULIS ke \"${cwd.value.trimEnd('/')}/.thor/suggestions.json\" " +
                    "(array JSON: [{\"title\",\"why\",\"how\"}]) via write_file, lalu tampilkan ketiganya." +
                    (if (args.isNotBlank()) "\n\nKonteks tambahan: $args" else "")
            }; true }
            "blueprint" -> { agentCommand(name, args) {
                "[MODE BLUEPRINT] Siapkan automation dari template untuk kebutuhan: $args\n\n" +
                    "Rancang langkah otomatis (trigger, aksi, tool yang dipakai, jadwal) lalu TULIS definisinya ke " +
                    "\"${cwd.value.trimEnd('/')}/.thor/automations/\" (satu berkas .md per automation, nama kebab-case). " +
                    "Tampilkan ringkasannya."
            }; true }
            "curator" -> { agentCommand(name, args) {
                "[MODE CURATOR — perawatan skill background] Periksa pustaka skill (skills_list) di CWD `${cwd.value}`.\n\n" +
                    "Args: ${args.ifBlank { "status" }}\n" +
                    "status → ringkas kesehatan skill (jumlah, kategori, yang mirip/duplikat); run → tulis laporan " +
                    "ke \"${cwd.value.trimEnd('/')}/.thor/curator-report.md\" berisi usulan merge/hapus/urutan belajar."
            }; true }
            "rollback" -> { agentCommand(name, args) {
                "[MODE ROLLBACK — checkpoint filesystem] Bila `${cwd.value}` repo git: tampilkan daftar checkpoint " +
                    "(`git stash list`, `git log --oneline -10`). Args: ${args.ifBlank { "(pilihan aman)" }}\n" +
                    "Bila pengguna menyebut nomor/commit, JELASKAN dulu apa yang dipulihkan, minta konfirmasi singkat, " +
                    "baru eksekusi (`git stash apply` / `git checkout <ref> -- .`). Bila bukan repo: laporkan apa adanya."
            }; true }

            // ==================== INFO-TIER (padanan jujur) ====================
            "egress" -> {
                insertAssistantNotice("**Egress**: mobile terhubung LANGSUNG ke provider LLM — tidak ada proxy Docker antara (fitur gateway desktop). Koneksi: penuh, tanpa perantara.")
                true
            }
            "codex-runtime" -> {
                insertAssistantNotice("**Codex runtime** (app-server OpenAI) hanya untuk desktop. Mobile memakai HTTP API OpenAI-compatible langsung — semua model berjalan lewat jalur yang sama.")
                true
            }
            "browser" -> {
                insertAssistantNotice("**Koneksi CDP Chromium** tidak tersedia di Android.\n\nPadanan: agent memakai `terminal` (curl/wget bila ada di perangkat) untuk mengambil halaman web — minta saja: \"baca https://…\".")
                true
            }
            "wake" -> {
                insertAssistantNotice("**Wake word \"Hey Thor\"** butuh mikrofon always-on khusus perangkat — belum bisa di Android.\n\nCara tercehat: buka aplikasi & ketik. (Mode suara balasan tersedia: `/voice on`.)")
                true
            }
            "plugins" -> {
                val n = rt.skills.index().count
                insertAssistantNotice("**Plugin** adalah ekstensi gateway desktop. Di mobile, ekstensi = **skill** ($n terindeks) — kelola lewat `/skills`.")
                true
            }
            "bundles" -> {
                insertAssistantNotice("**Skill bundle** (alias multi-skill) dikelola gateway desktop. Di mobile seluruh skill reposi sudah terpasang — panggil `/skills` untuk menjelajah.")
                true
            }
            "footer" -> {
                insertAssistantNotice(
                    "**Footer metadata runtime** adalah fitur TUI desktop (info runtime di bawah balasan).\n\n" +
                        "Padanan mobile: **status bar mini** di atas composer — aktifkan dengan `/statusbar on`, " +
                        "lengkap dengan token, antrean, dan baterai (`/battery on`)."
                )
                true
            }
            "autoprune" -> {
                insertAssistantNotice(
                    "**Auto-prune sesi lama** berjalan di gateway desktop.\n\n" +
                        "Padanan mobile: hapus sesi manual lewat **Riwayat Sesi** (ikon jam), atau " +
                        "`/compress` untuk merampingkan context sesi aktif."
                )
                true
            }

            // ==================== Lainnya: bungkus untuk agent ====================
            else -> {
                // Semua perintah registry kini didukung; yang tidak punya handler
                // khusus di atas diteruskan ke agent sebagai instruksi (tier AGENT).
                agentCommand(name, args) {
                    "[PERINTAH /$name — dari COMMAND_REGISTRY repo Thor-agent]\n" +
                        "Deskripsi: ${parsed.command.description}\n" +
                        (if (parsed.command.subcommands.isNotEmpty()) "Subcommand: ${parsed.command.subcommands.joinToString(", ")}\n" else "") +
                        (if (args.isNotBlank()) "Args: $args\n" else "") +
                        "Kerjakan padanan terbaiknya di perangkat ini dengan tool yang tersedia, lalu laporkan hasilnya."
                }
                true
            }
        }
    }

    // ===================== HELPER SLASH =====================

    private suspend fun handleQueue(args: String): Boolean {
        val parts = args.split(" ", limit = 2)
        val sub = parts.firstOrNull()?.lowercase() ?: "list"
        val rest = parts.getOrNull(1)?.trim().orEmpty()
        when (sub) {
            "list", "" -> insertAssistantNotice(
                if (_queue.value.isEmpty()) "**Antrean kosong** — pesan yang dikirim saat agent bekerja otomatis mengantre di sini."
                else "**Antrean pesan** (${_queue.value.size})\n\n" +
                    _queue.value.mapIndexed { i, s -> "${i + 1}. ${s.take(80)}" }.joinToString("\n") +
                    "\n\n`/queue rm <nomor>` hapus · `/queue clear` semua"
            )
            "add" -> if (rest.isBlank()) { _prefill.value = "/queue add "; _toast.tryEmit("pesan apa yang diantrekan?") } else enqueue(rest)
            "rm", "remove" -> {
                val idx = rest.toIntOrNull()
                if (idx == null || idx !in 1.._queue.value.size) _toast.tryEmit("nomor tidak valid — lihat /queue list")
                else { removeFromQueue(idx - 1); _toast.tryEmit("antrean #$idx dihapus") }
            }
            "clear" -> clearQueue()
            "edit" -> insertAssistantNotice("Edit antrean di mobile: `/queue rm <nomor>` lalu `/queue add <teks baru>`.")
            "move" -> {
                val nums = rest.split(" ").mapNotNull { it.toIntOrNull() }
                if (nums.size == 2) {
                    val (from, to) = nums[0] - 1 to nums[1] - 1
                    val q = _queue.value.toMutableList()
                    if (from in q.indices && to in q.indices) {
                        q.add(to, q.removeAt(from))
                        _queue.value = q
                        _toast.tryEmit("antrean dipindah")
                    } else _toast.tryEmit("nomor di luar antrean")
                } else _toast.tryEmit("format: /queue move <dari> <ke>")
            }
        }
        return true
    }

    private suspend fun handleHeartbeat(args: String): Boolean {
        val parts = args.split(" ", limit = 2)
        when (parts.firstOrNull()?.lowercase()) {
            null, "", "status" -> insertAssistantNotice(
                "**Heartbeat**: ${if (repeatJob?.isActive == true && repeatKind == "heartbeat") "AKTIF tiap ${repeatIntervalMs / 60000} menit — _${repeatPrompt.take(60)}_" else "nonaktif"}\n\n" +
                    "Setel: `/heartbeat <menit> <prompt>` — prompt dikirim ulang saat idle. `/heartbeat clear` menghentikan."
            )
            "pause" -> { repeatJob?.cancel(); _toast.tryEmit("heartbeat dijeda — /heartbeat resume") }
            "resume" -> { if (repeatPrompt.isNotBlank()) startRepeat("heartbeat", repeatIntervalMs, repeatPrompt); _toast.tryEmit("heartbeat lanjut") }
            "clear", "off" -> { stopRepeat(); _toast.tryEmit("heartbeat dihentikan") }
            else -> {
                val min = parts.first().toLongOrNull()
                val prompt = parts.getOrNull(1)?.trim().orEmpty()
                if (min == null || min < 1 || prompt.isBlank()) {
                    _toast.tryEmit("format: /heartbeat <menit> <prompt>")
                } else {
                    startRepeat("heartbeat", min * 60_000, prompt)
                    _toast.tryEmit("heartbeat aktif tiap $min menit")
                }
            }
        }
        return true
    }

    private suspend fun handleLoop(args: String): Boolean {
        val parts = args.split(" ", limit = 2)
        when (parts.firstOrNull()?.lowercase()) {
            null, "", "status" -> insertAssistantNotice(
                "**Loop**: ${if (repeatJob?.isActive == true && repeatKind == "loop") "AKTIF tiap ${repeatIntervalMs / 60000} menit" else "nonaktif"}\n\n" +
                    "Setel: `/loop <menit> [prompt]` — tanpa prompt, prompt terakhir diulang. `/loop clear` berhenti."
            )
            "clear", "off", "stop" -> { stopRepeat(); _toast.tryEmit("loop dihentikan") }
            else -> {
                val min = parts.first().toLongOrNull()
                if (min == null || min < 1) {
                    _toast.tryEmit("format: /loop <menit> [prompt]")
                    return true
                }
                val prompt = parts.getOrNull(1)?.trim()
                    ?: messages.value.lastOrNull { it.role == "user" && it.kind == "text" }?.content.orEmpty()
                if (prompt.isBlank()) { _toast.tryEmit("tidak ada prompt untuk diulang — tulis: /loop <menit> <prompt>"); return true }
                startRepeat("loop", min * 60_000, prompt)
                _toast.tryEmit("loop aktif tiap $min menit")
            }
        }
        return true
    }

    private suspend fun handleCron(args: String): Boolean {
        val parts = args.split(" ", limit = 2)
        when (parts.firstOrNull()?.lowercase()) {
            null, "", "list" -> {
                val list = loadCron()
                insertAssistantNotice(
                    if (list.isEmpty()) "**Cron** kosong.\n\nTambah: `/cron add <menit> <prompt>` — berjalan selama aplikasi terbuka."
                    else "**Cron terjadwal**\n\n" + list.joinToString("\n") {
                        "- #${it.id} tiap ${it.everyMin} mnt: ${it.prompt.take(60)}"
                    } + "\n\n`/cron rm <id>` hapus · `/cron run <id>` jalankan sekarang"
                )
            }
            "add", "create" -> {
                val rest = parts.getOrNull(1)?.trim().orEmpty()
                val sub = rest.split(" ", limit = 2)
                val min = sub.firstOrNull()?.toLongOrNull()
                val prompt = sub.getOrNull(1)?.trim().orEmpty()
                if (min == null || min < 1 || prompt.isBlank()) {
                    _toast.tryEmit("format: /cron add <menit> <prompt>")
                } else {
                    val list = loadCron()
                    val id = (list.maxOfOrNull { it.id } ?: 0) + 1
                    list.add(CronEntry(id, prompt, min))
                    saveCron(list)
                    _toast.tryEmit("cron #$id ditambahkan (tiap $min mnt)")
                }
            }
            "rm", "remove" -> {
                val id = parts.getOrNull(1)?.trim()?.toIntOrNull()
                val list = loadCron()
                if (id == null || list.none { it.id == id }) _toast.tryEmit("id tidak ditemukan — /cron list")
                else { saveCron(list.filter { it.id != id }); _toast.tryEmit("cron #$id dihapus") }
            }
            "run" -> {
                val id = parts.getOrNull(1)?.trim()?.toIntOrNull()
                val e = loadCron().firstOrNull { it.id == id }
                if (e == null) _toast.tryEmit("id tidak ditemukan — /cron list")
                else send(e.prompt)
            }
            else -> _toast.tryEmit("subcommand: list | add | rm | run")
        }
        return true
    }

    private suspend fun handlePet(args: String): Boolean {
        val parts = args.split(" ", limit = 2)
        when (parts.firstOrNull()?.lowercase()) {
            "off" -> { rt.settings.setPet(""); _toast.tryEmit("pet kembali tidur") }
            "list" -> insertAssistantNotice("**Spesies petdex**: " + PET_SPECIES.joinToString(" · ") + "\n\nAdopsi: `/hatch <deskripsi>`")
            "scale" -> {
                val cur = rt.settings.pet.first()
                if (cur.isBlank()) _toast.tryEmit("belum ada pet — /hatch dulu")
                else {
                    val seg = cur.split("#")
                    val size = seg.getOrNull(2) ?: "m"
                    val next = when (size) { "s" -> "m"; "m" -> "l"; else -> "s" }
                    rt.settings.setPet("${seg[0]}#${seg.getOrNull(1) ?: "petdex"}#$next")
                    _toast.tryEmit("ukuran pet: $next")
                }
            }
            "toggle", "" -> {
                val cur = rt.settings.pet.first()
                if (cur.isBlank()) {
                    val sp = PET_SPECIES.random()
                    rt.settings.setPet("thor#$sp#m")
                    insertAssistantNotice("**Pet diadopsi!** thor si $sp menemani di layar kosong Chat. `/pet scale` ubah ukuran, `/pet off` tidurkan.")
                } else { rt.settings.setPet(""); _toast.tryEmit("pet tidur — /pet untuk bangunkan lagi") }
            }
            else -> _toast.tryEmit("subcommand: toggle | list | scale | off")
        }
        return true
    }

    private suspend fun handleBoolSetting(
        args: String,
        flow: kotlinx.coroutines.flow.Flow<Boolean>,
        setter: suspend (Boolean) -> Unit,
        label: String
    ): Boolean {
        val cur = flow.first()
        val next = when (args.lowercase()) {
            "on", "1", "true" -> true
            "off", "0", "false" -> false
            else -> !cur
        }
        setter(next)
        _toast.tryEmit("$label: ${if (next) "AKTIF" else "NONAKTIF"}")
        return true
    }

    /** Perintah agent-tier: butuh argumen (prefill bila kosong), lalu bungkus jadi instruksi. */
    private fun agentCommand(name: String, args: String, instruction: (String) -> String) {
        if (args.isBlank()) {
            _prefill.value = "/$name "
            _toast.tryEmit("tentukan argumennya: /$name <…>")
            return
        }
        sendToAgent(instruction(args), display = "/$name $args")
    }

    private suspend fun setGlobalModelNow(model: String) {
        val p = rt.providers.current()
        if (p == null) _toast.tryEmit("belum ada profil provider")
        else {
            rt.providers.setModel(p.id, model)
            rt.settings.setSessionModel("")
            _toast.tryEmit("model bawaan provider: $model")
        }
    }

    private suspend fun statusReport(): String {
        val p = rt.providers.current()
        val s = sessions.value.firstOrNull { it.id == _sessionId.value }
        val u = _usage.value
        val rootSt = runCatching { rt.root.status() }.getOrNull()
        return buildString {
            append("**Status sesi**\n\n")
            append("- Judul: ${s?.title ?: "-"}\n")
            append("- Provider: ${p?.name ?: "belum diatur"}\n")
            append(
                "- Model: " + (
                    if (sessionModel.value.isNotBlank()) "${sessionModel.value} (sesi)"
                    else p?.model ?: "-"
                ) + "\n"
            )
            if (_effectiveModel.value.isNotBlank() && _autoModelNote.value.isNotBlank()) {
                append("  - efektif: `${_effectiveModel.value}` ($_autoModelNote)\n")
            }
            append("- Token sesi: ${u.totalTokens} (p ${u.promptTokens} + c ${u.completionTokens})\n")
            append("- Tool calls: ${u.toolCalls}\n")
            append("- Antrean: ${_queue.value.size} · background: ${_bgJobs.value.size}\n")
            append("- CWD: `${cwd.value}`\n")
            append("- Root: ${if (rootSt?.appGrantedRoot == true) "tersedia" else "tidak"}\n")
            val g = rt.settings.goal.first()
            if (g.isNotBlank()) append("- Goal aktif: ${g.take(80)}\n")
            val per = rt.settings.personality.first()
            if (per.isNotBlank()) append("- Personality: $per\n")
        }
    }

    /** Laporan debug — tanpa kredensial. */
    private suspend fun writeDebugReport(): File? = withContext(Dispatchers.IO) {
        runCatching {
            val dir = File(
                android.os.Environment.getExternalStoragePublicDirectory(
                    android.os.Environment.DIRECTORY_DOWNLOADS
                ), "thor-sesi"
            ).apply { mkdirs() }
            val f = File(dir, "thor-debug-${System.currentTimeMillis()}.md")
            val p = rt.providers.current()
            f.writeText(
                buildString {
                    appendLine("# Thor Agent Mobile — laporan debug")
                    appendLine()
                    appendLine("- Versi: ${com.thoragent.app.BuildConfig.VERSION_NAME} (${com.thoragent.app.BuildConfig.VERSION_CODE})")
                    appendLine("- Android: ${android.os.Build.VERSION.RELEASE} (SDK ${android.os.Build.VERSION.SDK_INT})")
                    appendLine("- Perangkat: ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}")
                    appendLine("- Provider: ${p?.name ?: "-"} @ ${p?.baseUrl ?: "-"} (API key TIDAK disertakan)")
                    appendLine("- Model profil: ${p?.model ?: "-"} · sesi: ${sessionModel.value.ifBlank { "-" }}")
                    appendLine("- Root: ${runCatching { rt.root.status()?.appGrantedRoot }.getOrDefault(false)}")
                    appendLine("- CWD: ${cwd.value}")
                    appendLine("- Sesi aktif: ${_sessionId.value} · pesan: ${messages.value.size}")
                    appendLine("- Antrean: ${_queue.value.size} · bg: ${_bgJobs.value.size}")
                    appendLine("- Pengaturan: yolo=${rt.settings.yolo.first()} reasoning=${rt.settings.reasoningLevel.first()} voice=${rt.settings.voice.first()} skin=${rt.settings.skin.first()}")
                    appendLine("- Skill terindeks: ${rt.skills.index().count}")
                }
            )
            f
        }.getOrNull()
    }

    /** Sisipkan pesan sistem-notis (hasil slash command) ke percakapan. */
    private suspend fun insertAssistantNotice(markdown: String) {
        val sid = _sessionId.value ?: rt.db.sessionDao().upsert(
            SessionEntity(title = "Sesi baru", mode = "agent", providerId = 0, cwd = cwd.value)
        ).also { _sessionId.value = it }
        rt.db.messageDao().insert(
            MessageEntity(sessionId = sid, role = "assistant", kind = "text", content = markdown)
        )
    }

    companion object {
        val PET_SPECIES = listOf(
            "Rubah Listrik", "Naga Kecil", "Kucing Kilat", "Burung Petir",
            "Kodok Emas", "Serigala Bulan", "Kupu-kupu Neon"
        )

        /** Balasan akhir maksimum yang disimpan (jauh di bawah batas CursorWindow). */
        const val MAX_STORED_TEXT = 200_000

        /** Argumen tool (mis. isi berkas dari write_file) maksimum yang disimpan. */
        const val MAX_STORED_TOOL_ARGS = 24_000
    }

    // ===================== LOOP AGENT =====================

    /**
     * Kirim pesan → loop agen NATIF dengan PEMILIHAN MODEL EKSPLISIT:
     * bila model profil/sesi masih "auto"/kosong DAN daftar hidup provider
     * tidak bisa diambil, request DIBLOK (tidak ditebak default katalog —
     * tebakan model yang salah langsung memotong saldo provider custom).
     * Tidak ada retry otomatis dengan model lain: satu request, satu biaya.
     * Pesan yang datang saat agent sibuk MENGAntre (perilaku queue repo sumber).
     */
    private fun sendToAgent(
        rawText: String,
        display: String? = null,
        after: (suspend () -> Unit)? = null
    ) {
        val trimmed = rawText.trim()
        if (trimmed.isEmpty()) return
        if (busy.value) {
            enqueue(display ?: trimmed)
            return
        }
        agentJob = viewModelScope.launch {
            busy.value = true
            waitingFirstToken.value = true
            val fgWasOff = !rt.settings.foregroundService.first()
            if (fgWasOff) runCatching { AgentForegroundService.start(getApplication()) }
            try {
                val sid = _sessionId.value ?: rt.db.sessionDao().upsert(
                    SessionEntity(title = (display ?: trimmed).take(40), mode = "agent", providerId = 0, cwd = cwd.value)
                ).also { _sessionId.value = it }

                val profile: ProviderProfile? = rt.providers.current()
                if (profile == null) {
                    _toast.tryEmit("Atur provider dulu (Pengaturan Provider) — ikon kunci di layar Chat")
                    return@launch
                }

                // Lampiran gambar aktif (/image) → dicatat di pesan user.
                val att = _attachment.value
                val userVisible = display ?: trimmed
                val effectiveUserText = if (att != null) "[lampiran gambar: $att]\n\n$trimmed" else trimmed
                if (att != null) _attachment.value = null

                // History dibaca SEBELUM pesan user baru disisipkan (hindari duplikasi
                // antara history dan userText yang dikirim terpisah).
                val history = rt.db.messageDao().listSession(sid)
                    .filter { it.kind == "text" && (it.role == "user" || it.role == "assistant") }
                    .takeLast(24)
                    .map { LlmMessage(role = it.role, content = it.content) }

                rt.db.messageDao().insert(
                    MessageEntity(sessionId = sid, role = "user", kind = "text", content = userVisible)
                )

                // --- resolusi model (TANPA default katalog) ---
                val configured = sessionModel.value.ifBlank { profile.model }
                var auto = rt.modelCatalog.resolveAuto(configured, profile.baseUrl, profile.apiKey)
                if (auto.model.isBlank()) {
                    // model belum dipilih & daftar hidup tak tersedia → BLOK,
                    // jangan kirim request tebakan (hemat saldo provider custom).
                    insertAssistantNotice(
                        "**[model belum dipilih]**\n\n${auto.reason}\n\n" +
                            "Buka chip **Model** di atas kolom ketik lalu pilih model dari daftar " +
                            "\"Dari provider Anda\" — atau isi kolom Model di Pengaturan Provider."
                    )
                    return@launch
                }
                _effectiveModel.value = auto.model
                _autoModelNote.value = auto.reason
                if (configured != auto.model) {
                    _toast.tryEmit(auto.reason)
                }

                val systemPrompt = systemPrompt()
                var finalText = StringBuilder()

                suspend fun runOnce(model: String) {
                    finalText = StringBuilder()
                    rt.agent.run(
                        baseUrl = profile.baseUrl,
                        apiKey = profile.apiKey,
                        model = model,
                        systemPrompt = systemPrompt,
                        history = history,
                        userText = effectiveUserText,
                        temperature = profile.temperature,
                        maxTokens = profile.maxTokens,
                        stream = profile.streaming,
                        cwd = cwd.value,
                        reasoningEffort = rt.settings.reasoningLevel.first(),
                        approver = ::approveTool
                    ) { ev ->
                        when (ev) {
                            is AgentEvent.TextDelta -> {
                                waitingFirstToken.value = false
                                finalText.append(ev.text)
                                streamingText.value = finalText.toString()
                            }
                            is AgentEvent.ToolCallStart -> {
                                waitingFirstToken.value = false
                                _usage.value = _usage.value.copy(toolCalls = _usage.value.toolCalls + 1)
                                rt.db.messageDao().insert(
                                    MessageEntity(
                                        sessionId = sid, role = "assistant", kind = "tool_call",
                                        content = "", toolCallId = ev.id, toolName = ev.name,
                                        toolArgs = capStore(prettyJson(ev.argsJson), MAX_STORED_TOOL_ARGS)
                                    )
                                )
                            }
                            is AgentEvent.ToolResult -> {
                                rt.db.messageDao().insert(
                                    MessageEntity(
                                        sessionId = sid, role = "tool", kind = "tool_result",
                                        content = ev.resultJson.take(4000), toolCallId = ev.id, toolName = ev.name
                                    )
                                )
                            }
                            is AgentEvent.Failure -> {
                                // TIDAK ADA retry otomatis dengan model lain:
                                // retry = panggilan berbayar kedua yang memotong saldo.
                                // Cukup laporkan kegagalan; user yang memilih model berikutnya.
                                if (ev.httpCode == 404 || ev.httpCode == 400) {
                                    _autoModelNote.value =
                                        "model \"${auto.model}\" ditolak provider (${ev.httpCode}) — " +
                                            "pilih model lain lewat chip Model (daftar dari endpoint Anda)"
                                }
                                _toast.tryEmit(ev.message)
                            }
                            is AgentEvent.Finished -> {
                                ev.usage?.let { _usage.value = _usage.value.add(it) }
                            }
                            // Peringatan dini budget iterasi (port WARN_RATIO 80%) —
                            // tampil sebagai toast supaya pengguna tahu giliran hampir penuh.
                            is AgentEvent.IterationWarning ->
                                _toast.tryEmit("iterasi ${ev.used}/${ev.max} — sisa ${ev.remaining}, agent hampir menutup giliran")
                        }
                    }
                }

                runOnce(auto.model)

                // persist balasan akhir
                if (finalText.isNotBlank()) {
                    rt.db.messageDao().insert(
                        MessageEntity(
                            sessionId = sid, role = "assistant", kind = "text",
                            content = capStore(finalText.toString(), MAX_STORED_TEXT)
                        )
                    )
                    // mode suara (/voice): bacakan balasan via TTS on-device
                    if (rt.settings.voice.first()) speak(finalText.toString())
                    // judul sesi otomatis dari balasan pertama
                    val s = sessions.value.firstOrNull { it.id == sid }
                    if (s != null && s.title == "Sesi baru") {
                        rt.db.sessionDao().rename(sid, finalText.toString().take(40))
                    }
                }
                after?.invoke()
            } catch (e: Throwable) {
                if (e is kotlinx.coroutines.CancellationException) {
                    _toast.tryEmit("dihentikan pengguna")
                } else {
                    _toast.tryEmit("kesalahan: ${e.message}")
                }
            } finally {
                streamingText.value = ""
                busy.value = false
                waitingFirstToken.value = false
                if (fgWasOff) runCatching { AgentForegroundService.stop(getApplication()) }
                // Antrean (pesan yang datang saat sibuk) diproses berikutnya.
                drainQueue()
            }
        }
    }

    /** System prompt stabil per (cwd, root, skills, goal, personality, subgoal, model). */
    private suspend fun systemPrompt(): String {
        val rootSt = runCatching { rt.root.status() }.getOrNull()
        val goal = rt.settings.goal.first()
        val personality = rt.settings.personality.first()
        val subgoals = rt.settings.subgoals.first()
        val model = rt.settings.sessionModel.first()
        val provider = ""
        val key = "${cwd.value}|${rootSt?.appGrantedRoot ?: false}|$goal|$personality|$subgoals|$model"
        if (cachedPromptKey == key && cachedPrompt != null) return cachedPrompt!!
        cachedPrompt = rt.promptBuilder.build(
            cwd = cwd.value,
            rootAvailable = rootSt?.appGrantedRoot == true,
            skillsEnabled = true,
            goal = goal,
            personality = personality,
            subgoals = subgoals,
            model = model,
            provider = provider,
            sessionId = _sessionId.value?.toString().orEmpty(),
            // Guidance per-tool: baris yang menyebut tool absen dibuang prompt.
            skillNames = rt.tools.toolNames()
        )
        cachedPromptKey = key
        return cachedPrompt!!
    }

    /** Persetujuan interaktif untuk tool berbahaya — dipanggil SEBELUM eksekusi. */
    /**
     * Batasi ukuran teks SEBELUM disimpan ke Room.
     *
     * Room membaca baris lewat CursorWindow (~2 MB); satu baris yang lebih besar
     * membuat pembacaan berikutnya gagal (`Row too big to fit into CursorWindow`)
     * dan — karena flow `messages` — langsung mematikan aplikasi. Balasan akhir
     * hasil debug kode dan argumen `write_file` bisa sebesar itu, jadi keduanya
     * dipotong di sini, bukan setelah crash.
     */
    private fun capStore(text: String, limit: Int): String =
        if (text.length <= limit) text
        else text.take(limit) + "\n… [dipotong ${text.length - limit} karakter saat disimpan]"

    private suspend fun approveTool(id: String, name: String, argsJson: String): Boolean {
        // HARDLINE adalah lantai keras: mode yolo tidak membebaskannya.
        if (rt.tools.hardlineReasonFor(name, argsJson) != null) return false

        val yolo = runCatching { rt.settings.yolo.first() }.getOrDefault(false)
        if (yolo) return true
        val confirm = runCatching { rt.settings.confirmDangerous.first() }.getOrDefault(true)
        if (!confirm) return true
        // Hanya tool terminal yang melewati gerbang pola berbahaya.
        if (name != "terminal") return true
        val command = AgentTools.commandOf(name, argsJson)
        if (command.isBlank()) return true
        val reason = rt.tools.approvalReason(command)
        return suspendCancellableCoroutine { cont ->
            _approval.value = ApprovalRequest(name, command, reason) { r -> cont.resume(r) }
        }
    }

    private fun prettyJson(raw: String): String = runCatching {
        Json.parseToJsonElement(raw).toString()
    }.getOrElse { raw }

}
