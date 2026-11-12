package com.thoragent.app.core.approval

/**
 * Port `tools/approval_detection.py` repo Thor-agent — deteksi perintah berbahaya.
 *
 * Yang diport UTUH dari sumber:
 *  - normalisasi (strip ANSI, NFKC, hapus NUL, gabung backslash-newline, buang
 *    escape backslash & literal kosong, lipat $IFS/$THOR_HOME/$HOME);
 *  - lexer quote-aware `_scan_shell` (state machine tunggal: quote, escape,
 *    komentar, substitusi $() / backtick / ${}) yang jadi dasar SEMUA pemindai;
 *  - `_mark_command_starts` (menandai awal perintah nyata — termasuk subshell
 *    `(cmd)` dan brace-group `{ cmd; }` — supaya aturan ber-anchor _CMDPOS
 *    tidak tertipu prosa dalam kutipan);
 *  - `_mask_quoted_prose` (aturan hardline tanpa token nama-perintah: redirect
 *    ke block device + fork bomb, dicocokkan pada varian ter-mask);
 *  - tabel HARDLINE (lantai di bawah yolo: tidak ada pemulihan) dan
 *    DANGEROUS (butuh persetujuan pengguna);
 *  - `_execution_flag_findings` (sh -c / python -c / interpretasi payload)
 *    sehingga payload yang akan benar-benar dijalankan ikut dipindai.
 *
 * Perbedaan yang DISENGAJA (dan jujur) vs desktop:
 *  - hanya aturan relevan POSIX/Android; tier PowerShell/Windows (cmd, pwsh,
 *    icacls, vssadmin, wmic, taskkill, bcdedit…) tidak dibawa — shell Android
 *    tidak menjalankannya, jadi kehadirannya hanya menambah prompt palsu;
 *  - aturan spesifik desktop (mmap gateway, pnpm/npm, DB SQL) dipertahankan
 *    bila masih bisa dipicu di Android (mis. DROP TABLE via `sqlite3`).
 */

/** Satu aturan deteksi: regex + deskripsi (kunci pola ala repo). */
data class DangerRule(val regex: Regex, val description: String, val quoteMasked: Boolean = false)

object ApprovalDetection {

    // ===================== LEXER (port _scan_shell) =====================

    /** Hasil satu langkah leksikal: kind = char|esc|quote|subst|comment. */
    data class Step(val kind: String, val i: Int, val j: Int, val quote: Char?)

    /**
     * Pemindai tunggal quote/escape untuk semua deteksi — port `_scan_shell`.
     * `subst`: "u" kenali substitusi di luar kutipan, "q" di dalam kutipan ganda.
     * `brace`: tambah `${...}` di luar kutipan. `comments`: lompati komentar.
     * `naiveBacktick`: backtick ditutup pada backtick berikutnya (perilaku masker).
     */
    fun scanShell(
        text: String,
        start: Int = 0,
        end: Int? = null,
        subst: String = "",
        brace: Boolean = false,
        stopUnterminated: Boolean = false,
        naiveBacktick: Boolean = false,
        comments: Boolean = false
    ): List<Step> {
        val out = ArrayList<Step>()
        val n = end ?: text.length
        var quote: Char? = null
        var i = start
        while (i < n) {
            val ch = text[i]
            var kind = "char"
            var j = i + 1
            if (comments && quote == null && isCommentStart(text, i)) {
                kind = "comment"
                val nl = text.indexOf('\n', i)
                j = if (nl < 0 || nl >= n) n else nl
            } else if (quote != '\'' && ch == '\\' && i + 1 < n) {
                kind = "esc"; j = i + 2
            } else if (ch == quote || (quote == null && (ch == '\'' || ch == '"'))) {
                kind = "quote"
            } else if (quote != '\'' && ((if (quote != null) "q" else "u") in subst) &&
                (ch == '`' || text.startsWith("$(", i) || (brace && quote == null && text.startsWith("${'$'}{", i)))
            ) {
                val close: Int? = when {
                    ch == '`' -> if (naiveBacktick) {
                        val at = text.indexOf('`', i + 1)
                        if (at < 0) null else at + 1
                    } else scanBacktickEnd(text, i)
                    text.startsWith("$(", i) -> scanDollarParenEnd(text, i)
                    else -> {
                        val at = text.indexOf('}', i + 2)
                        if (at < 0) null else at + 1
                    }
                }
                if (close != null) {
                    kind = "subst"; j = close
                } else if (stopUnterminated) {
                    out.add(Step("subst", i, -1, quote))
                    return out
                }
            }
            out.add(Step(kind, i, j, quote))
            if (kind == "quote") quote = if (quote == null) ch else null
            i = j
        }
        return out
    }

