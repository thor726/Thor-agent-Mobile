package com.thoragent.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Type.kt — UX redesign v0.7.0. Skala typography Thor Mobile.
 *
 * HANYA fontSize (sp), fontWeight, dan lineHeight yang didefinisikan
 * ulang. Warna TIDAK disentuh sama sekali: Color.kt, SKINS, dan
 * schemeFor() di Theme.kt tetap menjadi satu sumber kebenaran tema
 * (emas klasik / Midnight / Ember / Mono).
 *
 * Prinsip perombakan (mengganti default M3 yang sebelumnya hanya di-tweak
 * dua slot):
 * 1. KONTRAS HIERARKI JELAS — Title > Subtitle > Body kini punya tiga
 *    pembeda sekaligus: ukuran (sp), ketebalan (weight), dan jarak
 *    antar baris (lineHeight) — mata memindai struktur layar tanpa
 *    membaca. Sebelumnya titleMedium 16sp vs bodyLarge 16sp terlihat
 *    sama besar; kini 17sp SemiBold vs 17sp Normal — dibedakan ketebalan
 *    & lineHeight-nya.
 * 2. BODY CHAT LEGA — bodyMedium (teks bubble chat & MarkdownText,
 *    dipakai 24×) naik 14sp/20sp → 16sp/24sp (1,5×). Balasan agent
 *    Markdown multi-paragraf jadi nyaman dibaca di layar kecil.
 *    bodyLarge 17sp/26lh untuk paragraf panjang (dok skill).
 * 3. LABEL TIDAK LAGI MIKROSKOPIS — labelSmall (11sp) dipakai 60× di
 *    seluruh app untuk teks PENTING (subtitle menu, jam, badge) — naik
 *    ke 12sp/16lh; labelMedium naik ke 13sp/18lh. Tanpa zoom manual,
 *    subtitle tetap terbaca.
 * 4. TITLE DUA KELAS JELAS — titleLarge 22sp ExtraBold (judul layar),
 *    titleMedium 17sp SemiBold (judul Card), titleSmall 15sp SemiBold
 *    (judul baris item / SettingItemRow) — tidak lagi "semuanya sama".
 *
 * FontFamily tetap SansSerif seperti sebelumnya (labelLarge lama juga
 * SansSerif) — nol perubahan identitas huruf & warna.
 */
val ThorType: Typography = Typography(
    // ---- DISPLAY / judul besar istimewa ----
    displayLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 40.sp,
        lineHeight = 46.sp
    ),
    displayMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 34.sp,
        lineHeight = 40.sp
    ),
    displaySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 30.sp,
        lineHeight = 36.sp
    ),

    // ---- TITLE: kontras tegas melawan Body ----
    titleLarge = TextStyle(                       // judul layar / TopAppBar
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 22.sp,
        lineHeight = 28.sp
    ),
    titleMedium = TextStyle(                      // judul Card / seksi
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 17.sp,
        lineHeight = 22.sp
    ),
    titleSmall = TextStyle(                        // judul baris item
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 15.sp,
        lineHeight = 20.sp
    ),

    // ---- HEADLINE: judul di dalam konten (heading Markdown) ----
    headlineLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        lineHeight = 34.sp
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp,
        lineHeight = 30.sp
    ),
    headlineSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 20.sp,
        lineHeight = 26.sp
    ),

    // ---- BODY: nyaman dibaca lama di layar kecil ----
    bodyLarge = TextStyle(                         // paragraf panjang / dok skill
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 17.sp,
        lineHeight = 26.sp          // ~1.53x — baca panjang tetap lega
    ),
    bodyMedium = TextStyle(                        // TEKS BUBBLE CHAT & UI utama
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp          // 1.5x — kunci kenyamanan chat
    ),
    bodySmall = TextStyle(                         // teks bantu sekunder
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 18.sp          // ~1.38x
    ),

    // ---- LABEL: naik dari 11sp agar subtitle menu terbaca ----
    labelLarge = TextStyle(                        // tombol & tab NavigationBar
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 18.sp
    ),
    labelMedium = TextStyle(                       // chip, badge, meta kecil
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp,
        lineHeight = 18.sp
    ),
    labelSmall = TextStyle(                        // subtitle helper & timestamp
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp
    )
)
