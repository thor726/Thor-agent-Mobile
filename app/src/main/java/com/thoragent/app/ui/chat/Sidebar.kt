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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.thoragent.app.ui.icons.ThorIcons
import com.thoragent.app.ui.icons.ThorIconsExtra
import com.thoragent.app.ui.theme.ThorTextSecondary

/**
 * Sidebar (v0.8.0) — panel riwayat sesi ala Gemini, dirapikan total:
 *
 * 1. INSET BENAR — statusBarsPadding + navigationBarsPadding: konten tidak
 *    lagi menempel di atas (tertutup jam/baterai) dan tidak tertindih
 *    gesture bar di bawah, di semua versi Android yang support edge-to-edge.
 * 2. SATU SUMBU SCROLL — LazyColumn tunggal untuk seluruh isi (dulu:
 *    LazyColumn bersarang di dalam Column.verticalScroll — scroll contest
 *    yang bermasalah di Android lama).
 * 3. TEKS TIDAK BERLEBIH — duplikat dibuang: menu "Setelan" (sudah ada di
 *    tab bawah NavigationBar) dan blok "GOAL AKTIF" (sudah ada strip goal
 *    di layar chat). Sisa isi: tombol Chat baru, riwayat, versi aplikasi.
 * 4. SENTUH NYAMAN — ikon hapus dibungkus Box 44dp (dulu 16dp, di bawah
 *    standar sentuh Material); ikon seragam 20dp/16dp.
 */
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
    val gold = MaterialTheme.colorScheme.primary

    Column(
        Modifier
            .fillMaxHeight()
            .width(300.dp)
            .background(MaterialTheme.colorScheme.surface)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        // ---- kepala ala Gemini: satu tombol besar "Chat baru" ----
        Surface(
            shape = RoundedCornerShape(28),
            color = gold.copy(alpha = 0.14f),
            border = androidx.compose.foundation.BorderStroke(1.dp, gold.copy(alpha = 0.4f)),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .clickable { onNewChat() }
        ) {
            Row(
                Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(ThorIconsExtra.EditNote, null, tint = gold, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(10.dp))
                Text(
                    "Chat baru",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        // ---- seluruh isi: SATU LazyColumn (satu sumbu scroll) ----
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                horizontal = 16.dp,
                vertical = 8.dp
            ),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            // label seksi + jumlah
            item(key = "header") {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Riwayat",
                        style = MaterialTheme.typography.labelMedium,
                        color = ThorTextSecondary,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.weight(1f)
                    )
                    if (sessions.isNotEmpty()) {
                        Text(
                            "${sessions.size}",
                            style = MaterialTheme.typography.labelMedium,
                            color = ThorTextSecondary
                        )
                    }
                }
            }

            if (sessions.isEmpty()) {
                item(key = "empty") {
                    Text(
                        "Belum ada sesi. Ketuk “Chat baru” untuk mulai.",
                        style = MaterialTheme.typography.bodySmall,
                        color = ThorTextSecondary,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 12.dp)
                    )
                }
            } else {
                items(sessions, key = { it.id }) { s ->
                    SessionRow(
                        s = s,
                        active = s.id == sessionId,
                        gold = gold,
                        onSelect = { onSelectSession(s.id) },
                        onDelete = { onDeleteSession(s.id) }
                    )
                }
            }

            // ---- identitas aplikasi, menempel ke dasar daftar ----
            item(key = "footer") {
                Spacer(Modifier.height(8.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Spacer(Modifier.height(8.dp))
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(ThorIcons.Bolt, null, tint = gold, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "Thor Agent Mobile v" + com.thoragent.app.BuildConfig.VERSION_NAME,
                        style = MaterialTheme.typography.labelMedium,
                        color = ThorTextSecondary
                    )
                }
            }
        }
    }
}

/** Satu baris sesi: avatar, judul, waktu — ikon hapus dengan area sentuh 44dp. */
@Composable
private fun SessionRow(
    s: com.thoragent.app.data.db.SessionEntity,
    active: Boolean,
    gold: Color,
    onSelect: () -> Unit,
    onDelete: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = if (active) gold.copy(alpha = 0.14f) else Color.Transparent,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSelect() }
    ) {
        Row(
            Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // titik status — 10dp, cukup untuk skanning cepat
            Box(
                Modifier
                    .size(10.dp)
                    .background(
                        if (active) gold else MaterialTheme.colorScheme.outlineVariant,
                        CircleShape
                    )
            )
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    s.title.ifBlank { "Sesi" },
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (s.updatedAt > 0) {
                    Text(
                        java.text.SimpleDateFormat("dd MMM · HH:mm", java.util.Locale("id"))
                            .format(java.util.Date(s.updatedAt)),
                        style = MaterialTheme.typography.labelSmall,
                        color = ThorTextSecondary
                    )
                }
            }
            // hapus — dibungkus Box 44dp agar mudah disentuh (standar Material)
            Box(
                Modifier
                    .size(44.dp)
                    .clickable { onDelete() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    ThorIcons.Delete,
                    "Hapus sesi",
                    tint = ThorTextSecondary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}
