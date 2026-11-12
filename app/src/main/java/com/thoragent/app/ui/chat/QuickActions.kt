package com.thoragent.app.ui.chat

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.thoragent.app.ui.icons.ThorIcons
import com.thoragent.app.ui.icons.ThorIconsExtra
import com.thoragent.app.ui.theme.ThorTextSecondary

/**
 * SLASH COMMAND JADI TOMBOL (v0.5.0) — aksi cepat ala Gemini:
 * perintah paling sering dipakai tinggal disentuh, tanpa mengetik "/".
 * Setiap tombol mengeksekusi perintah aslinya — registry repo sumber
 * tetap menjadi satu sumber kebenaran (MobileCommands tier terlihat
 * sebagai titik kecil di kanan label).
 */
private data class QuickAction(
    val command: String,   // "/status" — dikirim apa adanya
    val label: String,     // teks tombol
    val icon: ImageVector,
    val fills: Boolean = false // perlu argumen → prefill composer
)

private val QUICK_ACTIONS = listOf(
    QuickAction("/plan", "Rencanakan", ThorIcons.Description, fills = true),
    QuickAction("/goal", "Set goal", ThorIcons.Lightbulb, fills = true),
    QuickAction("/learn", "Pelajari", ThorIcons.MenuBook, fills = true),
    QuickAction("/review", "Review kerja", ThorIcons.CheckCircle),
    QuickAction("/status", "Status", ThorIcons.Info),
    QuickAction("/compress", "Padatkan", ThorIcons.Refresh),
    QuickAction("/usage", "Token", ThorIcons.Bolt),
    QuickAction("/queue", "Antrean", ThorIcons.Schedule),
    QuickAction("/model", "Model", ThorIcons.AutoAwesome),
    QuickAction("/personality", "Gaya", ThorIcons.Person, fills = true),
    QuickAction("/skin", "Skin", ThorIcons.Settings, fills = true),
    QuickAction("/skills", "Skill", ThorIcons.Memory),
    QuickAction("/voice", "Suara", ThorIcons.VolumeUp),
    QuickAction("/agents", "Agent", ThorIcons.SmartToy),
    QuickAction("/new", "Sesi baru", ThorIcons.Add)
)

/**
 * Baris tombol aksi cepat di atas composer. onSend menerima perintah penuh
 * ("/status") — ChatViewModel menjalankannya persis seperti diketik.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun QuickActions(
    onSend: (String) -> Unit,
    onPrefill: (String) -> Unit,
    modifier: Modifier = Modifier,
    expanded: Boolean = true
) {
    val gold = MaterialTheme.colorScheme.primary
    val actions = remember(expanded) { if (expanded) QUICK_ACTIONS else QUICK_ACTIONS.take(8) }

    FlowRow(
        modifier = modifier.padding(horizontal = 8.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        actions.forEach { a ->
            QuickChip(a, gold, onSend, onPrefill)
        }
    }
}

@Composable
private fun QuickChip(
    a: QuickAction,
    gold: Color,
    onSend: (String) -> Unit,
    onPrefill: (String) -> Unit
) {
    // micro-interaction ala ChatScreen: mengecil saat ditekan
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.93f else 1f, label = "qa", animationSpec = tween(120))
    val tint by animateColorAsState(if (pressed) gold else ThorTextSecondary, label = "qa2")
    Surface(
        shape = RoundedCornerShape(50),
        color = gold.copy(alpha = 0.08f),
        border = BorderStroke(1.dp, gold.copy(alpha = 0.30f)),
        modifier = Modifier
            .scale(scale)
            .clickable(
                interactionSource = interaction,
                indication = null
            ) { if (a.fills) onPrefill("${a.command} ") else onSend(a.command) }
    ) {
        Row(
            Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
        ) {
            Icon(a.icon, null, tint = tint, modifier = Modifier.size(14.dp))
            Spacer(Modifier.size(4.dp))
            Text(
                a.label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Medium
            )
        }
    }
}
