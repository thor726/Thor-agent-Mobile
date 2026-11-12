package com.thoragent.app.core.agent

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Pustaka kecerdasan: seluruh skill Thor-agent (skills/**/SKILL.md dari repo
 * https://github.com/dwisetyabudi15581/Thor-agent.git) dibundel sebagai aset APK
 * dan dimuat on-demand ke konteks agent — tanpa engine eksternal.
 *
 * v0.3.0 — LOOP PEMBELAJARAN TERTUTUP ala repo sumber (/learn, /skills):
 * agent dapat menulis SKILL.md baru ke direktori skill-pengguna
 * (filesDir/thor-skills/<nama>/SKILL.md) lewat tool write_file; pustaka
 * memindainya ulang dan skill itu langsung tersedia untuk tugas berikutnya.
 */
@Serializable
data class SkillEntry(
    val name: String,
    val description: String = "",
    val category: String = "",
    val origin: String = "",
    val path: String = "",
    val hint: String = ""
)

@Serializable
data class SkillIndex(
    val source: String,
    val generated: String,
    val count: Int = 0,
    val skills: List<SkillEntry> = emptyList()
)

class SkillLibrary(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    @Volatile private var cachedIndex: SkillIndex? = null
    @Volatile private var cachedSoul: String? = null

    /** Direktori skill buatan pengguna (hasil /learn — agent menulis via write_file). */
    val userSkillsDir: File
        get() = File(context.filesDir, "thor-skills")

    /** Indeks skill gabungan: aset repo + skill pengguna. */
    suspend fun index(): SkillIndex = withContext(Dispatchers.IO) {
        cachedIndex ?: run {
            val bundled = runCatching {
                context.assets.open("skills/skills_index.json").use {
                    json.decodeFromString(SkillIndex.serializer(), it.bufferedReader().readText())
                }
            }.getOrNull() ?: SkillIndex(source = "", generated = "")
            val merged = SkillIndex(
                source = bundled.source,
                generated = bundled.generated,
                count = bundled.skills.size + userSkills().size,
                skills = bundled.skills + userSkills()
            )
            merged.also { cachedIndex = it }
        }
    }

    /** Pindai direktori skill pengguna → entri indeks. */
    private fun userSkills(): List<SkillEntry> {
        val dir = userSkillsDir
        if (!dir.isDirectory) return emptyList()
        return dir.listFiles { f -> f.isDirectory }
            ?.mapNotNull { cat ->
                val skill = File(cat, "SKILL.md")
                if (!skill.isFile) return@mapNotNull null
                val meta = parseFrontMatter(skill.readText())
                SkillEntry(
                    name = cat.name,
                    description = meta.second,
                    category = "pelajari (${cat.parentFile?.name ?: "user"})",
                    origin = "user",
                    path = skill.absolutePath
                )
            }
            ?: emptyList()
    }

    /** Ambil (judul, deskripsi) dari header markdown skill pengguna. */
    private fun parseFrontMatter(text: String): Pair<String, String> {
        val lines = text.lines()
        val h1 = lines.firstOrNull { it.startsWith("# ") }?.removePrefix("# ")?.trim() ?: ""
        val desc = lines.firstOrNull { it.isNotBlank() && !it.startsWith("#") }?.trim() ?: ""
        return h1 to desc
    }

    /** SOUL.md repo — identitas & gaya komunikasi agent.
     *  Fallback aman: bila aset tak terbaca (unit test JVM / aset korup),
     *  identitas dasar tetap dipakai agar system prompt tidak gagal total. */
    suspend fun soul(): String = withContext(Dispatchers.IO) {
        cachedSoul ?: runCatching {
            context.assets.open("skills/SOUL.md").use {
                it.bufferedReader().readText()
            }
        }.getOrDefault("").also { cachedSoul = it }
    }

    /** Muat isi SKILL.md lengkap untuk satu skill (aset repo ATAU file pengguna). */
    suspend fun load(path: String): String = withContext(Dispatchers.IO) {
        if (path.startsWith("/")) {
            // path absolut = skill pengguna
            runCatching { File(path).readText() }.getOrDefault("")
        } else {
            runCatching {
                context.assets.open(path).use { it.bufferedReader().readText() }
            }.getOrDefault("")
        }
    }

    /** Cari skill berdasarkan kueri (nama/deskripsi/kategori). */
    suspend fun search(query: String): List<SkillEntry> {
        val q = query.trim().lowercase()
        val all = index().skills
        if (q.isBlank()) return all
        return all.filter {
            it.name.lowercase().contains(q) ||
                it.description.lowercase().contains(q) ||
                it.category.lowercase().contains(q)
        }
    }

    /** Paksa pindai ulang skill pengguna (dipanggil setelah /learn menulis skill baru). */
    fun invalidateCache() {
        cachedIndex = null
    }

    /** Ringkasan katalog untuk system prompt: per kategori, nama + deskripsi satu baris. */
    suspend fun catalogSummary(): String {
        val idx = index()
        return idx.skills
            .groupBy { it.category }
            .toSortedMap()
            .map { (cat, list) ->
                val lines = list.joinToString("\n") { s ->
                    "- ${s.name}: ${s.description.take(160)}"
                }
                "## $cat (${list.size} skill)\n$lines"
            }
            .joinToString("\n\n")
            .take(18_000)
    }
}
