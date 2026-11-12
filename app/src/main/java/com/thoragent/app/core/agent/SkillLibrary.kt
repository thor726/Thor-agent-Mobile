package com.thoragent.app.core.agent

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

/** Entri index skill (padanan `skills_index.json` milik build Thor). */
@Serializable
data class SkillEntry(
    val name: String,
    val description: String = "",
    val category: String = "",
    val path: String = ""
)

@Serializable
data class SkillIndex(
    val source: String = "",
    val generated: String = "",
    val count: Int = 0,
    val skills: List<SkillEntry> = emptyList()
)

/**
 * Skills library — PORT `tools/skills_tool.py` + `tools/skill_manager_tool.py`.
 *
 * Layout on-disk sama dengan Thor desktop:
 *   skills/<category>/<name>/SKILL.md      (frontmatter YAML + body markdown)
 *   skills/<category>/<name>/references/   (berkas pendukung, dibaca skill_view)
 *   skills/<category>/<name>/scripts|templates|assets/
 *   skills/SOUL.md                          (identitas)
 *
 * Berkas bawaan hidup di APK (`assets/skills`, read-only). Saat skill diubah
 * (skill_manage) seluruh pohon disalin ke `filesDir/skills` (editable) dan
 * salinan itu yang dipakai — sama seperti Thor desktop yang menulis ke
 * `~/skills` di samping skill bawaan repo.
 */