    private fun scanDollarParenEnd(command: String, start: Int): Int? {
        var depth = 1
        for (s in scanShell(command, start + 2)) {
            if (s.kind == "char" && s.quote == null) {
                if (command.startsWith("$(", s.i)) depth++
                if (command[s.i] == ')') depth--
                if (depth == 0) return s.i + 1
            }
        }
        return null
    }

    private fun scanBacktickEnd(command: String, start: Int): Int? {
        // Backtick tertutup pada backtick tak-ter-escape berikutnya.
        var i = start + 1
        while (i < command.length) {
            if (command[i] == '\\' && i + 1 < command.length) {
                i += 2; continue
            }
            if (command[i] == '`') return i + 1
            i++
        }
        return null
    }

    private fun isCommentStart(command: String, index: Int): Boolean =
        command[index] == '#' && (index == 0 || command[index - 1].isWhitespace() ||
            command[index - 1] in ";&|()<>")

    private fun skipShellWhitespace(command: String, pos: Int): Int {
        var p = pos
        while (p < command.length && command[p].isWhitespace()) p++
        return p
    }

    private fun readShellWord(command: String, pos: Int): Triple<Int, Int, String> {
        var start = skipShellWhitespace(command, pos)
        var end = start
        for (s in scanShell(command, start, subst = "u", brace = true)) {
            if (s.kind == "char" && s.quote == null && (command[s.i].isWhitespace() || command[s.i] in ";&|<>()")) break
            end = s.j
        }
        return Triple(start, end, command.substring(start.coerceAtMost(command.length), end.coerceAtMost(command.length)))
    }

    /** Awal perintah nyata (quote-aware) — port `_iter_shell_command_starts`. */
    private fun shellCommandStarts(command: String): List<Int> {
        val starts = mutableListOf(0)
        fun scan(start: Int, end: Int) {
            var skip = -1
            for (s in scanShell(command, start, end, subst = "uq", stopUnterminated = true, comments = true)) {
                if (s.kind == "subst") {
                    val inner = s.i + if (command[s.i] == '`') 1 else 2
                    starts.add(inner)
                    if (s.j >= 0) scan(inner, s.j - 1)
                } else if (s.kind == "char" && s.quote == null && s.i != skip) {
                    val c = command[s.i]
                    if (c == '(' || c == ';' || c == '\n' ||
                        (c == '{' && (s.i == 0 || command[s.i - 1].isWhitespace() || command[s.i - 1] in "(;&|)"))
                    ) {
                        starts.add(s.i + 1)
                    } else if (c == '&' || c == '|') {
                        val repeated = s.i + 1 < end && command[s.i + 1] == c
                        if (repeated) skip = s.i + 1
                        starts.add(s.i + 1 + if (repeated) 1 else 0)
                    }
                }
            }
        }
        scan(0, command.length)

        val seen = mutableSetOf<Int>()
        val out = mutableListOf<Int>()
        var idx = 0
        while (idx < starts.size) {
            var start = skipShellWhitespace(command, starts[idx])
            if (start < command.length && !seen.contains(start) && !isCommentStart(command, start)) {
                seen.add(start)
                out.add(start)
                val word = readShellWord(command, start).third
                if (word in SHELL_COMMAND_TRANSITIONS) starts.add(readShellWord(command, start).second)
            }
            idx++
        }
        return out
    }

