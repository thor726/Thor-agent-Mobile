package com.thoragent.app.ui.settings.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.thoragent.app.ui.icons.ThorIcons

/**
 * SettingItemRow — pola baris pengaturan standar Thor Mobile.
 *
 * Anatomi satu baris (progressive disclosure skala mikro):
 *   [Icon 20dp] Nama Fitur (titleSmall SemiBold)     [chevron kalau bisa dibuka]
 *               Subtitle penjelas (bodySmall, warna sekunder)
 *
 * v0.8.0: subtitle memakai bodySmall (13sp) — sebelumnya labelSmall 12sp
 * yang berdempetan dengan judul; ikon kontainer 40dp + chevron 20dp
 * seragam dengan seluruh app (16/20/24dp saja); bobot judul cukup
 * SemiBold agar tidak "berteriak" di layar berisi banyak baris.
 */
@Composable
fun SettingItemRow(
    title: String,
    subtitle: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: (@Composable () -> Unit)? = null
) {
    val titleStyle = MaterialTheme.typography.titleSmall.copy(
        fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold
    )
    val subtitleStyle = MaterialTheme.typography.bodySmall

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (onClick != null) Modifier.clickable(
                    onClickLabel = "Buka $title",
                    role = Role.Button
                ) { onClick() } else Modifier
            )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Ikon fitur — 20dp di kontainer 40dp, area sentuh lega.
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    icon,
                    contentDescription = null, // title sudah menyebut nama
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(10.dp).size(20.dp)
                )
            }
            Spacer(Modifier.width(12.dp))

            // Teks: nama tebal + penjelasan tipis di bawahnya.
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    style = titleStyle,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (subtitle.isNotBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        subtitle,
                        style = subtitleStyle,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Kontrol inline (Switch / nilai saat ini) ATAU panah "bisa dibuka".
            if (content != null) {
                content()
            } else if (onClick != null) {
                Spacer(Modifier.width(8.dp))
                Icon(
                    ThorIcons.ChevronRight,
                    contentDescription = "Buka $title",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}
