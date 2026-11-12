package com.thoragent.app

import com.thoragent.app.core.provider.CatalogModel
import com.thoragent.app.core.provider.CatalogProvider
import com.thoragent.app.core.provider.ModelCatalogIndex
import com.thoragent.app.core.provider.ProviderChoice
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Test pemilihan model per penyedia layanan (v0.5.0) — meniru
 * /model --provider repo sumber: daftar penyedia katalog + preset
 * OpenAI-compatible + endpoint aktif.
 */
class ProviderChoiceTest {

    private val catalog = ModelCatalogIndex(
        providers = mapOf(
            "openrouter" to CatalogProvider(
                display_name = "OpenRouter",
                models = listOf(
                    CatalogModel("z-ai/glm-5.3", default = true),
                    CatalogModel("anthropic/claude-sonnet-5.5")
                )
            ),
            "thor" to CatalogProvider(
                display_name = "Thor Portal",
                models = listOf(CatalogModel("anthropic/claude-opus-5.5"))
            )
        )
    )

    @Test
    fun `setiap penyedia katalog punya label dan model sendiri`() {
        val choices = catalog.providers.map { (key, p) ->
            ProviderChoice(
                key = key,
                label = p.display_name.ifBlank { key },
                baseUrl = null,
                models = p.models.sortedByDescending { it.default }
            )
        }
        assertEquals(2, choices.size)
        assertEquals("OpenRouter", choices[0].label)
        assertEquals("Thor Portal", choices[1].label)
        // model default penyedia selalu di urutan pertama
        assertEquals("z-ai/glm-5.3", choices[0].models.first().id)
    }

    @Test
    fun `model penyedia berbeda tidak tercampur`() {
        val openrouter = catalog.providers.getValue("openrouter").models.map { it.id }
        val thor = catalog.providers.getValue("thor").models.map { it.id }
        assertTrue(openrouter.none { it in thor })
        assertEquals(listOf("z-ai/glm-5.3", "anthropic/claude-sonnet-5.5"), openrouter)
    }

    @Test
    fun `preset penyedia tanpa katalog tetap masuk daftar pilihan`() {
        val PRESET_LABELS = listOf(
            "OpenRouter", "OpenAI", "Groq", "Z.ai GLM",
            "Lokal (LM Studio)", "Lokal (Ollama)"
        )
        val catalogLabels = catalog.providers.values.map { it.display_name }
        // entri preset ditambahkan bila belum terwakili katalog
        val merged = catalogLabels + PRESET_LABELS.filter { l ->
            catalogLabels.none { c -> c.contains(l, true) || l.contains(c, true) }
        }.map { it }
        assertTrue(merged.any { it.contains("OpenAI") })
        assertTrue(merged.any { it.contains("Groq") })
        assertTrue(merged.any { it.contains("LM Studio") })
        // OpenRouter sudah ada → tidak dobel
        assertEquals(1, merged.count { it == "OpenRouter" })
    }
}
