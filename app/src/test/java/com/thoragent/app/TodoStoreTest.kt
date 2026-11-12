package com.thoragent.app

import com.thoragent.app.core.agent.TodoItem
import com.thoragent.app.core.agent.TodoResult
import com.thoragent.app.core.agent.TodoStore
import com.thoragent.app.core.agent.toToolJson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Test TodoStore — memverifikasi aturan yang di-port dari `tools/todo_tool.py`:
 *  - status valid: pending | in_progress | completed | cancelled,
 *  - cap konten 4000 char dengan penanda "… [truncated]",
 *  - cap 256 item (ekor dibuang),
 *  - dedupe by id menyimpan kemunculan TERAKHIR,
 *  - merge hanya mengubah field yang dikirim,
 *  - sanitasi parent (buang menggantung, putus siklus),
 *  - normalisasi: in_progress naik di depan pending pertama,
 *  - revision naik hanya bila isi berubah,
 *  - penulisan tidak valid tidak mengubah daftar (tanpa penulisan parsial).
 */
class TodoStoreTest {

    private fun item(id: String, content: String, status: String = "pending", parent: String? = null) =
        TodoItem(id = id, content = content, status = status, parent = parent)

    @Test
    fun `konstanta sama dengan repo sumber`() {
        assertEquals(4000, TodoStore.MAX_TODO_CONTENT_CHARS)
        assertEquals(256, TodoStore.MAX_TODO_ITEMS)
        assertEquals("… [truncated]", TodoStore.TRUNCATION_MARKER)
        assertEquals(
            setOf("pending", "in_progress", "completed", "cancelled"),
            TodoStore.VALID_STATUSES
        )
    }

    @Test
    fun `tulis daftar baru menggantikan seluruh isi`() {
        val st = TodoStore.State()
        st.write(listOf(item("1", "satu"), item("2", "dua")))
        val res = st.write(listOf(item("3", "tiga")))
        assertEquals(listOf("3"), res.getOrThrow().map { it.id })
    }

    @Test
    fun `revision naik hanya saat isi berubah`() {
        val st = TodoStore.State()
        st.write(listOf(item("1", "satu")))
        val revAwal = st.snapshot().second
        // Tulis daftar identik → revision tidak berubah.
        st.write(listOf(item("1", "satu")))
        assertEquals(revAwal, st.snapshot().second)
        // Tulis berbeda → revision naik.
        st.write(listOf(item("1", "satu diubah")))
        assertTrue(st.snapshot().second > revAwal)
    }

    @Test
    fun `status tidak valid jatuh ke pending`() {
        val st = TodoStore.State()
        val res = st.write(listOf(item("1", "satu", status = "selesai-banget")))
        assertEquals("pending", res.getOrThrow().first().status)
    }

    @Test
    fun `konten melebihi cap dipotong dengan penanda`() {
        val st = TodoStore.State()
        val panjang = "a".repeat(5000)
        val res = st.write(listOf(item("1", panjang)))
        val content = res.getOrThrow().first().content
        assertEquals(TodoStore.MAX_TODO_CONTENT_CHARS, content.length)
        assertTrue(content.endsWith(TodoStore.TRUNCATION_MARKER))
    }

    @Test
    fun `konten kosong membuat penulisan ditolak tanpa mengubah daftar`() {
        val st = TodoStore.State()
        st.write(listOf(item("1", "satu")))
        val res = st.write(listOf(item("2", "  ")))
        assertTrue(res.isFailure)
        // Daftar lama tidak berubah — tidak ada penulisan parsial.
        assertEquals(listOf("1"), st.read().map { it.id })
    }

    @Test
    fun `dedupe by id menyimpan kemunculan terakhir`() {
        val st = TodoStore.State()
        val res = st.write(
            listOf(item("1", "versi lama"), item("2", "dua"), item("1", "versi baru"))
        )
        val items = res.getOrThrow()
        assertEquals(2, items.size)
        assertEquals("versi baru", items.first { it.id == "1" }.content)
    }

    @Test
    fun `merge hanya mengubah field yang dikirim`() {
        val st = TodoStore.State()
        st.write(listOf(item("1", "tugas satu", status = "pending")))
        // Kirim hanya status → konten tetap.
        st.write(listOf(item("1", "", status = "completed")), merge = true)
        val it = st.read().first()
        assertEquals("tugas satu", it.content)
        assertEquals("completed", it.status)
    }

    @Test
    fun `merge menambahkan id baru`() {
        val st = TodoStore.State()
        st.write(listOf(item("1", "satu")))
        st.write(listOf(item("2", "dua")), merge = true)
        assertEquals(listOf("1", "2"), st.read().map { it.id })
    }

    @Test
    fun `merge tanpa id valid dilewati`() {
        val st = TodoStore.State()
        st.write(listOf(item("1", "satu")))
        st.write(listOf(item("   ", "tanpa id")), merge = true)
        assertEquals(1, st.read().size)
    }

