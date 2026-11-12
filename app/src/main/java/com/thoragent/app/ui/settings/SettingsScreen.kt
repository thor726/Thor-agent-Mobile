package com.thoragent.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.thoragent.app.ui.commands.SettingsAgentViewModel
import com.thoragent.app.ui.icons.ThorIcons
import com.thoragent.app.ui.icons.ThorIconsExtra
import com.thoragent.app.ui.settings.components.SettingItemRow

/**
 * SettingsScreen — rumah fitur teknis hasil re-strukturisasi menu.
 *
 * v0.8.1:
 * - "Gaya jawaban" kini pilihan TOMBOL LANGSUNG (chip) di sini — dulu
 *   mengetikkan perintah "/personality" ke kotak chat, memaksa pengguna
 *   kembali ke chat hanya untuk memilih. Pilihan tersimpan saat-saat
 *   juga (DataStore, jalur yang sama dengan slash command).
 * - Dua entri yang membuka layar sama ("Kedalaman berpikir" dan "Pusat
 *   Perintah") digabung jadi SATU: "Konfigurasi agent" — semua kontrol
 *   (slider reasoning, toggle, pilihan segmented) ada di sana.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    nav: NavHostController,
    onSendSlash: (String) -> Unit = {}
) {
    // ViewModel level-layar: akses SettingsRuntime yang sama dengan
    // CommandCenter — state & pesan toast identik.
    val vm: SettingsAgentViewModel = viewModel()
    val personality by vm.personality.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Setelan") },
                navigationIcon = {
                    IconButton(onClick = { nav.popBackStack() }) {
                        Icon(ThorIcons.ArrowBack, "Kembali")
                    }
                }
            )
        }
    ) { pad ->
        LazyColumn(
            modifier = Modifier.padding(pad).fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // ===== Gaya jawaban: pilihan LANGSUNG (tidak lewat chat) =====
            sectionHeader("Sering dipakai")
            item {
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Text(
                            "Gaya jawaban",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "cara agent menyampaikan balasan",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Row(
                        Modifier.padding(top = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        PERSONALITIES.forEach { p ->
                            FilterChip(
                                selected = personality == p.id,
                                onClick = { vm.setPersonality(p.id) },
                                label = { Text(p.label) }
                            )
                        }
                    }
                }
            }
            item {
                SettingItemRow(
                    title = "Konfigurasi agent",
                    subtitle = "Kedalaman berpikir, mode cepat, YOLO, tema, dan lain-lain",
                    icon = ThorIconsExtra.Tune,
                    onClick = { nav.navigate("commands") }
                )
            }

            // ===== Lanjutan: fitur teknis dibuka secara sadar =====
            sectionHeader("Lanjutan")
            item {
                SettingItemRow(
                    title = "Penyedia AI & model",
                    subtitle = "OpenRouter, OpenAI, atau komputer lokal",
                    icon = ThorIconsExtra.SwitchAccess,
                    onClick = { nav.navigate("provider") }
                )
            }
            item {
                SettingItemRow(
                    title = "Akses Root",
                    subtitle = "Hak akses penuh, hanya saat dibutuhkan",
                    icon = ThorIcons.Security,
                    onClick = { nav.navigate("root") }
                )
            }
            item {
                SettingItemRow(
                    title = "Penjelajah berkas",
                    subtitle = "Folder kerja agent di penyimpanan",
                    icon = ThorIcons.Folder,
                    onClick = { nav.navigate("files") }
                )
            }
        }
    }
}

/** Opsi gaya jawaban — id = nilai yang sama dengan /personality. */
private data class Personality(val id: String, val label: String)

private val PERSONALITIES = listOf(
    Personality("default", "Standar"),
    Personality("precise", "Teliti"),
    Personality("ramah", "Ramah"),
    Personality("guru", "Guru"),
    Personality("senior", "Senior"),
    Personality("singkat", "Singkat")
)

/**
 * Judul seksi — pemisah visual antar kelompok pengaturan.
 */
private fun LazyListScope.sectionHeader(title: String) {
    item {
        Text(
            title,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 2.dp)
        )
    }
}
