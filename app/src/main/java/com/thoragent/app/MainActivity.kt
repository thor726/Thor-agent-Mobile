package com.thoragent.app

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.material3.DismissibleNavigationDrawer
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalDensity
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.thoragent.app.runtime.ThorRuntime
import com.thoragent.app.ui.chat.ChatScreen
import com.thoragent.app.ui.chat.Sidebar
import com.thoragent.app.ui.commands.CommandCenterScreen
import com.thoragent.app.ui.files.FilesScreen
import com.thoragent.app.ui.navigation.MainScaffold
import com.thoragent.app.ui.navigation.ThorRoute
import com.thoragent.app.ui.provider.ProviderScreen
import com.thoragent.app.ui.root.RootScreen
import com.thoragent.app.ui.settings.SettingsScreen
import com.thoragent.app.ui.skills.SkillHubScreen
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

        // Keyboard terbuka → sembunyikan bottom bar (ruang untuk mengetik)
        val imeVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0

        // ---- SIDEBAR (v0.7.0): murni riwayat sesi chat ala Gemini ----
        // Navigasi utama kini satu jalur di NavigationBar (MainScaffold);
        // blok menu Terminal/Berkas/Skill/Provider/Root dipindah ke
        // Setelan → seksi "Lanjutan" (progressive disclosure).
        DismissibleNavigationDrawer(
            drawerState = drawer,
            drawerContent = {
                ModalDrawerSheet(
                    drawerContainerColor = MaterialTheme.colorScheme.surface,
                    windowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0)
                ) {
                    Sidebar(
                        vm = chatVm,
                        currentRoute = "",
                        onNavigate = { route ->
                            nav.navigate(route) {
                                popUpTo(ThorRoute.Chat.path)
                                launchSingleTop = true
                            }
                        },
                        onDismiss = { scope.launch { drawer.close() } },
                        onNewChat = {
                            chatVm.send("/new")
                            scope.launch {
                                drawer.close()
                                if (nav.currentDestination?.route != ThorRoute.Chat.path) {
                                    nav.navigate(ThorRoute.Chat.path) {
                                        popUpTo(ThorRoute.Chat.path)
                                        launchSingleTop = true
                                    }
                                }
                            }
                        },
                        onSelectSession = { id ->
                            chatVm.selectSession(id)
                            scope.launch {
                                drawer.close()
                                if (nav.currentDestination?.route != ThorRoute.Chat.path) {
                                    nav.navigate(ThorRoute.Chat.path) {
                                        popUpTo(ThorRoute.Chat.path)
                                        launchSingleTop = true
                                    }
                                }
                            }
                        },
                        onDeleteSession = { chatVm.deleteSession(it) }
                    )
                }
            }
        ) {
            // ---- MainScaffold: NavigationBar 4 tab + NavHost inti ----
            MainScaffold(
                nav = nav,
                chatVm = chatVm,
                onOpenDrawer = { scope.launch { drawer.open() } },
                imeVisible = imeVisible,
                extraDestinations = {
                    composable(Routes.COMMANDS) {
                        // Pusat Perintah: tombol untuk slash command ber-state.
                        // sendSlash disalurkan ke dispatch 105 perintah ChatViewModel
                        // (scope aktivitas) — semua tombol lewat jalur teruji itu.
                        CommandCenterScreen(
                            nav = nav,
                            connectSlash = { sender -> chatVm.setSlashSender(sender) }
                        )
                    }
                    // v0.9.0: FILES tidak di sini lagi — naik jadi tab
                    // utama di MainScaffold (jejer Terminal).
                    composable(Routes.ROOT) { RootScreen(nav) }
                    composable(Routes.PROVIDER) { ProviderScreen(nav) }
                    composable(Routes.SETTINGS) {
                        SettingsScreen(
                            nav = nav,
                            // Pengaturan ringan (gaya jawaban) tetap dispatch lewat
                            // jalur slash teruji ChatViewModel, tanpa layar baru.
                            onSendSlash = { cmd -> chatVm.send(cmd) }
                        )
                    }
                }
            )
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

    /** Rute non-tab: dibuka dari Setelan → seksi "Lanjutan" (v0.7.0). */
    object Routes {
        const val COMMANDS = "commands"
        const val FILES = "files"
        const val ROOT = "root"
        const val PROVIDER = "provider"
        const val SETTINGS = "settings"
    }
}
