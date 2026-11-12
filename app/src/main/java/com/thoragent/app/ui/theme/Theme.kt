package com.thoragent.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

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

// Typografi UX redesign v0.7.0 — HANYA fontSize (sp), fontWeight, dan
// lineHeight yang dirombak; warna & font family tidak berubah sama sekali
// (Color.kt + SKINS + schemeFor() tetap satu sumber kebenaran Thor).
//
// Definisi lengkap 15 slot ada di ui/theme/Type.kt (ThorType). Prinsip:
// 1. KONTRAS HIERARKI JELAS — Title > Subtitle > Body dibedakan tiga hal
//    sekaligus: ukuran (sp), ketebalan, dan lineHeight — struktur layar
//    terbaca tanpa harus membaca.
// 2. BODY CHAT LEGA — bodyMedium (bubble chat & Markdown) naik
//    14sp/20sp → 16sp/24sp (1,5x); balasan agent panjang nyaman dibaca.
// 3. LABEL TIDAK MIKROSKOPIS — labelSmall (dipakai 60× untuk subtitle
//    menu & penjelas) naik 11sp → 12sp/16lh agar tetap terbaca jelas.
private val ThorTypography = ThorType

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
