package com.thoragent.app.core.agent

import android.content.Context
import com.thoragent.app.core.exec.AndroidShellRunner
import com.thoragent.app.core.root.RootManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.zip.ZipInputStream

private fun jstr(s: String): String = Json.encodeToString(String.serializer(), s)

/** Antarmuka tool agent — impl asli di device, fake di unit test. */
interface AgentTools {
    fun schemas(): JsonArray
    suspend fun execute(name: String, argsJson: String, cwd: String): String
    fun needsApproval(name: String, argsJson: String): Boolean

    /** Alasan pola berbahaya yang terdeteksi (untuk dialog persetujuan). */
    fun approvalReason(command: String): String? = null
}

/** Definisi satu tool native (nama, deskripsi, skema parameter OpenAI). */
data class NativeTool(val name: String, val description: String, val parameters: JsonObject)

/**
 * Registry tool native Thor Agent Mobile — port praktis tool inti repo Thor-agent
 * (shell + file IO + skill) yang relevan untuk perangkat Android:
 *
 * shell, read_file, write_file, edit_file, list_dir, search_files,
 * github_clone, web_fetch, skill_list, skill_load, root_status.
 *
 * Root dipakai SEPERLUNYA: shell biasa berjalan non-root; bila ditolak izinnya
 * dan root tersedia (dan diizinkan pengguna), eksekusi diulang via su.
 */