class SkillLibrary(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    /** Direktori editable; dibuat lazy saat pertama kali ada tulisan. */
    private val editableRoot: File by lazy {
        File(context.filesDir, "skills").apply { mkdirs() }
    }

    private var bootstrapped = false
    private var cachedIndex: SkillIndex? = null

    // ─────────────────────────── lokasi & bootstrap ───────────────────────────

    private fun bootstrap() {
        if (bootstrapped) return
        synchronized(this) {
            if (bootstrapped) return
            val marker = File(editableRoot, ".bootstrapped")
            if (!marker.exists()) {
                copyAssetTree("skills", editableRoot)
                marker.writeText("ok")
            }
            bootstrapped = true
        }
    }

    private fun copyAssetTree(assetPath: String, target: File) {
        target.mkdirs()
        val children = context.assets.list(assetPath) ?: return
        if (children.isEmpty()) {
            // Berkas, bukan direktori.
            runCatching {
                context.assets.open(assetPath).use { input ->
                    File(target.parentFile, target.name).outputStream().use { out -> input.copyTo(out) }
                }
            }
            return
        }
        for (child in children) copyAssetTree("$assetPath/$child", File(target, child))
    }

    /** Root skill yang aktif: editable bila sudah dibootstrap, selain itu assets. */
    private fun activeRoot(): File {
        bootstrap()
        return editableRoot
    }

    // ─────────────────────────── index ───────────────────────────

    /**
     * Index skill — port `_build_index`/`skills_index.json`. Kalau aset index
     * tersedia dipakai lebih dulu (cepat, sudah terurut); kalau tidak, direktori
     * dipindai. Hasil di-cache sampai [invalidate].
     */
    fun indexSync(): SkillIndex {
        cachedIndex?.let { return it }
        val fromAsset = runCatching {
            context.assets.open("skills/skills_index.json").bufferedReader().use { it.readText() }
        }.getOrNull()?.let { runCatching { json.decodeFromString<SkillIndex>(it) }.getOrNull() }
        val scanned = scanSkills()
        val merged = if (fromAsset != null && fromAsset.skills.isNotEmpty()) {
            // Aset index = snapshot build; skill hasil edit user menang.
            val scannedByName = scanned.skills.associateBy { it.name }
            val assetNames = fromAsset.skills.map { it.name }.toSet()
            fromAsset.copy(skills = fromAsset.skills.map { scannedByName[it.name] ?: it } + scanned.skills.filter { it.name !in assetNames })
        } else {
            scanned
        }
        cachedIndex = merged
        return merged
    }

    suspend fun index(): SkillIndex = withContext(Dispatchers.IO) { indexSync() }

    fun invalidate() {
        cachedIndex = null
    }

    /** Alias kompatibilitas UI lama (dulu `invalidateCache`). */
    fun invalidateCache() = invalidate()

    /**
     * Direktori skill yang bisa ditulis user — padanan `~/skills` Thor desktop.
     * Dipakai UI impor/ekspor skill (zip berisi direktori skill).
     */
    val userSkillsDir: File get() = activeRoot()

    /** Pindai disk — port `_scan_skill_dirs`. */
    private fun scanSkills(): SkillIndex {
        val root = activeRoot()
        val entries = mutableListOf<SkillEntry>()
        val categories = root.listFiles()?.filter { it.isDirectory } ?: emptyList()
        for (category in categories) {
            val dirs = category.listFiles()?.filter { it.isDirectory } ?: continue
            for (dir in dirs) {
                val skillMd = File(dir, "SKILL.md")
                if (!skillMd.exists()) continue
                val meta = runCatching { parseFrontmatter(skillMd.readText()) }.getOrNull() ?: continue
                val name = (meta["name"] ?: dir.name).trim()
                entries.add(
                    SkillEntry(
                        name = name,
                        description = (meta["description"] ?: "").trim().removeSurrounding("\""),
                        category = category.name,
                        path = "${category.name}/$name"
                    )
                )
            }
        }
        return SkillIndex(source = root.absolutePath, count = entries.size, skills = entries.sortedBy { it.name })
    }

    // ─────────────────────────── SOUL.md ───────────────────────────

    /**
     * Identitas — port pembacaan SOUL.md di `system_prompt.py`. Urutan: SOUL.md
     * editable → SOUL.md aset → string kosong (pemanggil jatuh ke
     * DEFAULT_AGENT_IDENTITY).
     */
    fun soulSync(): String {
        val editable = File(editableRoot, "SOUL.md")
        if (editable.exists()) return editable.readText()
        return runCatching {
            context.assets.open("skills/SOUL.md").bufferedReader().use { it.readText() }
        }.getOrDefault("")
    }

    // ─────────────────────────── skill_view ───────────────────────────

    /**
     * Baca satu skill — port `skills_tool.skill_view`.
     *
     * Mengembalikan SKILL.md (dengan frontmatter dibuang seperti sumber) plus
     * daftar berkas pendukung. [filePath] membaca berkas pendukung tertentu
     * (`references/foo.md`), dengan proteksi path-traversal.
     */
    fun view(name: String, filePath: String? = null): SkillViewResult {
        val entry = indexSync().skills.firstOrNull { it.name == name }
            ?: return SkillViewResult.NotFound(name)
        val dir = File(activeRoot(), entry.path)
        if (filePath != null) {
            val safe = filePath.trim().removePrefix("/")
            if (safe.split("/").any { it == ".." }) return SkillViewResult.Error("path tidak valid: $filePath")
            val target = File(dir, safe)
            if (!target.exists() || !target.canonicalPath.startsWith(dir.canonicalPath)) {
                return SkillViewResult.Error("berkas pendukung tidak ditemukan: $filePath")
            }
            return SkillViewResult.File(
                name = entry.name,
                filePath = safe,
                content = readMaybeBinary(target)
            )
        }
        val skillMd = File(dir, "SKILL.md")
        if (!skillMd.exists()) return SkillViewResult.NotFound(name)
        val raw = skillMd.readText()
        val body = stripFrontmatter(raw)
        val linked = linkedFiles(dir)
        return SkillViewResult.Skill(
            name = entry.name,
            description = entry.description,
            category = entry.category,
            content = body.trim(),
            linkedFiles = linked
        )
    }

    /** Berkas pendukung — port `_linked_files`. */
    private fun linkedFiles(dir: File): List<String> {
        val out = mutableListOf<String>()
        fun walk(current: File, prefix: String) {
            for (f in current.listFiles()?.sortedBy { it.name } ?: emptyList()) {
                if (f.name == "SKILL.md" || f.name.startsWith(".")) continue
                val rel = if (prefix.isEmpty()) f.name else "$prefix/${f.name}"
                if (f.isDirectory) walk(f, rel) else out.add(rel)
            }
        }
        walk(dir, "")
        return out
    }

    private fun readMaybeBinary(file: File): String {
        val bytes = file.readBytes()
        if (bytes.any { it == 0.toByte() }) return "<binary: ${bytes.size} bytes>"
        return bytes.toString(Charsets.UTF_8)
    }

    // ─────────────────────────── skill_manage ───────────────────────────

    /**
     * Tulis/ubah skill — port `skills_tool.skill_manage`.
     *
     * action: create | patch | write_file | remove_file | delete | edit.
     * Semua operasi memaksa [activeRoot] editable (asset tidak bisa ditulis) dan
     * meng-invalidate index.
     */
    fun manage(
        action: String,
        name: String,
        content: String? = null,
        filePath: String? = null,
        oldString: String? = null,
        newString: String? = null,
        category: String? = null,
        description: String? = null,
        replaceAll: Boolean = false
    ): String {
        val root = activeRoot()
        cacheDirIfNeeded()
        return try {
            when (action.lowercase()) {
                "create" -> {
                    val cat = (category ?: "general").trim().ifBlank { "general" }
                    val dir = File(root, "$cat/$name")
                    if (File(dir, "SKILL.md").exists()) return "Skill '$name' sudah ada di kategori '$cat'."
                    dir.mkdirs()
                    val front = buildString {
                        append("---\n")
                        append("name: $name\n")
                        append("description: \"${(description ?: name).replace("\"", "'")}\"\n")
                        append("---\n\n")
                    }
                    val body = content ?: "# $name\n\nTODO: tulis isi skill."
                    File(dir, "SKILL.md").writeText(front + body)
                    invalidate()
                    "Skill '$name' dibuat di $cat/$name."
                }
                "patch" -> {
                    val target = filePath?.takeIf { it.isNotBlank() }
                        ?.let { File(requireSkillDir(root, name), it.trim().removePrefix("/")) }
                        ?: File(requireSkillDir(root, name), "SKILL.md")
                    if (!target.exists()) return "Berkas tidak ditemukan: ${target.name}"
                    if (oldString.isNullOrEmpty()) return "patch butuh old_string."
                    if (newString == null) return "patch butuh new_string (boleh string kosong untuk menghapus)."
                    val text = target.readText()
                    val occurrences = countOccurrences(text, oldString)
                    when {
                        occurrences == 0 -> return "old_string tidak ditemukan di ${target.name} — tidak ada yang diubah."
                        occurrences > 1 && !replaceAll ->
                            return "old_string muncul $occurrences kali — sertakan lebih banyak konteks atau pakai replace_all=true."
                    }
                    target.writeText(if (replaceAll) text.replace(oldString, newString) else text.replaceFirst(oldString, newString))
                    invalidate()
                    "patch diterapkan ke ${target.name} ($occurrences kecocokan)."
                }
                "write_file" -> {
                    if (filePath.isNullOrBlank()) return "write_file butuh file_path."
                    val dir = requireSkillDir(root, name)
                    val safe = filePath.trim().removePrefix("/")
                    if (safe.split("/").any { it == ".." }) return "path tidak valid: $filePath"
                    val target = File(dir, safe)
                    target.parentFile?.mkdirs()
                    target.writeText(content ?: "")
                    invalidate()
                    "Berkas $safe ditulis di skill '$name'."
                }
                "remove_file" -> {
                    if (filePath.isNullOrBlank()) return "remove_file butuh file_path."
                    val dir = requireSkillDir(root, name)
                    val target = File(dir, filePath.trim().removePrefix("/"))
                    if (!target.exists()) return "Berkas tidak ditemukan: $filePath"
                    val ok = target.delete()
                    invalidate()
                    if (ok) "Berkas $filePath dihapus dari skill '$name'." else "Gagal menghapus $filePath."
                }
                "delete" -> {
                    val dir = requireSkillDir(root, name)
                    val existed = dir.exists()
                    dir.deleteRecursively()
                    invalidate()
                    if (existed) "Skill '$name' dihapus." else "Skill '$name' tidak ditemukan."
                }
                "edit" -> {
                    if (content == null) return "edit butuh content (isi SKILL.md baru, tanpa frontmatter)."
                    val dir = requireSkillDir(root, name)
                    val skillMd = File(dir, "SKILL.md")
                    val front = if (skillMd.exists()) frontmatterBlock(skillMd.readText()) else ""
                    skillMd.writeText(front + content)
                    invalidate()
                    "SKILL.md skill '$name' diperbarui."
                }
                else -> "action tidak dikenal: $action (pakai create|patch|write_file|remove_file|delete|edit)."
            }
        } catch (e: Exception) {
            "Gagal: ${e.message}"
        }
    }

    private fun cacheDirIfNeeded() { /* index cache tidak perlu dibuang saat menulis; invalidate() dipanggil per-op */ }

    private fun requireSkillDir(root: File, name: String): File {
        val entry = indexSync().skills.firstOrNull { it.name == name }
        return if (entry != null) File(root, entry.path) else File(root, "general/$name")
    }

    private fun countOccurrences(text: String, needle: String): Int {
        if (needle.isEmpty()) return 0
        var count = 0
        var idx = text.indexOf(needle)
        while (idx >= 0) {
            count++
            idx = text.indexOf(needle, idx + needle.length)
        }
        return count
    }

    // ─────────────────────────── frontmatter ───────────────────────────

    /** Ambil blok `---...---` apa adanya (termasuk pembatas). */
    private fun frontmatterBlock(text: String): String {
        if (!text.startsWith("---")) return ""
        val end = text.indexOf("\n---", startIndex = 3)
        if (end < 0) return ""
        val closeEnd = text.indexOf('\n', end + 1)
        return if (closeEnd < 0) text else text.substring(0, closeEnd + 1)
    }

    /** Buang frontmatter — port `_strip_frontmatter`. */
    fun stripFrontmatter(text: String): String {
        if (!text.startsWith("---")) return text
        val end = text.indexOf("\n---", startIndex = 3)
        if (end < 0) return text
        return text.substring(end + 4).trimStart('\n')
    }

    /**
     * Parse frontmatter minimal (key: value satu baris) — cukup untuk
     * name/description/version/license. Blok bertingkat (metadata:) dilewati,
     * sama seperti index build Thor yang hanya butuh dua field itu.
     */
    private fun parseFrontmatter(text: String): Map<String, String>? {
        if (!text.startsWith("---")) return null
        val end = text.indexOf("\n---", startIndex = 3)
        if (end < 0) return null
        val block = text.substring(3, end)
        val out = mutableMapOf<String, String>()
        var nestedIndent = -1
        for (line in block.lines()) {
            if (line.isBlank()) continue
            val indent = line.length - line.trimStart().length
            if (nestedIndent >= 0 && indent > nestedIndent) continue
            nestedIndent = -1
            val colon = line.indexOf(':')
            if (colon <= 0) continue
            val key = line.substring(0, colon).trim()
            val value = line.substring(colon + 1).trim()
            if (value.isEmpty()) {
                nestedIndent = indent
                continue
            }
            out[key] = value
        }
        return out
    }
}

/** Hasil `skill_view` — dipetakan langsung ke bentuk tool result Thor. */
sealed class SkillViewResult {
    data class Skill(
        val name: String,
        val description: String,
        val category: String,
        val content: String,
        val linkedFiles: List<String>
    ) : SkillViewResult()

    data class File(val name: String, val filePath: String, val content: String) : SkillViewResult()
    data class NotFound(val name: String) : SkillViewResult()
    data class Error(val message: String) : SkillViewResult()
}
