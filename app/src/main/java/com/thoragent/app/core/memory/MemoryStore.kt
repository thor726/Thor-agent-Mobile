package com.thoragent.app.core.memory

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.File

/**
 * MemoryStore — port `tools/memory_tool_store.py` repo Thor-agent.
 *
 * Memori terkurasi, berbatas karakter, dan file-backed (MEMORY.md / USER.md).
 * Perilaku yang dipertahankan persis dari sumber:
 *  - entri dipisah delimiter "\n§\n";
 *  - budget default 2.200 char (memory) dan 1.375 char (user);
 *  - `add` ditolak bila melewati budget, dengan entri live dikembalikan agar
 *    model bisa mengonsolidasi;
 *  - `patch` hanya mengganti span yang muncul TEPAT SEKALI di satu entri
 *    (nol atau >1 kemunculan = gagal, tanpa menyentuh berkas);
 *  - `remove` menghapus satu entri utuh (dipilih lewat teks unik);
 *  - batch operasi atomik: limit hanya dicek pada hasil AKHIR, sehingga satu
 *    panggilan bisa menghapus/memangkas entri basi DAN menambah entri baru;
 *  - snapshot system prompt beku saat load (prefix-cache stabil): tulisan di
 *    tengah sesi tidak mengubah prompt yang sudah dikirim.
 */