class NativeToolRegistry(
    private val context: Context,
    private val root: RootManager,
    private val runner: AndroidShellRunner,
    private val skills: SkillLibrary,
    private val ok: OkHttpClient,
    private val useRootWhenNeeded: suspend () -> Boolean = { true }
) : AgentTools {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    // ===================== SKEMA (OpenAI function calling) =====================

    private fun obj(vararg props: Pair<String, JsonObject>, required: List<String> = emptyList()): JsonObject =
        buildJsonObject {
            put("type", "object")
            put("properties", buildJsonObject { props.forEach { (k, v) -> put(k, v) } })
            if (required.isNotEmpty()) put("required", buildJsonArray { required.forEach { add(it) } })
        }

    private fun pString(desc: String) = buildJsonObject { put("type", "string"); put("description", desc) }
    private fun pInt(desc: String) = buildJsonObject { put("type", "integer"); put("description", desc) }
    private fun pBool(desc: String) = buildJsonObject { put("type", "boolean"); put("description", desc) }

    private val toolsDefs = listOf(
        NativeTool(
            "shell",
            "Jalankan perintah shell Android (mksh/toybox: ls, cat, cp, mv, tar, unzip, curl tidak tersedia — pakai github_clone/web_fetch untuk jaringan). " +
                "Berjalan non-root; bila ditolak izin dan root tersedia, otomatis diulang via su. Kembalikan stdout/stderr + exit code.",
            obj(
                "command" to pString("Perintah yang dijalankan, mis. 'ls -la /sdcard/Download'"),
                "timeout_sec" to pInt("Batas waktu detik, bawaan 30")
            , required = listOf("command"))
        ),
        NativeTool(
            "read_file",
            "Baca isi berkas teks di perangkat (maks 512 KB) — absolut atau relatif CWD.",
            obj("path" to pString("Path berkas"), required = listOf("path"))
        ),
        NativeTool(
            "write_file",
            "Tulis berkas teks (buat baru / timpa penuh) di perangkat. Untuk perubahan kecil pada berkas besar gunakan edit_file.",
            obj(
                "path" to pString("Path berkas"),
                "content" to pString("Isi lengkap yang ditulis")
            , required = listOf("path", "content"))
        ),
        NativeTool(
            "edit_file",
            "Edit berkas dengan pencarian-penggantian persis ala patch Thor-agent: temukan old_string lalu ganti jadi new_string.",
            obj(
                "path" to pString("Path berkas"),
                "old_string" to pString("Teks persis yang dicari"),
                "new_string" to pString("Penggantinya"),
                "replace_all" to pBool("Ganti semua kemunculan, bawaan false")
            , required = listOf("path", "old_string", "new_string"))
        ),
        NativeTool(
            "list_dir",
            "Daftar isi direktori: nama, ukuran, tipe. Absolut atau relatif CWD.",
            obj("path" to pString("Path direktori"))
        ),
        NativeTool(
            "search_files",
            "Cari berkas bernama cocok glob di bawah path; bila 'content' diisi, sekaligus grep isinya. Mengembalikan path + baris cocok.",
            obj(
                "path" to pString("Direktori awal pencarian, bawaan CWD"),
                "pattern" to pString("Glob nama berkas, mis. '*.md' atau 'report*'"),
                "content" to pString("Teks yang dicari di dalam berkas (opsional)")
            , required = listOf("pattern"))
        ),
        NativeTool(
            "github_clone",
            "Ambil repo GitHub ke penyimpanan lokal (contoh pemakaian terminal user): unduh arsip zip via codeload.github.com lalu ekstrak ke dest. Repo publik.",
            obj(
                "repo" to pString("URL repo atau 'owner/name'"),
                "branch" to pString("Branch, bawaan main"),
                "dest" to pString("Direktori tujuan, bawaan CWD/<nama-repo>")
            , required = listOf("repo"))
        ),
        NativeTool(
            "web_fetch",
            "Ambil isi URL (http/https) sebagai teks: JSON/api dibuka mentah, HTML disederhanakan jadi teks. Untuk baca dokumentasi/data sebelum menjawab.",
            obj(
                "url" to pString("URL lengkap"),
                "max_chars" to pInt("Batas karakter, bawaan 12000")
            , required = listOf("url"))
        ),
        NativeTool(
            "skill_list",
            "Daftar skill kecerdasan Thor-agent yang dibawa aplikasi (dari repo Thor-agent) — bisa difilter kueri. Panggil sebelum mengerjakan tugas yang berpotensi cocok dengan skill.",
            obj("query" to pString("Filter nama/deskripsi/kategori (opsional)"))
        ),
        NativeTool(
            "skill_load",
            "Buka satu skill penuh: prosedur langkah-demi-langkah cara mengerjakan/menyelesaikan tugas. Muat sebelum mengikuti prosedurnya.",
            obj(
                "name" to pString("Nama skill persis dari skill_list"),
                "path" to pString("Atau path aset dari skill_list")
            )
        ),
        NativeTool(
            "root_status",
            "Status root perangkat: manager (KernelSU/APatch/Magisk), grant su, shell hidup.",
            obj()
        )
    )

    override fun schemas(): JsonArray = buildJsonArray {
        toolsDefs.forEach { t ->
            add(
                buildJsonObject {
                    put("type", "function")
                    put("function", buildJsonObject {
                        put("name", t.name)
                        put("description", t.description)
                        put("parameters", t.parameters)
                    })
                }
            )
        }
    }

    // ===================== APPROVAL (port tools/approval_detection.py repo Thor-agent) =====================

    /** Pola HARDLINE repo sumber (subset relevan Android/mksh-toybox). */
    private val dangerPatterns = listOf(
        // rm rekursif pada path root-anchored yang collapse ke "/" (ala _hardline_rm_path)
        Pattern(
            Regex("""\brm\s+(-[a-zA-Z]*r[a-zA-Z]*f?|-[a-zA-Z]*f[a-zA-Z]*r?|--recursive)\s+(/|(?:/(?:\.\.?)+)+|\$(\{?HOME\}?)|~)(?:/|\s*\*|$)"""),
            "hapus rekursif sistem berkas root"
        ),
        // rm rekursif pada direktori sistem Android
        Pattern(
            Regex("""\brm\s+(-[a-zA-Z]*r[a-zA-Z]*f?|--recursive)\s+/(system|vendor|data|proc|sys|dev|sdcard|storage)(/|\s|$)"""),
            "hapus rekursif direktori sistem"
        ),
        // rm rekursif generik (pola DANGEROUS repo)
        Pattern(Regex("""\brm\s+(-[^\s]*r|--recursive)""", RegexOption.IGNORE_CASE), "hapus rekursif"),
        // dd ke perangkat blok mentah
        Pattern(Regex("""\bdd\b[^\n]*\bof=/dev/(block|sd|nvme|mmcblk)""", RegexOption.IGNORE_CASE), "dd ke perangkat blok mentah"),
        // redirect ke perangkat blok
        Pattern(Regex(""">\s*/dev/(block|sd|nvme|mmcblk)""", RegexOption.IGNORE_CASE), "redirect ke perangkat blok"),
        // fork bomb (posisi-less, repo sumber)
        Pattern(Regex(""":\(\)\s*\{\s*:\s*\|\s*:&\s*\}\s*;\s*:"""), "fork bomb"),
        // matikan semua proses
        Pattern(Regex("""\bkill\s+(-[^\s]+\s+)*-1\b""", RegexOption.IGNORE_CASE), "kill semua proses"),
        Pattern(Regex("""\bkillall\s+(-[^\s]*\s+)*-9\b""", RegexOption.IGNORE_CASE), "killall paksa"),
        // format / shutdown (mkfs, reboot, shutdown, halt)
        Pattern(Regex("""\b(mkfs(\.[a-z0-9]+)?|make_ext4fs)\b""", RegexOption.IGNORE_CASE), "format filesystem (mkfs)"),
        Pattern(Regex("""^(sudo\s+)?(reboot|shutdown|halt|poweroff)\b""", RegexOption.IGNORE_CASE), "matikan/nyalakan ulang sistem"),
        // flash partisi (Android spesifik)
        Pattern(
            Regex("""\b(fastboot\s+flash|flash_image|dd\b[^\n]*boot\.img|dd\b[^\n]*recovery\.img|magiskboot)\b"""),
            "flash partisi/boot image"
        ),
        // chmod -R 777 pada root
        Pattern(Regex("""\bchmod\s+-R\s+777\s+/""", RegexOption.IGNORE_CASE), "chmod 777 rekursif di root"),
        // pipe remote ke shell (curl | sh — pola _PipeRemoteToShellMatcher repo)
        Pattern(
            Regex("""\b(curl|wget)[^|;]*\|\s*(su|sh|bash|ash)\b""", RegexOption.IGNORE_CASE),
            "pipa konten remote ke shell"
        ),
        // remount / wipe persist
        Pattern(Regex("""\b(mount\s+-o\s+[^,]*,?remount|wipe\s+(data|cache))\b""", RegexOption.IGNORE_CASE), "remount/wipe partisi")
    )

    private data class Pattern(val regex: Regex, val reason: String)

    /** Alasan persetujuan (dipakai dialog UI). */
    override fun approvalReason(command: String): String? =
        dangerPatterns.firstOrNull { it.regex.containsMatchIn(command) }?.reason

    override fun needsApproval(name: String, argsJson: String): Boolean {
        if (name != "shell") return false
        val args = runCatching { json.parseToJsonElement(argsJson).jsonObject }.getOrNull() ?: return false
        val command = (args["command"] as? JsonPrimitive)?.content ?: return false
        return dangerPatterns.any { it.regex.containsMatchIn(command) }
    }

    // ===================== EKSEKUSI =====================

    override suspend fun execute(name: String, argsJson: String, cwd: String): String =
        withContext(Dispatchers.IO) {
            val args: JsonObject = runCatching {
                json.parseToJsonElement(argsJson).jsonObject
            }.getOrNull() ?: JsonObject(emptyMap())
            fun str(k: String): String = (args[k] as? JsonPrimitive)?.content.orEmpty()
            fun int(k: String, def: Int): Int =
                (args[k] as? JsonPrimitive)?.content?.toIntOrNull() ?: def

            when (name) {
                "shell" -> {
                    val cmd = str("command")
                    if (cmd.isBlank()) return@withContext """{"ok":false,"error":"command kosong"}"""
                    val timeout = int("timeout_sec", 30)
                    // 1) jalankan non-root (root hanya saat dibutuhkan)
                    var r = runner.executeNonRoot(cmd, timeout, cwd)
                    var escalated = false
                    if (!r.isSuccess && looksPermissionDenied(r) && useRootWhenNeeded() &&
                        com.topjohnwu.superuser.Shell.isAppGrantedRoot() == true
                    ) {
                        escalated = true
                        r = runner.executeRoot(cmd, timeout, cwd)
                    }
                    """{"ok":${r.isSuccess},"exit_code":${r.exitCode},"root_escalated":$escalated,"duration_ms":${r.durationMs},"stdout":${jstr(r.stdout)},"stderr":${jstr(r.stderr)}}"""
                }

                "read_file" -> {
                    val f = resolve(str("path"), cwd)
                    when {
                        !f.exists() -> """{"ok":false,"error":"berkas tidak ditemukan: ${jstr(f.path)}"}"""
                        f.isDirectory -> """{"ok":false,"error":"path adalah direktori — pakai list_dir"}"""
                        f.length() > 512 * 1024 -> """{"ok":false,"error":"berkas >512KB — potong dengan shell"}"""
                        else -> """{"ok":true,"size":${f.length()},"content":${jstr(f.readText().take(200_000))}}"""
                    }
                }

                "write_file" -> {
                    val f = resolve(str("path"), cwd)
                    try {
                        f.parentFile?.mkdirs()
                        f.writeText(str("content"))
                        """{"ok":true,"path":${jstr(f.absolutePath)},"bytes":${f.length()}}"""
                    } catch (e: Exception) {
                        // bila ditolak izin & root tersedia: coba via su (root seperlunya)
                        val suRetry = tryRootWrite(f, str("content"))
                        if (suRetry) """{"ok":true,"path":${jstr(f.absolutePath)},"bytes":${f.length()},"via":"su"}"""
                        else """{"ok":false,"error":${jstr(e.message ?: "gagal menulis")}}"""
                    }
                }

                "edit_file" -> {
                    val f = resolve(str("path"), cwd)
                    if (!f.isFile) return@withContext """{"ok":false,"error":"berkas tidak ditemukan"}"""
                    try {
                        val oldText = f.readText()
                        val needle = str("old_string")
                        val repl = str("new_string")
                        val all = (args["replace_all"] as? JsonPrimitive)?.content?.toBooleanStrictOrNull() ?: false
                        if (needle.isEmpty()) return@withContext """{"ok":false,"error":"old_string kosong"}"""
                        val occurrences = Regex(Regex.escape(needle)).findAll(oldText).count()
                        if (occurrences == 0) return@withContext """{"ok":false,"error":"old_string tidak ditemukan"}"""
                        if (occurrences > 1 && !all) return@withContext """{"ok":false,"error":"old_string muncul $occurrences kali — berikan konteks lebih atau set replace_all"}"""
                        val newText = if (all) oldText.replace(needle, repl) else oldText.replaceFirst(needle, repl)
                        f.writeText(newText)
                        """{"ok":true,"replaced":$occurrences,"bytes":${f.length()}}"""
                    } catch (e: Exception) {
                        """{"ok":false,"error":${jstr(e.message ?: "gagal mengedit")}}"""
                    }
                }

                "list_dir" -> {
                    val d = resolve(str("path").ifBlank { cwd }, cwd)
                    if (!d.isDirectory) return@withContext """{"ok":false,"error":"bukan direktori"}"""
                    val items = d.listFiles()
                        ?.sortedWith(compareBy<File> { !it.isDirectory }.thenBy { it.name.lowercase() })
                        ?.take(500)
                        ?: return@withContext """{"ok":false,"error":"tidak bisa membaca direktori"}"""
                    val arr = items.joinToString(",") {
                        """{"name":${jstr(it.name)},"is_dir":${it.isDirectory},"size":${it.length()}}"""
                    }
                    """{"ok":true,"path":${jstr(d.absolutePath)},"count":${items.size},"items":[$arr]}"""
                }

                "search_files" -> {
                    val base = resolve(str("path").ifBlank { cwd }, cwd)
                    if (!base.isDirectory) return@withContext """{"ok":false,"error":"path awal bukan direktori"}"""
                    val pattern = str("pattern").ifBlank { "*" }
                    val content = str("content")
                    val rx = Regex(globToRegex(pattern), RegexOption.IGNORE_CASE)
                    val hits = mutableListOf<String>()
                    var visited = 0
                    fun walk(d: File, depth: Int) {
                        if (hits.size >= 60 || depth > 8 || visited > 4000) return
                        d.listFiles()?.forEach { ch ->
                            if (visited > 4000 || hits.size >= 60) return
                            if (ch.isDirectory) {
                                val n = ch.name
                                if (n != "." && n != ".." && !n.startsWith(".")) walk(ch, depth + 1)
                            } else {
                                visited++
                                if (rx.matches(ch.name)) {
                                    if (content.isBlank()) {
                                        hits.add("""{"path":${jstr(ch.absolutePath)},"size":${ch.length()}}""")
                                    } else if (ch.length() < 512 * 1024 && ch.canRead()) {
                                        val m = runCatching {
                                            ch.readText().lineSequence()
                                                .withIndex()
                                                .firstOrNull { it.value.contains(content, ignoreCase = true) }
                                        }.getOrNull()
                                        if (m != null) {
                                            hits.add(
                                                """{"path":${jstr(ch.absolutePath)},"line":${m.index + 1},"preview":${jstr(m.value.trim().take(200))}}"""
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                    walk(base, 0)
                    """{"ok":true,"base":${jstr(base.absolutePath)},"scanned":$visited,"matches":${hits.size},"items":[${hits.joinToString(",")}]}"""
                }

                "github_clone" -> {
                    val (owner, repo) = parseGithub(str("repo"))
                        ?: return@withContext """{"ok":false,"error":"repo tidak valid — pakai 'owner/name' atau URL GitHub"}"""
                    val branch = str("branch").ifBlank { "main" }
                    val dest = resolve(
                        str("dest").ifBlank { "${cwd.trimEnd('/')}/$repo" }, cwd
                    )
                    try {
                        dest.mkdirs()
                        val url = "https://codeload.github.com/$owner/$repo/zip/refs/heads/$branch"
                        val req = Request.Builder().url(url).get().build()
                        ok.newCall(req).execute().use { resp ->
                            if (!resp.isSuccessful) {
                                return@withContext """{"ok":false,"error":"HTTP ${resp.code} — cek owner/repo/branch","url":${jstr(url)}}"""
                            }
                            val body = resp.body ?: return@withContext """{"ok":false,"error":"body kosong"}"""
                            var files = 0
                            var topDir: String? = null
                            ZipInputStream(body.byteStream()).use { zin ->
                                while (true) {
                                    val e = zin.nextEntry ?: break
                                    if (e.isDirectory) continue
                                    val rel = e.name.removePrefix("$repo-$branch/")
                                    if (rel.isBlank()) continue
                                    val out = File(dest, rel)
                                    out.parentFile?.mkdirs()
                                    out.outputStream().use { zin.copyTo(it) }
                                    files++
                                }
                            }
                            val size = dest.walkTopDown().filter { it.isFile }.sumOf { it.length() }
                            """{"ok":true,"repo":${jstr("$owner/$repo")},"branch":${jstr(branch)},"dest":${jstr(dest.absolutePath)},"files":$files,"bytes":$size,"note":"git clone via arsip zip (Android tanpa biner git)"}"""
                        }
                    } catch (e: Exception) {
                        """{"ok":false,"error":${jstr(e.message ?: "gagal mengunduh")}}"""
                    }
                }

                "web_fetch" -> {
                    val url = str("url")
                    if (!url.startsWith("http")) return@withContext """{"ok":false,"error":"url harus http(s)"}"""
                    val maxChars = int("max_chars", 12_000)
                    try {
                        val req = Request.Builder().url(url).get()
                            .header("User-Agent", "ThorAgentMobile/0.2 (Android)")
                            .build()
                        ok.newCall(req).execute().use { resp ->
                            val ct = resp.header("Content-Type") ?: ""
                            if (!resp.isSuccessful) return@withContext """{"ok":false,"error":"HTTP ${resp.code}","url":${jstr(url)}}"""
                            val raw = resp.body?.string() ?: ""
                            val text = if (ct.contains("html", ignoreCase = true)) stripHtml(raw) else raw
                            """{"ok":true,"url":${jstr(url)},"content_type":${jstr(ct)},"content":${jstr(text.take(maxChars))}}"""
                        }
                    } catch (e: Exception) {
                        """{"ok":false,"error":${jstr(e.message ?: "gagal fetch")}}"""
                    }
                }

                "skill_list" -> {
                    val list = skills.search(str("query"))
                    val arr = list.take(80).joinToString(",") { s ->
                        """{"name":${jstr(s.name)},"category":${jstr(s.category)},"description":${jstr(s.description.take(200))},"path":${jstr(s.path)}}"""
                    }
                    """{"ok":true,"total":${list.size},"skills":[$arr],"hint":"panggil skill_load(name) untuk prosedur lengkap"}"""
                }

                "skill_load" -> {
                    val name = str("name")
                    val path = str("path")
                    val doc = when {
                        path.isNotBlank() -> skills.load(path)
                        name.isNotBlank() -> {
                            val found = skills.search(name).firstOrNull { it.name.equals(name, true) }
                                ?: skills.search(name).firstOrNull()
                            found?.let { skills.load(it.path) } ?: ""
                        }
                        else -> ""
                    }
                    if (doc.isBlank()) return@withContext """{"ok":false,"error":"skill tidak ditemukan — panggil skill_list dulu"}"""
                    """{"ok":true,"content":${jstr(doc.take(20_000))}}"""
                }

                "root_status" -> {
                    val st = root.status()
                    """{"ok":true,"granted":${st.appGrantedRoot},"shell_alive":${st.rootShellAlive},"manager":"${st.manager.label}","hint":${jstr(st.managerHint)}}"""
                }

                else -> """{"ok":false,"error":${jstr("tool tidak dikenal: $name")}}"""
            }
        }

    // ===================== pembantu =====================

    /** Path relatif diresolve ke cwd. */
    private fun resolve(p: String, cwd: String): File =
        if (p.startsWith("/")) File(p) else File(cwd.trimEnd('/'), p.trimStart('/'))

    private fun looksPermissionDenied(r: com.thoragent.app.core.exec.ExecResult): Boolean {
        val t = (r.stderr + " " + r.stdout).lowercase()
        return r.exitCode == 13 || r.exitCode == 126 ||
            (r.exitCode == 1 && t.contains("denied")) ||
            t.contains("permission denied") || t.contains("read-only") ||
            t.contains("operation not permitted") || t.contains("not permitted")
    }

    /** Tulis via su bila root tersedia (fallback write_file yang ditolak izin). */
    private fun tryRootWrite(f: File, content: String): Boolean {
        if (com.topjohnwu.superuser.Shell.isAppGrantedRoot() != true) return false
        return runCatching {
            val tmp = File(context.cacheDir, "thor_write_${System.nanoTime()}")
            tmp.writeText(content)
            val res = com.topjohnwu.superuser.Shell.cmd(
                "mkdir -p '$(dirname '${f.absolutePath}')'; mv '${tmp.absolutePath}' '${f.absolutePath}'"
            ).exec()
            tmp.delete()
            res.isSuccess
        }.getOrDefault(false)
    }

    private fun globToRegex(glob: String): String = buildString {
        append("^")
        glob.forEach { c ->
            when (c) {
                '*' -> append("[^/]*")
                '?' -> append("[^/]")
                '.' -> append("\\.")
                else -> if (c.isLetterOrDigit() || c == '-' || c == '_') append(c) else append("\\").append(c)
            }
        }
        append("$")
    }

    private fun parseGithub(input: String): Pair<String, String>? {
        val s = input.trim().removeSuffix(".git")
        val m1 = Regex("github\\.com[/:]([^/]+)/([^/]+)$", RegexOption.IGNORE_CASE).find(s)
        if (m1 != null) return m1.groupValues[1] to m1.groupValues[2]
        val m2 = Regex("^([\\w.-]+)/([\\w.-]+)$").find(s)
        if (m2 != null) return m2.groupValues[1] to m2.groupValues[2]
        return null
    }

    companion object {
        /** HTML -> teks sederhana (untuk web_fetch). */
        fun stripHtml(html: String): String = html
            .replace(Regex("(?is)<(script|style|noscript)[^>]*>.*?</\\1>"), " ")
            .replace(Regex("(?i)<br\\s*/?>"), "\n")
            .replace(Regex("(?i)</(p|div|li|h[1-6]|tr)>"), "\n")
            .replace(Regex("<[^>]+>"), " ")
            .replace("&nbsp;", " ").replace("&amp;", "&")
            .replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"")
            .replace(Regex("[ \\t]{2,}"), " ")
            .replace(Regex("\\n{3,}"), "\n\n")
            .trim()
    }
}
