package com.thoragent.app.core.provider

import android.content.Context
import com.thoragent.app.core.llm.LlmClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Katalog model — PORT dari repo sumber Thor-agent:
 * website/static/api/model-catalog.json (75 model OpenRouter + 59 Thor,
 * default z-ai/glm-5.3). Inilah sumber "pemilihan model otomatis":
 * entri bertanda default = model yang didaratkan Thor diam-diam saat
 * pengguna belum pernah memilih (persis perilaku /model di repo sumber).
 */
@Serializable
data class CatalogModel(
    val id: String,
    val description: String = "",
    val default: Boolean = false
)

@Serializable
data class CatalogProvider(
    val display_name: String = "",
    val note: String = "",
    val models: List<CatalogModel> = emptyList()
)

@Serializable
data class ModelCatalogIndex(
    val source: String = "",
    val version: Int = 0,
    val updated_at: String = "",
    val providers: Map<String, CatalogProvider> = emptyMap()
)

/** Hasil resolusi model otomatis. */
data class AutoModelResult(
    val model: String,
    val reason: String
)

/** Entri penyedia layanan untuk pemilih — katalog repo + preset lokal. */
data class ProviderChoice(
    val key: String,            // id unik (mis. "openrouter")
    val label: String,          // tampilan ("OpenRouter")
    val baseUrl: String?,       // null = custom/endpoint milik profil
    val models: List<CatalogModel>,
    val isLocal: Boolean = false
)

