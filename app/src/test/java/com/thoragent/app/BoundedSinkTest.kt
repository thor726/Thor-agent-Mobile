package com.thoragent.app

import com.thoragent.app.core.exec.AndroidShellRunner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regresi untuk pembatasan memori output perintah.
 *
 * Sebelumnya libsu menulis ke `CallbackList` yang menyimpan SEMUA baris; perintah
 * bervolume besar (debug repo dari GitHub, `curl` berkas panjang, `git log`) bisa
 * menghabiskan heap lalu mematikan aplikasi dengan OutOfMemoryError — `Error`
 * yang tidak tertangkap `catch (Exception)`.
 */
class BoundedSinkTest {

    @Test
    fun `menahan hanya sejumlah baris yang ditentukan`() {
        val sink = AndroidShellRunner.BoundedSink(limit = 10, onAdd = { })

        repeat(1000) { sink.add("baris-$it") }

        assertEquals("penyimpanan harus dibatasi", 10, sink.size)
        assertEquals("baris-0", sink[0])
        assertEquals("baris-9", sink[9])
    }

    @Test
    fun `tetap meneruskan setiap baris ke callback walau penyimpanan penuh`() {
        val diteruskan = mutableListOf<String>()
        val sink = AndroidShellRunner.BoundedSink(limit = 5, onAdd = { diteruskan.add(it) })

        repeat(50) { sink.add("l$it") }

        // Streaming tidak boleh berhenti walau penyimpanan sudah penuh.
        assertEquals(50, diteruskan.size)
        assertEquals("l49", diteruskan.last())
        assertEquals(5, sink.size)
    }

    @Test
    fun `addAll juga menghormati batas`() {
        val sink = AndroidShellRunner.BoundedSink(limit = 3, onAdd = { })

        sink.addAll(listOf("a", "b", "c", "d", "e"))

        assertEquals(3, sink.size)
        assertEquals(listOf("a", "b", "c"), sink.toList())
    }

    @Test
    fun `isi yang tersimpan bisa dibaca lewat iterasi seperti list biasa`() {
        val sink = AndroidShellRunner.BoundedSink(limit = 100, onAdd = { })
        sink.add("x")
        sink.add("y")

        // Kalau delegasi MutableList menunjuk list yang salah, isi ini kosong.
        assertTrue("sink harus bisa dibaca", sink.isNotEmpty())
        assertEquals(listOf("x", "y"), sink.toList())
        assertEquals(2, sink.size)
    }
}
