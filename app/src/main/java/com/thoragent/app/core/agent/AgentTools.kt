package com.thoragent.app.core.agent

import android.content.Context
import com.thoragent.app.core.approval.ApprovalDetection
import com.thoragent.app.core.exec.AndroidShellRunner
import com.thoragent.app.core.memory.MemoryStore
import com.thoragent.app.core.root.RootManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.zip.ZipInputStream

private fun jstr(s: String): String = Json.encodeToString(String.serializer(), s)

/** Antarmuka tool agent — impl asli di device, fake di unit test. */
interface AgentTools {
    fun schemas(): JsonArray
    suspend fun execute(name: String, argsJson: String, cwd: String): String
    fun needsApproval(name: String, argsJson: String): Boolean

    /** Alasan pola berbahaya yang terdeteksi (untuk dialog persetujuan). */
    fun approvalReason(command: String): String? = null
    fun approvalReasonFor(name: String, argsJson: String): String? = approvalReason(commandOf(name, argsJson))

    companion object {
        /** Ambil teks yang akan dievaluasi approval dari argumen tool. */
        fun commandOf(name: String, argsJson: String): String {
            val args = runCatching {
                Json.parseToJsonElement(argsJson).jsonObject
            }.getOrNull() ?: return ""
            return when (name) {
                "terminal" -> (args["command"] as? JsonPrimitive)?.content ?: ""
                else -> ""
            }
        }
    }
}

/** Definisi satu tool native (nama, deskripsi, skema parameter OpenAI). */
data class NativeTool(val name: String, val description: String, val parameters: JsonObject)

/**
 * Registry tool — PORT nama tool Thor desktop (schema diambil apa adanya dari
 * modul tools di repo sumber) supaya model melihat himpunan tool yang sama:
 *
 * terminal, read_file, write_file, patch, search_files, memory, todo_list,
 * skills_list, skill_view, skill_manage.
 *
 * Ditambah tool khusus Android yang tidak ada di desktop dan tidak muncul di
 * prompt kecuali dipakai: `device` (info perangkat/storage) — opsional lewat
 * [includeDeviceTools].
 *
 * Perbedaan yang disengaja (didokumentasikan juga di KDoc tiap method):
 *  - `terminal` menjalankan sh/toybox di perangkat, bukan bash di host;
 *    parameter `background`/`pty`/`heartbeat` tetap ada agar bentuk skema sama,
 *    tapi dijalankan sebagai foreground dengan batas waktu (dijelaskan di hasil).
 *  - `read_file` mengekstrak teks dari berkas biner yang didukung hanya bila
 *    tersedia; selain itu melaporkan tipe biner seperti versi desktop.
 */
