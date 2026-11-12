package com.thoragent.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight

// Konstanta warna klasik Thor-agent tetap di Color.kt (satu sumber kebenaran).

/** Definisi skin /skin — primary & secondary mengikuti, identitas dasar tetap gelap. */
data class ThorSkin(
    val id: String,
    val label: String,
    val primary: Color,
    val secondary: Color,
    val bg: Color,
    val surface: Color,
    val surfaceHigh: Color
)

val SKINS = mapOf(
    "thor" to ThorSkin(
        id = "thor", label = "Thor (emas klasik)",
        primary = ThorGold, secondary = ThorAmber,
        bg = ThorBg, surface = ThorSurface, surfaceHigh = ThorSurfaceHigh
    ),
    "midnight" to ThorSkin(
        id = "midnight", label = "Midnight (biru malam)",
        primary = Color(0xFF64B5F6), secondary = Color(0xFF42A5F5),
        bg = Color(0xFF0A0E16), surface = Color(0xFF121826), surfaceHigh = Color(0xFF1A2233)
    ),
    "ember" to ThorSkin(
        id = "ember", label = "Ember (merah bara)",
        primary = Color(0xFFFF8A65), secondary = Color(0xFFFF7043),
        bg = Color(0xFF130D0B), surface = Color(0xFF1D1512), surfaceHigh = Color(0xFF271B17)
    ),
    "mono" to ThorSkin(
        id = "mono", label = "Mono (abu netral)",
        primary = Color(0xFFE0E0E0), secondary = Color(0xFFB0BEC5),
        bg = Color(0xFF0F0F0F), surface = Color(0xFF181818), surfaceHigh = Color(0xFF222222)
    )
)

private fun schemeFor(skin: ThorSkin) = darkColorScheme(
    primary = skin.primary,
    onPrimary = skin.bg,
    primaryContainer = skin.secondary,
    onPrimaryContainer = skin.bg,
    secondary = skin.secondary,
    onSecondary = skin.bg,
    tertiary = ThorInfo,
    background = skin.bg,
    onBackground = ThorTextPrimary,
    surface = skin.surface,
    onSurface = ThorTextPrimary,
    surfaceVariant = skin.surfaceHigh,
    onSurfaceVariant = ThorTextSecondary,
    outline = ThorOutline,
    outlineVariant = ThorOutline,
    error = ThorErr,
    onError = skin.bg
)

private val ThorTypography = Typography().let { t ->
    t.copy(
        titleLarge = t.titleLarge.copy(fontWeight = FontWeight.Bold),
        titleMedium = t.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        labelLarge = t.labelLarge.copy(fontFamily = FontFamily.SansSerif)
    )
}

@Composable
fun ThorTheme(skinId: String = "thor", content: @Composable () -> Unit) {
    // Selalu gelap: identitas TUI Thor-agent adalah terminal gelap + aksen skin.
    val skin = SKINS[skinId] ?: SKINS.getValue("thor")
    MaterialTheme(
        colorScheme = schemeFor(skin),
        typography = ThorTypography,
        content = content
    )
}
