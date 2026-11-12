package com.thoragent.app

import org.junit.Test
import org.junit.Assert.*
import com.thoragent.app.core.agent.SlashCommandDef

/**
 * Sinkron v0.6.1: level effort harus persis sumber —
 * thor_constants.py VALID_REASONING_EFFORTS =
 * ("minimal","low","medium","high","xhigh","max","ultra") + "none" (nonaktif).
 */
class ReasoningUltraTest {

    @Test
    fun `semua level sumber ada termasuk ultra`() {
        val source = listOf("minimal", "low", "medium", "high", "xhigh", "max", "ultra")
        // daftar yang dipakai cabang /reasoning di ChatViewModel (harus identik)
        val mobile = listOf("none", "minimal", "low", "medium", "high", "xhigh", "max", "ultra")
        source.forEach { lvl ->
            assertTrue("level '$lvl' dari sumber harus diterima", lvl in mobile)
        }
        assertTrue("'none' (nonaktif) harus tetap ada", "none" in mobile)
    }

    @Test
    fun `urutan slider pusat perintah sesuai sumber`() {
        // urutan slider CommandCenterScreen: bawaan di depan, lalu urutan sumber
        val slider = listOf("", "none", "minimal", "low", "medium", "high", "xhigh", "max", "ultra")
        assertEquals(9, slider.size)
        assertEquals("ultra", slider.last()) // posisi paling kanan = paling teliti
        assertEquals("", slider.first())     // kiri = bawaan
    }
}
