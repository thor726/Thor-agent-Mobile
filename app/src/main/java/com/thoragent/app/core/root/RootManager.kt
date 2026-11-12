package com.thoragent.app.core.root

import android.content.Context
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Status akses root terkini. */
data class RootStatus(
    val appGrantedRoot: Boolean,
    val rootShellAlive: Boolean,
    val manager: RootManagerType,
    val managerHint: String
)

enum class RootManagerType(val label: String) {
    KERNELSU("KernelSU"),
    APATCH("APatch"),
    MAGISK("Magisk"),
    OTHER_SU("su lain"),
    NONE("Tanpa root")
}

/**
 * Pengelola shell libsu — SEDERHANA sesuai kebutuhan nyata:
 * root hanya dipakai untuk eksekusi yang membutuhkannya (izin filesystem dsb.),
 * sisanya berjalan shell biasa. Tanpa provisioning/chroot/bridge apa pun.
 */
class RootManager(private val context: Context) {

    @Volatile private var cachedStatus: RootStatus? = null
    @Volatile private var cachedRootShell: Shell? = null
    @Volatile private var cachedNonRootShell: Shell? = null

    /** Shell utama (libsu default): root bila su diberikan, sh bila tidak. */
    fun mainShell(): Shell = Shell.getShell()

    /** Shell su eksplisit — null bila su ditolak/tidak ada. Di-cache. (flags=0 = minta root) */
    fun rootShell(): Shell = cachedRootShell ?: try {
        Shell.Builder.create()
            .setFlags(0)
            .setTimeout(10)
            .build()
            .also { cachedRootShell = it }
    } catch (e: Exception) {
        throw IllegalStateException("shell root tidak tersedia: ${e.message}")
    }

    /** Shell NON-root (sh/toybox) — jalur default semua eksekusi. Di-cache. */
    fun nonRootShell(): Shell = cachedNonRootShell ?: try {
        Shell.Builder.create()
            .setFlags(Shell.FLAG_NON_ROOT_SHELL)
            .setTimeout(10)
            .build()
            .also { cachedNonRootShell = it }
    } catch (e: Exception) {
        throw IllegalStateException("shell sh tidak tersedia: ${e.message}")
    }

    /** Bangun shell mode tertentu (dipakai terminal). flags=0 → root bila granted. */
    fun buildShell(root: Boolean): Shell? = try {
        Shell.Builder.create()
            .setFlags(if (root) 0 else Shell.FLAG_NON_ROOT_SHELL)
            .setTimeout(10)
            .build()
    } catch (e: Exception) {
        null
    }

    /**
     * Probe status root: grant libsu + deteksi manager (KernelSU/APatch/Magisk).
     *
     * PERBAIKAN (bug "root tidak terdeteksi padahal sudah dikasih izin"):
     * 1. `Shell.isAppGrantedRoot()` bisa null (status belum tersedia sebelum
     *    shell pertama dibangun) — null ≠ tidak diizinkan. Sekarang bila null
     *    kita BANGUN shell root sungguhan dulu (`Shell.cmd("id")` lewat builder
     *    flags=0): sukses = granted; gagal = benar-benar tanpa root.
     * 2. Deteksi manager TIDAK lagi bergantung granted==true: Ksu/APatch
     *    sering baru meng-grant app setelah request, sementara /data/adb
     *    terbaca lewat su hanya setelah grant — jadi urutan: probe → baru
     *    deteksi manager.
     */
    suspend fun status(force: Boolean = false): RootStatus = withContext(Dispatchers.IO) {
        cachedStatus?.takeIf { !force }?.let { return@withContext it }
        var granted = Shell.isAppGrantedRoot()
        if (granted == null) {
            // belum ada shell root yang dibangun → coba bangun & uji "id".
            // Sukses berarti su ada DAN app diizinkan (bila manager memakai
            // deny default, exec su ini akan gagal → granted=false).
            granted = try {
                val idOut = mutableListOf<String>()
                val r = Shell.Builder.create().setFlags(0).setTimeout(10).build()
                    .newJob().add("id").to(idOut, null).exec()
                r.isSuccess && idOut.any { it.contains("uid=0") }
            } catch (e: Exception) {
                false
            }
        }
        val grantedBool = granted == true
        var shellAlive = false
        var manager = RootManagerType.NONE
        var hint = ""
        if (grantedBool) {
            val out = mutableListOf<String>()
            val res = try {
                Shell.cmd("ls /data/adb 2>/dev/null").to(out, null).exec()
            } catch (e: Exception) {
                null
            }
            if (res?.isSuccess == true) {
                shellAlive = true
                val entries = out.joinToString("\n")
                manager = when {
                    entries.contains("ksud") || entries.contains("ksu") -> RootManagerType.KERNELSU
                    entries.contains("apd") || entries.contains("ap ") -> RootManagerType.APATCH
                    entries.contains("magisk") -> RootManagerType.MAGISK
                    else -> RootManagerType.OTHER_SU
                }
                val ver = mutableListOf<String>()
                Shell.cmd(
                    "su -v 2>/dev/null; ksud --version 2>/dev/null; " +
                        "ls /data/adb/magisk 2>/dev/null | head -3"
                ).to(ver, null).exec()
                hint = ver.filter { it.isNotBlank() }.joinToString(" · ").take(120)
            }
        }
        RootStatus(appGrantedRoot = grantedBool, rootShellAlive = shellAlive, manager = manager, managerHint = hint)
            .also { cachedStatus = it }
    }

    fun clearCache() {
        cachedStatus = null
    }
}
