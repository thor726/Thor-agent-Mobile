package com.thoragent.app

import com.thoragent.app.core.agent.MobileCommands
import com.thoragent.app.core.agent.SlashCommandDef
import com.thoragent.app.core.agent.parseSlashAgainst
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit test registry slash command — port COMMAND_REGISTRY repo sumber.
 */
class SlashCommandRegistryTest {

    private val cmds = listOf(
        SlashCommandDef("new", "Mulai sesi baru", "Sesi", aliases = listOf("reset"), argsHint = "[name]"),
        SlashCommandDef("model", "Ganti model", "Konfigurasi", argsHint = "<model>"),
        SlashCommandDef("goal", "Tujuan berdiri", "Sesi", argsHint = "[text]"),
        SlashCommandDef("compress", "Padatkan context", "Sesi", aliases = listOf("compact"))
    )

    @Test
    fun `parse perintah tanpa argumen`() {
        val p = parseSlashAgainst(cmds, "/new")
        assertNotNull(p)
        assertEquals("new", p!!.name)
        assertEquals("", p.args)
    }

    @Test
    fun `parse perintah dengan argumen`() {
        val p = parseSlashAgainst(cmds, "/model glm-5.3 --global")
        assertNotNull(p)
        assertEquals("model", p!!.name)
        assertEquals("glm-5.3 --global", p.args)
    }

    @Test
    fun `parse lewat alias menyelesaikan nama kanonik`() {
        val p = parseSlashAgainst(cmds, "/reset nama-sesi")
        assertNotNull(p)
        // alias reset → kanonik new (perilaku registry repo sumber)
        assertEquals("new", p!!.name)
        assertEquals("nama-sesi", p.args)
    }

    @Test
    fun `input biasa dan perintah tak dikenal bukan slash command`() {
        assertNull(parseSlashAgainst(cmds, "halo apa kabar"))
        assertNull(parseSlashAgainst(cmds, "/tidakada"))
        assertNull(parseSlashAgainst(cmds, "/"))
    }

    @Test
    fun `mode argumen meniru infer_argument_mode repo sumber`() {
        // subcommands tanpa prosa → options
        val options = SlashCommandDef("yolo", "", "", subcommands = listOf("on", "off"))
        assertEquals("options", options.argumentMode)
        // subcommands + prosa → mixed
        val mixed = SlashCommandDef("goal", "", "", subcommands = listOf("show"), argsHint = "[text | off]")
        assertEquals("mixed", mixed.argumentMode)
        // tanpa subcommands, ada hint → text
        val text = SlashCommandDef("model", "", "", argsHint = "<model>")
        assertEquals("text", text.argumentMode)
        // tanpa keduanya → none
        assertEquals("none", SlashCommandDef("new", "", "").argumentMode)
    }

    @Test
    fun `perintah natif mobile mencakup inti repo sumber`() {
        listOf("new", "model", "status", "usage", "skills", "goal", "compress", "help").forEach {
            assertTrue("seharusnya natif: $it", MobileCommands.isNative(it))
        }
        // v0.4.0: journey & pet kini natif penuh di aplikasi
        assertTrue(MobileCommands.isNative("journey"))
        assertTrue(MobileCommands.isNative("pet"))
        assertTrue(MobileCommands.isNative("queue"))
        assertTrue(MobileCommands.isNative("voice"))
    }

    @Test
    fun `tier agent dan info terpetakan sadar (bukan default buta)`() {
        assertEquals(MobileCommands.SupportTier.AGENT, MobileCommands.tier("worktree"))
        assertEquals(MobileCommands.SupportTier.AGENT, MobileCommands.tier("diff"))
        assertEquals(MobileCommands.SupportTier.AGENT, MobileCommands.tier("review"))
        assertEquals(MobileCommands.SupportTier.INFO, MobileCommands.tier("browser"))
        assertEquals(MobileCommands.SupportTier.INFO, MobileCommands.tier("wake"))
        assertEquals(MobileCommands.SupportTier.INFO, MobileCommands.tier("platforms"))
    }

    /**
     * INVARIAN PENUH v0.4.0: setiap perintah di katalog repo sumber (105)
     * punya tier dukungan yang DISENGAJA — gabungan NATIVE+AGENT+INFO persis
     * sama dengan daftar perintah katalog. Tidak ada perintah "terlupa".
     */
    @Test
    fun `semua perintah katalog tercakup tier dukungan android`() {
        val catalogFile = java.io.File("src/main/assets/catalog/slash_commands.json")
        org.junit.Assume.assumeTrue("katalog tersedia di modul", catalogFile.exists())
        val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
        val text = catalogFile.readText()
        val count = json.parseToJsonElement(text)
            .let { it as kotlinx.serialization.json.JsonObject }["count"]
            ?.let { (it as kotlinx.serialization.json.JsonPrimitive).content.toInt() } ?: 105
        val commands = json.parseToJsonElement(text)
            .let { it as kotlinx.serialization.json.JsonObject }["commands"]
            ?.let { it as kotlinx.serialization.json.JsonArray }
            ?.mapNotNull { (it as? kotlinx.serialization.json.JsonObject)?.get("name")
                ?.let { n -> (n as kotlinx.serialization.json.JsonPrimitive).content } }
            .orEmpty()
        assertTrue("katalog kosong?", commands.isNotEmpty())
        assertEquals(count, commands.size)

        val intentional = MobileCommands.NATIVE + MobileCommands.AGENT + MobileCommands.INFO
        val missing = commands.filter { it !in intentional }
        assertTrue(
            "perintah tanpa tier disengaja: $missing",
            missing.isEmpty()
        )
        // dan tidak ada tier yang tidak ada di katalog (typo guard)
        val extra = intentional.filter { it !in commands }
        assertTrue("tier tidak dikenal katalog: $extra", extra.isEmpty())
    }
}
