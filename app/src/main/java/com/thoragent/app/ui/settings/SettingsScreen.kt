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
 * SettingsScreen — rumah fitur teknis hasil re-strukturisasi menu (v0.7.0),
 * dirapikan v0.8.0: judul TopAppBar pakai slot titleLarge tanpa timpaan
 * bobot, seksi diberi labelMedium + jarak lebih lega, item dipangkas
 * deskripsinya agar layar tidak terasa padat.
 *
 * Ini wujud "Progressive Disclosure": semua yang sebelumnya menjadi tab
 * level-atas (Root) atau menu sidebar (Provider, Root, Berkas, Pusat
 * Perintah) kini menjadi baris di satu layar Setelan dengan penjelasan
 * bahasa manusia. Pengguna awam tidak dipaksa melewati istilah seperti
 * "libsu" atau "endpoint" saat hanya ingin chat.
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
            // ===== Seksi: Agent — sering dipakai, bahasa manusia =====
            sectionHeader("Sering dipakai")
            item {
                SettingItemRow(
                    title = "Gaya jawaban",
                    subtitle = "Kepribadian & cara agent menyampaikan balasan",
                    icon = ThorIcons.Person,
                    onClick = { onSendSlash("/personality") }
                )
            }
            item {
                SettingItemRow(
                    title = "Kedalaman berpikir",
                    subtitle = "Agent berpikir lebih lama untuk soal yang sulit",
                    icon = ThorIcons.Bolt,
                    onClick = { nav.navigate("commands") }
                )
            }

            // ===== Seksi: Lanjutan — fitur teknis dibuka secara sadar =====
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
            item {
                SettingItemRow(
                    title = "Pusat Perintah",
                    subtitle = "Semua 105 perintah dalam satu layar",
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
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 2.dp)
        )
    }
}
