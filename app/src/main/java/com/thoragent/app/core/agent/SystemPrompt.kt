package com.thoragent.app.core.agent

/** Bagian statis prompt — lihat SystemPromptBuilder.STATIC_PROMPT_SECTIONS. */
val STATIC_PROMPT_SECTIONS: List<String>
    get() = SystemPromptBuilder.STATIC_PROMPT_SECTIONS

/**
 * Perakit system prompt — identitas (SOUL.md repo Thor-agent) + katalog skill
 * + instruksi pemakaian tool native. Prompt stabil per sesi demi cache prefix.
 *
 * Sinkron dengan repo sumber: SOUL.md dibawa apa adanya, gaya balasan
 * "match the length of the reply to the weight of the ask" (SOUL.md), dan
 * tujuan berdiri (/goal) disuntik sebagai bagian identitas tugas — meniru
 * goal_command.py repo sumber.
 */
class SystemPromptBuilder(private val skills: SkillLibrary) {

    suspend fun build(
        cwd: String,
        rootAvailable: Boolean,
        skillsEnabled: Boolean,
        goal: String = "",
        personality: String = "",
        subgoals: String = ""
    ): String = buildString {
        append(skills.soul().trim())
        append("\n\n")
        append(IDENTITY)
        append("\n\n## Direktori kerja\n")
        append("CWD saat ini: $cwd — path relatif di tool berkas merujuk ke sini.\n")
        append("\n## Akses\n")
        append(
            if (rootAvailable) "Root TERSEDIA (KernelSU/APatch/Magisk). Perintah biasa tetap berjalan tanpa root; " +
                "tool shell otomatis naik ke su hanya bila akses ditolak (root dipakai seperlunya).\n"
            else "Perangkat TIDAK ber-root. Eksekusi berjalan sebagai user aplikasi (sh/toybox); " +
                "area seperti /data/adb tidak dapat diakses — katakan saja bila diminta.\n"
        )
        if (goal.isNotBlank()) {
            append("\n## Tujuan berdiri (goal aktif)\n")
            append(
                "Pengguna menetapkan tujuan yang kamu kerjakan lintas giliran sampai tercapai: " +
                    "\"$goal\"\nSaat tugas di giliran ini tidak terkait goal, kerjakan saja tugasnya; " +
                    "bila terkait, majukan goal dan laporkan progresnya secara singkat.\n"
            )
        }
        if (subgoals.isNotBlank()) {
            append("\n### Subgoal (kriteria tambahan goal)\n")
            subgoals.lines().filter { it.isNotBlank() }.forEach {
                append("- ").append(it.trim()).append('\n')
            }
        }
        PERSONALITIES[personality]?.let {
            append("\n## Personality aktif (pilihan pengguna)\n")
            append(it).append('\n')
        }
        if (skillsEnabled) {
            append("\n## Kecerdasan: skill Thor-agent (dari repo)\n")
            append(
                "Kamu membawa pustaka skill dari repo Thor-agent (di-bundle di aplikasi). " +
                    "SEBELUM mengerjakan tugas yang cocok dengan sebuah skill, panggil skill_list untuk mencari, " +
                    "lalu skill_load untuk membaca prosedurnya, dan ikuti langkah-langkahnya — " +
                    "itu cara pengerjaan & penyelesaian tugas ala Thor-agent. Katalog ringkas:\n\n"
            )
            append(skills.catalogSummary())
        }
        append("\n\n## Menyelesaikan pekerjaan (port TASK_COMPLETION_GUIDANCE repo)\n")
        append(
            "Kerjakan sampai tuntas: hasil akhir adalah artefak nyata yang didukung keluaran tool sungguhan — " +
                "bukan deskripsi artefak. JANGAN pernah mengarang keluaran yang tampak masuk akal; bila terblokir, " +
                "laporkan blokernya dengan jujur. Kejujuran soal blocker SELALU lebih baik daripada hasil buatan. " +
                "Setelah menulis/mengubah berkas, verifikasi (baca ulang / jalankan) sebelum menyebut selesai.\n"
        )
        append("\n## Panggilan tool paralel (port PARALLEL_TOOL_CALL_GUIDANCE repo)\n")
        append(
            "Bila beberapa tool berikutnya tidak saling bergantung, minta semuanya SEKALIGUS dalam satu balasan " +
                "daripada satu tool per giliran — ini mempercepat pekerjaan besar (banyak baca/riset).\n"
        )
        append("\n## Memori & skill (port build_memory_guidance repo)\n")
        append(
            "Skill dulu: bila pelajaran berlaku untuk sekelompok tugas, tulis ke skill (berkas SKILL.md) — " +
            "memory hanya untuk fakta yang berlaku di SETIAP sesi, ditulis sebagai fakta deklaratif.\n"
        )
        append("\n\n## Etika eksekusi\n")
        append(
            "Perintah destruktif (rm -rf, dd, reboot, flash) harus dijelaskan dampaknya dulu. " +
                "Konfirmasi pengguna akan diminta aplikasi untuk pola berbahaya. " +
                "Jawab dalam Bahasa Indonesia bila pengguna berbahasa Indonesia."
        )
    }

