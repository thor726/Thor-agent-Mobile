package com.thoragent.app.ui.chat

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.thoragent.app.core.agent.MobileCommands
import com.thoragent.app.core.agent.SlashCommandDef
import com.thoragent.app.ui.icons.ThorIcons
import com.thoragent.app.ui.theme.ThorGold
import com.thoragent.app.ui.theme.ThorOk
import com.thoragent.app.ui.theme.ThorSurfaceHigh
import com.thoragent.app.ui.theme.ThorTextSecondary

/** Ikon per kategori perintah (meniru visual TUI repo sumber). */
private fun iconFor(cmd: SlashCommandDef) = when (cmd.category) {
    "Sesi" -> ThorIcons.History
    "Konfigurasi" -> ThorIcons.Settings
    "Info" -> ThorIcons.Info
    "Tool & Skill" -> ThorIcons.Memory
    "Keluar" -> ThorIcons.Close
    else -> ThorIcons.Bolt
}

/**
 * SlashPalette — SLASH COMMAND JADI UI INTERAKTIF (105 perintah, semua jalan
 * di Android sejak v0.4.0): pencarian, filter kategori + tier, hint argumen,
 * subcommand. Menyentuh perintah = langsung dieksekusi di aplikasi (atau
 * argumen diprefill bila dibutuhkan).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SlashPalette(
    vm: ChatViewModel,
    visible: Boolean,
    onDismiss: () -> Unit,
    onPickCommand: (SlashCommandDef) -> Unit
) {
    if (!visible) return
    val all by vm.slashCommands.collectAsState()
    var query by remember { mutableStateOf("") }
    var category by remember { mutableStateOf<String?>(null) }
    val focus = remember { FocusRequester() }

    val filtered = remember(all, query, category) {
        var list = if (query.isBlank()) all else all.filter { c ->
            c.name.contains(query.removePrefix("/"), true) ||
                c.aliases.any { it.contains(query.removePrefix("/"), true) } ||
                c.description.contains(query, true)
        }
        if (category != null && category != "native") list = list.filter { it.category == category }
        if (category == "native") list = list.filter { MobileCommands.isNative(it.name) }
        // urutkan: native mobile dulu, lalu agent-tier, lalu abjad
        list.sortedWith(
            compareByDescending<SlashCommandDef> { MobileCommands.isNative(it.name) }
                .thenBy { it.name }
        )
    }
    val categories = remember(all) { all.map { it.category }.distinct() }

    LaunchedEffect(Unit) { focus.requestFocus() }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(Modifier.padding(bottom = 16.dp)) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(ThorIcons.Bolt, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    "Perintah Thor (${all.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.weight(1f))
                Text(
                    "semua jalan di Android",
                    style = MaterialTheme.typography.labelSmall,
                    color = ThorOk
                )
            }
            Text(
                "Registry lengkap repo sumber (thor_cli/commands.py) — natif di aplikasi, lewat agent on-device, atau padanan mobilenya.",
                style = MaterialTheme.typography.labelSmall,
                color = ThorTextSecondary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp)
            )

            TextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
                    .focusRequester(focus),
                placeholder = { Text("Cari perintah… (nama / deskripsi)") },
                leadingIcon = { Icon(ThorIcons.Search, null, tint = MaterialTheme.colorScheme.primary) },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = ThorSurfaceHigh,
                    unfocusedContainerColor = ThorSurfaceHigh
                )
            )

            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                FilterChip(
                    selected = category == null,
                    onClick = { category = null },
                    label = { Text("Semua") }
                )
                FilterChip(
                    selected = category == "native",
                    onClick = { category = if (category == "native") null else "native" },
                    label = { Text("Natif") }
                )
                categories.forEach { cat ->
                    FilterChip(
                        selected = category == cat,
                        onClick = { category = if (category == cat) null else cat },
                        label = { Text(cat) }
                    )
                }
            }

            LazyColumn(
                state = rememberLazyListState(),
                modifier = Modifier.fillMaxWidth().height(430.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(filtered, key = { it.name }) { cmd ->
                    CommandRow(cmd, onClick = { onPickCommand(cmd) })
                }
                item {
                    if (filtered.isEmpty()) {
                        Text(
                            "Tidak ada perintah cocok \"${query}\" — kirim sebagai pesan biasa ke agent.",
                            style = MaterialTheme.typography.bodySmall,
                            color = ThorTextSecondary,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CommandRow(cmd: SlashCommandDef, onClick: () -> Unit) {
    val tier = MobileCommands.tier(cmd.name)
    val gold = MaterialTheme.colorScheme.primary
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = when (tier) {
            MobileCommands.SupportTier.NATIVE -> gold.copy(alpha = 0.07f)
            MobileCommands.SupportTier.AGENT -> MaterialTheme.colorScheme.surface
            MobileCommands.SupportTier.INFO -> MaterialTheme.colorScheme.surface
        },
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize()
            .clickable { onClick() }
    ) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                iconFor(cmd), null,
                tint = if (tier == MobileCommands.SupportTier.NATIVE) gold else ThorTextSecondary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "/${cmd.name}",
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (tier == MobileCommands.SupportTier.NATIVE) gold else MaterialTheme.colorScheme.onSurface
                    )
                    if (cmd.aliases.isNotEmpty()) {
                        Spacer(Modifier.width(6.dp))
                        Text(
                            cmd.aliases.joinToString(",") { "/$it" },
                            style = MaterialTheme.typography.labelSmall,
                            color = ThorTextSecondary
                        )
                    }
                }
                Text(
                    cmd.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = ThorTextSecondary,
                    maxLines = 2
                )
                if (cmd.argsHint.isNotBlank()) {
                    Text(
                        "argumen: ${cmd.argsHint}",
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }
            // badge tier dukungan
            when (tier) {
                MobileCommands.SupportTier.NATIVE ->
                    Icon(ThorIcons.CheckCircle, "dieksekusi natif di aplikasi", tint = ThorOk, modifier = Modifier.size(16.dp))
                MobileCommands.SupportTier.AGENT ->
                    Icon(ThorIcons.SmartToy, "dikerjakan agent on-device", tint = gold.copy(alpha = 0.7f), modifier = Modifier.size(16.dp))
                MobileCommands.SupportTier.INFO ->
                    Icon(ThorIcons.Info, "padanan mobile + info", tint = ThorTextSecondary, modifier = Modifier.size(16.dp))
            }
        }
    }
}
