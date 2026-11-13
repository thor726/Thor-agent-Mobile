package com.thoragent.app.core.agent

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Registry slash command — PORT PENUH dari repo sumber Thor-agent:
 * thor_cli/commands.py COMMAND_REGISTRY (105 perintah) + terjemahan
 * locales/id.yaml. Sumber: https://github.com/dwisetyabudi15581/Thor-agent.git
 *
 * Semua konsumen (palet interaktif, autocomplete, executor, /help) berbagi
 * registry ini — sama seperti CLI, gateway, Telegram BotCommands, dan Slack
 * di repo sumber semuanya diturunkan dari COMMAND_REGISTRY.
 */
@Serializable
data class SlashCommandDef(
    val name: String,
    val description: String = "",
    val category: String = "",
    val aliases: List<String> = emptyList(),
    val argsHint: String = "",
    val subcommands: List<String> = emptyList(),
    val cliOnly: Boolean = false,
    val gatewayOnly: Boolean = false
) {
    /** Mode argumen di composer — meniru infer_argument_mode() repo sumber. */
    val argumentMode: String
        get() = when {
            subcommands.isEmpty() -> if (argsHint.isBlank()) "none" else "text"
            argsHint.contains("<prompt>") || argsHint.contains("[text") ||
                argsHint.contains("instructions") || argsHint.contains("<what") -> "mixed"
            else -> "options"
        }
}

@Serializable
data class SlashCommandIndex(
    val source: String = "",
    val generated: String = "",
    val count: Int = 0,
    val categories: List<String> = emptyList(),
    val commands: List<SlashCommandDef> = emptyList()
)

/** Hasil parsing satu baris input "/nama argumen…". */
data class ParsedSlash(
    val command: SlashCommandDef,
    val name: String,
    val args: String
)

/**
 * Dukungan slash command di Android (v0.4.0 — SEMUA 105 perintah jalan).
 *
 * Tiga tier — meniru cara gateway repo sumber memetakan perintah CLI ke
 * platform selain terminal:
 *  - NATIVE : dieksekusi penuh oleh aplikasi (UI, DataStore, tool on-device).
 *  - AGENT  : dibungkus jadi instruksi terstruktur untuk agent loop on-device
 *             (pola /plan & /learn) — agent memakai tool-nya untuk menyelesaikan.
 *  - INFO   : perintah platform/hardware spesifik → respons informatif +
 *             aksi terdekat yang benar-benar tersedia di Android.
 */
object MobileCommands {

    enum class SupportTier { NATIVE, AGENT, INFO }

    /** Eksekusi native penuh di aplikasi. */
    val NATIVE = setOf(
        // Sesi (reset & compact = alias new/compress di katalog — dijawab cabang itu)
        "new", "title", "retry", "undo", "rewind", "sessions", "resume",
        "stop", "save", "copy", "paste", "clear", "history", "prompt", "queue",
        "approve", "deny", "pause", "discard", "btw", "steer", "bg", "agents",
        "branch", "start", "topic", "sethome", "snapshot", "export", "import",
        // Konfigurasi
        "model", "yolo", "approvals", "reasoning", "busy", "config",
        "timestamps", "focus", "verbose", "statusbar", "battery", "skin",
        "personality", "indicator", "voice", "fast", "palette",
        // Info
        "status", "usage", "context", "version", "profile", "whoami", "help",
        "commands", "insights", "debug", "update", "image", "crash",
        // Tool & skill
        "skills", "tools", "toolsets", "learn", "plan", "goal", "subgoal",
        "compress", "journey", "reload", "reload-skills", "memory", "pet",
        "hatch", "heartbeat", "loop", "cron",
        // Keluar
        "quit", "restart", "redraw"
    )

    /** Dibungkus jadi instruksi agent (agent menyelesaikan dengan tool on-device). */
    val AGENT = setOf(
        "worktree", "diff", "init", "refine", "review", "moa", "kanban",
        "suggestions", "blueprint", "curator", "rollback"
    )

    /** Platform/hardware desktop — respons informatif + padanan Android. */
    val INFO = setOf(
        "handoff", "egress", "codex-runtime", "browser", "wake", "reload-mcp",
        "plugins", "bundles", "platforms", "platform", "subscription", "login",
        "topup", "footer", "autoprune"
    )

    fun tier(name: String): SupportTier = when {
        name in NATIVE -> SupportTier.NATIVE
        name in AGENT -> SupportTier.AGENT
        name in INFO -> SupportTier.INFO
        else -> SupportTier.AGENT // default: biarkan agent yang mengerjakan
    }

    fun isNative(name: String): Boolean = tier(name) == SupportTier.NATIVE
}

class SlashCommandRegistry(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    @Volatile private var cached: SlashCommandIndex? = null

    suspend fun index(): SlashCommandIndex = withContext(Dispatchers.IO) {
        cached ?: runCatching {
            context.assets.open("catalog/slash_commands.json").use {
                json.decodeFromString(SlashCommandIndex.serializer(), it.bufferedReader().readText())
            }
        }.getOrNull()?.also { cached = it } ?: SlashCommandIndex()
    }

    suspend fun all(): List<SlashCommandDef> = index().commands

    /** Cari berdasarkan kueri: nama, alias, deskripsi, kategori (case-insensitive). */
    suspend fun search(query: String): List<SlashCommandDef> {
        val q = query.trim().removePrefix("/").lowercase()
        val all = all()
        if (q.isBlank()) return all
        return all.filter {
            it.name.lowercase().contains(q) ||
                it.aliases.any { a -> a.lowercase().contains(q) } ||
                it.description.lowercase().contains(q) ||
                it.category.lowercase().contains(q)
        }
    }

    /** Cari definisi berdasar nama ATAU alias (persis). */
    suspend fun find(name: String): SlashCommandDef? {
        val n = name.trim().removePrefix("/").lowercase()
        return all().firstOrNull { it.name == n || it.aliases.any { a -> a.lowercase() == n } }
    }
    /**
     * Parse input "/model glm-5.3" → ParsedSlash(command, "model", "glm-5.3").
     * Null bila input bukan slash command.
     */
    suspend fun parse(input: String): ParsedSlash? =
        parseSlashAgainst(all(), input)

    suspend fun categories(): List<String> = index().categories
}

/** Parse murni (testable tanpa Context): /nama argumen… terhadap daftar definisi. */
fun parseSlashAgainst(commands: List<SlashCommandDef>, input: String): ParsedSlash? {
    val t = input.trim()
    if (!t.startsWith("/")) return null
    val body = t.removePrefix("/")
    if (body.isBlank()) return null
    val sp = body.indexOf(' ')
    val head = (if (sp < 0) body else body.substring(0, sp)).lowercase()
    val args = if (sp < 0) "" else body.substring(sp + 1).trim()
    val def = commands.firstOrNull {
        it.name == head || it.aliases.any { a -> a.lowercase() == head }
    } ?: return null
    return ParsedSlash(def, def.name, args)
}
