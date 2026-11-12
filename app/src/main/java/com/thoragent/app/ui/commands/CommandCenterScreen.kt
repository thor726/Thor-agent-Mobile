package com.thoragent.app.ui.commands

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.thoragent.app.ui.icons.ThorIcons
import com.thoragent.app.ui.theme.ThorGold
import com.thoragent.app.ui.theme.ThorTextSecondary

/**
 * Pusat Perintah (v0.6.0) — tombol untuk slash command yang punya STATE:
 * slider (reasoning effort), toggle, pilihan segmented, dan aksi
 * satu-tombol. Perintah yang butuh argumen teks bebas TIDAK dipaksa jadi
 * tombol — ditampilkan sebagai chip pengingat sintaks, cukup jalankan
 * lewat perintah slash di kotak chat.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommandCenterScreen(
    nav: NavHostController,
    connectSlash: (suspend (String) -> Boolean) -> Unit
) {
    val vm: CommandCenterViewModel = viewModel()
    val snackbar = remember { SnackbarHostState() }

    // sambungkan jalur dispatch 105 perintah yang sudah teruji
    LaunchedEffect(Unit) { connectSlash { cmd -> vm.sendSlashSafe(cmd) } }

    val reasoning by vm.reasoningLevel.collectAsState()
    val yolo by vm.yolo.collectAsState()
    val timestamps by vm.timestamps.collectAsState()
    val focus by vm.focusMode.collectAsState()
    val statusbar by vm.statusbar.collectAsState()
    val battery by vm.battery.collectAsState()
    val voice by vm.voice.collectAsState()
    val verbose by vm.verboseMode.collectAsState()
    val skin by vm.skin.collectAsState()
    val personality by vm.personality.collectAsState()
    val toast by vm.toast.collectAsState()

    LaunchedEffect(toast) { if (toast.isNotBlank()) { snackbar.showSnackbar(toast); vm.clearToast() } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Pusat Perintah") },
                navigationIcon = {
                    IconButton(onClick = { nav.popBackStack() }) {
                        Icon(ThorIcons.ArrowBack, "Kembali")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { pad ->
        LazyColumn(
            Modifier.padding(pad).fillMaxSize(),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Text(
                    "Tombol untuk perintah yang punya pengaturan. Perintah berargumen " +
                        "teks (goal, title, steer, …) tetap lewat kotak chat.",
                    style = MaterialTheme.typography.bodySmall,
                    color = ThorTextSecondary
                )
            }

            // ============ SLIDER: REASONING EFFORT (contoh utama) ============
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors()
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(ThorIcons.Bolt, null, tint = ThorGold, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Reasoning effort", style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.weight(1f))
                            Text(
                                reasoning.ifBlank { "bawaan" },
                                fontFamily = FontFamily.Monospace,
                                color = ThorGold,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                        Text(
                            "Lama model berpikir sebelum menjawab (/reasoning). " +
                                "Kanan = teliti tapi boros token; kiri = instan dan irit.",
                            style = MaterialTheme.typography.labelSmall,
                            color = ThorTextSecondary
                        )
                        // 9 posisi: bawaan · none · minimal · low · medium · high · xhigh · max · ultra
                        // (ultra ditambahkan v0.6.1 — sinkron VALID_REASONING_EFFORTS sumber)
                        val levels = listOf("", "none", "minimal", "low", "medium", "high", "xhigh", "max", "ultra")
                        val idx = levels.indexOf(reasoning).let { if (it < 0) 0 else it }
                        Slider(
                            value = idx.toFloat(),
                            onValueChange = { v ->
                                val i = v.toInt().coerceIn(0, levels.size - 1)
                                if (i != idx) vm.setReasoning(levels[i])
                            },
                            valueRange = 0f..8f,
                            steps = 7,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                        Row(Modifier.fillMaxWidth()) {
                            Text("irit", style = MaterialTheme.typography.labelSmall, color = ThorTextSecondary)
                            Spacer(Modifier.weight(1f))
                            Text("teliti (boros token)", style = MaterialTheme.typography.labelSmall, color = ThorTextSecondary)
                        }
                        Text(
                            "Kombinasi hemat token: mode cepat di bawah + Max tokens kecil " +
                                "di Pengaturan Provider.",
                            style = MaterialTheme.typography.labelSmall,
                            color = ThorTextSecondary
                        )
                    }
                }
            }

            // ============ TOGGLE ============
            item { SectionTitle("Toggle") }
            item {
                SettingsCard {
                    ToggleRow("Mode cepat (/fast)", "Reasoning low — singkat & responsif", reasoning == "low") { vm.setFast(it) }
                    ToggleRow("YOLO (/yolo)", "Lewati persetujuan perintah berbahaya", yolo) { vm.toggleYolo() }
                    ToggleRow("Timestamp pesan (/timestamps)", "Jam di tiap balasan", timestamps) { vm.setTimestamps(it) }
                    ToggleRow("Focus view (/focus)", "Sembunyikan kartu tool", focus) { vm.setFocus(it) }
                    ToggleRow("Status bar mini (/statusbar)", "Bar info ringkas di atas", statusbar) { vm.setStatusbar(it) }
                    ToggleRow("Indikator baterai (/battery)", "Persentase baterai di bar", battery) { vm.setBattery(it) }
                    ToggleRow("Mode suara (/voice)", "Bacakan balasan (TTS perangkat)", voice) { vm.setVoice(it) }
                }
            }

            // ============ SEGMENTED ============
            item { SectionTitle("Pilihan") }
            item {
                SettingsCard {
                    SegmentedRow("Persetujuan (/approvals)", listOf("off", "smart", "manual"), if (yolo) "off" else "smart") { vm.setApprovals(it) }
                    SegmentedRow("Progres tool (/verbose)", listOf("off", "new", "all"), verbose) { vm.setVerbose(it) }
                    SegmentedRow("Skin (/skin)", listOf("thor", "midnight", "ember", "mono"), skin) { vm.setSkin(it) }
                    SegmentedRow(
                        "Personality (/personality)",
                        listOf("default", "precise", "ramah", "guru", "senior", "singkat"),
                        personality.ifBlank { "default" }
                    ) { vm.setPersonality(it) }
                }
            }

            // ============ AKSI SATU-TOMBOL ============
            item { SectionTitle("Aksi") }
            item {
                SettingsCard {
                    ActionRow(
                        "Sesi & agent",
                        listOf(
                            "new" to "Sesi baru",
                            "stop" to "Hentikan agent",
                            "sessions" to "Riwayat sesi",
                            "retry" to "Ulangi terakhir"
                        )
                    ) { vm.runSlash(it) }
                    ActionRow(
                        "Konten & berkas",
                        listOf(
                            "image" to "Lampir gambar",
                            "export" to "Ekspor profil",
                            "import" to "Impor profil",
                            "copy" to "Salin terakhir"
                        )
                    ) { vm.runSlash(it) }
                    ActionRow(
                        "Info & bantuan",
                        listOf(
                            "status" to "Status",
                            "usage" to "Pemakaian token",
                            "model" to "Pilih model",
                            "help" to "Bantuan (/help)"
                        )
                    ) { vm.runSlash(it) }
                    ActionRow(
                        "Skill & tool",
                        listOf(
                            "skills" to "Skill hub",
                            "tools" to "Daftar tool"
                        )
                    ) { vm.runSlash(it) }
                }
            }

            // ============ PERINTAH SLASH-SAJA (tidak dipaksa jadi tombol) ============
            item { SectionTitle("Lewat perintah slash saja") }
            item {
                SettingsCard {
                    Text(
                        "Perintah ini butuh argumen teks bebas — tidak dipaksa jadi tombol. " +
                            "Ketik / dan pilih dari palet di kotak chat:",
                        style = MaterialTheme.typography.labelSmall,
                        color = ThorTextSecondary
                    )
                    val slashOnly = listOf(
                        "/goal <tujuan>", "/subgoal <kriteria>", "/title <judul>",
                        "/prompt <teks>", "/btw <catatan>", "/steer <arahan>",
                        "/queue <pesan>", "/branch <nama>", "/cron <jadwal>",
                        "/save <nama>", "/topic <topik>"
                    )
                    Column(Modifier.padding(top = 6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        slashOnly.forEach { s ->
                            Text(
                                s,
                                fontFamily = FontFamily.Monospace,
                                style = MaterialTheme.typography.bodySmall,
                                color = ThorTextSecondary
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = ThorGold,
        modifier = Modifier.padding(start = 4.dp)
    )
}

@Composable
private fun SettingsCard(content: @Composable () -> Unit) {
    Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) { content() }
    }
}

@Composable
private fun ToggleRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            Text(subtitle, style = MaterialTheme.typography.labelSmall, color = ThorTextSecondary)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun SegmentedRow(title: String, options: List<String>, selected: String, onPick: (String) -> Unit) {
    Column {
        Text(title, style = MaterialTheme.typography.bodyMedium)
        Row(
            Modifier.padding(top = 6.dp).horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            options.forEach { opt ->
                FilterChip(selected = selected == opt, onClick = { onPick(opt) }, label = { Text(opt) })
            }
        }
    }
}

@Composable
private fun ActionRow(title: String, actions: List<Pair<String, String>>, onRun: (String) -> Unit) {
    Column {
        Text(title, style = MaterialTheme.typography.bodyMedium)
        Row(
            Modifier.padding(top = 6.dp).horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            actions.forEach { (cmd, label) ->
                OutlinedButton(onClick = { onRun(cmd) }) { Text(label) }
            }
        }
    }
}
