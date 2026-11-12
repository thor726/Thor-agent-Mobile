package com.thoragent.app.core.agent

import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/**
 * TodoStore — PORT `tools/todo_tool.py` (revisi, merge, sanitasi parent, cap).
 *
 * Satu instance per sesi (`AgentCore`), state hidup di memori, dan setiap
 * penulisan menaikkan revision monotonik supaya UI bisa menolak update basi.
 * Nilai yang dipertahankan persis dari sumber:
 *  - `VALID_STATUSES` = pending | in_progress | completed | cancelled
 *  - `MAX_TODO_CONTENT_CHARS` = 4000 (potong kepala + penanda "… [truncated]")
 *  - `MAX_TODO_ITEMS` = 256 (ekor dibuang, kepala prioritas dipertahankan)
 *  - `_dedupe_by_id` menyimpan kemunculan TERAKHIR di posisinya
 *  - `_normalize_order` mengangkat in_progress di depan placeholder pending
 *  - `_sanitize_parents` membuang parent menggantung & memutus siklus
 */
data class TodoItem(
    val id: String,
    val content: String,
    val status: String = "pending",
    val parent: String? = null
)

object TodoStore {
    const val MAX_TODO_CONTENT_CHARS = 4000
    const val MAX_TODO_ITEMS = 256
    const val TRUNCATION_MARKER = "… [truncated]"
    val VALID_STATUSES = setOf("pending", "in_progress", "completed", "cancelled")
    val ACTIVE_STATUSES = setOf("pending", "in_progress")
    const val TODO_INJECTION_HEADER = "[Your active task list was preserved across context compression]"

    private val STATUS_MARKERS = mapOf(
        "completed" to "[x]", "in_progress" to "[>]", "pending" to "[ ]", "cancelled" to "[~]"
    )

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    /** State satu sesi — dibuat sekali di AgentCore dan dioper ke tool. */
    class State {
        private var items: MutableList<TodoItem> = mutableListOf()
        private var revision: Int = 0

        fun read(): List<TodoItem> = items.toList()

        fun hasItems(): Boolean = items.isNotEmpty()

        fun snapshot(): Pair<List<TodoItem>, Int> = items.toList() to revision

        /**
         * Port `write`: replace (bawaan) atau merge by id. Bila ada item tidak
         * valid, daftar TIDAK berubah (tidak ada penulisan parsial).
         */
        fun write(todos: List<TodoItem>, merge: Boolean = false): Result<List<TodoItem>> {
            val before = items.toList()
            return try {
                items = if (merge) mergeItems(todos) else freshItems(todos)
                if (items.size > MAX_TODO_ITEMS) items = items.take(MAX_TODO_ITEMS).toMutableList()
                sanitizeParents(items)
                if (items != before) revision++
                Result.success(read())
            } catch (e: IllegalArgumentException) {
                items = before.toMutableList()
                Result.failure(e)
            }
        }

        /** Port `_fresh_items`. */
        private fun freshItems(todos: List<TodoItem>): MutableList<TodoItem> =
            normalizeOrder(dedupeById(todos).map { validate(it) }).toMutableList()

        /** Port `_merge`: hanya field yang dikirim yang diubah; id baru ditambahkan. */
        private fun mergeItems(todos: List<TodoItem>): MutableList<TodoItem> {
            val existing = items.associateBy { it.id }.toMutableMap()
            val order = items.map { it.id }.toMutableList()
            for (t in dedupeById(todos)) {
                val itemId = t.id.trim()
                if (itemId.isEmpty()) continue
                val cur = existing[itemId]
                if (cur == null) {
                    val v = validate(t)
                    existing[v.id] = v
                    order.add(v.id)
                    continue
                }
                var updated = cur
                if (t.content.isNotBlank()) updated = updated.copy(content = capContent(t.content.trim()))
                val st = t.status.trim().lowercase()
                if (st.isNotEmpty() && st in VALID_STATUSES) updated = updated.copy(status = st)
                // `parent` hadir (bukan null pada input mentah) → set/bersihkan.
                updated = if (t.parent == null) updated else {
                    val p = t.parent.trim()
                    if (p.isNotEmpty()) updated.copy(parent = p) else updated.copy(parent = null)
                }
                existing[itemId] = updated
            }
            val rebuilt = order.mapNotNull { existing[it] }
            return normalizeOrder(rebuilt).toMutableList()
        }

        /** Port `restore`: pasang snapshot tanpa membuat revisi baru. */
        fun restore(todos: List<TodoItem>, revisionValue: Int = 0) {
            items = freshItems(todos).take(MAX_TODO_ITEMS).toMutableList()
            revision = revisionValue.coerceAtLeast(0)
        }