    @Test
    fun `parent menggantung dibuang`() {
        val st = TodoStore.State()
        val res = st.write(listOf(item("1", "anak", parent = "tidak-ada")))
        assertNull(res.getOrThrow().first().parent)
    }

    @Test
    fun `parent yang menunjuk diri sendiri dibuang`() {
        val st = TodoStore.State()
        val res = st.write(listOf(item("1", "diri", parent = "1")))
        assertNull(res.getOrThrow().first().parent)
    }

    @Test
    fun `siklus parent diputus`() {
        val st = TodoStore.State()
        val res = st.write(
            listOf(item("1", "a", parent = "2"), item("2", "b", parent = "1"))
        )
        val items = res.getOrThrow()
        // Minimal satu parent diputus agar tidak ada siklus.
        assertTrue(items.any { it.parent == null })
    }

    @Test
    fun `in_progress naik di depan pending pertama`() {
        val st = TodoStore.State()
        val res = st.write(
            listOf(
                item("1", "pending satu", status = "pending"),
                item("2", "pending dua", status = "pending"),
                item("3", "aktif", status = "in_progress")
            )
        )
        assertEquals(listOf("3", "1", "2"), res.getOrThrow().map { it.id })
    }

    @Test
    fun `daftar bersarang tidak diurut ulang`() {
        val st = TodoStore.State()
        val res = st.write(
            listOf(
                item("1", "induk", status = "pending"),
                item("2", "anak", status = "in_progress", parent = "1")
            )
        )
        // Ada parent → urutan authored dipertahankan.
        assertEquals(listOf("1", "2"), res.getOrThrow().map { it.id })
    }

    @Test
    fun `cap 256 item membuang ekor`() {
        val st = TodoStore.State()
        val banyak = (1..300).map { item(it.toString(), "tugas $it") }
        val res = st.write(banyak)
        assertEquals(TodoStore.MAX_TODO_ITEMS, res.getOrThrow().size)
    }

    @Test
    fun `formatForInjection hanya memuat item aktif`() {
        val st = TodoStore.State()
        st.write(
            listOf(
                item("1", "selesai", status = "completed"),
                item("2", "aktif", status = "in_progress")
            )
        )
        val inj = st.formatForInjection()!!
        assertTrue(inj.startsWith(TodoStore.TODO_INJECTION_HEADER))
        assertTrue(inj.contains("aktif"))
        assertFalse(inj.contains("selesai"))
    }

    @Test
    fun `formatForInjection null bila semua selesai`() {
        val st = TodoStore.State()
        st.write(listOf(item("1", "selesai", status = "completed")))
        assertNull(st.formatForInjection())
    }

    @Test
    fun `formatForInjection mempertahankan induk yang punya anak aktif`() {
        val st = TodoStore.State()
        st.write(
            listOf(
                item("1", "induk", status = "completed"),
                item("2", "anak", status = "pending", parent = "1")
            )
        )
        val inj = st.formatForInjection()!!
        assertTrue(inj.contains("induk"))
        assertTrue(inj.contains("anak"))
    }

    @Test
    fun `marker status di injeksi sama dengan repo sumber`() {
        val st = TodoStore.State()
        st.write(
            listOf(
                item("1", "a", status = "in_progress"),
                item("2", "b", status = "pending")
            )
        )
        val inj = st.formatForInjection()!!
        assertTrue(inj.contains("[>]"))
        assertTrue(inj.contains("[ ]"))
    }

    @Test
    fun `restore memasang snapshot tanpa menaikkan revisi`() {
        val st = TodoStore.State()
        st.restore(listOf(item("1", "pulih")), revisionValue = 7)
        assertEquals(7, st.snapshot().second)
        assertEquals("pulih", st.read().first().content)
    }

    @Test
    fun `apply null membaca daftar tanpa mengubah`() {
        val st = TodoStore.State()
        st.write(listOf(item("1", "satu")))
        val res = TodoStore.apply(st, null)
        assertTrue(res is TodoResult.Ok)
        assertEquals(1, (res as TodoResult.Ok).todos.size)
    }

    @Test
    fun `apply mengembalikan error untuk item tidak valid`() {
        val st = TodoStore.State()
        val res = TodoStore.apply(st, listOf(item("1", "")))
        assertTrue(res is TodoResult.Error)
    }

    @Test
    fun `toToolJson memuat summary per status`() {
        val st = TodoStore.State()
        st.write(
            listOf(
                item("1", "a", status = "pending"),
                item("2", "b", status = "completed")
            )
        )
        val json = TodoStore.apply(st, null).toToolJson()
        assertTrue(json.contains("\"total\":2"))
        assertTrue(json.contains("\"pending\":1"))
        assertTrue(json.contains("\"completed\":1"))
        assertTrue(json.contains("\"revision\":"))
    }
}