    /** Sisipkan penanda sebelum setiap awal perintah nyata — port `_mark_command_starts`. */
    fun markCommandStarts(command: String, marker: String = "\n"): String {
        val offsets = shellCommandStarts(command).filter { it > 0 }.sorted()
        if (offsets.isEmpty()) return command
        val sb = StringBuilder()
        var cursor = 0
        for (o in offsets) {
            sb.append(command, cursor, o).append(marker)
            cursor = o
        }
        sb.append(command, cursor, command.length)
        return sb.toString()
    }

    /** Kosongkan ISI kutipan (posisi tanpa anchor) — port `_mask_quoted_prose`. */
    fun maskQuotedProse(command: String): String {
        val sb = StringBuilder()
        var cursor = 0
        for (s in scanShell(command, subst = "q", naiveBacktick = true)) {
            if (s.quote == null || s.kind == "quote" || s.kind == "subst") {
                sb.append(command, cursor.coerceAtMost(s.i), s.i)
                sb.append(command, s.i, s.j)
            } else {
                sb.append(command, cursor.coerceAtMost(s.i), s.i)
                sb.append(" ".repeat(maxOf(0, s.j - s.i)))
            }
            cursor = s.j
        }
        sb.append(command.substring(cursor.coerceAtMost(command.length)))
        return sb.toString()
    }

    /** Ganti newline di dalam kutipan dengan spasi — port `_mask_quoted_newlines`. */
    fun maskQuotedNewlines(command: String): String {
        if (!command.contains('\n')) return command
        return maskQuotedNewlinesSpan(command, 0, command.length)
    }

    private fun maskQuotedNewlinesSpan(command: String, start: Int, end: Int): String {
        val sb = StringBuilder()
        for (s in scanShell(command, start, end, subst = "q")) {
            if (s.kind == "subst") {
                val bodyStart = s.i + if (command.startsWith("$(", s.i)) 2 else 1
                val bodyEnd = if (s.j > 0) s.j - 1 else s.i
                sb.append(command, s.i, bodyStart)
                sb.append(maskQuotedNewlinesSpan(command, bodyStart, maxOf(bodyStart, bodyEnd)))
                sb.append(command, bodyStart.coerceAtMost(command.length), s.j.coerceAtMost(command.length))
            } else if (s.quote != null && s.kind == "char" && s.i < command.length && command[s.i] == '\n') {
                sb.append(' ')
            } else {
                sb.append(command, s.i, s.j)
            }
        }
        return sb.toString()
    }

    // ===================== NORMALISASI =====================

    /** Port `_normalize_command_for_detection` (tanpa fold home absolut: tidak ada $HOME tetap di Android). */
    fun normalize(command: String): String {
        var c = stripAnsi(command).replace("\u0000", "")
        c = java.text.Normalizer.normalize(c, java.text.Normalizer.Form.NFKC)
        // Gabung backslash-newline (rm -rf \<newline>/ berjalan sebagai rm -rf /).
        c = c.replace(Regex("\\\\\\r?\\n"), "")
        // Buang escape backslash (r\m -> rm) dan literal kosong (r''m -> rm).
        c = c.replace(Regex("\\\\([^\\n])"), "$1")
        c = c.replace(Regex("''|\"\""), "")
        // Lipat $IFS / ${IFS...} ke spasi (port `normalize_command`).
        // Pola regex dibangun lewat char agar '$' tidak dibaca sebagai template Kotlin.
        val dollar = "$"
        return c.replace(Regex("\\" + dollar + "\\{IFS\\b[^}]*}|\\" + dollar + "IFS\\b"), " ")
    }

