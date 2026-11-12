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
 * SettingsScreen — rumah baru fitur teknis hasil re-strukturisasi menu
 * (v0.7.0).
 *
 * Ini wujud "Progressive Disclosure": semua yang sebelumnya menjadi tab
 * level-atas (Root) atau menu sidebar (Provider, Root, Berkas, Pusat
 * Perintah) kini menjadi baris di satu layar Setelan dengan penjelasan
 * bahasa manusia. Pengguna awam tidak lagi dipaksa melewati kata-kata
 * seperti "libsu" atau "endpoint" saat hanya ingin chat.
 *
 * Tata letak: LazyColumn + SettingItemRow dengan spacing 8dp dan
 * contentPadding 16dp — kelompok pengaturan sejenis dipisah judul seksi
 * (labelLarge, warna sekunder) supaya tidak terasa padat.
 *
 * Rute yang dibuka adalah rute NavHost yang SAMA seperti sebelumnya
 * (provider/root/files/commands) — hanya pintu masuknya yang berpindah,
 * sehingga deep-link dan perilaku lama tetap berfungsi.
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
                title = { Text("Setelan", style = MaterialTheme.typography.titleLarge) },
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
            // ===== Seksi: Agent — sering dipakai, bahasa manusia =====
            sectionHeader("Agent")
            item {
                SettingItemRow(
                    title = "Gaya jawaban",
                    subtitle = "Ubah kepribadian & cara agent menyampaikan balasan (/personality)",
                    icon = ThorIcons.Person,
                    onClick = { onSendSlash("/personality") }
                )
            }
            item {
                SettingItemRow(
                    title = "Kedalaman berpikir",
                    subtitle = "Agent berpikir lebih lama untuk soal yang sulit (/reasoning)",
                    icon = ThorIcons.Bolt,
                    onClick = { nav.navigate("commands") }
                )
            }

            // ===== Seksi: Lanjutan — fitur teknis dibuka secara sadar =====
            sectionHeader("Lanjutan")
            item {
                SettingItemRow(
                    title = "Penyedia AI & model",
                    subtitle = "Pilih server otak agent: OpenRouter, OpenAI, atau komputer lokal",
                    icon = ThorIconsExtra.SwitchAccess,
                    onClick = { nav.navigate("provider") }
                )
            }
            item {
                SettingItemRow(
                    title = "Akses Root",
                    subtitle = "Izinkan agent memakai hak akses penuh hanya saat dibutuhkan",
                    icon = ThorIcons.Security,
                    onClick = { nav.navigate("root") }
                )
            }
            item {
                SettingItemRow(
                    title = "Penjelajah berkas",
                    subtitle = "Lihat dan atur folder kerja agent di penyimpanan",
                    icon = ThorIcons.Folder,
                    onClick = { nav.navigate("files") }
                )
            }
            item {
                SettingItemRow(
                    title = "Pusat Perintah",
                    subtitle = "Semua 105 perintah cepat dalam satu layar",
                    icon = ThorIconsExtra.Tune,
                    onClick = { nav.navigate("commands") }
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
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp, bottom = 2.dp)
        )
    }
}
