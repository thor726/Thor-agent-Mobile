# ⚡ Thor Agent Mobile — Android Native (Kotlin)

Aplikasi Android native yang membawa **Thor Agent** ke ponsel Anda —
agent pribadi otonom seperti Gemini versi pro: bisa membaca/menulis/mengedit
berkas, menjalankan terminal, mengunduh repo GitHub, menjelajah web, dan
memakai skill **cara pengerjaan & penyelesaian tugas** dari repo sumber:
**https://github.com/dwisetyabudi15581/Thor-agent.git**

> **v0.5.0 — SIDEBAR ALA GEMINI + SLASH COMMAND JADI TOMBOL + MODEL PER PENYEDIA.**
> Sidebar (drawer) ala Gemini: Chat baru, riwayat sesi, navigasi, goal aktif.
> Slash command populer kini tombol aksi cepat di atas composer — tinggal
> disentuh, tanpa mengetik "/". ModelPicker memilih model **per penyedia
> layanan** (OpenRouter, Thor Portal, OpenAI, Groq, Z.ai, LM Studio, Ollama,
> + model hidup endpoint aktif) seperti `/model --provider` repo sumber.
> "Otak" disinkronkan: guidance TASK_COMPLETION / PARALLEL_TOOL_CALLS /
> skills-first-memory dari `agent/prompt_builder.py` repo sumber kini
> di-bundle ke system prompt. Bug fix: reasoning_content tidak lagi
> mencemari jawaban, approval floor hanya untuk shell (sesuai repo),
> iterasi terakhir dipaksa menutup dengan ringkasan (grace call repo).

| | |
|---|---|
| Paket | `com.thoragent.app` (debug: `.debug`) |
| Versi | 0.5.0-thor-mobile |
| Bahasa UI | Indonesia |
| minSdk / targetSdk | 29 (Android 10) / 36 (Android 16) |
| Stack | Kotlin · Jetpack Compose (Material 3) · Coroutines/Flow · Room · DataStore · OkHttp SSE · libsu 6.0.0 · WorkManager · Foreground Service |
| Tema | Gelap + emas Thor (`#FFD700` / `#FFBF00` — dari `ui-tui/src/theme.ts` repo) |

## Fitur

1. **Agent pribadi native (Direct Chat)** — loop agen Kotlin murni:
   system prompt (identitas `SOUL.md` + katalog skill repo) → LLM streaming
   SSE → tool_calls dieksekusi on-device → hasil kembali ke LLM → ulangi
   sampai balasan akhir. **Tanpa server, tanpa engine eksternal.**
   Panggilan tool ter-visualisasi sebagai kartu lipat dengan ikon per-tool
   (accordion ala TUI Thor), balasan dirender **Markdown penuh**
   (heading, bold/italic, blok kode, daftar, kutipan, link).
2. **Slash command → TOMBOL + UI INTERAKTIF — SEMUA 105 JALAN** — v0.5.0: 15 perintah populer jadi tombol aksi cepat (QuickActions) di atas composer; seluruh
   `COMMAND_REGISTRY` repo sumber (**105 perintah** + terjemahan
   `locales/id.yaml`) di-bundle sebagai `catalog/slash_commands.json`, dan
   kini TIAP perintah punya jalur eksekusi Android (tier di
   `MobileCommands`):
   - **Natif (81)**: `/queue` (antrean pesan sungguhan — pesan saat agent
     sibuk menunggu, bukan hilang), `/bg` `/btw` `/steer` `/pause`
     `/approve` `/deny`, `/image` (photo picker), `/paste`, `/export`
     `/import` `/snapshot` (arszip profil + share sheet), `/voice` (TTS
     on-device), `/skin` `/personality` `/timestamps` `/focus` `/verbose`
     `/statusbar` `/battery`, `/pet` `/hatch` (maskot), `/heartbeat`
     `/loop` `/cron`, `/insights` `/debug` `/update`, `/journey`, `/branch`,
     dst.
   - **Agent on-device (11)**: `/worktree` `/diff` `/init` `/refine`
     `/review` `/moa` `/kanban` `/suggestions` `/blueprint` `/curator`
     `/rollback` — dibungkus instruksi terstruktur (pola `/plan` `/learn`),
     dikerjakan agent dengan tool-nya.
   - **Padanan jujur (13)**: perintah platform desktop (`/browser` `/wake`
     `/egress` `/subscription` dst.) menjawab dengan info + padanan Android
     terdekat (mis. `/handoff` → share profil, `/footer` → `/statusbar`).
   Ketik `/` di composer → **autocomplete chip langsung** + **SlashPalette**
   (bottom sheet): pencarian, filter kategori, badge tier dukungan.
3. **Pemilihan model otomatis (v0.3.0)** — katalog model repo sumber
   (`website/static/api/model-catalog.json`: 75 OpenRouter + 59 Thor,
   default `z-ai/glm-5.3`) di-bundle. Model profil `auto` diresolusi:
   default katalog → bila ditolak provider (404/400) → fallback rekomendasi
   katalog → model hidup pertama dari `/models`. **ModelPicker** bottom
   sheet: bagian Otomatis, Rekomendasi katalog, Daftar hidup provider,
   pilihan per-sesi atau permanen (`--global`) — persis `/model` repo.
