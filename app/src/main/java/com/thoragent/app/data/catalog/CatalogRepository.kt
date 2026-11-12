package com.thoragent.app.data.catalog

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Katalog skill & tool asli Thor-agent (aset APK, diambil dari repo). */
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
data class SkillIndex(val source: String, val generated: String, val count: Int = 0, val skills: List<SkillEntry> = emptyList())

@Serializable
data class ToolEntry(val name: String, val description: String = "", val origin: String = "")

@Serializable
data class ToolIndex(val source: String, val generated: String, val count: Int = 0, val note: String = "", val tools: List<ToolEntry> = emptyList())

class CatalogRepository(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true }

    private var skillsCache: SkillIndex? = null
    private var toolsCache: ToolIndex? = null
    private val docCache = mutableMapOf<String, String>()

    suspend fun skills(): SkillIndex = withContext(Dispatchers.IO) {
        skillsCache ?: run {
            val text = context.assets.open("skills/skills_index.json").bufferedReader().readText()
            json.decodeFromString(SkillIndex.serializer(), text).also { skillsCache = it }
        }
    }

    suspend fun tools(): ToolIndex = withContext(Dispatchers.IO) {
        toolsCache ?: run {
            val text = context.assets.open("catalog/tools_index.json").bufferedReader().readText()
            json.decodeFromString(ToolIndex.serializer(), text).also { toolsCache = it }
        }
    }

    /** Muat isi SKILL.md satu skill (untuk layar detail Skill Hub). */
    suspend fun skillDoc(path: String): String = withContext(Dispatchers.IO) {
        docCache[path] ?: run {
            val doc = runCatching {
                context.assets.open(path).use { it.bufferedReader().readText() }
            }.getOrDefault("")
            if (doc.isNotBlank()) docCache[path] = doc
            doc
        }
    }

    suspend fun skillCategories(): List<Pair<String, Int>> {
        val s = skills().skills
        return s.groupBy { it.category }.map { (k, v) -> k to v.size }.sortedByDescending { it.second }
    }

    fun searchSkills(all: List<SkillEntry>, query: String): List<SkillEntry> {
        if (query.isBlank()) return all
        val q = query.lowercase()
        return all.filter { it.name.lowercase().contains(q) || it.description.lowercase().contains(q) }
    }

    fun searchTools(all: List<ToolEntry>, query: String): List<ToolEntry> {
        if (query.isBlank()) return all
        val q = query.lowercase()
        return all.filter { it.name.lowercase().contains(q) || it.description.lowercase().contains(q) }
    }
}
