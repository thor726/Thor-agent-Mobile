package com.thoragent.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Type.kt — perombakan keterbacaan v0.8.0. Skala typography Thor Mobile.
 *
 * HANYA fontSize (sp), fontWeight, dan lineHeight yang didefinisikan
 * ulang. Warna TIDAK disentuh sama sekali: Color.kt, SKINS, dan
 * schemeFor() di Theme.kt tetap menjadi satu-satunya sumber tema
 * (emas klasik / Midnight / Ember / Mono).
 *
 * Prinsip skala v0.8.0 (keluhan pengguna: "susunan font berantakan"):
 * 1. KETEBALAN DARI STYLE, BUKAN DITIMPA — komponen kini cukup memakai
 *    slot tanpa menimpa FontWeight ad-hoc (dulu: `Bold` ditimpa di atas
 *    titleMedium yang sudah SemiBold → bobot tidak konsisten antar layar).
 * 2. HIERARKI 4 TINGKAT TERKALIBRASI — titleSmall 15/titleMedium 17/
 *    titleLarge 22/displaySmall 30, semua Medium→Bold bertingkat, dan
 *    setiap slot punya lineHeight 1.3–1.5× fontSize.
 * 3. BODY DUA UKURAN — bodyMedium 15sp/22sp (UI & bubble chat, dipakai
 *    paling luas) dan bodyLarge 16sp/24sp untuk paragraf panjang.
 *    bodyMedium turun dari 16sp agar label chip & baris daftar tidak
 *    terasa "gegem" berdampingan dengan label 13sp.
 * 4. LABEL TERBACA — labelSmall 11sp/14sp (meta sekunder murni),
 *    labelMedium 12sp/16sp (chip, meta), labelLarge 14sp/20sp (tombol &
 *    tab). Dulu subtitle menu memakai labelSmall 12sp di bawah judul
 *    15sp — kini pemetaan slot konsisten dengan bobotnya.
 *
 * FontFamily tetap SansSerif seperti sebelumnya — nol perubahan
 * identitas huruf & warna.
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

    // ---- TITLE: 4 tingkat terkalibrasi, kontras tegas melawan Body ----
    titleLarge = TextStyle(                       // judul layar / TopAppBar
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 20.sp,
        lineHeight = 26.sp
    ),
    titleMedium = TextStyle(                      // judul Card / tombol besar
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
        fontSize = 26.sp,
        lineHeight = 32.sp
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 22.sp,
        lineHeight = 28.sp
    ),
    headlineSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 19.sp,
        lineHeight = 25.sp
    ),

    // ---- BODY: nyaman dibaca lama di layar kecil ----
    bodyLarge = TextStyle(                         // paragraf panjang / dok skill
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp          // 1.5× — kunci kenyamanan baca panjang
    ),
    bodyMedium = TextStyle(                        // TEKS BUBBLE CHAT & UI utama
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 22.sp          // ~1.47× — bubble & baris daftar ringkas
    ),
    bodySmall = TextStyle(                         // teks bantu sekunder
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 18.sp          // ~1.38×
    ),

    // ---- LABEL: bobot Medium membedakan dari Body yang Normal ----
    labelLarge = TextStyle(                        // tombol & tab NavigationBar
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp
    ),
    labelMedium = TextStyle(                       // chip, badge, meta
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp
    ),
    labelSmall = TextStyle(                        // timestamp & meta sekunder
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 11.sp,
        lineHeight = 14.sp
    )
)
