package com.thoragent.app.runtime

import android.content.Context
import com.thoragent.app.core.agent.AgentCore
import com.thoragent.app.core.agent.AgentTools
import com.thoragent.app.core.agent.NativeToolRegistry
import com.thoragent.app.core.agent.SkillLibrary
import com.thoragent.app.core.agent.SlashCommandRegistry
import com.thoragent.app.core.agent.SystemPromptBuilder
import com.thoragent.app.core.agent.TodoStore
import com.thoragent.app.core.exec.AndroidShellRunner
import com.thoragent.app.core.llm.LlmClient
import com.thoragent.app.core.memory.MemoryStore
import com.thoragent.app.core.provider.ModelCatalog
import com.thoragent.app.core.provider.ProviderRepository
import com.thoragent.app.core.root.RootManager
import com.thoragent.app.core.terminal.TerminalSessionManager
import com.thoragent.app.data.catalog.CatalogRepository
import com.thoragent.app.data.db.ThorDatabase
import com.thoragent.app.data.prefs.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Runtime tunggal aplikasi (service locator sederhana) — SEMUA NATIF:
 * tidak ada engine Python/bridge eksternal; agent berjalan sebagai loop Kotlin
 * murni (LLM streaming + tool on-device) dengan kecerdasan skill dari repo
 * Thor-agent yang di-bundle sebagai aset.
 *
 * Susunan komponen mengikuti Thor desktop:
 *   MemoryStore (MEMORY.md/USER.md) → SkillLibrary (skills/) →
 *   NativeToolRegistry (tool + approval) → AgentCore (loop + budget) →
 *   SystemPromptBuilder (SOUL.md + guidance + skills index + memory).
 */
class ThorRuntime(context: Context) {

    val appContext: Context = context.applicationContext

    val db: ThorDatabase = ThorDatabase.build(appContext)
    val providers: ProviderRepository = ProviderRepository(db.providerDao())
    val settings: SettingsRepository = SettingsRepository(appContext)
    val catalogs: CatalogRepository = CatalogRepository(appContext)

    val ok: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(300, TimeUnit.SECONDS) // SSE stream panjang
        .writeTimeout(60, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    val root: RootManager = RootManager(appContext)
    val androidRunner: AndroidShellRunner = AndroidShellRunner(root)
    val terminalManager: TerminalSessionManager = TerminalSessionManager(root)
    val skills: SkillLibrary = SkillLibrary(appContext)
    val llm: LlmClient = LlmClient(ok)

    /**
     * Memori persisten — port `tools/memory_tool_store.py`. Direktori
     * `filesDir/memory` (padanan `~/.thor/memories` desktop).
     */
    val memory: MemoryStore = MemoryStore(
        dir = File(appContext.filesDir, "memory"),
        memoryCharLimit = MemoryStore.DEFAULT_MEMORY_CHAR_LIMIT,
        userCharLimit = MemoryStore.DEFAULT_USER_CHAR_LIMIT
    )

    /** Todo list per sesi — port `tools/todo_tool.py`. */
    val todoState: TodoStore.State = TodoStore.State()

    /** v0.3.0 — registry slash command repo sumber (105 perintah, terjemahan id). */
    val slash: SlashCommandRegistry = SlashCommandRegistry(appContext)

    /** v0.3.0 — katalog model repo sumber + pemilihan model otomatis. */
    val modelCatalog: ModelCatalog = ModelCatalog(appContext, ok)

    /** Tool native: root hanya saat dibutuhkan (toggle pengguna di layar Root). */
    val tools: NativeToolRegistry = NativeToolRegistry(
        context = appContext,
        root = root,
        runner = androidRunner,
        skills = skills,
        memory = memory,
        ok = ok,
        useRootWhenNeeded = {
            runCatching { kotlinx.coroutines.runBlocking { settings.useRoot.first() } }.getOrDefault(true)
        }
    ).also { it.todoState = todoState }

    /** Loop agen native — jantung aplikasi. */
    val agent: AgentCore = AgentCore(llm, tools)

    val promptBuilder: SystemPromptBuilder = SystemPromptBuilder(skills, memory)

    init {
        // Muat MEMORY.md / USER.md sekali saat runtime dibuat, lalu bekukan
        // snapshot system prompt (port `MemoryStore.load_from_disk` di Thor
        // desktop yang dipanggil saat agent diinisialisasi). Tanpa ini memori
        // tidak pernah masuk prompt dan tulisan sesi hilang dari konteks.
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            runCatching { memory.loadFromDisk() }
        }
    }

    companion object {
        /** Akses runtime tunggal dari konteks apa pun. */
        fun runtime(context: Context): ThorRuntime =
            com.thoragent.app.ThorApp.runtime(context)
    }
}
