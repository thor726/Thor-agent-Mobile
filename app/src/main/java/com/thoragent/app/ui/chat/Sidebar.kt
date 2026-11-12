package com.thoragent.app.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.thoragent.app.ui.icons.ThorIcons
import com.thoragent.app.ui.icons.ThorIconsExtra
import com.thoragent.app.ui.theme.ThorTextSecondary

/**
 * Sidebar (v0.5.0) — panel samping ala Gemini: tombol Chat baru, daftar
 * sesi terbaru (pindah/hapus langsung), navigasi utama, aksi cepat, dan
 * identitas aplikasi. Menggantikan fungsi riwayat sesi bottom-sheet lama:
 * satu tempat untuk segalanya tanpa mengetik perintah.
 */
@Suppress("UNUSED_PARAMETER")
@Composable
fun Sidebar(
    vm: ChatViewModel,
    currentRoute: String,
    onNavigate: (String) -> Unit,
    onDismiss: () -> Unit,
    onNewChat: () -> Unit,
    onSelectSession: (Long) -> Unit,
    onDeleteSession: (Long) -> Unit
) {
    val sessions by vm.sessions.collectAsState()
    val sessionId by vm.sessionId.collectAsState()
    val goal by vm.goal.collectAsState()
    val gold = MaterialTheme.colorScheme.primary
    val amber = MaterialTheme.colorScheme.secondary

    Column(
        Modifier
            .fillMaxHeight()
            .width(290.dp)
            .background(MaterialTheme.colorScheme.surface)
            .verticalScroll(rememberScrollState())
            .padding(12.dp)
    ) {
        // ---- kepala ala Gemini: "Chat baru" ----
        Surface(
            shape = RoundedCornerShape(30),
            color = gold.copy(alpha = 0.14f),
            border = androidx.compose.foundation.BorderStroke(1.dp, gold.copy(alpha = 0.4f)),
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onNewChat() }
        ) {
            Row(
                Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(ThorIconsExtra.EditNote, null, tint = gold, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(8.dp))
                Text("Chat baru", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(Modifier.height(12.dp))

        // ---- riwayat sesi terbaru (10 teratas) ----
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(ThorIcons.History, null, tint = ThorTextSecondary, modifier = Modifier.size(15.dp))
            Spacer(Modifier.width(6.dp))
            Text(
                "Riwayat sesi",
                style = MaterialTheme.typography.labelMedium,
                color = ThorTextSecondary,
                modifier = Modifier.weight(1f)
            )
            Text(
                "${sessions.size}",
                style = MaterialTheme.typography.labelSmall,
                color = ThorTextSecondary
            )
        }
        Spacer(Modifier.height(4.dp))
        if (sessions.isEmpty()) {
            Text(
                "Belum ada sesi — mulai dengan Chat baru.",
                style = MaterialTheme.typography.bodySmall,
                color = ThorTextSecondary,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp)
            )
        } else {
            val recent = sessions.take(10)
            LazyColumn(
                Modifier.height((recent.size * 46 + 8).coerceAtMost(500).dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                items(recent, key = { it.id }) { s ->
                    val active = s.id == sessionId
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (active) gold.copy(alpha = 0.14f) else Color.Transparent,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelectSession(s.id) }
                    ) {
                        Row(
                            Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                Modifier
                                    .size(26.dp)
                                    .background(
                                        if (active) Brush.linearGradient(listOf(gold, amber)) else Brush.linearGradient(
                                            listOf(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.surfaceVariant)
                                        ),
                                        CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    ThorIcons.Chat, null,
                                    tint = if (active) MaterialTheme.colorScheme.background else ThorTextSecondary,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                            Spacer(Modifier.width(8.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    s.title.ifBlank { "Sesi" },
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                val last = s.updatedAt
                                if (last > 0) {
                                    Text(
                                        java.text.SimpleDateFormat("dd MMM · HH:mm", java.util.Locale("id"))
                                            .format(java.util.Date(last)),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = ThorTextSecondary
                                    )
                                }
                            }
                            if (!active) {
                                Icon(
                                    ThorIcons.Delete, "Hapus sesi",
                                    tint = ThorTextSecondary,
                                    modifier = Modifier
                                        .size(16.dp)
                                        .clickable { onDeleteSession(s.id) }
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(10.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Spacer(Modifier.height(10.dp))

        // ---- v0.7.0: navigasi menuju Setelan (progressive disclosure) ----
        // Blok menu Terminal/Berkas/Skill Hub/Provider/Root dihapus dari
        // drawer: NavigationBar 4 tab kini menjadi satu-satunya navigasi
        // utama; fitur teknis dibuka dari Setelan → seksi "Lanjutan".
        SidebarItem(
            label = "Setelan",
            subtitle = "Penyedia AI, akses root, berkas & lainnya",
            icon = ThorIcons.Settings,
            active = currentRoute == "settings",
            highlight = gold
        ) { onNavigate("settings") }

        Spacer(Modifier.height(10.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Spacer(Modifier.height(10.dp))

        // ---- goal aktif ringkas ----
        if (goal.isNotBlank()) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = amber.copy(alpha = 0.10f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(10.dp)) {
                    Text("GOAL AKTIF", style = MaterialTheme.typography.labelSmall, color = amber, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(2.dp))
                    Text(
                        goal.take(120),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
        }

        // ---- identitas aplikasi ----
        Row(
            Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(ThorIcons.Bolt, null, tint = gold, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text(
                "Thor Agent Mobile v" + com.thoragent.app.BuildConfig.VERSION_NAME,
                style = MaterialTheme.typography.labelSmall,
                color = ThorTextSecondary
            )
        }
    }
}

/** Satu baris menu sidebar — ikon, label, subtitle, aksi saat disentuh. */
@Composable
private fun SidebarItem(
    label: String,
    subtitle: String,
    icon: ImageVector,
    active: Boolean,
    highlight: Color,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (active) highlight.copy(alpha = 0.12f) else Color.Transparent,
        modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp).clickable { onClick() }
    ) {
        Row(
            Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, null, tint = if (active) highlight else ThorTextSecondary, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Column {
                Text(
                    label,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal
                )
                if (subtitle.isNotBlank()) {
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = ThorTextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}