    /**
     * Lowercase sambil mempertahankan token ber-awalan '-' byte-for-byte.
     *
     * Port `_lower_preserving_flags`. Penting: spasi TIDAK boleh hilang —
     * versi awal memakai `split(capturing group)` + `joinToString("")` yang
     * membuang separator dan mengubah `rm -rf /` menjadi `rm-rf/`, membuat
     * semua aturan berbasis token tidak pernah cocok.
     */
    private fun lowerPreservingFlags(command: String): String =
        command.split(" ").joinToString(" ") { t ->
            if (t.startsWith("-")) t else t.lowercase()
        }

    private fun stripAnsi(s: String): String =
        s.replace(Regex("\u001B\\[[0-9;?]*[ -/]*[@-~]"), "")
            .replace(Regex("\u001B\\][^\u0007\u001B]*(\u0007|\u001B\\\\)"), "")

    /**
     * Varian deteksi dari satu perintah mentah — inti port
     * `_command_detection_variants`: perintah ternormalisasi, varian
     * penanda-awal-perintah, payload sh -c/-lc, dan varian deobfuscated.
     */
    fun detectionVariants(command: String): List<String> {
        val out = LinkedHashSet<String>()
        val normalized = normalize(maskQuotedNewlines(command))
        out.add(normalized)
        val marked = markCommandStarts(normalized)
        if (marked != normalized) out.add(marked)
        // Penanda pada teks mentah (paritas kutipan utuh), lalu dinormalisasi.
        val faithful = normalize(markCommandStarts(maskQuotedNewlines(command), marker = " \n"))
        out.add(faithful)
        // Payload yang benar-benar dieksekusi (sh -c "...", bash -lc "..."). BFS agar payload
        // bersarang (sh -c "sh -c '...'") juga dipindai.
        val pending = ArrayDeque<String>()
        pending.add(normalized)
        var guard = 0
        while (pending.isNotEmpty() && guard < 12) {
            guard++
            for (p in executionFlagPayloads(pending.removeFirst())) {
                if (out.add(p)) {
                    val mp = markCommandStarts(p)
                    if (mp != p) out.add(mp)
                    pending.add(p)
                }
            }
        }
        return out.toList()
    }

    /** Port `_iter_shell_command_word_spans` yang dipersempit ke kata-kata awal perintah. */
    private fun commandWordSpans(command: String): List<Int> = shellCommandStarts(command)

    private fun deobfuscateWord(word: String): String =
        word.replace(Regex("\\\\([^\\n])"), "$1").replace(Regex("''|\"\""), "")

    /**
     * Payload dari flag eksekusi interpreter/shell — port `_execution_flag_findings`.
     * Dikembalikan sebagai daftar string payload (yang akan benar-benar dijalankan).
     */
    fun executionFlagPayloads(command: String): List<String> {
        val payloads = mutableListOf<String>()
        for (segment in topLevelSegments(command)) {
            for (start in commandWordSpans(segment)) {
                val word = readShellWord(segment, start)
                val executable = deobfuscateWord(word.third)
                val name = executable.substringAfterLast('/').substringAfterLast('\\').lowercase()
                val args = tokenize(segment.substring(minOf(start, segment.length)))
                if (args.isEmpty()) continue
                if (name in SHELL_NAMES) {
                    val payload = bashExecPayload(args.drop(1))
                    if (payload != null) payloads.add(payload)
                }
            }
        }
        return payloads
    }

    private fun topLevelSegments(command: String): List<String> {
        val out = mutableListOf<String>()
        var start = 0
        for (s in scanShell(command, comments = true)) {
            if (s.kind == "comment" || (s.kind == "char" && s.quote == null && command[s.i] in ";&|\n")) {
                if (start < s.i) out.add(command.substring(start, s.i))
                start = s.j
            }
        }
        if (start < command.length) out.add(command.substring(start))
        return out
    }

