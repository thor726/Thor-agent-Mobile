package com.thoragent.app.core.agent

import android.content.res.AssetManager
import java.io.File
import java.io.InputStream

/**
 * Sumber aset — abstraksi tipis di atas `AssetManager`.
 *
 * Ada agar tata kelola berkas/direktori skill bisa diuji di JVM biasa tanpa
 * `Context`, termasuk reproduksi bug `EISDIR` yang pernah dilaporkan pengguna.
 */
internal interface AssetSource {
    /** Nama anak pada [path]; array kosong bila [path] adalah berkas. */
    fun list(path: String): Array<String>

    /** Buka [path] sebagai berkas; melempar bila tidak ada / bukan berkas. */
    fun open(path: String): InputStream
}

/** Implementasi produksi di atas `AssetManager` APK. */
internal class AndroidAssetSource(private val assets: AssetManager) : AssetSource {
    override fun list(path: String): Array<String> =
        runCatching { assets.list(path) }.getOrNull()?.map { it!! }?.toTypedArray() ?: emptyArray()

    override fun open(path: String): InputStream = assets.open(path)
}

/**
 * Menyalin pohon aset `assets/skills` ke direktori editable (`filesDir/skills`)
 * dan membaca identitas darinya.
 *
 * Bug yang diperbaiki di sini (dilaporkan sebagai
 * `/data/user/0/com.thoragent.app.debug/files/skills/SOUL.md: open failed:
 * EISDIR (Is a directory)`): versi lama memanggil `mkdirs()` tanpa syarat
 * sebelum mengetahui apakah sebuah aset berupa berkas. Akibatnya berkas tingkat
 * atas `skills/` — `SOUL.md` dan `skills_index.json` — dibuat sebagai direktori,
 * lalu pembacaan/pemulisannya gagal `EISDIR`. Kelas ini menentukan tipe lebih
 * dulu, dan `sync()` juga MEMULIHKAN instalasi yang sudah terlanjur rusak
 * dengan menghapus direktori yang menempati path berkas.
 */
internal class SkillAssetSync(
    private val assets: AssetSource,
    private val root: File,
    private val assetRootPath: String = "skills"
) {

    /**
     * Samakan [root] dengan pohon aset.
     *
     * Hanya menyentuh path yang belum ada atau salah tipe: berkas yang sudah ada
     * dibiarkan supaya suntingan pengguna (termasuk SOUL.md yang diedit) tidak
     * tertimpa, dan biaya satu kali jalan hanyalah `list()` per direktori.
     */
    fun sync() {
        root.mkdirs()
        syncTree(assetRootPath, root)
    }

    /**
     * Identitas agent: SOUL.md editable bila ada, selain itu aset bawaan.
     * Melempar `EISDIR` hanya mungkin bila [sync] belum dijalankan.
     */
    fun readSoul(): String {
        val editable = File(root, "SOUL.md")
        if (editable.isFile) {
            return runCatching { editable.readText() }.getOrDefault("")
        }
        // Sisa direktori dari instalasi lama: buang agar panggilan berikutnya bersih.
        if (editable.isDirectory) editable.deleteRecursively()
        return runCatching {
            assets.open("$assetRootPath/SOUL.md").bufferedReader().use { it.readText() }
        }.getOrDefault("")
    }

    private fun syncTree(assetPath: String, target: File) {
        val children = assets.list(assetPath)
        if (children.isNotEmpty()) {
            if (target.isFile) target.delete()
            target.mkdirs()
            for (child in children) syncTree("$assetPath/$child", File(target, child))
            return
        }

        // Tanpa anak: berkas, atau direktori kosong. Coba buka sebagai berkas.
        val input = runCatching { assets.open(assetPath) }.getOrNull()
        if (input == null) {
            if (target.isFile) target.delete()
            target.mkdirs()
            return
        }

        input.use { stream ->
            if (target.isDirectory) target.deleteRecursively()
            if (!target.isFile) {
                target.parentFile?.mkdirs()
                target.outputStream().use { out -> stream.copyTo(out) }
            }
        }
    }
}