        /** Port `format_for_injection` — hanya item aktif yang di-inject. */
        fun formatForInjection(): String? {
            if (items.isEmpty()) return null
            val children = items.filter { it.parent != null }.groupBy { it.parent!! }
            val lines = mutableListOf<String>()
            fun render(item: TodoItem, depth: Int, out: MutableList<String>): Boolean {
                val kidLines = mutableListOf<String>()
                var hasActiveKid = false
                for (kid in children[item.id].orEmpty()) {
                    if (render(kid, depth + 1, kidLines)) hasActiveKid = true
                }
                val keep = item.status in ACTIVE_STATUSES || hasActiveKid
                if (keep) {
                    val marker = STATUS_MARKERS[item.status] ?: "[?]"
                    out.add("${"  ".repeat(depth)}- $marker ${item.id}. ${item.content} (${item.status})")
                    out.addAll(kidLines)
                }
                return keep
            }
            val roots = mutableListOf<String>()
            for (item in items) {
                if (item.parent == null) render(item, 0, roots)
            }
            if (roots.isEmpty()) return null
            return (listOf(TODO_INJECTION_HEADER) + roots).joinToString("\n")
        }

        private fun capContent(content: String): String =
            if (content.length > MAX_TODO_CONTENT_CHARS) {
                content.take(MAX_TODO_CONTENT_CHARS - TRUNCATION_MARKER.length) + TRUNCATION_MARKER
            } else content

        /** Port `_validate` — konten kosong melempar error. */
        private fun validate(item: TodoItem): TodoItem {
            val id = item.id.trim().ifEmpty { "?" }
            val content = item.content.trim()
            require(content.isNotEmpty()) {
                "Todo item '$id' has empty or missing content. Each item must have a non-empty description."
            }
            val st = item.status.trim().lowercase()
            val parent = item.parent?.trim()?.takeIf { it.isNotEmpty() && it != id }
            return TodoItem(
                id = id,
                content = capContent(content),
                status = if (st in VALID_STATUSES) st else "pending",
                parent = parent
            )
        }

        /** Port `_sanitize_parents`. */
        private fun sanitizeParents(list: MutableList<TodoItem>) {
            val byId = list.associateBy { it.id }
            for (i in list.indices) {
                val p = list[i].parent
                if (p != null && p !in byId) list[i] = list[i].copy(parent = null)
            }
            for (i in list.indices) {
                val seen = mutableSetOf(list[i].id)
                var node = byId[list[i].id]
                while (node?.parent != null) {
                    if (node.parent in seen) {
                        list[i] = list[i].copy(parent = null)
                        break
                    }
                    seen.add(node.parent!!)
                    node = byId[node.parent]
                }
            }
        }

        /** Port `_dedupe_by_id` — kemunculan terakhir menang di posisinya. */
        private fun dedupeById(todos: List<TodoItem>): List<TodoItem> {
            val lastIndex = LinkedHashMap<String, Int>()
            todos.forEachIndexed { i, t ->
                val key = t.id.trim().ifEmpty { "?" }
                lastIndex[key] = i
            }
            return lastIndex.values.sorted().map { todos[it] }
        }

        /** Port `_normalize_order`. */
        private fun normalizeOrder(items: List<TodoItem>): List<TodoItem> {
            if (items.any { it.parent != null }) return items
            val statuses = items.map { it.status }
            val activeIndex = statuses.indexOf("in_progress")
            if (activeIndex < 0) return items
            val firstPending = statuses.indexOf("pending")
            if (firstPending < 0 || firstPending > activeIndex) return items
            val normalized = items.toMutableList()
            normalized.add(firstPending, normalized.removeAt(activeIndex))
            return normalized
        }
    }

    /**
     * Port `todo_tool`: tulis (replace/merge) atau baca, hasil selalu daftar
     * penuh + revision + ringkasan per status.
     */
    fun apply(
        state: State,
        todos: List<TodoItem>?,
        merge: Boolean = false
    ): TodoResult {
        if (todos == null) {
            val (items, rev) = state.snapshot()
            return TodoResult.Ok(items, rev)
        }
        return state.write(todos, merge).fold(
            onSuccess = { items -> TodoResult.Ok(items, state.snapshot().second) },
            onFailure = { e -> TodoResult.Error(e.message ?: "todos tidak valid") }
        )
    }

    /** Versi tanpa state (dipakai test cepat): memakai State baru. */
    fun applyOnce(todos: List<TodoItem>?, merge: Boolean = false): TodoResult {
        val fresh = State()
        return apply(fresh, todos, merge)
    }
}

sealed class TodoResult {
    data class Ok(val todos: List<TodoItem>, val revision: Int) : TodoResult()
    data class Error(val message: String) : TodoResult()
}

/** Render tool result JSON — port bentuk `json.dumps({todos, revision, summary})`. */
fun TodoResult.toToolJson(): String {
    fun jstr(s: String): String = Json.encodeToString(String.serializer(), s)
    return when (this) {
        is TodoResult.Error -> """{"ok":false,"error":${jstr(message)}}"""
        is TodoResult.Ok -> {
            val items = todos.joinToString(",") { t ->
                buildString {
                    append("""{"id":${jstr(t.id)},"content":${jstr(t.content)},"status":${jstr(t.status)}""")
                    t.parent?.let { append(""","parent":${jstr(it)}""") }
                    append("}")
                }
            }
            val summary = listOf("pending", "in_progress", "completed", "cancelled")
                .joinToString(",") { s -> """"$s":${todos.count { it.status == s }}""" }
            """{"ok":true,"todos":[$items],"revision":$revision,"summary":{"total":${todos.size},$summary}}"""
        }
    }
}
