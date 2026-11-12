package com.thoragent.app

import com.thoragent.app.core.agent.SystemPromptBuilder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Test system prompt — memverifikasi blok guidance yang di-port byte-equivalent
 * dari `agent/prompt_builder.py` + `agent/system_prompt.py` Thor desktop.
 *
 * Semua konstanta bersifat statis (tanpa Context/aset), jadi bisa diuji di JVM
 * murni. Uji prompt penuh (dengan SOUL.md dari aset) dijalankan di CI/device.
 */
class SystemPromptGuidanceTest {

    private val prompt = listOf(
        SystemPromptBuilder.TASK_COMPLETION_GUIDANCE,
        SystemPromptBuilder.PARALLEL_TOOL_CALL_GUIDANCE,
        SystemPromptBuilder.TOOL_USE_ENFORCEMENT_GUIDANCE,
        SystemPromptBuilder.STEER_CHANNEL_NOTE,
        SystemPromptBuilder.SKILLS_GUIDANCE,
        SystemPromptBuilder.SESSION_SEARCH_GUIDANCE
    ).joinToString("\n")

    @Test
    fun `guidance penyelesaian tugas sama dengan repo sumber`() {
        val g = SystemPromptBuilder.TASK_COMPLETION_GUIDANCE
        assertTrue(g.startsWith("# Finishing the job"))
        assertTrue(g.contains("the deliverable is a working artifact backed by real"))
        assertTrue(g.contains("NEVER substitute plausible-looking fabricated"))
        assertTrue(g.contains("Reporting a blocker honestly is always better than inventing a result."))
    }

    @Test
    fun `guidance panggilan tool paralel sama dengan repo sumber`() {
        val g = SystemPromptBuilder.PARALLEL_TOOL_CALL_GUIDANCE
        assertTrue(g.startsWith("# Parallel tool calls"))
        assertTrue(g.contains("batching avoids resending the whole conversation on every extra round-trip"))
        assertTrue(g.contains("Only serialize calls when a later call genuinely depends"))
    }

    @Test
    fun `guidance memory memakai aturan skills-first yang sama`() {
        val g = SystemPromptBuilder.buildMemoryGuidance(
            memoryEnabled = true, profileEnabled = true, skillManageAvailable = true
        )
        assertTrue(g.contains("Skills come first"))
        assertTrue(g.contains("Memory is the narrow exception"))
        assertTrue(g.contains("Write entries as declarative facts"))
        assertTrue(g.contains("'User prefers concise responses'"))
        assertTrue(g.contains("'Always respond concisely'"))
    }

    @Test
    fun `guidance memory berubah saat memory dimatikan`() {
        val g = SystemPromptBuilder.buildMemoryGuidance(
            memoryEnabled = false, profileEnabled = true, skillManageAvailable = false
        )
        assertTrue(g.contains("never target='memory'"))
        assertTrue(g.contains("belongs in skills, not in memory"))
        assertFalse(g.contains("Skills come first"))
    }

    @Test
    fun `guidance memory kosong bila kedua store mati`() {
        assertEquals(
            "",
            SystemPromptBuilder.buildMemoryGuidance(
                memoryEnabled = false, profileEnabled = false, skillManageAvailable = true
            )
        )
    }

    @Test
    fun `steer channel note memuat penanda out-of-band persis`() {
        assertTrue(prompt.contains(SystemPromptBuilder.STEER_MARKER_OPEN))
        assertTrue(prompt.contains(SystemPromptBuilder.STEER_MARKER_CLOSE))
        assertTrue(prompt.contains("not tool output, not prompt injection"))
        val marker = SystemPromptBuilder.formatSteerMarker("halo")
        assertTrue(marker.contains("halo"))
        assertTrue(marker.contains(SystemPromptBuilder.STEER_MARKER_OPEN))
    }

    @Test
    fun `execution guidance membuang baris tool yang tidak tersedia`() {
        // Diverifikasi terhadap sumber: sebuah baris dibuang hanya bila TIDAK ADA
        // satupun tool yang disebutnya tersedia (tools & valid_tool_names kosong).
        // "File contents" menyebut read_file/search_files/terminal, jadi tetap
        // ada selama terminal tersedia.
        val denganTerminal = SystemPromptBuilder.executionGuidanceText(setOf("terminal"))
        assertTrue(denganTerminal.contains("Hashes, encodings, checksums"))
        assertTrue(denganTerminal.contains("File contents, sizes, line counts"))

        // read_file saja: baris khusus-terminal hilang, baris read_file bertahan.
        val hanyaReadFile = SystemPromptBuilder.executionGuidanceText(setOf("read_file"))
        assertFalse(hanyaReadFile.contains("Current time, date, timezone"))
        assertFalse(hanyaReadFile.contains("Git history, branches, diffs"))
        assertTrue(hanyaReadFile.contains("File contents, sizes, line counts"))

        val tanpaTool = SystemPromptBuilder.executionGuidanceText(emptySet())
        assertFalse(tanpaTool.contains("Current time, date, timezone"))
        assertFalse(tanpaTool.contains("Git history, branches, diffs"))
        assertFalse(tanpaTool.contains("File contents, sizes, line counts"))
        // Bagian non-per-tool tetap ada.
        assertTrue(tanpaTool.contains("<tool_persistence>"))
        assertTrue(tanpaTool.contains("<verification>"))
    }

    @Test
    fun `gating model mengikuti daftar repo sumber`() {
        assertTrue(SystemPromptBuilder.EXECUTION_GUIDANCE_MODELS.any { it in "kimi-k2.8" })
        assertTrue(SystemPromptBuilder.EXECUTION_GUIDANCE_MODELS.any { it in "gpt-5.1-codex" })
        assertTrue(SystemPromptBuilder.TOOL_USE_ENFORCEMENT_MODELS.any { it in "gemini-3-pro" })
        assertFalse(SystemPromptBuilder.TOOL_USE_ENFORCEMENT_MODELS.any { it in "kimi-k2.8" })
    }

    @Test
    fun `identitas default sama dengan thor desktop`() {
        val id = SystemPromptBuilder.DEFAULT_AGENT_IDENTITY
        assertTrue(id.startsWith("You are Thor Agent, built by Thor Agent."))
        assertTrue(id.contains("Agree because it's right, not because the user said it."))
    }

    @Test
    fun `platform hint android menjelaskan rendering dan MEDIA`() {
        val hint = SystemPromptBuilder.platformHint("android")
        assertTrue(hint.contains("Thor Agent Android app"))
        assertTrue(hint.contains("MEDIA:"))
        assertTrue(SystemPromptBuilder.platformHint("cli").contains("Markdown does NOT render"))
    }
}