    private fun tokenize(segment: String): List<String> {
        val out = mutableListOf<String>()
        var i = 0
        val sb = StringBuilder()
        while (i < segment.length) {
            val s = scanShell(segment, i, subst = "u", brace = true)
            if (s.isEmpty()) break
            val first = s.first()
            val ch = segment[first.i]
            if (first.quote == null && (ch.isWhitespace() || ch in ";&|")) {
                if (sb.isNotEmpty()) {
                    out.add(sb.toString()); sb.clear()
                }
                i = first.j
                continue
            }
            if (first.kind == "esc") {
                // Escape: karakter setelah backslash diperlakukan literal.
                sb.append(segment[first.i + 1])
                i = first.j
                continue
            }
            if (first.kind == "quote") {
                // scanShell melaporkan SATU karakter kutip (j == i+1), bukan
                // rentang kutipan utuh, jadi kutip tutup harus dicari sendiri.
                // Tanpa ini payload `sh -c 'rm -rf /'` pecah jadi token terpisah
                // dan _bash_exec_payload tidak pernah menemukan string perintah.
                val quoteChar = ch
                var k = first.j
                while (k < segment.length && segment[k] != quoteChar) {
                    if (segment[k] == '\\' && k + 1 < segment.length) k += 1
                    k += 1
                }
                val close = if (k < segment.length) k else segment.length
                sb.append(segment, first.i + 1, close)
                i = if (k < segment.length) k + 1 else segment.length
                continue
            }
            sb.append(segment, first.i, first.j)
            i = first.j
        }
        if (sb.isNotEmpty()) out.add(sb.toString())
        return out
    }

    /** Port `_bash_exec_payload`: cari flag -c dan ambil string perintah miliknya. */
    private fun bashExecPayload(args: List<String>): String? {
        var index = 0
        while (index < args.size) {
            val token = args[index]
            if (token == "--" || !(token.startsWith("-") || token.startsWith("+"))) break
            if (token in BASH_OPTIONS_WITH_ARG) {
                index += 2; continue
            }
            val chars = token.substring(1)
            if (token.startsWith("--") || !chars.all { it in BASH_SHORT_OPTION_LETTERS }) {
                index += 1; continue
            }
            val consumed = if (chars.contains('O') || chars.contains('o')) 1 else 0
            if (chars.contains('c')) {
                val pi = index + 1 + consumed
                return if (pi < args.size) args[pi] else null
            }
            index += 1 + consumed
        }
        return null
    }

    // ===================== ATURAN =====================

