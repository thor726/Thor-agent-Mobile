package com.thoragent.app

import com.thoragent.app.core.agent.STATIC_PROMPT_SECTIONS
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Test system prompt sinkron repo sumber (v0.5.0) — guidance
 * TASK_COMPLETION_GUIDANCE / PARALLEL_TOOL_CALL_GUIDANCE /
 * build_memory_guidance di-bundle ke prompt agent mobile.
 *
 * Bagian statis prompt diuji lewat STATIC_PROMPT_SECTIONS (teks yang
 * sama persis dengan yang disusun SystemPromptBuilder.build), sehingga
 * test JVM murni tidak perlu Context Android/aset. Uji e2e prompt penuh
 * (dengan aset) dijalankan device/CI.
 */
class SystemPromptGuidanceTest {

    private val prompt = STATIC_PROMPT_SECTIONS.joinToString("\n")

    @Test
    fun `prompt memuat guidance penyelesaian tugas repo sumber`() {
        // port TASK_COMPLETION_GUIDANCE
        assertTrue(prompt.contains("Menyelesaikan pekerjaan"))
        assertTrue(prompt.contains("artefak nyata"))
        // port PARALLEL_TOOL_CALL_GUIDANCE
        assertTrue(prompt.contains("Panggilan tool paralel"))
        assertTrue(prompt.contains("SEKALIGUS"))
        // port build_memory_guidance (skills-first)
        assertTrue(prompt.contains("Skill dulu"))
        assertTrue(prompt.contains("fakta deklaratif"))
    }

    @Test
    fun `identitas menyebut thor agent mobile natif dan akses root`() {
        assertTrue(prompt.contains("Thor Agent Mobile"))
        assertTrue(prompt.contains("NATIF"))
        // dua jalur akses root sama seperti build()
        val rootYes = STATIC_PROMPT_SECTIONS.joinToString("\n") { it.trimIndent() }
        assertTrue(rootYes.contains("Perintah destruktif"))
    }

    @Test
    fun `etika eksekusi tetap hadir`() {
        assertTrue(prompt.contains("rm -rf"))
        assertTrue(prompt.contains("Bahasa Indonesia"))
    }
}