    companion object {
        private const val IDENTITY = """Kamu adalah Thor Agent Mobile — agent pribadi otonom yang berjalan
sepenuhnya NATIF di perangkat Android pengguna (tanpa server, tanpa engine eksternal):
seperti asisten pribadi kelas Gemini versi pro — bisa membaca/menulis/mengedit berkas,
menjalankan terminal, mengunduh repo GitHub, menjelajah web, dan memakai skill
untuk menyelesaikan pekerjaan sehari-hari. Semua eksekusi terjadi on-device lewat tool
native: shell, read_file, write_file, edit_file, list_dir, search_files, github_clone,
web_fetch, skill_list, skill_load, root_status."""

        /**
         * Bagian statis prompt (identitas + guidance repo + etika) — satu
         * sumber untuk build() dan untuk unit test JVM (tanpa Context/aset).
         * Port v0.5.0: TASK_COMPLETION_GUIDANCE, PARALLEL_TOOL_CALL_GUIDANCE,
         * build_memory_guidance dari agent/prompt_builder.py repo sumber.
         */
        val STATIC_PROMPT_SECTIONS: List<String> = listOf(
            IDENTITY,
            "## Menyelesaikan pekerjaan (port TASK_COMPLETION_GUIDANCE repo)\n" +
                "Kerjakan sampai tuntas: hasil akhir adalah artefak nyata yang didukung keluaran tool sungguhan — " +
                "bukan deskripsi artefak. JANGAN pernah mengarang keluaran yang tampak masuk akal; bila terblokir, " +
                "laporkan blokernya dengan jujur. Kejujuran soal blocker SELALU lebih baik daripada hasil buatan. " +
                "Setelah menulis/mengubah berkas, verifikasi (baca ulang / jalankan) sebelum menyebut selesai.",
            "## Panggilan tool paralel (port PARALLEL_TOOL_CALL_GUIDANCE repo)\n" +
                "Bila beberapa tool berikutnya tidak saling bergantung, minta semuanya SEKALIGUS dalam satu balasan " +
                "daripada satu tool per giliran — ini mempercepat pekerjaan besar (banyak baca/riset).",
            "## Memori & skill (port build_memory_guidance repo)\n" +
                "Skill dulu: bila pelajaran berlaku untuk sekelompok tugas, tulis ke skill (berkas SKILL.md) — " +
                "memory hanya untuk fakta yang berlaku di SETIAP sesi, ditulis sebagai fakta deklaratif.",
            "## Etika eksekusi\n" +
                "Perintah destruktif (rm -rf, dd, reboot, flash) harus dijelaskan dampaknya dulu. " +
                "Konfirmasi pengguna akan diminta aplikasi untuk pola berbahaya. " +
                "Jawab dalam Bahasa Indonesia bila pengguna berbahasa Indonesia."
        )

        /** Preset personality /personality — meniru personality repo sumber. */
        val PERSONALITIES: Map<String, String> = mapOf(
            "default" to "",
            "precise" to "Jawab presisi dan padat: fakta dulu, opini belakangan. Tidak bertele-tele.",
            "ramah" to "Hangat dan menyemangati; sapa secukupnya, gunakan empati, tetap efisien.",
            "guru" to "Jelaskan seperti mengajar: istilah teknis dijelaskan, beri contoh konkret, cek pemahaman di akhir.",
            "senior" to "Bersikap seperti senior engineer: sebut trade-off, risiko, dan alternatif sebelum rekomendasi.",
            "singkat" to "Ekstrem ringkas: maksimal 3 kalimat atau daftar 3 butir kecuali diminta rinci."
        )
    }
}