    private val RE = setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)

    // -- _CMDPOS: posisi awal perintah (awal string, newline, backtick, $( ).
    private const val CMDPOS =
        "(?:^|[\\n`]|\\$\\()\\s*(?:sudo\\s+(?:-[^\\s]+\\s+)*)?(?:env\\s+(?:\\w+=\\S*\\s+)*)?" +
            "(?:(?:exec|nohup|setsid|time)\\s+)*\\s*"

    private fun hardlineRmPath(pathAlt: String, tail: String = "(?:\\s|$|[)`;|&])"): String =
        "(?:[\"'](?:$pathAlt)[\"']|(?:$pathAlt)$tail)"

    /**
     * Daftar direktori sistem HARDLINE — disalin persis dari `_HARDLINE_SYSTEM_DIRS`
     * repo sumber. Sengaja TIDAK ditambah partisi Android (/system, /data,
     * /vendor, /proc, /sys): sumber memperlakukannya sebagai DANGEROUS
     * ("delete in root path") sehingga pengguna masih punya jalur persetujuan,
     * sedangkan HARDLINE tidak bisa dilewati sama sekali.
     */
    private const val HARDLINE_SYSTEM_DIRS =
        "/home|/home/\\*|/root|/root/\\*|/etc|/etc/\\*|/usr|/usr/\\*|" +
            "/var|/var/\\*|/bin|/bin/\\*|/sbin|/sbin/\\*|/boot|/boot/\\*|/lib|/lib/\\*"

    private const val RM_FLAG_PREFIX = CMDPOS + "rm\\s+(-[^\\s]*\\s+)*"
    private const val DEV_BLOCK = "(sd|nvme|hd|mmcblk|vd|xvd)"

    /** Lantai tanpa pemulihan — tidak dilewati yolo. */
    val HARDLINE: List<DangerRule> = listOf(
        DangerRule(
            Regex(RM_FLAG_PREFIX + hardlineRmPath("/(?:(?:\\.\\.?)?/)*(?:\\.\\.?)?\\**|/ \\*"), RE),
            "recursive delete of root filesystem"
        ),
        DangerRule(
            Regex(RM_FLAG_PREFIX + hardlineRmPath(HARDLINE_SYSTEM_DIRS), RE),
            "recursive delete of system directory"
        ),
        DangerRule(
            Regex(RM_FLAG_PREFIX + hardlineRmPath("(?:~|\\$\\{?HOME\\}?)(?:/?|/\\*)"), RE),
            "recursive delete of home directory"
        ),
        DangerRule(Regex(CMDPOS + "mkfs(\\.[a-z0-9]+)?\\b", RE), "format filesystem (mkfs)"),
        DangerRule(Regex(CMDPOS + "dd\\b[^\\n]*\\bof=/dev/$DEV_BLOCK[a-z0-9]*", RE), "dd to raw block device"),
        // Aturan tanpa token nama-perintah: dicocokkan pada varian ter-mask.
        DangerRule(Regex(">\\s*/dev/$DEV_BLOCK[a-z0-9]*\\b", RE), "redirect to raw block device", quoteMasked = true),
        DangerRule(Regex(":\\(\\)\\s*\\{\\s*:\\s*\\|\\s*:\\s*&\\s*}\\s*;\\s*:", RE), "fork bomb", quoteMasked = true),
        DangerRule(Regex(CMDPOS + "kill\\s+(-[^\\s]+\\s+)*-1\\b", RE), "kill all processes"),
        DangerRule(Regex(CMDPOS + "(shutdown|reboot|halt|poweroff)\\b", RE), "system shutdown/reboot"),
        DangerRule(Regex(CMDPOS + "init\\s+[06]\\b", RE), "init 0/6 (shutdown/reboot)"),
        DangerRule(Regex(CMDPOS + "systemctl\\s+(poweroff|reboot|halt|kexec)\\b", RE), "systemctl poweroff/reboot"),
        DangerRule(Regex(CMDPOS + "telinit\\s+[06]\\b", RE), "telinit 0/6 (shutdown/reboot)"),
        // Android: reboot ke bootloader/recovery/fastboot = tanpa jalur pemulihan bila salah
        DangerRule(Regex(CMDPOS + "reboot\\s+(bootloader|recovery|fastboot|sideload)\\b", RE), "reboot to bootloader/recovery"),
        DangerRule(Regex(CMDPOS + "setprop\\s+sys\\.(powerctl|boot)\\b", RE), "system power control via setprop")
    ).map { it }

    /** Membutuhkan persetujuan pengguna (yolo melewati). */
    val DANGEROUS: List<DangerRule> = listOf(
        DangerRule(Regex("\\brm\\s+(-[^\\s]*\\s+)*/", RE), "delete in root path"),
        DangerRule(Regex("\\brm\\s+-[^\\s]*r", RE), "recursive delete"),
        DangerRule(Regex("\\brm\\s+--recursive\\b", RE), "recursive delete (long flag)"),
        DangerRule(
            Regex(
                "\\brm\\s+(?!--(?:\\s|$))(?:(?!\\s--(?:\\s|$))[^\\n\"';|&])*\\s(?:-[a-z]*r[a-z]*\\b|--recursive\\b)", RE
            ),
            "recursive delete (flags after operands)"
        ),
        DangerRule(Regex("\\bchmod\\s+(-[^\\s]*\\s+)*(777|666|o\\+[rwx]*w|a\\+[rwx]*w)\\b", RE), "world/other-writable permissions"),
        DangerRule(Regex("\\bchmod\\s+--recursive\\b.*(777|666|o\\+[rwx]*w|a\\+[rwx]*w)", RE), "recursive world/other-writable (long flag)"),
        DangerRule(Regex("\\bchown\\s+(-[^\\s]*)?R\\s+root", RE), "recursive chown to root"),
        DangerRule(Regex("\\bchown\\s+--recur[a-z]*\\b.*root", RE), "recursive chown to root (long flag)"),
        DangerRule(Regex(CMDPOS + "mkfs\\b", RE), "format filesystem"),
        DangerRule(Regex(CMDPOS + "dd\\s+.*if=", RE), "disk copy"),
        DangerRule(Regex(">\\s*/dev/sd", RE), "write to block device"),
        // Android: flash partisi & wipe data
        DangerRule(Regex("\\b(fastboot\\s+flash|flash_image|magiskboot|edl\\s+w)\\b", RE), "flash partition/boot image"),
        DangerRule(Regex("\\b(wipe|mkfs)\\s+(data|cache|system|userdata)\\b", RE), "wipe partition"),
        DangerRule(Regex("\\bmount\\s+-o\\s+[^,]*remount\\b", RE), "remount filesystem read-write"),
        DangerRule(Regex("\\bDROP\\s+(TABLE|DATABASE)\\b", RE), "SQL DROP"),
        DangerRule(Regex("\\bDELETE\\s+FROM\\b(?![^\\n]*\\bWHERE\\b)", RE), "SQL DELETE without WHERE"),
        DangerRule(Regex("\\bTRUNCATE\\s+(TABLE)?\\s*\\w", RE), "SQL TRUNCATE"),
        DangerRule(Regex(">\\s*/etc/", RE), "overwrite system config"),
        DangerRule(Regex("\\bsystemctl\\s+(-[^\\s]+\\s+)*(stop|restart|disable|mask)\\b", RE), "stop/restart system service"),
        DangerRule(Regex("\\bkill\\s+-9\\s+-1\\b", RE), "kill all processes"),
        DangerRule(Regex("\\bpkill\\s+-9\\b", RE), "force kill processes"),
        DangerRule(Regex("\\bkillall\\s+(-[^\\s]*\\s+)*-(9|KILL|SIGKILL)\\b", RE), "force kill processes (killall -KILL)"),
        DangerRule(Regex("\\bkillall\\s+(-[^\\s]*\\s+)*-s\\s+(KILL|SIGKILL|9)\\b", RE), "force kill processes (killall -s KILL)"),
        DangerRule(Regex("\\bkillall\\s+(-[^\\s]*\\s+)*-r\\b", RE), "kill processes by regex (killall -r)"),
        DangerRule(Regex(":\\(\\)\\s*\\{\\s*:\\s*\\|\\s*:\\s*&\\s*}\\s*;\\s*:", RE), "fork bomb"),
        DangerRule(
            Regex(
                "\\b(curl|wget)\\b.*(?:\\|&?\\s*(?:sudo\\s+)?(?:sh|bash|zsh|ksh|dash|ash)" +
                    "|>\\(\\s*(?:sh|bash|zsh|ksh|dash|ash))(?=['\"\\s;&|<>)]|\\b)",
                RE
            ),
            "pipe remote content to shell"
        ),
        DangerRule(
            Regex("(?<![\\w.-])(?:sh|bash|zsh|ksh|dash)\\s+<\\s*<?\\s*\\(\\s*(curl|wget)\\b", RE),
            "execute remote script via process substitution"
        ),
        DangerRule(Regex(CMDPOS + "(?:source|\\.)\\s+<\\s*\\(\\s*(curl|wget)\\b", RE), "execute remote script via process substitution"),
        DangerRule(Regex("(?:\\beval\\b|\\bsource\\b|\\.)\\s*(?:\\$\\(\\s*|`\\s*)(?:curl|wget)\\b", RE), "execute remote content via command substitution"),
        // Perangkat Android: jalur kredensial & partisi sensitif yang bisa dihapus/ditimpa
        DangerRule(Regex("/data/(adb|misc)/", RE), "access to root-only device path"),
        DangerRule(Regex("/data/data/[\\w.]+", RE), "access to app private data of another package"),
        DangerRule(Regex("\\bpm\\s+(uninstall|clear|disable)\\b", RE), "uninstall/disable Android package"),
        DangerRule(Regex("\\bcontent\\s+(delete|insert|update)\\b", RE), "modify Android content provider"),
        DangerRule(Regex("\\bsettings\\s+put\\b", RE), "modify Android system settings"),
        DangerRule(Regex("\\binput\\s+(keyevent|tap|swipe|text)\\b", RE), "inject input events (device control)")
    )

    private val HARDLINE_COMPILED = HARDLINE
    private val DANGEROUS_COMPILED = DANGEROUS

    // ===================== ENTRY POINT =====================

    /** Hasil deteksi: level + deskripsi aturan + varian yang memicu. */
    data class Finding(val level: Level, val description: String) {
        enum class Level { HARDLINE, DANGEROUS }
    }

    /**
     * Cari SEMUA temuan (hardline + dangerous). Port `_iter_dangerous_descriptions`:
     * tiap varian dipindai, aturan tanpa-anchor dicocokkan pada varian ter-mask,
     * dan payload sh -c ikut dipindai lewat [detectionVariants].
     */
    fun detectAll(command: String): List<Finding> {
        if (command.isBlank()) return emptyList()
        val findings = LinkedHashMap<String, Finding>()
        for (variant in detectionVariants(command)) {
            val lower = lowerPreservingFlags(variant)
            val masked by lazy { lowerPreservingFlags(maskQuotedProse(variant)) }
            for (rule in HARDLINE_COMPILED + DANGEROUS_COMPILED) {
                val haystack = if (rule.quoteMasked) masked else lower
                if (rule.regex.containsMatchIn(haystack)) {
                    val level = if (rule in HARDLINE_COMPILED) Finding.Level.HARDLINE else Finding.Level.DANGEROUS
                    findings.putIfAbsent(rule.description, Finding(level, rule.description))
                }
            }
        }
        return findings.values.toList()
    }

    /** Deskripsi temuan pertama untuk dialog persetujuan (null = aman). */
    fun dangerReason(command: String): String? = detectAll(command).firstOrNull()?.description

    /** Deskripsi temuan HARDLINE pertama (null bila tidak ada). */
    fun hardlineReason(command: String): String? =
        detectAll(command).firstOrNull { it.level == Finding.Level.HARDLINE }?.description

    /** Apakah perintah menyentuh aturan hardline (lantai di bawah yolo). */
    fun isHardline(command: String): Boolean = hardlineReason(command) != null

    /** Apakah perintah butuh persetujuan. */
    fun isDangerous(command: String): Boolean = detectAll(command).isNotEmpty()

    private val SHELL_COMMAND_TRANSITIONS = setOf("if", "then", "else", "elif", "do", "while", "until", "!")
    private val SHELL_NAMES = setOf("bash", "sh", "zsh", "ksh", "dash", "ash", "mksh")
    private val BASH_OPTIONS_WITH_ARG = setOf("-O", "+O", "-o", "+o", "--init-file", "--rcfile")
    private val BASH_SHORT_OPTION_LETTERS = "ilrsDcabefhkmnptuvxBCEHPTOo".toSet()
}
