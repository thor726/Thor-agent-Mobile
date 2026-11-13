package com.thoragent.app

import com.thoragent.app.core.agent.AssetSource
import com.thoragent.app.core.agent.SkillAssetSync
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream

/**
 * Regresi untuk kesalahan produksi:
 * `files/skills/SOUL.md: open failed: EISDIR (Is a directory)`.
 *
 * Penyebabnya `mkdirs()` tanpa syarat sebelum mengetahui apakah sebuah aset
 * berupa berkas, sehingga `SOUL.md` dan `skills_index.json` tercipta sebagai
 * direktori. Test ini mereproduksi kondisi itu lalu memastikan `sync()`
 * memulihkannya.
 */
class SkillAssetSyncTest {

    @get:Rule
    val tmp = TemporaryFolder()

    /** Aset palsu di memori: path -> isi berkas. */
    private class FakeAssets(private val files: Map<String, String>) : AssetSource {
        override fun list(path: String): Array<String> {
            val prefix = if (path.isEmpty()) "" else "$path/"
            return files.keys
                .filter { it.startsWith(prefix) && it != path }
                .map { it.removePrefix(prefix).substringBefore('/') }
                .distinct()
                .toTypedArray()
        }

        override fun open(path: String): InputStream =
            files[path]?.byteInputStream() ?: throw java.io.FileNotFoundException(path)
    }

    private fun assets(): FakeAssets = FakeAssets(
        mapOf(
            "skills/SOUL.md" to "# SOUL\nidentitas asli\n",
            "skills/skills_index.json" to "{\"count\":0}",
            "skills/software-development/systematic-debugging/SKILL.md" to
                "---\nname: systematic-debugging\ndescription: debug\n---\nbody\n",
            "skills/software-development/systematic-debugging/references/notes.md" to "catatan\n"
        )
    )

    @Test
    fun `sync membuat berkas tingkat atas skills bukan direktori`() {
        val root = tmp.newFolder("skills")
        SkillAssetSync(assets(), root).sync()

        assertTrue("SOUL.md harus berupa berkas", File(root, "SOUL.md").isFile)
        assertFalse("SOUL.md tidak boleh berupa direktori", File(root, "SOUL.md").isDirectory)
        assertTrue(File(root, "skills_index.json").isFile)
        assertEquals("# SOUL\nidentitas asli\n", File(root, "SOUL.md").readText())
    }

    @Test
    fun `sync menyalin skill bersarang beserta references`() {
        val root = tmp.newFolder("skills")
        SkillAssetSync(assets(), root).sync()

        val dir = File(root, "software-development/systematic-debugging")
        assertTrue(File(dir, "SKILL.md").isFile)
        assertTrue(File(dir, "references/notes.md").isFile)
        assertEquals("catatan\n", File(dir, "references/notes.md").readText())
    }

    @Test
    fun `sync memulihkan instalasi rusak yang SOUL md nya direktori`() {
        val root = tmp.newFolder("skills")
        // Kondisi persis yang dilaporkan: direktori menempati path berkas.
        val broken = File(root, "SOUL.md").apply { mkdirs() }
        File(broken, "sampah.txt").writeText("sisa")
        File(root, "skills_index.json").apply { mkdirs() }

        SkillAssetSync(assets(), root).sync()

        assertTrue(File(root, "SOUL.md").isFile)
        assertEquals("# SOUL\nidentitas asli\n", File(root, "SOUL.md").readText())
        assertTrue(File(root, "skills_index.json").isFile)
    }

    @Test
    fun `readSoul mengembalikan aset ketika belum ada salinan editable`() {
        val root = tmp.newFolder("skills")
        val sync = SkillAssetSync(assets(), root)
        sync.sync()
        assertEquals("# SOUL\nidentitas asli\n", sync.readSoul())
    }

    @Test
    fun `readSoul tidak melempar EISDIR pada direktori sisa`() {
        val root = tmp.newFolder("skills")
        File(root, "SOUL.md").mkdirs()
        // Tanpa sync(): jalur lama melempar "open failed: EISDIR".
        val soul = SkillAssetSync(assets(), root).readSoul()
        assertEquals("# SOUL\nidentitas asli\n", soul)
        assertTrue(File(root, "SOUL.md").isDirectory.not())
    }

    @Test
    fun `sync tidak menimpa SOUL md hasil suntingan pengguna`() {
        val root = tmp.newFolder("skills")
        val sync = SkillAssetSync(assets(), root)
        sync.sync()
        File(root, "SOUL.md").writeText("versi saya\n")

        sync.sync() // jalan kedua, seperti tiap pembukaan aplikasi

        assertEquals("versi saya\n", File(root, "SOUL.md").readText())
        assertEquals("versi saya\n", sync.readSoul())
    }
}
