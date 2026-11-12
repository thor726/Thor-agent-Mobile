package com.thoragent.app.ui.chat

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.thoragent.app.ui.icons.ThorIcons
import com.thoragent.app.ui.theme.ThorGold
import com.thoragent.app.ui.theme.ThorOk
import com.thoragent.app.ui.theme.ThorSurfaceHigh
import com.thoragent.app.ui.theme.ThorTextSecondary

/**
 * ModelPicker — pemilih model EKSPLISIT (anti salah model = anti penalti
 * saldo): TIDAK ada lagi "Otomatis"/default katalog/katalog penyedia.
 * Daftar satu-satunya = model hidup dari endpoint profil aktif (GET
 * /models di base URL sendiri), jadi semua yang bisa dipilih pasti
 * terdaftar di provider. Meniru /model repo sumber: pilihan sesi
 * (default) atau permanen (--global).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelPickerSheet(
    vm: ChatViewModel,
    visible: Boolean,
    onDismiss: () -> Unit,
    onPick: (model: String, global: Boolean) -> Unit
) {
    if (!visible) return
    var query by remember { mutableStateOf("") }
    var global by remember { mutableStateOf(false) }
    val live by vm.liveModels.collectAsState()
    val loading by vm.modelsLoading.collectAsState()
    val sessionModel by vm.sessionModel.collectAsState()
    val effective by vm.effectiveModel.collectAsState()

    LaunchedEffect(Unit) { vm.refreshLiveModels() }

    val liveFiltered = remember(live, query) {
        if (query.isBlank()) live else live.filter { it.contains(query, true) }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(Modifier.padding(bottom = 16.dp)) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(ThorIcons.AutoAwesome, null, tint = ThorGold, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("Pilih Model", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.weight(1f))
                if (loading) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
            }
            if (effective.isNotBlank()) {
                Text(
                    "Model efektif saat ini: $effective",
                    style = MaterialTheme.typography.labelSmall,
                    color = ThorTextSecondary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp)
                )
            }

            TextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = { Text("Cari model…") },
                leadingIcon = { Icon(ThorIcons.Search, null, tint = ThorGold) },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = ThorSurfaceHigh,
                    unfocusedContainerColor = ThorSurfaceHigh
                )
            )

            // (chip tab penyedia katalog DIHAPUS — hanya model dari endpoint sendiri)

            Row(
                Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                FilterChip(
                    selected = !global,
                    onClick = { global = false },
                    label = { Text("Sesi ini") }
                )
                FilterChip(
                    selected = global,
                    onClick = { global = true },
                    label = { Text("Permanen (--global)") }
                )
            }

            LazyColumn(
                modifier = Modifier.fillMaxWidth().height(430.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // ---- INFO: TIDAK ADA model otomatis/default (anti salah
                // model = anti saldo terpotong). Model HARUS dipilih
                // eksplisit — tab "Endpoint ini" menampilkan model hidup
                // dari base URL Anda sendiri. ----
                item(key = "__noauto") {
                    Text(
                        "Pilih model eksplisit — tidak ada model otomatis/default. " +
                            "Daftar \"Dari provider Anda\" diambil langsung dari base URL Anda, " +
                            "jadi model yang dipilih selalu terdaftar di provider.",
                        style = MaterialTheme.typography.labelSmall,
                        color = ThorTextSecondary,
                        modifier = Modifier.padding(vertical = 6.dp)
                    )
                }
                // ---- MODEL HIDUP DARI PROFIL AKTIF SAJA ----
                // Tab penyedia katalog (OpenRouter/Thor/OpenAI/…) DIHAPUS:
                // memilih model katalog yang tidak ada di endpoint custom
                // = 404 = penalti saldo. Hanya daftar dari base URL sendiri.
                if (liveFiltered.isNotEmpty()) {
                    item(key = "__live") { SectionLabel("Dari provider Anda (${liveFiltered.size}) — dijamin terdaftar di endpoint", ThorIcons.Cloud) }
                    items(liveFiltered.take(200), key = { "live_" + it }) { id ->
                        ModelRow(
                            id = id,
                            desc = "",
                            selected = sessionModel == id,
                            highlight = false,
                            onClick = { onPick(id, global) }
                        )
                    }
                }
                if (live.isEmpty() && !loading) {
                    item(key = "__nolive") {
                        Column {
                            Text(
                                "Daftar model tidak tersedia — cek base URL / API key di Pengaturan Provider, " +
                                    "lalu tekan ikon segarkan. Ketik nama model persis seperti di dokumentasi provider Anda:",
                                style = MaterialTheme.typography.labelSmall,
                                color = ThorTextSecondary,
                                modifier = Modifier.padding(vertical = 6.dp)
                            )
                            Text(
                                "(nama model harus persis ID di provider — salah nama = penalti saldo)",
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = FontFamily.Monospace,
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
private fun SectionLabel(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Row(
        Modifier.padding(top = 10.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = ThorGold, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text(text, style = MaterialTheme.typography.labelLarge, color = ThorGold)
    }
}

@Composable
private fun ModelRow(id: String, desc: String, selected: Boolean, highlight: Boolean, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = when {
            selected -> ThorGold.copy(alpha = 0.16f)
            highlight -> ThorGold.copy(alpha = 0.07f)
            else -> MaterialTheme.colorScheme.surface
        },
        modifier = Modifier.fillMaxWidth().clickable { onClick() }
    ) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    id,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = if (selected || highlight) FontWeight.Bold else FontWeight.Normal,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (selected || highlight) ThorGold else MaterialTheme.colorScheme.onSurface
                )
                if (desc.isNotBlank()) {
                    Text(desc, style = MaterialTheme.typography.labelSmall, color = ThorTextSecondary, maxLines = 2)
                }
            }
            if (selected) {
                Icon(ThorIcons.CheckCircle, "dipilih", tint = ThorOk, modifier = Modifier.size(18.dp))
            } else if (highlight) {
                Icon(ThorIcons.Bolt, "default katalog", tint = ThorGold, modifier = Modifier.size(16.dp))
            }
        }
    }
}