4. **Kecerdasan dari repo Thor-agent** — 44 `SKILL.md` asli (11 kategori)
   di-bundle; agent memanggil `skill_list` / `skill_load` untuk membaca
   prosedur sebelum mengerjakan tugas. Layar Skill Hub = penjelajah
   katalog + baca isi SKILL.md. **`/learn`** menulis SKILL.md baru ke
   `filesDir/thor-skills/` (loop pembelajaran tertutup repo sumber) —
   skill pengguna langsung terindeks.
5. **Tool native on-device** — `shell`, `read_file`, `write_file`,
   `edit_file` (patch search/replace), `list_dir`, `search_files`
   (glob+grep), `github_clone` (unduh repo via arsip zip), `web_fetch`,
   `skill_list`, `skill_load`, `root_status`.
6. **Approval floor (port `tools/approval_detection.py`)** — 14 pola
   hardline repo sumber (rm rekursif root/sistem, dd ke block device,
   fork bomb, killall -9, mkfs, fastboot flash, curl|sh, chmod -R 777 /,
   remount/wipe): dialog persetujuan menampilkan **alasan pola** yang
   cocok. `/yolo` melewatkannya.
7. **Tracking token & konteks (v0.3.0)** — usage per-panggilan diakumulasi
   (prompt/completion/total + jumlah tool call); `/status` `/usage`
   `/context` melaporkan ala repo sumber.
8. **`/goal` tujuan berdiri (v0.3.0)** — goal disuntik ke system prompt
   lintas giliran (port `goal_command.py`), strip goal aktif di layar Chat.
9. **Terminal** — terminal embedded multi-sesi, mode `sh` / `su`,
   streaming baris-per-baris, riwayat perintah, cwd nyata.
