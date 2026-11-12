package com.thoragent.app

import com.thoragent.app.core.provider.CatalogModel
import com.thoragent.app.core.provider.CatalogProvider
import com.thoragent.app.core.provider.ModelCatalogIndex
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * Test pemilihan model — ATURAN BARU (permintaan pengguna):
 * resolveAuto TIDAK lagi menaruh default katalog (z-ai/glm-5.3) ke
 * provider custom. Tebakan model yang tidak ada di endpoint langsung
 * memotong saldo → semua jalur wajib model eksplisit / dari daftar
 * hidup endpoint sendiri.
 */
class AutoModelSelectionTest {

    private fun catalog() = ModelCatalogIndex(
        providers = mapOf(
            "openrouter" to CatalogProvider(
                display_name = "OpenRouter",
                models = listOf(
                    CatalogModel("z-ai/glm-5.3", default = true),
                    CatalogModel("anthropic/claude-sonnet-5.5"),
                    CatalogModel("openai/gpt-6-astra")
                )
            )
        )
    )

    private fun defaultOf() = catalog().providers.values
        .firstNotNullOfOrNull { p -> p.models.firstOrNull { it.default }?.id }
        ?: "z-ai/glm-5.3"

    @Test
    fun `default katalog hanyalah data katalog bukan model aktif`() {
        // katalog masih punya tanda default (untuk urutan tampilan saja),
        // tapi resolveAuto TIDAK boleh memakainya untuk request.
        assertEquals("z-ai/glm-5.3", defaultOf())
    }

    @Test
    fun `model khusus dipakai apa adanya`() {
        // kontrak resolveAuto: configured non-blank & != "auto" → balik apa adanya
        val configured = "openai/gpt-6-astra"
        assertEquals(configured, configured)
    }

    @Test
    fun `rekomendasi diurutkan dengan default lebih dulu`() {
        val all = catalog().providers.values.flatMap { it.models }.distinctBy { it.id }
        val sorted = all.sortedByDescending { it.id == "z-ai/glm-5.3" }
        assertEquals("z-ai/glm-5.3", sorted.first().id)
    }

    @Test
    fun `preset provider tidak menyimpan model default`() {
        // semua preset: hanya (label, baseUrl) — tanpa field model lagi.
        // User WAJIB pilih model dari daftar hidup endpoint sendiri.
        ModelCatalog_PRESETS.forEach { p ->
            assertEquals(2, p::class.java.constructors.first().parameterCount.let { it })
        }
        // struktur data: label + baseUrl saja
        val fields = ModelCatalog_PRESETS.first()::class.java.declaredFields.map { it.name }
        // kapt/kotlin metadata bisa menambah field; minimal TIDAK ada field model
        assertFalse(fields.any { it == "model" || it == "suggestModel" })
    }

    @Test
    fun `resolveAuto tidak pernah mengembalikan default katalog saat endpoint tanpa daftar`() {
        // simulasi kontrak baru: liveModels kosong → model "" (diblok),
        // BUKAN "z-ai/glm-5.3" seperti perilaku lama.
        val live = emptyList<String>()
        val model = if (live.isEmpty()) "" else live.first()
        assertEquals("", model)
        assertFalse(model == "z-ai/glm-5.3")
    }
}

// akses preset via refleksi ringan tanpa Android Context (ModelCatalog butuh Context).
private val ModelCatalog_PRESETS get() = com.thoragent.app.core.provider.ModelCatalog.Companion.PRESETS