class ModelCatalog(
    private val context: Context,
    private val ok: OkHttpClient
) {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    @Volatile private var cached: ModelCatalogIndex? = null

    suspend fun index(): ModelCatalogIndex = withContext(Dispatchers.IO) {
        cached ?: runCatching {
            context.assets.open("catalog/model_catalog.json").use {
                json.decodeFromString(ModelCatalogIndex.serializer(), it.bufferedReader().readText())
            }
        }.getOrNull()?.also { cached = it } ?: ModelCatalogIndex()
    }

    /** Model default katalog repo sumber (z-ai/glm-5.3). */
    suspend fun catalogDefault(): String =
        index().providers.values.firstNotNullOfOrNull { p ->
            p.models.firstOrNull { it.default }?.id
        } ?: "z-ai/glm-5.3"

    /** Daftar model rekomendasi katalog (urut: default dulu). */
    suspend fun recommended(limit: Int = 30): List<CatalogModel> {
        val def = catalogDefault()
        val all = index().providers.values.flatMap { it.models }
            .distinctBy { it.id }
        return all.sortedByDescending { it.id == def }.take(limit)
    }

    /**
     * DAFTAR PENYEDIA LAYANAN untuk pemilih model kustom (v0.5.0):
     * katalog repo sumber (OpenRouter/Thor Portal) + preset penyedia
     * OpenAI-compatible populer + server lokal — meniru /model --provider
     * repo sumber. Model tiap penyedia diambil dari katalog repo; penyedia
     * preset memakai model hidup saat di-fetch.
     */
    suspend fun providerChoices(): List<ProviderChoice> {
        val idx = index()
        val choices = idx.providers.map { (key, p) ->
            ProviderChoice(
                key = key,
                label = p.display_name.ifBlank { key.replaceFirstChar { it.uppercase() } },
                baseUrl = PRESETS.firstOrNull { it.label.contains(key, true) }?.baseUrl,
                models = p.models.sortedByDescending { it.default }
            )
        }.toMutableList()
        // penyedia preset yang tidak ada di katalog → entri tanpa model
        PRESETS.forEach { preset ->
            val known = choices.any { preset.label.contains(it.label, true) || it.label.contains(preset.label, true) }
            if (!known) {
                choices.add(
                    ProviderChoice(
                        key = preset.label.lowercase().replace(Regex("[^a-z0-9]+"), "_"),
                        label = preset.label,
                        baseUrl = preset.baseUrl,
                        models = emptyList(),
                        isLocal = preset.label.startsWith("Lokal")
                    )
                )
            }
        }
        // entri "endpoint ini (profil aktif)" selalu ada di depan
        choices.add(0, ProviderChoice(key = "__live", label = "Endpoint ini", baseUrl = null, models = emptyList()))
        return choices
    }

    /**
     * RESOLUSI MODEL — TANPA default otomatis (permintaan pengguna:
     * pemilihan model yang salah langsung memotong saldo provider custom).
     *
     * Aturan:
     * 1. model khusus (bukan "auto") → pakai apa adanya.
     * 2. "auto"/kosong → TIDAK ADA tebakan default katalog lagi. Model harus
     *    dipilih eksplisit; bila daftar hidup provider tersedia kita
     *    menampilkan info agar UI memaksa pemilihan, tapi resolveAuto
     *    TIDAK pernah mengembalikan model yang bukan milik endpoint sendiri.
     */
    suspend fun resolveAuto(
        configuredModel: String,
        baseUrl: String,
        apiKey: String,
        liveModels: List<String>? = null
    ): AutoModelResult {
        if (configuredModel.isNotBlank() && configuredModel != MODEL_AUTO) {
            return AutoModelResult(configuredModel, "model khusus pengguna")
        }
        // "auto": cari model HIDUP dari endpoint sendiri — bukan default katalog.
        val live = liveModels ?: fetchLiveModels(baseUrl, apiKey)
        if (live.isEmpty()) {
            // endpoint tidak punya /models (atau gagal diambil) → jangan tebak;
            // kembalikan kosong supaya caller memblok request (hemat saldo).
            return AutoModelResult(
                "",
                "model belum dipilih — buka pilihan model (chip \"Model\") dan pilih dari daftar provider Anda"
            )
        }
        val first = live.firstOrNull { !it.contains("embed", true) && !it.contains("whisper", true) && !it.contains("tts", true) }
        return AutoModelResult(
            first ?: live.first(),
            "otomatis — model dari daftar hidup endpoint Anda (bukan default katalog)"
        )
    }

    /**
     * Ambil daftar model HIDUP dari endpoint /models provider
     * (OpenAI-compatible). Mirip picker TUI repo sumber yang menggabungkan
     * katalog terkurasi + daftar hidup provider.
     */
    suspend fun fetchLiveModels(baseUrl: String, apiKey: String): List<String> =
        withContext(Dispatchers.IO) {
            runCatching {
                val req = Request.Builder()
                    .url("${baseUrl.trimEnd('/')}/models")
                    .header("Authorization", "Bearer $apiKey")
                    .get()
                    .build()
                ok.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) return@use emptyList()
                    val body = resp.body?.string() ?: return@use emptyList()
                    json.parseToJsonElement(body).jsonObject["data"]?.jsonArray
                        ?.mapNotNull { el ->
                            (el.jsonObject["id"] as? kotlinx.serialization.json.JsonPrimitive)?.content
                        }?.sorted() ?: emptyList()
                }
            }.getOrDefault(emptyList())
        }

    companion object {
        /** Nilai khusus pada profil → pemilihan otomatis. */
        const val MODEL_AUTO = "auto"

        /** Preset provider cepat — TANPA model default (user wajib pilih
         *  dari daftar hidup endpoint sendiri agar tidak salah model =
         *  saldo terpotong). */
        data class ProviderPreset(val label: String, val baseUrl: String)

        val PRESETS = listOf(
            ProviderPreset("OpenRouter", "https://openrouter.ai/api/v1"),
            ProviderPreset("OpenAI", "https://api.openai.com/v1"),
            ProviderPreset("Groq", "https://api.groq.com/openai/v1"),
            ProviderPreset("Z.ai GLM", "https://api.z.ai/api/coding/paas/v4"),
            ProviderPreset("Lokal (LM Studio)", "http://localhost:1234/v1"),
            ProviderPreset("Lokal (Ollama)", "http://localhost:11434/v1")
        )
    }
}