10. **Root sederhana — hanya saat dibutuhkan** — shell biasa default;
    `su` otomatis hanya bila akses ditolak (exit 13/126, "Permission
    denied") dan root tersedia (KernelSU / APatch / Magisk).
11. **Pengaturan Provider** — profil LLM kustom OpenAI-compatible:
    preset cepat (OpenRouter, OpenAI, Groq, Z.ai, LM Studio, Ollama),
    Base URL, API Key (terenkripsi **Android Keystore AES/GCM**), model
    (`auto` atau pilih dari katalog/hidup), temperature, max tokens,
    streaming, uji koneksi.
12. **Akses berkas** — MANAGE_EXTERNAL_STORAGE + SAF, penjelajah berkas,
    pratinjau teks, pemilih CWD.
13. **Anti-kill** — `AgentForegroundService` (tipe `dataSync`) aktif
    selama tugas berjalan + `AgentWorker` (WorkManager) keep-alive.

## UI/UX hidup (v0.5.0 — sidebar ala Gemini + slash jadi tombol)

- **SIDEBAR ala Gemini (v0.5.0)**: drawer dari kiri (ikon hamburger di top
  bar Chat) berisi tombol **Chat baru**, **riwayat sesi 10 terbaru**
  (pindah/hapus seketika), navigasi (Terminal, Berkas, Skill Hub,
  Provider & Model, Root), goal aktif ringkas, dan identitas versi.
  ViewModel Chat di-scope aktivitas — pilih sesi di sidebar langsung
  terlihat di layar Chat.
- **SLASH COMMAND JADI TOMBOL (v0.5.0)**: 15 aksi cepat ala Gemini di atas
  composer (`Rencanakan` `/plan` · `Set goal` `/goal` · `Pelajari` `/learn`
  · `Review` · `Status` · `Padatkan` · `Token` · `Antrean` · `Model` ·
  `Gaya` · `Skin` · `Skill` · `Suara` · `Agent` · `Sesi baru`) — disentuh =
  perintah aslinya dieksekusi; yang butuh argumen mengisi composer.
  Perintah lanjutan tetap lewat `/` + SlashPalette (105 perintah).
- **Pemilihan model per penyedia (v0.5.0)**: ModelPicker kini bertab —
  `Endpoint ini` (model hidup `/models` profil aktif) · `OpenRouter` ·
  `Thor Portal` · `OpenAI` · `Groq` · `Z.ai GLM` · `LM Studio` · `Ollama`
  — meniru `/model --provider` repo sumber; pemilihan sesi vs permanen
  (--global) tetap tersedia.
- **Composer mengambang**: `enableEdgeToEdge()` + `imePadding()` — kotak
  ketik SELALU di atas keyboard (perbaikan "kolom ketik tenggelam" di
  Android 15/16 yang memaksa edge-to-edge); bottom bar menyusut saat
  keyboard terbuka; garis rambut gradasi aksen memisah composer dari chat.
- **Micro-interaction**: pesan masuk dengan `animateItem()` (fade+placement),
  indikator "berpikir" tiga titik emas melompat (bukan spinner kaku), tombol
  kirim mengecil saat ditekan + haptic, chip saran ber-scale, chevron kartu
  tool berputar halus, logo kosong berdenyut, pet maskot melompat.
- **Scroll pintar**: auto-follow hanya saat di dasar; FAB "gulir ke bawah"
  dengan badge jumlah pesan baru saat membaca ke atas.
- **Tekan-lama pesan**: salin / kutip / edit & kirim ulang (+ haptic).
- **Timestamp [HH:MM]** per gelembung (`/timestamps on|off`).
- **Status bar mini**: model · token · antrean · background · baterai
  (`/statusbar`, `/battery`).
- **Antrean terlihat**: chip "N pesan mengantre" di atas composer → lembar
  kelola (`/queue`).
- **Skin** (`/skin`): thor · midnight · ember · mono — aksen aplikasi
  berganti seketika.
- **Autocomplete perintah**: ketik `/pe` → chip `/personality` muncul
  seketika di atas composer.

## Ikon vektor mandiri (v0.3.1 — fill fix)

`ui/icons/ThorIcons.kt` — **49 ikon** (+4 di `ThorIconsExtra.kt` v0.5.0) path-data Material Symbols 24dp
yang di-generate mandiri (`ImageVector.Builder` + `path {}`), TANPA
`material-icons-core/extended`.

**Pelajaran bug v0.3.0**: `ImageVector.Builder.path { ... }` dengan
parameter default memiliki `fill = null` DAN `stroke = null`. Di Compose,
`PathComponent.draw()` hanya memanggil `drawPath()` bila salah satunya
non-null — jadi path yang "benar" sekalipun TIDAK digambar sama sekali;
se-45 ikon tampak hilang (label tab tetap tampil). Perbaikan: setiap
`path()` kini eksplisit `fill = SolidColor(Color(0xFF000000))` (warna
digantikan `tint` `Icon()` via ColorFilter, persis ikon Material resmi).
`ThorIconsTest` (5 test) mem-walk tree `ImageVector.root` dan gagalkan
build bila ada ikon tanpa fill — regression terkunci.

## Build

```bash
# butuh JDK 21, Android SDK 36 + build-tools 36.0.0
./gradlew assembleRelease   # R8 minified ±3.9 MB (debug-signed)
./gradlew assembleDebug     # ±33 MB tanpa minify
./gradlew test              # 26 unit test JVM: SSE + loop agen + slash + model + ikon + provider + prompt
# hasil: app/build/outputs/apk/{release,debug}/
```

## Instal (sideload)

Aktifkan "Sumber tidak dikenal", lalu:

```bash
adb install -r ThorAgentMobile-v0.5.0-release.apk
```

Debug-signed (kunci debug bawaan) — untuk distribusi luas, buat keystore rilis sendiri.

## Struktur

```
app/src/main/java/com/thoragent/app/
├── ThorApp.kt, MainActivity.kt          # host + navigasi 5 tab
├── runtime/ThorRuntime.kt               # service locator modul
├── core/
│   ├── agent/  AgentCore (loop agen), AgentTools (registry tool native +
│   │           approval floor port approval_detection), SkillLibrary (+skill
│   │           pengguna /learn), SystemPromptBuilder (+goal), SlashCommands
│   │           (registry 105 perintah repo + parse)
│   ├── llm/    LlmClient (SSE + usage token + reasoning_effort)
│   ├── exec/   ExecutionInterface, AndroidShellRunner (non-root default)
│   ├── provider/ ProviderRepository, ModelCatalog (katalog repo + auto)
│   ├── root/   RootManager (libsu), deteksi KernelSU/APatch/Magisk
│   ├── terminal/ TerminalSession, TerminalSessionManager
│   └── service/ AgentForegroundService, AgentWorker
├── data/  Room (sesi/pesan/provider), KeystoreCrypto, DataStore, katalog
└── ui/    chat (ChatScreen + SlashPalette + ModelPicker bertab penyedia +
           Sidebar ala Gemini + QuickActions + MarkdownText),
           provider (preset + pemilih model), skills (SkillHub), files,
           terminal, root, icons (ThorIcons 49 ikon + ThorIconsExtra v0.5)
app/src/main/assets/
├── skills/            # 44 SKILL.md asli repo + skills_index.json + SOUL.md
└── catalog/           # tools_index.json (297 tool) + slash_commands.json
                       # (105 perintah terjemahan id) + model_catalog.json
                       # (katalog model repo: openrouter + thor)
app/src/test/          # 26 unit test JVM (MockWebServer): SSE, loop agen,
                       # slash parse + invarian 105-perintah-terdukung,
                       # pemilihan model otomatis + per penyedia, ikon fill,
                       # system prompt guidance repo sumber
```

## Catatan

- Kecerdasan tidak ditulis ulang: SKILL.md, SOUL.md, COMMAND_REGISTRY,
  katalog model, dan katalog tool **disalin apa adanya** dari repo
  Thor-agent ke aset APK; agent memuatnya ke konteks saat bekerja.
- Lisensi kode aplikasi: MIT (mengikuti repo Thor-agent).