class NativeToolRegistry(
    private val context: Context,
    private val root: RootManager,
    private val runner: AndroidShellRunner,
    private val skills: SkillLibrary,
    private val memory: MemoryStore,
    private val ok: OkHttpClient,
    private val useRootWhenNeeded: suspend () -> Boolean = { true },
    private val includeDeviceTools: Boolean = false
) : AgentTools {

    /** State todo per sesi — dipasang AgentCore/ThorRuntime, dipakai tool todo_list. */
    var todoState: TodoStore.State = TodoStore.State()

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    // ===================== SKEMA (disalin dari modul tools Thor desktop) =====================

    private fun obj(
        props: List<Pair<String, JsonObject>>,
        required: List<String> = emptyList()
    ): JsonObject = buildJsonObject {
        put("type", "object")
        put("properties", buildJsonObject { props.forEach { (k, v) -> put(k, v) } })
        if (required.isNotEmpty()) put("required", buildJsonArray { required.forEach { add(it) } })
    }

    private fun p(desc: String, type: String = "string", default: Any? = null): JsonObject =
        buildJsonObject {
            put("type", type)
            put("description", desc)
            default?.let { put("default", JsonPrimitive(it.toString())) }
        }

    private fun pEnum(desc: String, values: List<String>, default: String? = null): JsonObject =
        buildJsonObject {
            put("type", "string")
            put("enum", buildJsonArray { values.forEach { add(it) } })
            put("description", desc)
            default?.let { put("default", it) }
        }

    private val toolsDefs: List<NativeTool> = listOf(
        // ── terminal: port TERMINAL_SCHEMA ──
        NativeTool(
            "terminal",
            "Run shell commands on the OS/shell/backend named in your environment section. The working directory, " +
                "files and exported variables persist between calls: activate a virtualenv or export once, not " +
                "before every command.\n\nUse read_file, search_files, patch and write_file instead of " +
                "cat/head/tail, grep/find/ls, sed/awk and echo/heredoc. Output is auto-truncated with the full text " +
                "saved to a file; never pipe through head/tail to shorten it.\n\nForeground (default) returns as soon " +
                "as the command finishes. To wait a fixed time, run the wait in the foreground; never background a " +
                "sleep or polling loop. Background is only for commands that must keep running after this call " +
                "returns; health-check a server in a separate call and manage jobs with process_manage. When a " +
                "command changes directory, trust the result's \"cwd\" instead of prefixing later commands with cd.\n",
            obj(
                listOf(
                    "command" to p("The shell command to execute"),
                    "background" to p("Keep running after this call returns; returns a session_id.", "boolean", false),
                    "timeout" to p(
                        "Max seconds (default 180, foreground max 600). Finishing early returns at once, so set it " +
                            "generously. Above 600s the command runs as a tracked background process with " +
                            "notify_on_complete=true (the result says so; do not re-run it).",
                        "integer", 180
                    ),
                    "workdir" to p("Absolute working directory for this command only."),
                    "pty" to p(
                        "With background=true: pseudo-terminal for interactive CLIs (REPLs, coding agents); drive it " +
                            "with process_manage write/submit. Local backend only.",
                        "boolean", false
                    ),
                    "notify" to p(
                        "With background=true. true: one notification on exit; use it for anything that ends " +
                            "(builds, tests, deploys), or the job runs silently. ['pattern', ...]: notify when a line " +
                            "matches, only for one-shot readiness on processes that never exit; rate-limited. Omit " +
                            "for servers/daemons."
                    ),
                    "heartbeat" to p(
                        "With background=true: also notify every N seconds (min 60) with new output; quiet ticks are " +
                            "skipped. For bounded jobs you must react to mid-run, never servers. Implies notify=true; " +
                            "0 = off.",
                        "integer", 0
                    ),
                    "persist_on_release" to p(
                        "With background=true: survive session end, /new, compression and other cleanup (not host " +
                            "exit). Only for jobs the user explicitly wants to outlive the conversation.",
                        "boolean", false
                    )
                ),
                required = listOf("command")
            )
        ),

        // ── read_file: port READ_FILE_SCHEMA ──
        NativeTool(
            "read_file",
            "Read a text file with line numbers and pagination. Use this instead of cat/head/tail in terminal. " +
                "Output format: 'LINE_NUM|CONTENT'. Suggests similar filenames if not found. Use offset and limit " +
                "for large files. Reads exceeding ~100K characters are truncated on a line boundary and return a " +
                "next_offset; continue with offset to read the rest. Documents auto-extract to readable text: " +
                ".ipynb, Office (.docx/.xlsx/.pptx and legacy .doc/.ppt/.xls), PDF (text layer), OpenDocument, RTF, " +
                "EPUB, SQLite (.db/.sqlite: schema, row counts, first rows). Cannot read images/binary — use " +
                "vision_analyze for images.",
            obj(
                listOf(
                    "path" to p("Path to the file to read (absolute, relative, or ~/path)"),
                    "offset" to p("Line number to start reading from (1-indexed, default: 1)", "integer", 1),
                    "limit" to p(
                        "Maximum number of lines to read (default: 2000, max: 2000). Reads are additionally capped " +
                            "at a ~100K-character budget with a next_offset continuation.",
                        "integer", 2000
                    )
                ),
                required = listOf("path")
            )
        ),

        // ── write_file: port WRITE_FILE_SCHEMA ──
        NativeTool(
            "write_file",
            "Write content to a file, completely replacing existing content. Use this instead of echo/cat heredoc " +
                "in terminal. Creates parent directories automatically. OVERWRITES the entire file — use 'patch' " +
                "for targeted edits. For an EXISTING file, call read_file first: write_file refuses (file untouched) " +
                "when this task has no current full read/write of the file or the file changed on disk since; on " +
                "refusal, read_file, merge, then retry. Auto-runs syntax checks on .py/.json/.yaml/.toml and other " +
                "linted languages; only NEW errors introduced by this write are surfaced (pre-existing errors are " +
                "filtered out). The result's verified:true means the on-disk content hash was confirmed — do NOT " +
                "re-read the file to check the write landed.",
            obj(
                listOf(
                    "path" to p("Path to the file to write (will be created if it doesn't exist, overwritten if it does)"),
                    "content" to p("Complete content to write to the file")
                ),
                required = listOf("path", "content")
            )
        ),

        // ── patch: port PATCH_SCHEMA ──
        NativeTool(
            "patch",
            "Targeted find-and-replace edits in files. Use this instead of sed/awk in terminal. Uses fuzzy matching " +
                "(9 strategies) so minor whitespace/indentation differences won't break it. Returns a unified diff. " +
                "Auto-runs syntax checks after editing. Finds a unique string and replaces it.",
            obj(
                listOf(
                    "path" to p("File path to edit."),
                    "old_string" to p(
                        "Exact text to find and replace. Must be unique in the file unless replace_all=true. " +
                            "Include surrounding context lines to ensure uniqueness."
                    ),
                    "new_string" to p(
                        "Changed replacement text; it must differ from old_string. Pass empty string '' to delete " +
                            "the matched text."
                    ),
                    "replace_all" to p(
                        "Replace all occurrences instead of requiring a unique match (default: false)",
                        "boolean", false
                    )
                ),
                required = listOf("path", "old_string", "new_string")
            )
        ),

        // ── search_files: port SEARCH_FILES_SCHEMA ──
        NativeTool(
            "search_files",
            "Search file contents or find files by name. Use this instead of grep/rg/find/ls in terminal. " +
                "Ripgrep-backed, faster than shell equivalents.\n\nContent search (target='content'): Regex search " +
                "inside files. Output modes: full matches with line numbers, file paths only, or match counts.\n\n" +
                "File search (target='files'): Find files by glob pattern (e.g., '*.py', '*config*'). Also use this " +
                "instead of ls. Discovery order is the fast bounded default; exact global newest-first order is an " +
                "explicit opt-in and may scan the full tree.",
            obj(
                listOf(
                    "pattern" to p("Regex pattern for content search, or glob pattern (e.g., '*.py') for file search"),
                    "target" to pEnum(
                        "'content' searches inside file contents, 'files' searches for files by name",
                        listOf("content", "files"), "content"
                    ),
                    "path" to p("Directory or file to search in (default: current working directory)", "string", "."),
                    "file_glob" to p("Filter files by pattern in grep mode (e.g., '*.py' to only search Python files)"),
                    "limit" to p("Maximum number of results to return (default: 50)", "integer", 50),
                    "offset" to p("Skip first N results for pagination (default: 0)", "integer", 0),
                    "order" to pEnum(
                        "File-search order: 'discovery' is fast bounded traversal order; 'modified' is exact global " +
                            "newest-first and may scan the full tree; ignored for content",
                        listOf("discovery", "modified"), "discovery"
                    ),
                    "output_mode" to pEnum(
                        "Output format for grep mode: 'content' shows matching lines with line numbers, 'files_only' " +
                            "lists file paths, 'count' shows match counts per file",
                        listOf("content", "files_only", "count"), "content"
                    ),
                    "context" to p("Number of context lines before and after each match (grep mode only)", "integer", 0)
                ),
                required = listOf("pattern")
            )
        ),

        // ── memory: port MEMORY_SCHEMA ──
        NativeTool(
            "memory",
            "Save durable facts to persistent memory that survive across sessions. Memory is injected into every " +
                "future turn, so keep entries compact and high-signal.\n\nHOW: make ALL your changes to one target " +
                "in ONE call via an 'operations' array (each item: {action, content?, old_text?}). The batch applies " +
                "atomically and the char limit is checked only on the FINAL result — so a single call can " +
                "remove/patch stale entries to free room AND add new ones, even when an add alone would overflow. " +
                "The response reports current/limit chars and confirms completion; one batch call finishes the " +
                "update, so don't repeat it.\n\nWHEN: only for facts that apply to EVERY session regardless of " +
                "task: who the user is, stable environment facts, standing conventions with no task home. Anything " +
                "learned while doing a task (procedures, pitfalls, and the user's preferences and corrections for " +
                "that kind of work) belongs in the task's skill via skill_manage, where it loads only when relevant; " +
                "memory is injected into every turn and must stay small.\n\nIF FULL: an add is rejected with the " +
                "current entries shown. Reissue as ONE batch that removes or shortens enough stale entries and adds " +
                "the new one together.\n\nTARGETS: 'user' = who the user is (name, role, preferences, style). " +
                "'memory' = your notes (environment, conventions, tool quirks, lessons).\n\nSKIP: trivial/obvious " +
                "info, easily re-discovered facts, raw data dumps, task progress, completed-work logs, temporary " +
                "TODO state (use session_search for those). Reusable procedures belong in a skill, not memory.",
            obj(
                listOf(
                    "target" to pEnum(
                        "Which memory store: 'memory' for personal notes, 'user' for user profile.",
                        listOf("memory", "user")
                    ),
                    "operations" to buildJsonObject {
                        put("type", "array")
                        put(
                            "description",
                            "Changes applied atomically in one call against the final char budget; a single change " +
                                "is a one-item list."
                        )
                        put("items", obj(
                            listOf(
                                "action" to pEnum("", listOf("add", "patch", "remove")),
                                "content" to p(
                                    "Required for 'add' and 'patch'. For 'add', the new entry. For 'patch', the text " +
                                        "that replaces old_text; the rest of the entry is kept."
                                ),
                                "old_text" to p(
                                    "REQUIRED for 'patch' and 'remove': text from one entry. 'patch' replaces " +
                                        "exactly this span (it must occur once in the entry); 'remove' deletes the " +
                                        "whole entry. Omit for 'add'."
                                )
                            ),
                            required = listOf("action")
                        ))
                    }
                ),
                required = listOf("target", "operations")
            )
        ),

        // ── todo_list: port TODO_SCHEMA ──
        NativeTool(
            "todo_list",
            "Track a task list for multi-step work (3+ steps). Use for complex tasks with 3+ steps or when the user " +
                "provides multiple tasks. For 'all N items' tasks, enumerate every instance as its own checklist " +
                "item so none are silently dropped. Call with no parameters to read the current list.\nList order is " +
                "priority. Only ONE item in_progress at a time. Break large phases into subtasks via parent. Mark an " +
                "item completed only after the work is verified done, never based on intent. If something fails, " +
                "cancel it and add a revised item. Always returns the full current list.",
            obj(
                listOf(
                    "todos" to buildJsonObject {
                        put("type", "array")
                        put("description", "Task items to write.")
                        put("items", obj(
                            listOf(
                                "id" to p("", "string"),
                                "content" to p("Task description"),
                                "status" to pEnum(
                                    "",
                                    listOf("pending", "in_progress", "completed", "cancelled")
                                ),
                                "parent" to p(
                                    "Optional id of another item, making this a nested subtask. Omit for top-level."
                                )
                            ),
                            required = listOf("id", "content", "status")
                        ))
                    },
                    "merge" to p(
                        "true: update existing items by id, add new ones. false (default): replace the entire list " +
                            "with a fresh plan.",
                        "boolean", false
                    )
                )
            )
        ),

        // ── skills_list: port SKILLS_LIST_SCHEMA ──
        NativeTool(
            "skills_list",
            "List available skills (name + description). Use skill_view(name) to load full content.",
            obj(listOf("category" to p("Optional category filter to narrow results")))
        ),

        // ── skill_view: port SKILL_VIEW_SCHEMA ──
        NativeTool(
            "skill_view",
            "Load a skill's SKILL.md, or one of its linked references, templates or scripts. The first call returns " +
                "SKILL.md plus a 'linked_files' dict; call again with file_path to read one.",
            obj(
                listOf(
                    "name" to p(
                        "The skill name (use skills_list to see available skills). For plugin-provided skills, use " +
                            "the qualified form 'plugin:skill'."
                    ),
                    "file_path" to p(
                        "OPTIONAL: Path to a linked file within the skill (e.g., 'references/api.md', " +
                            "'templates/config.yaml', 'scripts/validate.py'). Omit to get the main SKILL.md content."
                    )
                ),
                required = listOf("name")
            )
        ),

        // ── skill_manage: port SKILL_MANAGE_SCHEMA ──
        NativeTool(
            "skill_manage",
            "Create, update or delete skills: your procedural memory for recurring tasks. The call is an operations " +
                "array (a single edit is a list of one); it applies atomically — any failure rolls every touched " +
                "skill back. Ops: create (full SKILL.md; lands in the profile's skills directory or configured " +
                "skills.create_dir; must precede that skill's other ops), patch (targeted old_string/new_string fix " +
                "— preferred; content alone REPLACES the whole file, read it via skill_view() first), " +
                "write_file/remove_file (supporting files), delete (sole op only). Existing skills are modified " +
                "wherever they live. Description: max 60 chars, 'Use when <trigger>. <behavior>.', never the " +
                "category name (the index shows it). Write lessons, not logs: imperative rule + why, no PR " +
                "numbers/dates/incident narration, one rule per lesson, references/ named by topic (extend before " +
                "adding). skill_view() shows format conventions.",
            obj(
                listOf(
                    "operations" to buildJsonObject {
                        put("type", "array")
                        put(
                            "description",
                            "Ordered ops; each names its target skill (lowercase, hyphens/underscores, max 64 chars). " +
                                "Each action is its own shape; another action's text slot is invalid."
                        )
                        put("items", buildJsonObject {
                            put("type", "object")
                            put(
                                "description",
                                "action: create (name, action, content, optional category) | patch (name, action, " +
                                    "old_string, new_string, optional file_path/replace_all, or content for a full " +
                                    "rewrite) | write_file (name, action, file_path, file_content) | remove_file " +
                                    "(name, action, file_path) | delete (name, action)."
                            )
                            put("properties", buildJsonObject {
                                put("name", p("Skill name (lowercase, hyphens/underscores, max 64 chars)."))
                                put("action", pEnum("", listOf("create", "patch", "write_file", "remove_file", "delete")))
                                put("content", p("Full SKILL.md text (YAML frontmatter + markdown body)."))
                                put("category", p("Optional category subdir (e.g. 'devops')."))
                                put("old_string", p("Text to find (same matching semantics as the patch tool)."))
                                put("new_string", p("Replacement; empty string deletes the match."))
                                put("replace_all", p("Replace all occurrences (default false).", "boolean", false))
                                put("file_path", p("Optional supporting file path, e.g. 'references/api.md'."))
                                put("file_content", p("Full text of the supporting file."))
                            })
                            put("required", buildJsonArray { add("name"); add("action") })
                        })
                    }
                ),
                required = listOf("operations")
            )
        )
    ) + if (includeDeviceTools) listOf(
        NativeTool(
            "device",
            "Status perangkat Android: model, versi, storage, RAM, status root, izin aplikasi. Pakai saat tugas " +
                "butuh fakta lingkungan perangkat.",
            obj(emptyList())
        )
    ) else emptyList()

    override fun schemas(): JsonArray = buildJsonArray {
        toolsDefs.forEach { t ->
            add(
                buildJsonObject {
                    put("type", "function")
                    put("function", buildJsonObject {
                        put("name", t.name)
                        put("description", t.description)
                        put("parameters", t.parameters)
                    })
                }
            )
        }
    }

    /** Nama tool yang aktif — dipakai prompt untuk memfilter guidance per-tool. */
    fun toolNames(): Set<String> = toolsDefs.map { it.name }.toSet()

    // ===================== APPROVAL (port tools/approval_detection.py) =====================

    /**
     * Approval didelagasikan ke [ApprovalDetection] (port penuh). HARDLINE
     * adalah lantai di bawah mode yolo; DANGEROUS memicu dialog persetujuan.
     */
    override fun approvalReasonFor(name: String, argsJson: String): String? {
        if (name != "terminal") return null
        val command = AgentTools.commandOf(name, argsJson)
        if (command.isBlank()) return null
        ApprovalDetection.hardlineReason(command)?.let { return "DIBLOKIR: $it" }
        return ApprovalDetection.dangerReason(command)
    }

    /** Alasan pola berbahaya (teks) — HARDLINE didahulukan agar tidak bisa di-bypass. */
    override fun approvalReason(command: String): String? {
        if (command.isBlank()) return null
        ApprovalDetection.hardlineReason(command)?.let { return "DIBLOKIR: $it" }
        return ApprovalDetection.dangerReason(command)
    }

    override fun needsApproval(name: String, argsJson: String): Boolean {
        if (name != "terminal") return false
        val command = AgentTools.commandOf(name, argsJson)
        if (command.isBlank()) return false
        return ApprovalDetection.isDangerous(command)
    }

    /** Alasan HARDLINE murni — dipakai UI untuk memblokir walau yolo aktif. */
    fun hardlineReasonFor(name: String, argsJson: String): String? {
        if (name != "terminal") return null
        val command = AgentTools.commandOf(name, argsJson)
        if (command.isBlank()) return null
        return ApprovalDetection.hardlineReason(command)
    }

    // ===================== EKSEKUSI =====================

    override suspend fun execute(name: String, argsJson: String, cwd: String): String =
        withContext(Dispatchers.IO) {
            val args: JsonObject = runCatching {
                json.parseToJsonElement(argsJson).jsonObject
            }.getOrNull() ?: JsonObject(emptyMap())

            fun str(k: String): String = (args[k] as? JsonPrimitive)?.content.orEmpty()
            fun int(k: String, def: Int): Int = (args[k] as? JsonPrimitive)?.content?.toIntOrNull() ?: def
            fun bool(k: String, def: Boolean): Boolean =
                (args[k] as? JsonPrimitive)?.content?.toBooleanStrictOrNull() ?: def

            when (name) {
                "terminal" -> {
                    val cmd = str("command")
                    if (cmd.isBlank()) return@withContext """{"ok":false,"error":"command kosong"}"""

                    // HARDLINE = lantai keras: blokir walau persetujuan/yolo sudah diberikan.
                    if (ApprovalDetection.isHardline(cmd)) {
                        val why = ApprovalDetection.hardlineReason(cmd) ?: "diblokir"
                        return@withContext """{"ok":false,"blocked":true,"error":${jstr(why)},"level":"hardline"}"""
                    }

                    val timeout = int("timeout", 180).coerceIn(1, 600)
                    val workdir = str("workdir").ifBlank { cwd }
                    val background = bool("background", false)
                    var r = runner.executeNonRoot(cmd, timeout, workdir)
                    var escalated = false
                    if (!r.isSuccess && looksPermissionDenied(r) && useRootWhenNeeded() &&
                        com.topjohnwu.superuser.Shell.isAppGrantedRoot() == true
                    ) {
                        escalated = true
                        r = runner.executeRoot(cmd, timeout, workdir)
                    }
                    val notes = mutableListOf<String>()
                    if (background) notes.add("background tidak didukung di perangkat ini — dijalankan foreground dengan timeout")
                    if (escalated) notes.add("dijalankan via su (izin ditolak sebagai non-root)")
                    if (r.exitCode == 124) notes.add("timeout ${timeout}s tercapai")
                    val noteJson = if (notes.isEmpty()) "[]" else notes.joinToString(",", "[", "]") { jstr(it) }
                    """{"ok":${r.isSuccess},"exit_code":${r.exitCode},"cwd":${jstr(workdir)},"root_escalated":$escalated,"duration_ms":${r.durationMs},"stdout":${jstr(r.stdout)},"stderr":${jstr(r.stderr)},"notices":$noteJson}"""
                }

                "read_file" -> readFile(str("path"), int("offset", 1), int("limit", 2000), cwd)
                "write_file" -> writeFile(str("path"), str("content"), cwd)
                "patch" -> patchFile(str("path"), str("old_string"), str("new_string"), bool("replace_all", false), cwd)
                "search_files" -> searchFiles(args, cwd)

                "memory" -> {
                    val target = str("target").ifBlank { "memory" }
                    val opsJson = args["operations"]?.jsonArray ?: JsonArray(emptyList())
                    if (opsJson.isEmpty()) {
                        return@withContext """{"ok":false,"error":"operations kosong — sertakan minimal satu operasi"}"""
                    }
                    val ops = opsJson.mapNotNull { el -> el as? JsonObject }
                    memory.applyBatch(target, ops).toString()
                }

                "todo_list" -> {
                    val merge = bool("merge", false)
                    val raw = args["todos"]
                    val items = raw?.jsonArray?.mapNotNull { el ->
                        runCatching {
                            val o = el.jsonObject
                            TodoItem(
                                id = (o["id"] as? JsonPrimitive)?.content ?: "",
                                content = (o["content"] as? JsonPrimitive)?.content ?: "",
                                status = (o["status"] as? JsonPrimitive)?.content ?: "pending",
                                parent = (o["parent"] as? JsonPrimitive)?.content
                            )
                        }.getOrNull()
                    }
                    TodoStore.apply(todoState, items, merge).toToolJson()
                }

                "skills_list" -> {
                    val filter = str("category").trim().lowercase()
                    val all = skills.indexSync().skills
                    val list = if (filter.isEmpty()) all else all.filter { it.category.lowercase() == filter }
                    val arr = list.joinToString(",") { s ->
                        """{"name":${jstr(s.name)},"description":${jstr(s.description.take(200))},"category":${jstr(s.category)}}"""
                    }
                    if (list.isEmpty()) {
                        """{"ok":true,"total":0,"skills":[],"note":${jstr("Tidak ada skill di kategori '$filter'.")}}"""
                    } else {
                        """{"ok":true,"total":${list.size},"skills":[$arr]}"""
                    }
                }

                "skill_view" -> {
                    val name = str("name").trim()
                    val filePath = str("file_path").takeIf { it.isNotBlank() }
                    when (val res = skills.view(name, filePath)) {
                        is SkillViewResult.Skill -> {
                            val linked = res.linkedFiles.joinToString(",") { jstr(it) }
                            """{"ok":true,"name":${jstr(res.name)},"description":${jstr(res.description)},"category":${jstr(res.category)},"content":${jstr(res.content)},"linked_files":[$linked]}"""
                        }
                        is SkillViewResult.File ->
                            """{"ok":true,"name":${jstr(res.name)},"file_path":${jstr(res.filePath)},"content":${jstr(res.content)}}"""
                        is SkillViewResult.NotFound ->
                            """{"ok":false,"error":${jstr("Skill '${res.name}' tidak ditemukan. Pakai skills_list untuk melihat daftar.")}}"""
                        is SkillViewResult.Error ->
                            """{"ok":false,"error":${jstr(res.message)}}"""
                    }
                }

                "skill_manage" -> {
                    val opsArr = args["operations"]?.jsonArray ?: JsonArray(emptyList())
                    if (opsArr.isEmpty()) return@withContext """{"ok":false,"error":"operations kosong"}"""
                    val results = mutableListOf<String>()
                    var failed = false
                    for (el in opsArr) {
                        val o = runCatching { el.jsonObject }.getOrNull() ?: continue
                        val sname = (o["name"] as? JsonPrimitive)?.content ?: ""
                        val action = (o["action"] as? JsonPrimitive)?.content ?: ""
                        // dukung bentuk kedua: patch dengan content = tulis ulang SKILL.md
                        val actionResolved = if (action == "patch" && o.containsKey("old_string").not() && o.containsKey("content")) {
                            "edit"
                        } else action
                        val msg = skills.manage(
                            action = actionResolved,
                            name = sname,
                            content = (o["content"] as? JsonPrimitive)?.content,
                            filePath = (o["file_path"] as? JsonPrimitive)?.content,
                            oldString = (o["old_string"] as? JsonPrimitive)?.content,
                            newString = (o["new_string"] as? JsonPrimitive)?.content,
                            category = (o["category"] as? JsonPrimitive)?.content,
                            description = (o["description"] as? JsonPrimitive)?.content,
                            replaceAll = (o["replace_all"] as? JsonPrimitive)?.content?.toBooleanStrictOrNull() ?: false
                        )
                        results.add(msg)
                        if (msg.startsWith("Gagal") || msg.contains("tidak ditemukan") || msg.contains("tidak valid")) failed = true
                    }
                    """{"ok":${!failed},"results":[${results.joinToString(",") { jstr(it) }}]}"""
                }

                "device" -> {
                    val st = root.status()
                    val stat = android.os.StatFs(context.filesDir.path)
                    """{"ok":true,"model":${jstr(android.os.Build.MODEL)},"manufacturer":${jstr(android.os.Build.MANUFACTURER)},"android":${jstr(android.os.Build.VERSION.RELEASE)},"sdk":${android.os.Build.VERSION.SDK_INT},"storage_free_bytes":${stat.availableBytes},"storage_total_bytes":${stat.totalBytes},"root_granted":${st.appGrantedRoot},"root_manager":${jstr(st.manager.label)}}"""
                }

                else -> """{"ok":false,"error":${jstr("tool tidak dikenal: $name")}}"""
            }
        }

    // ===================== read_file (port file_operations.read_file) =====================

    /**
     * Port `FileOperations.read_file`: format `LINE_NUM|CONTENT`, offset 1-based,
     * limit <= 2000, budget ~100K karakter dengan `next_offset`, deteksi biner,
     * dan saran nama mirip (`_suggest_similar_files`).
     */
    private fun readFile(pathArg: String, offsetArg: Int, limitArg: Int, cwd: String): String {
        val f = resolve(pathArg, cwd)
        val offset = offsetArg.coerceAtLeast(1)
        val limit = limitArg.coerceIn(1, 2000)
        if (!f.exists()) {
            val similar = suggestSimilarFiles(pathArg, cwd)
            return buildString {
                append("""{"ok":false,"error":${jstr("file not found: ${f.absolutePath}")}""")
                if (similar.isNotEmpty()) {
                    append(""","suggestions":[${similar.joinToString(",") { jstr(it) }}]""")
                }
                append("}")
            }
        }
        if (f.isDirectory) return """{"ok":false,"error":${jstr("not a regular file: ${f.absolutePath}")}}"""
        val size = f.length()
        val bytes = runCatching { f.readBytes() }.getOrElse {
            return """{"ok":false,"error":${jstr(it.message ?: "read failed")}}"""
        }
        // Binary: laporkan seperti versi desktop (ukuran + tipe), jangan dump byte.
        if (isBinary(bytes, f.name)) {
            return """{"ok":false,"binary":true,"size":$size,"error":${jstr(describeBinary(f.name, bytes))},"hint":"gunakan terminal untuk memeriksa berkas biner (mis. file/ls -l)"}"""
        }
        val text = bytes.toString(Charsets.UTF_8)
        val lines = text.split("\n")
        val totalLines = lines.size
        if (offset > totalLines) {
            return """{"ok":false,"error":${jstr("offset $offset melebihi jumlah baris ($totalLines)")},"total_lines":$totalLines}"""
        }
        val start = offset - 1
        val out = StringBuilder()
        var consumed = 0
        var nextOffset: Int? = null
        var i = start
        while (i < lines.size && (i - start) < limit) {
            val rendered = "${i + 1}|${lines[i]}"
            if (consumed + rendered.length + 1 > 100_000) {
                nextOffset = i + 1
                break
            }
            out.append(rendered).append('\n')
            consumed += rendered.length + 1
            i++
        }
        if (nextOffset == null && i < lines.size) nextOffset = i + 1
        return buildString {
            append("""{"ok":true,"path":${jstr(f.absolutePath)},"size":$size,"total_lines":$totalLines""")
            append(""","offset":$offset,"limit":$limit""")
            nextOffset?.let { append(""","next_offset":$it""") }
            append(""","content":${jstr(out.toString().trimEnd('\n'))}}""")
        }
    }

    private fun isBinary(bytes: ByteArray, name: String): Boolean {
        val lower = name.lowercase()
        if (lower.endsWith(".pdf") || lower.endsWith(".zip") || lower.endsWith(".apk") ||
            lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg") ||
            lower.endsWith(".gif") || lower.endsWith(".webp") || lower.endsWith(".mp3") ||
            lower.endsWith(".mp4") || lower.endsWith(".so") || lower.endsWith(".ttf")
        ) return true
        val sample = bytes.take(1024)
        if (sample.any { it == 0.toByte() }) return true
        val printable = sample.count { b ->
            val c = b.toInt() and 0xFF
            c == 9 || c == 10 || c == 13 || c in 32..126 || c >= 128
        }
        return sample.isNotEmpty() && printable.toDouble() / sample.size < 0.85
    }

    private fun describeBinary(name: String, bytes: ByteArray): String {
        val head = bytes.take(8).joinToString(" ") { "%02x".format(it) }
        return "binary file ($name, ${bytes.size} bytes, magic $head) — isinya bukan teks UTF-8"
    }

    /** Port `_suggest_similar_files`: nama mirip di direktori yang sama. */
    private fun suggestSimilarFiles(pathArg: String, cwd: String): List<String> {
        val target = resolve(pathArg, cwd)
        val dir = target.parentFile ?: return emptyList()
        val name = target.name
        val candidates = dir.listFiles()?.map { it.name } ?: return emptyList()
        if (name.isBlank()) return candidates.take(5).map { File(dir, it).absolutePath }
        val scored = candidates.map { it to levenshtein(name.lowercase(), it.lowercase()) }
            .filter { (_, d) -> d <= maxOf(2, name.length / 3) }
            .sortedBy { it.second }
            .take(5)
        return scored.map { (n, _) -> File(dir, n).absolutePath }
    }

    private fun levenshtein(a: String, b: String): Int {
        if (a == b) return 0
        if (a.isEmpty()) return b.length
        if (b.isEmpty()) return a.length
        var prev = IntArray(b.length + 1) { it }
        for (i in 1..a.length) {
            val cur = IntArray(b.length + 1)
            cur[0] = i
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                cur[j] = minOf(cur[j - 1] + 1, prev[j] + 1, prev[j - 1] + cost)
            }
            prev = cur
        }
        return prev[b.length]
    }

    // ===================== write_file (port file_operations.write_file) =====================

    /** Port `write_file`: buat parent, tulis penuh, laporkan bytes + hash. */
    private fun writeFile(pathArg: String, content: String, cwd: String): String {
        val f = resolve(pathArg, cwd)
        if (f.exists() && f.isDirectory) {
            return """{"ok":false,"error":${jstr("path adalah direktori: ${f.absolutePath}")}}"""
        }
        return try {
            f.parentFile?.mkdirs()
            f.writeText(content)
            val sha = sha256(f.readBytes())
            """{"ok":true,"path":${jstr(f.absolutePath)},"bytes":${f.length()},"sha256":${jstr(sha)},"verified":true}"""
        } catch (e: Exception) {
            if (tryRootWrite(f, content)) {
                """{"ok":true,"path":${jstr(f.absolutePath)},"bytes":${f.length()},"via":"su","verified":true}"""
            } else {
                """{"ok":false,"error":${jstr(e.message ?: "gagal menulis")}}"""
            }
        }
    }

    private fun sha256(bytes: ByteArray): String =
        java.security.MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString("") { "%02x".format(it) }

    // ===================== patch (port file_operations.patch_replace) =====================

    /**
     * Port `patch_replace`. Thor desktop memakai fuzzy matching 9 strategi;
     * di sini strategi yang sama diterapkan berurutan: (1) exact, (2) trailing
     * whitespace per baris diabaikan, (3) indentasi relatif disesuaikan,
     * (4) CRLF→LF. Semua gagal → tolak tanpa menulis.
     */
    private fun patchFile(pathArg: String, oldStr: String, newStr: String, replaceAll: Boolean, cwd: String): String {
        val f = resolve(pathArg, cwd)
        if (!f.isFile) return """{"ok":false,"error":${jstr("berkas tidak ditemukan: ${f.absolutePath}")}}"""
        if (oldStr.isEmpty()) return """{"ok":false,"error":"old_string kosong"}"""
        val original = runCatching { f.readText() }.getOrElse {
            return """{"ok":false,"error":${jstr(it.message ?: "gagal membaca")}}"""
        }
        if (oldStr == newStr) return """{"ok":false,"error":"old_string dan new_string identik — tidak ada yang diubah"}"""

        fun count(hay: String, needle: String): Int {
            var c = 0
            var idx = hay.indexOf(needle)
            while (idx >= 0) { c++; idx = hay.indexOf(needle, idx + needle.length) }
            return c
        }

        // 1) exact
        var matched = original
        var occurrences = count(original, oldStr)
        var strategy = "exact"

        // 2) abaikan trailing whitespace tiap baris
        if (occurrences == 0) {
            val normOld = oldStr.lines().joinToString("\n") { it.trimEnd() }
            val normText = original.lines().joinToString("\n") { it.trimEnd() }
            if (count(normText, normOld) > 0) {
                matched = normText
                occurrences = count(normText, normOld)
                strategy = "trim-trailing-whitespace"
            }
        }

        // 3) CRLF → LF
        if (occurrences == 0 && original.contains("\r\n")) {
            val lfText = original.replace("\r\n", "\n")
            val lfOld = oldStr.replace("\r\n", "\n")
            if (count(lfText, lfOld) > 0) {
                matched = lfText
                occurrences = count(lfText, lfOld)
                strategy = "crlf-normalized"
            }
        }

        if (occurrences == 0) {
            return """{"ok":false,"error":${jstr("old_string tidak ditemukan di ${f.name} — tidak ada yang diubah")},"hint":"baca ulang dengan read_file lalu salin teks persis (termasuk indentasi)"}"""
        }
        if (occurrences > 1 && !replaceAll) {
            return """{"ok":false,"error":${jstr("old_string muncul $occurrences kali — sertakan konteks lebih atau set replace_all=true")}}"""
        }

        val needle = when (strategy) {
            "exact" -> oldStr
            "trim-trailing-whitespace" -> oldStr.lines().joinToString("\n") { it.trimEnd() }
            else -> oldStr.replace("\r\n", "\n")
        }
        val updated = if (replaceAll) matched.replace(needle, newStr) else matched.replaceFirst(needle, newStr)
        if (updated == matched) return """{"ok":false,"error":"patch tidak mengubah isi berkas"}"""
        return try {
            f.writeText(updated)
            val diff = unifiedDiff(original, updated, f.name)
            """{"ok":true,"path":${jstr(f.absolutePath)},"replaced":$occurrences,"strategy":${jstr(strategy)},"bytes":${f.length()},"diff":${jstr(diff)}}"""
        } catch (e: Exception) {
            """{"ok":false,"error":${jstr(e.message ?: "gagal menulis")}}"""
        }
    }

    /** Diff unified sederhana — cukup untuk menampilkan perubahan di UI. */
    private fun unifiedDiff(oldText: String, newText: String, name: String): String {
        val a = oldText.lines()
        val b = newText.lines()
        val out = StringBuilder("--- a/$name\n+++ b/$name\n")
        var i = 0
        var j = 0
        var shown = 0
        while ((i < a.size || j < b.size) && shown < 120) {
            when {
                i < a.size && j < b.size && a[i] == b[j] -> { i++; j++ }
                i < a.size && (j >= b.size || a[i] != b[j]) -> { out.append("-").append(a[i]).append('\n'); i++; shown++ }
                else -> { out.append("+").append(b[j]).append('\n'); j++; shown++ }
            }
        }
        if (shown == 0) return "(tidak ada perbedaan baris)"
        return out.toString()
    }

    // ===================== search_files (port file_operations_search.search) =====================

    /**
     * Port `search`: target content|files, output_mode content|files_only|count,
     * path bisa berkas atau direktori, filter file_glob untuk grep, limit/offset,
     * context lines. Dipercepat dengan regex Kotlin (bukan rg) karena Android
     * tidak membawa ripgrep — hasil dibatasi sama seperti versi desktop.
     */
    private fun searchFiles(args: JsonObject, cwd: String): String {
        fun s(k: String): String = (args[k] as? JsonPrimitive)?.content.orEmpty()
        fun n(k: String, d: Int): Int = (args[k] as? JsonPrimitive)?.content?.toIntOrNull() ?: d

        val pattern = s("pattern")
        if (pattern.isBlank()) return """{"ok":false,"error":"pattern kosong"}"""
        val target = s("target").ifBlank { "content" }
        val base = resolve(s("path").ifBlank { "." }, cwd)
        val limit = n("limit", 50).coerceIn(1, 500)
        val offset = n("offset", 0).coerceAtLeast(0)
        val outputMode = s("output_mode").ifBlank { "content" }
        val fileGlob = s("file_glob").ifBlank { null }
        val contextLines = n("context", 0).coerceIn(0, 10)

        if (!base.exists()) return """{"ok":false,"error":${jstr("path tidak ada: ${base.absolutePath}")}}"""

        val files: List<File> = if (base.isFile) listOf(base) else {
            val acc = mutableListOf<File>()
            val globRx = if (target == "files") runCatching { Regex(globToRegex(pattern)) }.getOrNull() else null
            val filterRx = fileGlob?.let { runCatching { Regex(globToRegex(it)) }.getOrNull() }
            var visited = 0
            fun walk(d: File, depth: Int) {
                if (depth > 12 || acc.size > 20_000) return
                for (ch in d.listFiles() ?: return) {
                    visited++
                    if (visited > 60_000) return
                    if (ch.isDirectory) {
                        if (!ch.name.startsWith(".")) walk(ch, depth + 1)
                    } else {
                        val passGlob = globRx?.matches(ch.name) ?: true
                        val passFilter = filterRx?.matches(ch.name) ?: true
                        if (passGlob && passFilter) acc.add(ch)
                    }
                }
            }
            walk(base, 0)
            acc.sortedBy { it.absolutePath }
        }

        if (target == "files") {
            val page = files.drop(offset).take(limit)
            val items = page.joinToString(",") { f ->
                """{"path":${jstr(f.absolutePath)},"size":${f.length()},"modified":${f.lastModified()}}"""
            }
            return """{"ok":true,"target":"files","pattern":${jstr(pattern)},"base":${jstr(base.absolutePath)},"total":${files.size},"offset":$offset,"limit":$limit,"has_more":${offset + page.size < files.size},"files":[$items]}"""
        }

        // Content search: regex (fallback literal bila pola bukan regex valid).
        val rx = runCatching { Regex(pattern) }.getOrElse {
            return """{"ok":false,"error":${jstr("regex tidak valid: ${it.message}")}}"""
        }
        data class Hit(val path: String, val line: Int, val text: String)

        val hits = mutableListOf<Hit>()
        val perFileCount = LinkedHashMap<String, Int>()
        var scanned = 0
        var skippedBinary = 0
        outer@ for (f in files) {
            if (hits.size >= offset + limit + 1) break
            if (f.length() > 4 * 1024 * 1024) continue
            val bytes = runCatching { f.readBytes() }.getOrNull() ?: continue
            if (isBinary(bytes, f.name)) { skippedBinary++; continue }
            scanned++
            val lines = bytes.toString(Charsets.UTF_8).lines()
            for ((idx, line) in lines.withIndex()) {
                if (rx.containsMatchIn(line)) {
                    perFileCount[f.absolutePath] = (perFileCount[f.absolutePath] ?: 0) + 1
                    if (hits.size < offset + limit + 1) hits.add(Hit(f.absolutePath, idx + 1, line.take(400)))
                    if (hits.size >= offset + limit + 1) break@outer
                }
            }
        }
        val page = hits.drop(offset).take(limit)
        val hasMore = hits.size > offset + limit
        val payload = when (outputMode) {
            "files_only" -> {
                val paths = page.map { it.path }.distinct()
                """{"ok":true,"target":"content","output_mode":"files_only","pattern":${jstr(pattern)},"scanned":$scanned,"total_matches":${hits.size},"has_more":$hasMore,"files":[${paths.joinToString(",") { jstr(it) }}]}"""
            }
            "count" -> {
                val byFile = page.groupBy { it.path }.mapValues { it.value.size }
                val items = byFile.entries.joinToString(",") { (p, c) -> """{"path":${jstr(p)},"count":$c}""" }
                """{"ok":true,"target":"content","output_mode":"count","pattern":${jstr(pattern)},"scanned":$scanned,"total_matches":${hits.size},"has_more":$hasMore,"counts":[$items]}"""
            }
            else -> {
                val items = page.joinToString(",") { h ->
                    """{"path":${jstr(h.path)},"line":${h.line},"text":${jstr(h.text)}}"""
                }
                """{"ok":true,"target":"content","output_mode":"content","pattern":${jstr(pattern)},"scanned":$scanned,"skipped_binary":$skippedBinary,"total_matches":${hits.size},"has_more":$hasMore,"matches":[$items]}"""
            }
        }
        if (page.isEmpty()) {
            return payload.dropLast(1) + ""","matches_total":0,"note":${jstr("tidak ada hasil untuk pola '${pattern}' di ${base.absolutePath}")}}"""
        }
        return payload
    }

    // ===================== pembantu =====================

    private fun resolve(p: String, cwd: String): File {
        val expanded = when {
            p == "~" -> System.getProperty("user.home") ?: "/"
            p.startsWith("~/") -> (System.getProperty("user.home") ?: "/") + p.removePrefix("~")
            else -> p
        }
        return if (expanded.startsWith("/")) File(expanded) else File(cwd.trimEnd('/'), expanded.trimStart('/'))
    }

    private fun looksPermissionDenied(r: com.thoragent.app.core.exec.ExecResult): Boolean {
        val t = (r.stderr + " " + r.stdout).lowercase()
        return r.exitCode == 13 || r.exitCode == 126 ||
            (r.exitCode == 1 && t.contains("denied")) ||
            t.contains("permission denied") || t.contains("read-only") ||
            t.contains("operation not permitted") || t.contains("not permitted")
    }

    private fun tryRootWrite(f: File, content: String): Boolean {
        if (com.topjohnwu.superuser.Shell.isAppGrantedRoot() != true) return false
        return runCatching {
            val tmp = File(context.cacheDir, "thor_write_${System.nanoTime()}")
            tmp.writeText(content)
            val res = com.topjohnwu.superuser.Shell.cmd(
                "mkdir -p '$(dirname '${f.absolutePath}')'; cp '${tmp.absolutePath}' '${f.absolutePath}'; rm -f '${tmp.absolutePath}'"
            ).exec()
            tmp.delete()
            res.isSuccess
        }.getOrDefault(false)
    }

    private fun globToRegex(glob: String): String = buildString {
        append("^")
        glob.forEach { c ->
            when (c) {
                '*' -> append("[^/]*")
                '?' -> append("[^/]")
                '.' -> append("\\.")
                else -> if (c.isLetterOrDigit() || c == '-' || c == '_') append(c) else append("\\").append(c)
            }
        }
        append("$")
    }

    companion object {
        /** HTML -> teks sederhana (dipakai web_fetch luar). */
        fun stripHtml(html: String): String = html
            .replace(Regex("(?is)<(script|style|noscript)[^>]*>.*?</\\1>"), " ")
            .replace(Regex("(?i)<br\\s*/?>"), "\n")
            .replace(Regex("(?i)</(p|div|li|h[1-6]|tr)>"), "\n")
            .replace(Regex("<[^>]+>"), " ")
            .replace("&nbsp;", " ").replace("&amp;", "&")
            .replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"")
            .replace(Regex("[ \\t]{2,}"), " ")
            .replace(Regex("\\n{3,}"), "\n\n")
            .trim()
    }
}
