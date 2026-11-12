package com.thoragent.app.ui.navigation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import com.thoragent.app.ui.chat.ChatScreen
import com.thoragent.app.ui.icons.ThorIcons
import com.thoragent.app.ui.skills.SkillHubScreen
import com.thoragent.app.ui.terminal.TerminalScreen

/**
 * MainScaffold — kerangka navigasi utama hasil UX redesign (v0.7.0).
 *
 * Prinsip desain:
 * 1. COGNITIVE LOAD RENDAH — NavigationBar hanya memuat 4 tab inti yang
 *    relevan untuk semua pengguna: Chat AI, Skill, Terminal, Setelan.
 *    Fitur teknis (Root/libsu, endpoint provider, berkas, pusat perintah)
 *    DITURUNKAN ke sub-menu Setelan (progressive disclosure), bukan lagi
 *    tab level-atas.
 * 2. SATU JALUR NAVIGASI — sebelumnya bottom bar (5 tab) + sidebar (5
 *    menu kembar) membentuk dua sistem navigasi tumpang tindih; kini
 *    drawer sidebar fokus pada riwayat sesi chat, dan NavigationBar
 *    menjadi satu-satunya navigasi utama.
 * 3. LABEL BAHASA MANUSIA — "Chat AI", "Skill", "Terminal", "Setelan"
 *    menggantikan label teknis ("Root", "Berkas", "Skill Hub").
 *
 * Rute COMMANDS/PROVIDER/FILES/ROOT tetap terdaftar di NavHost
 * MainActivity (deep-link & aksi lama tetap berfungsi) — hanya pintu
 * masuk primernya yang berpindah ke SettingsScreen → seksi "Lanjutan".
 */
@Composable
fun MainScaffold(
    nav: NavHostController,
    chatVm: com.thoragent.app.ui.chat.ChatViewModel,
    onOpenDrawer: () -> Unit,
    imeVisible: Boolean,
    extraDestinations: NavGraphBuilder.() -> Unit = {}
) {
    val backstack by nav.currentBackStackEntryAsState()
    val currentRoute = backstack?.destination?.route ?: ThorRoute.Chat.path

    Scaffold(
        bottomBar = {
            AnimatedVisibility(
                visible = !imeVisible,
                enter = slideInVertically(tween(220)) { it } + fadeIn(tween(180)),
                exit = slideOutVertically(tween(200)) { it } + fadeOut(tween(160))
            ) {
                ThorBottomBar(
                    currentRoute = currentRoute,
                    onSelect = { item ->
                        nav.navigate(item.path) {
                            popUpTo(ThorRoute.Chat.path)
                            launchSingleTop = true
                        }
                    }
                )
            }
        }
    ) { pad ->
        NavHost(
            navController = nav,
            startDestination = ThorRoute.Chat.path,
            modifier = Modifier.padding(pad)
        ) {
            composable(ThorRoute.Chat.path) {
                ChatScreen(
                    vm = chatVm,
                    nav = nav,
                    showBar = true,
                    onOpenDrawer = onOpenDrawer
                )
            }
            composable(ThorRoute.Skills.path) {
                SkillHubScreen(nav)
            }
            composable(ThorRoute.Terminal.path) {
                TerminalScreen(nav)
            }
            composable(ThorRoute.Settings.path) {
                // Pusat pengaturan hasil re-strukturisasi; seluruh fitur
                // teknis dibuka dari sini (progressive disclosure dua
                // tingkat: seksi "Agent" dan seksi "Lanjutan").
                com.thoragent.app.ui.settings.SettingsScreen(nav)
            }
            // Rute non-tab (commands/files/root/provider): dibuka dari
            // Setelan → "Lanjutan"; tetap terdaftar agar deep-link &
            // aksi lama tetap berfungsi.
            extraDestinations()
        }
    }
}

/**
 * Tab NavigationBar final — label bahasa manusia; menambah/mengubah
 * tab cukup satu baris enum (label, ikon, dan rute tidak bisa tidak
 * sinkron seperti pada BottomItem + bottomBarRoutes terpisah dulu).
 */
enum class ThorRoute(val path: String, val label: String, val icon: ImageVector) {
    Chat("chat", "Chat AI", ThorIcons.Chat),
    Skills("skillhub", "Skill", ThorIcons.MenuBook),
    Terminal("terminal", "Terminal", ThorIcons.Terminal),
    Settings("settings", "Setelan", ThorIcons.Settings);
}

/**
 * ThorBottomBar — NavigationBar M3 4 tab dengan indicator lembut.
 * Label selalu tampil (pengguna awam tidak boleh menebak arti ikon);
 * terpilih → SemiBold, tidak → Normal. Nol perubahan warna tema.
 */
@Composable
fun ThorBottomBar(
    currentRoute: String,
    onSelect: (ThorRoute) -> Unit
) {
    NavigationBar {
        ThorRoute.entries.forEach { item ->
            val selected = currentRoute == item.path
            val tint by androidx.compose.animation.animateColorAsState(
                targetValue = if (selected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant,
                animationSpec = tween(180),
                label = "navTint"
            )
            val iconScale by animateFloatAsState(
                targetValue = if (selected) 1.12f else 1f,
                animationSpec = tween(180),
                label = "navScale"
            )
            NavigationBarItem(
                selected = selected,
                onClick = { onSelect(item) },
                icon = {
                    Icon(
                        item.icon,
                        contentDescription = item.label,
                        tint = tint,
                        modifier = Modifier.scale(iconScale)
                    )
                },
                label = {
                    Text(
                        item.label,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
                    )
                },
                colors = NavigationBarItemDefaults.colors(
                    indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                )
            )
        }
    }
}
