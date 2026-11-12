package com.thoragent.app

import com.thoragent.app.core.memory.MemoryStore
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

/**
 * Test MemoryStore — memverifikasi aturan yang di-port dari
 * `tools/memory_tool_store.py` Thor desktop:
 *  - budget **2200** char (memory) / **1375** char (user),
 *  - delimiter antar entri `\n§\n`,
 *  - `add` ditolak bila melewati budget DAN mengembalikan entri live,
 *  - `patch` hanya jalan bila span muncul tepat sekali di satu entri,
 *  - `remove` menghapus satu entri utuh,
 *  - batch diperiksa pada hasil AKHIR (bukan per operasi),
 *  - snapshot system prompt beku saat load (tidak berubah oleh tulisan sesi).
 */
class MemoryStoreTest {

    private lateinit var dir: File

    @Before
    fun setUp() {
        dir = Files.createTempDirectory("thor-memory-test").toFile()
    }

    @After
    fun tearDown() {
        dir.deleteRecursively()
    }

    private fun freshStore(): MemoryStore = MemoryStore(dir = dir)

    private fun addOp(content: String): JsonObject = buildJsonObject {
        put("action", "add")
        put("content", content)
    }

    private fun patchOp(old: String, new: String): JsonObject = buildJsonObject {
        put("action", "patch")
        put("old_text", old)
        put("content", new)
    }

    private fun removeOp(old: String): JsonObject = buildJsonObject {
        put("action", "remove")
        put("old_text", old)
    }

    @Test
    fun `budget default sama dengan repo sumber`() {
        assertEquals(2200, MemoryStore.DEFAULT_MEMORY_CHAR_LIMIT)
        assertEquals(1375, MemoryStore.DEFAULT_USER_CHAR_LIMIT)
        assertEquals("\n§\n", MemoryStore.ENTRY_DELIMITER)
    }

    @Test
    fun `add menulis entri ke MEMORY_md dengan delimiter`() = runBlocking {
        val store = freshStore()
        store.loadFromDisk()
        val res = store.applyBatch("memory", listOf(addOp("User suka jawaban ringkas")))
        assertTrue(res["success"]!!.toString().toBoolean())

        val file = File(dir, "MEMORY.md")
        assertTrue(file.exists())
        assertEquals("User suka jawaban ringkas", file.readText().trim())
    }

    @Test
    fun `dua entri dipisah delimiter sumber`() = runBlocking {
        val store = freshStore()
        store.loadFromDisk()
        store.applyBatch("memory", listOf(addOp("entri satu")))
        store.applyBatch("memory", listOf(addOp("entri dua")))
        val body = File(dir, "MEMORY.md").readText()
        assertTrue(body.contains("\n§\n"))
        assertEquals(listOf("entri satu", "entri dua"), body.split("\n§\n"))
    }

    @Test
    fun `add di atas budget ditolak dan menyertakan entri live`() = runBlocking {
        val store = freshStore()
        store.loadFromDisk()
        // 2200 char budget — isi hampir penuh lalu tambah yang melewati.
        store.applyBatch("memory", listOf(addOp("x".repeat(2000))))
        val res = store.applyBatch("memory", listOf(addOp("y".repeat(500))))
        assertEquals("false", res.getValue("success").jsonPrimitive.content)
        assertEquals("over_budget", res.getValue("error_class").jsonPrimitive.content)
        // Entri live harus dikembalikan agar model bisa mengonsolidasikan.
        assertTrue(res.toString().contains("xxxx"))
    }

    @Test
    fun `batch diperiksa pada hasil akhir bukan per operasi`() = runBlocking {
        val store = freshStore()
        store.loadFromDisk()
        store.applyBatch("memory", listOf(addOp("a".repeat(1800))))
        // Satu batch: hapus yang basi DAN tambah yang baru → harus lolos.
        val res = store.applyBatch(
            "memory",
            listOf(removeOp("a".repeat(100)), addOp("entri baru yang cukup panjang"))
        )
        assertEquals("true", res.getValue("success").jsonPrimitive.content)
        val body = File(dir, "MEMORY.md").readText()
        assertTrue(body.contains("entri baru yang cukup panjang"))
    }

