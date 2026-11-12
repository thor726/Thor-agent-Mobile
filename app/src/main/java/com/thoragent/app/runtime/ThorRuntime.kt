package com.thoragent.app.runtime

import android.content.Context
import com.thoragent.app.core.agent.AgentCore
import com.thoragent.app.core.agent.AgentTools
import com.thoragent.app.core.agent.NativeToolRegistry
import com.thoragent.app.core.agent.SkillLibrary
import com.thoragent.app.core.agent.SlashCommandRegistry
import com.thoragent.app.core.agent.SystemPromptBuilder
import com.thoragent.app.core.exec.AndroidShellRunner
import com.thoragent.app.core.llm.LlmClient
import com.thoragent.app.core.provider.ModelCatalog
import com.thoragent.app.core.provider.ProviderRepository
import com.thoragent.app.core.root.RootManager
import com.thoragent.app.core.terminal.TerminalSessionManager
import com.thoragent.app.data.catalog.CatalogRepository
import com.thoragent.app.data.db.ThorDatabase
import com.thoragent.app.data.prefs.SettingsRepository
import kotlinx.coroutines.flow.first
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * Runtime tunggal aplikasi (service locator sederhana) — SEMUA NATIF:
 * tidak ada engine Python/bridge eksternal; agent berjalan sebagai loop Kotlin
 * murni (LLM streaming + tool on-device) dengan kecerdasan skill dari repo
 * Thor-agent yang di-bundle sebagai aset.
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

    /** v0.3.0 — registry slash command repo sumber (105 perintah, terjemahan id). */
    val slash: SlashCommandRegistry = SlashCommandRegistry(appContext)

    /** v0.3.0 — katalog model repo sumber + pemilihan model otomatis. */
    val modelCatalog: ModelCatalog = ModelCatalog(appContext, ok)

    /** Tool native: root hanya saat dibutuhkan (toggle pengguna di layar Root). */
    val tools: AgentTools = NativeToolRegistry(
        context = appContext,
        root = root,
        runner = androidRunner,
        skills = skills,
        ok = ok,
        useRootWhenNeeded = {
            runCatching { settings.useRoot.first() }.getOrDefault(true)
        }
    )

    /** Loop agen native — jantung aplikasi. */
    val agent: AgentCore = AgentCore(llm, tools)

    val promptBuilder: SystemPromptBuilder = SystemPromptBuilder(skills)

    companion object {
        /** Akses runtime tunggal dari konteks apa pun. */
        fun runtime(context: Context): ThorRuntime =
            com.thoragent.app.ThorApp.runtime(context)
    }
}
