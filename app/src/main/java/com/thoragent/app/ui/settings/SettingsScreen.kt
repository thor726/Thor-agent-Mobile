package com.thoragent.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.compose.ui.unit.dp
import com.thoragent.app.ui.icons.ThorIcons
import com.thoragent.app.ui.icons.ThorIconsExtra
import com.thoragent.app.ui.settings.components.SettingItemRow

/**
 * SettingsScreen — rumah fitur teknis hasil re-strukturisasi menu.
 *
 * v0.9.0:
 * - "Gaya jawaban" tidak lagi di sini — satu pintu di Konfigurasi
 *   agent (Pilihan → Gaya jawaban) supaya semua pengaturan perilaku
 *   agent terkumpul di satu layar.
 * - Pintu "Skill & Tool" pindah ke sini (tab Skill di bottom bar
 *   diganti "Berkas" yang naik ke tampilan utama).
 * - Pintu "Penjelajah berkas" dihapus dari sini — Berkas kini tab
 *   utama berjejer dengan Terminal.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    nav: NavHostController,
    onSendSlash: (String) -> Unit = {}
) {
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
            // ===== Seksi Agent — pengaturan perilaku agent =====
            sectionHeader("Agent")
            item {
                SettingItemRow(
                    title = "Konfigurasi agent",
                    subtitle = "Gaya jawaban, kedalaman berpikir, mode cepat, YOLO, tema",
                    icon = ThorIconsExtra.Tune,
                    onClick = { nav.navigate("commands") }
                )
            }

            // ===== Lanjutan: fitur teknis dibuka secara sadar =====
            sectionHeader("Lanjutan")
            item {
                SettingItemRow(
                    title = "Skill & Tool",
                    subtitle = "44 skill + 297 tool — prosedur langkah demi langkah",
                    icon = ThorIcons.MenuBook,
                    onClick = { nav.navigate("skillhub") }
                )
            }
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
        }
    }
}

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