    @Test
    fun `patch butuh span unik dalam satu entri`() = runBlocking {
        val store = freshStore()
        store.loadFromDisk()
        store.applyBatch("memory", listOf(addOp("warna favorit: biru; warna favorit: biru")))
        val res = store.applyBatch("memory", listOf(patchOp("warna favorit: biru", "warna favorit: hijau")))
        assertEquals("false", res.getValue("success").jsonPrimitive.content)
        // Isi tidak boleh berubah saat patch ambigu.
        assertTrue(File(dir, "MEMORY.md").readText().contains("biru"))
    }

    @Test
    fun `patch unik diterapkan`() = runBlocking {
        val store = freshStore()
        store.loadFromDisk()
        store.applyBatch("memory", listOf(addOp("User memakai Arch Linux")))
        val res = store.applyBatch("memory", listOf(patchOp("Arch Linux", "Fedora")))
        assertEquals("true", res.getValue("success").jsonPrimitive.content)
        assertTrue(File(dir, "MEMORY.md").readText().contains("Fedora"))
    }

    @Test
    fun `remove menghapus satu entri utuh`() = runBlocking {
        val store = freshStore()
        store.loadFromDisk()
        store.applyBatch("memory", listOf(addOp("entri A"), addOp("entri B")))
        val res = store.applyBatch("memory", listOf(removeOp("entri A")))
        assertEquals("true", res.getValue("success").jsonPrimitive.content)
        val body = File(dir, "MEMORY.md").readText()
        assertTrue(!body.contains("entri A"))
        assertTrue(body.contains("entri B"))
    }

    @Test
    fun `target user memakai file USER_md dan budget sendiri`() = runBlocking {
        val store = freshStore()
        store.loadFromDisk()
        store.applyBatch("user", listOf(addOp("Budi, backend engineer")))
        val userFile = File(dir, "USER.md")
        assertTrue(userFile.exists())
        assertTrue(userFile.readText().contains("Budi"))
        // MEMORY.md tidak boleh ikut terisi.
        assertTrue(!File(dir, "MEMORY.md").exists() || File(dir, "MEMORY.md").readText().isBlank())
    }

    @Test
    fun `snapshot system prompt beku saat load`() = runBlocking {
        val store = freshStore()
        File(dir, "MEMORY.md").writeText("fakta awal")
        store.loadFromDisk()
        val snapshotAwal = store.formatForSystemPrompt("memory")
        assertNotNull(snapshotAwal)
        assertTrue(snapshotAwal!!.contains("fakta awal"))

        // Tulis di tengah sesi — snapshot prompt TIDAK berubah (prefix-cache stabil).
        store.applyBatch("memory", listOf(addOp("fakta baru")))
        val snapshotSetelah = store.formatForSystemPrompt("memory")
        assertEquals(snapshotAwal, snapshotSetelah)
        assertTrue(!snapshotSetelah!!.contains("fakta baru"))
    }

    @Test
    fun `formatForSystemPrompt kosong mengembalikan null`() = runBlocking {
        val store = freshStore()
        store.loadFromDisk()
        assertNull(store.formatForSystemPrompt("memory"))
        assertNull(store.formatForSystemPrompt("user"))
    }

    @Test
    fun `header blok prompt memakai judul repo sumber`() = runBlocking {
        val store = freshStore()
        File(dir, "MEMORY.md").writeText("catatan")
        File(dir, "USER.md").writeText("profil")
        store.loadFromDisk()
        val mem = store.formatForSystemPrompt("memory")!!
        val usr = store.formatForSystemPrompt("user")!!
        assertTrue(mem.contains("MEMORY (your personal notes)"))
        assertTrue(usr.contains("USER PROFILE (who the user is)"))
        assertTrue(mem.contains("═".repeat(46)))
    }

    @Test
    fun `target tidak valid ditolak`() = runBlocking {
        val store = freshStore()
        store.loadFromDisk()
        val res = store.applyBatch("lainnya", listOf(addOp("x")))
        assertEquals("false", res.getValue("success").jsonPrimitive.content)
    }

    @Test
    fun `store yang dimatikan menolak tulisan`() = runBlocking {
        val store = MemoryStore(dir = dir, memoryEnabled = false)
        store.loadFromDisk()
        val res = store.applyBatch("memory", listOf(addOp("x")))
        assertEquals("false", res.getValue("success").jsonPrimitive.content)
        assertTrue(!File(dir, "MEMORY.md").exists() || File(dir, "MEMORY.md").readText().isBlank())
    }
}
