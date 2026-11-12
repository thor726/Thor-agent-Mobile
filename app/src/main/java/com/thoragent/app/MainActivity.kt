package com.thoragent.app

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DismissibleNavigationDrawer
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.thoragent.app.runtime.ThorRuntime
import com.thoragent.app.ui.chat.ChatScreen
import com.thoragent.app.ui.chat.Sidebar
import com.thoragent.app.ui.commands.CommandCenterScreen
import com.thoragent.app.ui.icons.ThorIcons
import com.thoragent.app.ui.skills.SkillHubScreen
import com.thoragent.app.ui.files.FilesScreen
import com.thoragent.app.ui.provider.ProviderScreen
import com.thoragent.app.ui.root.RootScreen
import com.thoragent.app.ui.terminal.TerminalScreen
import com.thoragent.app.ui.theme.ThorTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val notifPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Edge-to-edge eksplisit: IME & system bar ditangani Compose (imePadding),
        // sehingga composer TIDAK tenggelam di balik keyboard (Android 15+).
        enableEdgeToEdge()
        setContent {
            val skin by ThorRuntime.runtime(applicationContext)
                .settings.skin.collectAsState(initial = "thor")
            ThorTheme(skinId = skin) {
                ThorApp()
            }
        }
    }

    @Composable
    private fun ThorApp() {
        val nav = rememberNavController()
        val scope = rememberCoroutineScope()
        val drawer = rememberDrawerState(initialValue = DrawerValue.Closed)
        // ViewModel Chat level-AKTIVITAS: satu instance dipakai Sidebar di
        // drawer dan layar Chat (pilih sesi di sidebar terlihat seketika
        // di chat; viewModel() di NavHost meng-scope per entry — hindari).
        val chatVm: com.thoragent.app.ui.chat.ChatViewModel = androidx.lifecycle.viewmodel.compose.viewModel(
            viewModelStoreOwner = this
        )
        RequestNotifPermissionOnce()
        val backstack by nav.currentBackStackEntryAsState()
        val current = backstack?.destination?.route ?: Routes.CHAT

        // Keyboard terbuka → sembunyikan bottom bar (ruang untuk mengetik)
        val imeVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0

        // ---- SIDEBAR ala Gemini (v0.5.0): chat baru, riwayat sesi, navigasi ----
        DismissibleNavigationDrawer(
            drawerState = drawer,
            drawerContent = {
                ModalDrawerSheet(
                    drawerContainerColor = MaterialTheme.colorScheme.surface,
                    windowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0)
                ) {
                    Sidebar(
                        vm = chatVm,
                        currentRoute = current,
                        onNavigate = { route ->
                            nav.navigate(route) {
                                popUpTo(Routes.CHAT)
                                launchSingleTop = true
                            }
                        },
                        onDismiss = { scope.launch { drawer.close() } },
                        onNewChat = {
                            chatVm.send("/new")
                            scope.launch {
                                drawer.close()
                                if (current != Routes.CHAT) nav.navigate(Routes.CHAT) {
                                    popUpTo(Routes.CHAT); launchSingleTop = true
                                }
                            }
                        },
                        onSelectSession = { id ->
                            chatVm.selectSession(id)
                            scope.launch {
                                drawer.close()
                                if (current != Routes.CHAT) nav.navigate(Routes.CHAT) {
                                    popUpTo(Routes.CHAT); launchSingleTop = true
                                }
                            }
                        },
                        onDeleteSession = { chatVm.deleteSession(it) }
                    )
                }
            }
        ) {
        Scaffold(
            bottomBar = {
                AnimatedVisibility(
                    visible = !imeVisible,
                    enter = slideInVertically(tween(220)) { it } + fadeIn(tween(180)),
                    exit = slideOutVertically(tween(200)) { it } + fadeOut(tween(160))
                ) {
                    if (Routes.bottomBarRoutes.contains(current)) {
                        NavigationBar {
                            Routes.bottomItems.forEach { item ->
                                val selected = current == item.route
                                val tint by androidx.compose.animation.animateColorAsState(
                                    if (selected) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                                    label = "navtint"
                                )
                                val iconScale by animateFloatAsState(
                                    if (selected) 1.12f else 1f,
                                    animationSpec = tween(180),
                                    label = "navscale"
                                )
                                NavigationBarItem(
                                    selected = selected,
                                    onClick = {
                                        nav.navigate(item.route) {
                                            popUpTo(Routes.CHAT)
                                            launchSingleTop = true
                                        }
                                    },
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
                }
            }
        ) { pad ->
            NavHost(
                navController = nav,
                startDestination = Routes.CHAT,
                modifier = Modifier.padding(pad)
            ) {
                composable(Routes.CHAT) {
                    // ViewModel sama dengan Sidebar (scope aktivitas) —
                    // ChatScreen memakai instance yang di-share.
                    ChatScreen(
                        vm = chatVm,
                        nav = nav,
                        showBar = true,
                        onOpenDrawer = { scope.launch { drawer.open() } }
                    )
                }
                composable(Routes.COMMANDS) {
                    // Pusat Perintah: tombol untuk slash command ber-state.
                    // sendSlash disalurkan ke dispatch 105 perintah ChatViewModel
                    // (scope aktivitas) — semua tombol lewat jalur teruji itu.
                    CommandCenterScreen(
                        nav = nav,
                        connectSlash = { sender -> chatVm.setSlashSender(sender) }
                    )
                }
                composable(Routes.TERMINAL) { TerminalScreen(nav) }
                composable(Routes.FILES) { FilesScreen(nav) }
                composable(Routes.ROOT) { RootScreen(nav) }
                composable(Routes.SKILLHUB) { SkillHubScreen(nav) }
                composable(Routes.PROVIDER) { ProviderScreen(nav) }
            }
        }
        }
    }

    @Composable
    private fun RequestNotifPermissionOnce() {
        LaunchedEffect(Unit) {
            if (Build.VERSION.SDK_INT >= 33) {
                notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    object Routes {
        const val CHAT = "chat"
        const val COMMANDS = "commands"
        const val TERMINAL = "terminal"
        const val FILES = "files"
        const val ROOT = "root"
        const val PROVIDER = "provider"
        const val SKILLHUB = "skillhub"
        val bottomBarRoutes = listOf(CHAT, TERMINAL, FILES, SKILLHUB, ROOT)
        data class BottomItem(val route: String, val label: String, val icon: ImageVector)
        val bottomItems = listOf(
            BottomItem(CHAT, "Chat", ThorIcons.Chat),
            BottomItem(TERMINAL, "Terminal", ThorIcons.Terminal),
            BottomItem(FILES, "Berkas", ThorIcons.Folder),
            BottomItem(SKILLHUB, "Skill", ThorIcons.MenuBook),
            BottomItem(ROOT, "Root", ThorIcons.Security)
        )
    }
}