class MemoryStore(
    val dir: File,
    val memoryCharLimit: Int = DEFAULT_MEMORY_CHAR_LIMIT,
    val userCharLimit: Int = DEFAULT_USER_CHAR_LIMIT,
    val memoryEnabled: Boolean = true,
    val userProfileEnabled: Boolean = true
) {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val lock = Mutex()

    /** Entri live (state sesi) — dipisah, sudah dipangkas spasi tepi. */
    private var memoryEntries: MutableList<String> = mutableListOf()
    private var userEntries: MutableList<String> = mutableListOf()

    /** Snapshot beku untuk system prompt (diambil saat load). */
    private var memorySnapshot: String = ""
    private var userSnapshot: String = ""

    val memoryFile: File get() = File(dir, "MEMORY.md")
    val userFile: File get() = File(dir, "USER.md")

    // ===================== LOAD / PERSIST =====================

    /** Muat MEMORY.md / USER.md dan bekukan snapshot system prompt. */
    suspend fun loadFromDisk(): Unit = withContext(Dispatchers.IO) {
        lock.withLock {
            dir.mkdirs()
            memoryEntries = parseEntries(readRaw(memoryFile)).toMutableList()
            userEntries = parseEntries(readRaw(userFile)).toMutableList()
            memorySnapshot = if (memoryEnabled) renderBlock("memory", memoryEntries) else ""
            userSnapshot = if (userProfileEnabled) renderBlock("user", userEntries) else ""
        }
    }

    /**
     * Blok system prompt — memakai SNAPSHOT beku (bukan state live), supaya
     * prompt byte-stabil sepanjang sesi. null bila kosong/nonaktif.
     */
    fun formatForSystemPrompt(target: String): String? =
        (if (target == "user") userSnapshot else memorySnapshot).ifBlank { null }

    private fun readRaw(file: File): String = runCatching {
        if (file.isFile) file.readText() else ""
    }.getOrDefault("")

    private fun entriesFor(target: String): MutableList<String> =
        if (target == "user") userEntries else memoryEntries

    private fun targetEnabled(target: String): Boolean =
        if (target == "user") userProfileEnabled else memoryEnabled

    private fun charLimit(target: String): Int =
        if (target == "user") userCharLimit else memoryCharLimit

    private fun charCount(target: String): Int = entriesFor(target).joinToString(ENTRY_DELIMITER).length

    /** Tulis entri ke disk secara atomik (tmp + rename). */
    private fun persist(target: String) {
        val file = if (target == "user") userFile else memoryFile
        val body = entriesFor(target).joinToString(ENTRY_DELIMITER)
        runCatching {
            dir.mkdirs()
            val tmp = File(dir, "${file.name}.tmp")
            tmp.writeText(body)
            if (file.exists()) file.delete()
            tmp.renameTo(file)
        }
    }

    // ===================== RENDER =====================

    private fun usagePct(target: String, current: Int): String {
        val limit = charLimit(target)
        val pct = if (limit > 0) minOf(100, current * 100 / limit) else 0
        return "$pct% — ${fmt(current)}/${fmt(limit)} chars"
    }

    private fun fmt(n: Int): String = "%,d".format(n)

    /** Header blok system prompt: judul + indikator pemakaian + entri. */
    private fun renderBlock(target: String, entries: List<String>): String {
        if (entries.isEmpty()) return ""
        val content = entries.joinToString(ENTRY_DELIMITER)
        val sep = "═".repeat(46)
        val title = BLOCK_HEADERS[if (target == "user") "user" else "memory"]!!
        return "$sep\n$title [${usagePct(target, content.length)}]\n$sep\n$content"
    }

    // ===================== OPERASI =====================

    /**
     * Jalankan satu operasi memori. [action] = add | patch | remove.
     * Mengembalikan JsonObject hasil ala repo (success/error/usage/...).
     */
    suspend fun apply(
        target: String,
        action: String,
        content: String = "",
        oldText: String = ""
    ): JsonObject = withContext(Dispatchers.IO) {
        lock.withLock {
            if (target != "memory" && target != "user") {
                return@withLock err("target harus 'memory' atau 'user'")
            }
            if (!targetEnabled(target)) {
                return@withLock err(
                    if (target == "user") "User profile store dinonaktifkan."
                    else "Memory store dinonaktifkan."
                )
            }
            val operations = buildJsonArray {
                addJsonObject {
                    put("action", action)
                    if (content.isNotEmpty()) put("content", content)
                    if (oldText.isNotEmpty()) put("old_text", oldText)
                }
            }
            applyBatchLocked(target, operations)
        }
    }

    /** Batch atomik: semua operasi diterapkan atau tidak sama sekali. */
    suspend fun applyBatch(target: String, operations: List<JsonObject>): JsonObject =
        withContext(Dispatchers.IO) {
            lock.withLock {
                if (target != "memory" && target != "user") return@withLock err("target tidak valid")
                if (!targetEnabled(target)) return@withLock err("store '$target' dinonaktifkan")
                applyBatchLocked(target, buildJsonArray { operations.forEach { add(it) } })
            }
        }

    private fun applyBatchLocked(target: String, operations: kotlinx.serialization.json.JsonArray): JsonObject {
        if (operations.isEmpty()) return err("operations kosong — sertakan minimal satu operasi")
        val working = entriesFor(target).toMutableList()
        val limit = charLimit(target)
        val notes = mutableListOf<String>()

        for (opEl in operations) {
            val op = opEl as? JsonObject ?: return err("operasi harus objek {action, content?, old_text?}")
            val action = (op["action"] as? kotlinx.serialization.json.JsonPrimitive)?.content ?: ""
            val content = (op["content"] as? kotlinx.serialization.json.JsonPrimitive)?.content ?: ""
            val oldText = (op["old_text"] as? kotlinx.serialization.json.JsonPrimitive)?.content ?: ""

            when (action) {
                "add" -> {
                    if (content.isBlank()) return err("'add' butuh 'content'")
                    val entry = content.replace('\uFEFF', ' ').trim()
                    if (entry.isBlank()) return err("'add' dengan content kosong")
                    if (working.any { it == entry }) {
                        notes.add("entri sudah ada — tidak diduplikasi")
                        continue
                    }
                    working.add(entry)
                }

                "patch" -> {
                    if (oldText.isEmpty()) return err("'patch' butuh 'old_text'", "missing_old_text")
                    if (content.isEmpty()) return err("'patch' butuh 'content' (teks pengganti)")
                    val idx = indexOfEntryContaining(working, oldText)
                        ?: return overBudgetOrMissing(working, target, oldText, "patch")
                    val entry = working[idx]
                    val occurrences = countOccurrences(entry, oldText)
                    if (occurrences == 0) {
                        return err(
                            "old_text tidak ditemukan di entri mana pun" +
                                if (oldText.contains('§')) " (old_text harus berasal dari SATU entri — '§' memisahkan entri)" else "",
                            "no_match"
                        )
                    }
                    if (occurrences > 1) {
                        return err(
                            "old_text muncul $occurrences kali di entri itu — sertakan konteks lebih banyak agar unik",
                            "ambiguous"
                        )
                    }
                    working[idx] = entry.replaceFirst(oldText, content).trim()
                }

                "remove" -> {
                    if (oldText.isEmpty()) return err("'remove' butuh 'old_text'")
                    val idx = indexOfEntryContaining(working, oldText)
                        ?: return err(
                            "tidak ada entri yang memuat old_text" +
                                if (oldText.contains('§')) " (old_text harus berasal dari SATU entri)" else "",
                            "no_match"
                        )
                    working.removeAt(idx)
                }

                else -> return err("action tidak dikenal: '$action' (add|patch|remove)")
            }
        }

        // Bersihkan entri kosong hasil patch yang menghapus seluruh isi.
        working.removeAll { it.isBlank() }

        // Limit dicek pada hasil AKHIR batch (bukan per operasi).
        val finalCount = working.joinToString(ENTRY_DELIMITER).length
        if (finalCount > limit) {
            val over = finalCount - limit
            return err(
                "Di atas budget memori: kelebihan $over karakter (${fmt(finalCount)}/${fmt(limit)}). " +
                    "Konsolidasikan dalam SATU batch: hapus/pangkas entri basi DAN tambahkan yang baru bersama-sama.",
                "over_budget",
                currentEntries = working,
                usage = "${fmt(finalCount)}/${fmt(limit)}"
            )
        }

        // Commit: state live + disk. Snapshot prompt TIDAK diubah (prefix-cache stabil).
        if (target == "user") userEntries = working else memoryEntries = working
        persist(target)

        return buildJsonObject {
            put("success", true)
            put("done", true)
            put("target", target)
            put("usage", usagePct(target, charCount(target)))
            put("entry_count", entriesFor(target).size)
            if (notes.isNotEmpty()) {
                put("message", notes.joinToString("; "))
            }
            put("note", "Write saved. This update is complete — do not repeat it.")
        }
    }

    private fun indexOfEntryContaining(entries: List<String>, needle: String): Int? {
        val exact = entries.indexOfFirst { it == needle }
        if (exact >= 0) return exact
        return entries.indexOfFirst { it.contains(needle) }.takeIf { it >= 0 }
    }

    private fun countOccurrences(haystack: String, needle: String): Int {
        if (needle.isEmpty()) return 0
        var count = 0
        var from = 0
        while (true) {
            val at = haystack.indexOf(needle, from)
            if (at < 0) break
            count++
            from = at + needle.length
        }
        return count
    }

    private fun overBudgetOrMissing(
        entries: List<String>, target: String, oldText: String, action: String
    ): JsonObject = err(
        "'$action': old_text tidak ditemukan di entri mana pun" +
            if (oldText.contains('§')) " (old_text harus berasal dari SATU entri — '§' memisahkan entri)" else "",
        "no_match",
        currentEntries = entries,
        usage = "${fmt(entries.joinToString(ENTRY_DELIMITER).length)}/${fmt(charLimit(target))}"
    )

    private fun err(
        message: String,
        errorClass: String? = null,
        currentEntries: List<String>? = null,
        usage: String? = null
    ): JsonObject = buildJsonObject {
        put("success", false)
        put("error", message)
        errorClass?.let { put("error_class", it) }
        currentEntries?.let { entries ->
            put("current_entries", buildJsonArray { entries.forEach { add(it) } })
        }
        usage?.let { put("usage", it) }
    }

    /** Akses baca untuk UI/laporan (bukan untuk prompt). */
    fun liveEntries(target: String): List<String> = entriesFor(target).toList()

    fun usageLine(target: String): String = usagePct(target, charCount(target))

    fun targetOn(target: String): Boolean = targetEnabled(target)

    /** Dump JSON untuk laporan debug. */
    fun debugJson(): JsonObject = buildJsonObject {
        put("memory", buildJsonObject {
            put("entries", memoryEntries.size)
            put("chars", charCount("memory"))
            put("limit", memoryCharLimit)
        })
        put("user", buildJsonObject {
            put("entries", userEntries.size)
            put("chars", charCount("user"))
            put("limit", userCharLimit)
        })
    }

    companion object {
        const val ENTRY_DELIMITER = "\n§\n"
        const val DEFAULT_MEMORY_CHAR_LIMIT = 2200
        const val DEFAULT_USER_CHAR_LIMIT = 1375

        val BLOCK_HEADERS = mapOf(
            "memory" to "MEMORY (your personal notes)",
            "user" to "USER PROFILE (who the user is)"
        )

        /**
         * Pecah isi berkas menjadi entri. Memakai delimiter PENUH ("\n§\n") agar
         * "§" tunggal di dalam teks entri tetap utuh (perilaku sumber).
         */
        fun parseEntries(raw: String): List<String> {
            if (raw.isBlank()) return emptyList()
            return raw.removePrefix("\uFEFF")
                .split(ENTRY_DELIMITER)
                .map { it.trim() }
                .filter { it.isNotBlank() }
        }
    }
}
