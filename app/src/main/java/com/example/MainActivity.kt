package com.example

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Downloading
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Downloading
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.ai.configureAppCheck
import com.example.data.local.DownloadCategory
import com.example.data.local.DownloadEntity
import com.example.data.preferences.AppSettings
import com.example.ui.ai.AiChatViewModel
import com.example.ui.ai.AiStudioScreen
import com.example.ui.ai.AiStudioViewModel
import com.example.ui.browser.BrowserScreen
import com.example.ui.browser.BrowserViewModel
import com.example.ui.components.VortexSplashScreen
import com.example.ui.downloads.DownloadsScreen
import com.example.ui.downloads.DownloadsViewModel
import com.example.ui.home.HomeScreen
import com.example.ui.home.HomeViewModel
import com.example.ui.library.LibraryScreen
import com.example.ui.library.LibraryViewModel
import com.example.ui.navigation.VortexRoutes
import com.example.ui.player.VideoPlayerScreen
import com.example.ui.settings.SettingsScreen
import com.example.ui.settings.SettingsViewModel
import com.example.ui.theme.LocalVortexColors
import com.example.ui.theme.VortexDynamicBackdrop
import com.example.ui.theme.VortexTheme
import java.io.File

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        configureAppCheck(this, intent)
        enableEdgeToEdge()
        val container = (application as VortexApplication).container

        setContent {
            val settings by container.settingsRepository.settingsFlow
                .collectAsStateWithLifecycle(initialValue = AppSettings())

            var showSplash by rememberSaveable { mutableStateOf(true) }

            VortexTheme(themeMode = settings.themeMode) {
                if (showSplash) {
                    VortexSplashScreen(onSplashFinished = { showSplash = false })
                } else {
                    VortexMainApp(container = container)
                }
            }
        }
    }
}

private data class NavTabItem(
    val route: String,
    val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
)

@Composable
private fun VortexMainApp(container: AppContainer) {
    val context = LocalContext.current
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    var downloadsInitialTab by rememberSaveable { mutableIntStateOf(0) }
    var aiInitialTab by rememberSaveable { mutableIntStateOf(0) }
    var aiTabRequestNonce by rememberSaveable { mutableIntStateOf(0) }
    var libraryInitialCategory by remember { mutableStateOf<DownloadCategory?>(null) }

    val homeStateFlow = remember { container.database.downloadDao().observeActiveAndPausedDownloads() }
    val activeList by homeStateFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    val activeBadgeCount = activeList.count { it.statusEnum.isActive }

    // Request POST_NOTIFICATIONS cleanly on Android 13+ on first entry
    val notifLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    val bottomNavTabs = listOf(
        NavTabItem(VortexRoutes.HOME, R.string.nav_home, Icons.Rounded.Home, Icons.Outlined.Home, "nav_home"),
        NavTabItem(VortexRoutes.AI_STUDIO, R.string.nav_ai_studio, Icons.Rounded.AutoAwesome, Icons.Outlined.AutoAwesome, "nav_ai_studio"),
        NavTabItem(VortexRoutes.BROWSER, R.string.nav_browser, Icons.Rounded.Language, Icons.Outlined.Language, "nav_browser"),
        NavTabItem(VortexRoutes.DOWNLOADS, R.string.nav_downloads, Icons.Rounded.Downloading, Icons.Outlined.Downloading, "nav_downloads"),
        NavTabItem(VortexRoutes.LIBRARY, R.string.nav_library, Icons.Rounded.Folder, Icons.Outlined.Folder, "nav_library")
    )
    val railNavTabs = bottomNavTabs + NavTabItem(
        VortexRoutes.SETTINGS, R.string.nav_settings, Icons.Rounded.Settings, Icons.Outlined.Settings, "nav_settings"
    )

    val isPlayerRoute = currentRoute?.startsWith("player/") == true

    val navigateTab: (String) -> Unit = { route ->
        navController.navigate(route) {
            popUpTo(VortexRoutes.HOME) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    val openDownloadedItem: (DownloadEntity) -> Unit = { item ->
        when (item.categoryEnum) {
            DownloadCategory.VIDEO, DownloadCategory.AUDIO -> {
                navController.navigate(VortexRoutes.playerForDownload(item.id, item.title))
            }
            DownloadCategory.APK -> {
                libraryInitialCategory = DownloadCategory.APK
                navigateTab(VortexRoutes.LIBRARY)
            }
            else -> {
                runCatching {
                    val uri = container.safeFileManager.getContentUri(File(item.localFilePath))
                    val intent = Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(uri, item.mimeType)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    context.startActivity(Intent.createChooser(intent, "Open file"))
                }
            }
        }
    }

    VortexDynamicBackdrop {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val useNavigationRail = maxWidth >= 680.dp && !isPlayerRoute
            val extra = LocalVortexColors.current

            Row(modifier = Modifier.fillMaxSize()) {
                AnimatedVisibility(visible = useNavigationRail) {
                    NavigationRail(
                        containerColor = extra.glassSurface,
                        modifier = Modifier.fillMaxHeight()
                    ) {
                        railNavTabs.forEach { tab ->
                            val selected = currentRoute == tab.route
                            NavigationRailItem(
                                selected = selected,
                                onClick = { navigateTab(tab.route) },
                                icon = {
                                    TabIconWithBadge(tab, selected, activeBadgeCount)
                                },
                                label = { Text(stringResource(tab.labelRes)) },
                                modifier = Modifier.testTag(tab.testTag)
                            )
                        }
                    }
                }

                Scaffold(
                    containerColor = Color.Transparent,
                    contentWindowInsets = if (isPlayerRoute) WindowInsets(0, 0, 0, 0) else WindowInsets.safeDrawing,
                    bottomBar = {
                        if (!useNavigationRail && !isPlayerRoute) {
                            NavigationBar(
                                containerColor = extra.glassSurface,
                                tonalElevation = 0.dp
                            ) {
                                bottomNavTabs.forEach { tab ->
                                    val selected = currentRoute == tab.route
                                    NavigationBarItem(
                                        selected = selected,
                                        onClick = { navigateTab(tab.route) },
                                        icon = {
                                            TabIconWithBadge(tab, selected, activeBadgeCount)
                                        },
                                        label = {
                                            Text(
                                                text = stringResource(tab.labelRes),
                                                maxLines = 1
                                            )
                                        },
                                        modifier = Modifier.testTag(tab.testTag)
                                    )
                                }
                            }
                        }
                    },
                    modifier = Modifier.weight(1f)
                ) { innerPadding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    ) {
                        NavHost(
                            navController = navController,
                            startDestination = VortexRoutes.HOME,
                            enterTransition = { fadeIn() },
                            exitTransition = { fadeOut() }
                        ) {
                            composable(VortexRoutes.HOME) {
                                val vm: HomeViewModel = viewModel(
                                    factory = HomeViewModel.Factory(
                                        downloadDao = container.database.downloadDao(),
                                        mediaAnalyzer = container.mediaAnalyzer,
                                        downloadCoordinator = container.downloadCoordinator,
                                        safeFileManager = container.safeFileManager,
                                        settingsRepository = container.settingsRepository
                                    )
                                )
                                HomeScreen(
                                    viewModel = vm,
                                    onNavigateDownloads = { tab ->
                                        downloadsInitialTab = tab
                                        navigateTab(VortexRoutes.DOWNLOADS)
                                    },
                                    onNavigateAiStudio = { tab ->
                                        aiInitialTab = tab
                                        aiTabRequestNonce++
                                        navigateTab(VortexRoutes.AI_STUDIO)
                                    },
                                    onNavigateBrowser = { navigateTab(VortexRoutes.BROWSER) },
                                    onNavigateLibrary = { cat ->
                                        libraryInitialCategory = cat
                                        navigateTab(VortexRoutes.LIBRARY)
                                    },
                                    onNavigateSettings = { navigateTab(VortexRoutes.SETTINGS) },
                                    onOpenMedia = openDownloadedItem,
                                    onStreamUrl = { url, title ->
                                        navController.navigate(VortexRoutes.playerForStream(url, title))
                                    }
                                )
                            }

                            composable(VortexRoutes.AI_STUDIO) {
                                val chatVm: AiChatViewModel = viewModel(
                                    factory = AiChatViewModel.Factory(
                                        aiRepository = container.aiRepository,
                                        chatDao = container.database.chatMessageDao()
                                    )
                                )
                                val studioVm: AiStudioViewModel = viewModel(
                                    factory = AiStudioViewModel.Factory(
                                        aiRepository = container.aiRepository,
                                        safeFileManager = container.safeFileManager,
                                        downloadDao = container.database.downloadDao()
                                    )
                                )
                                AiStudioScreen(
                                    chatViewModel = chatVm,
                                    studioViewModel = studioVm,
                                    downloadDao = container.database.downloadDao(),
                                    initialTabIndex = aiInitialTab,
                                    tabRequestNonce = aiTabRequestNonce,
                                    onPlayTrack = openDownloadedItem
                                )
                            }

                            composable(VortexRoutes.BROWSER) {
                                val vm: BrowserViewModel = viewModel(
                                    factory = BrowserViewModel.Factory(
                                        historyDao = container.database.browserHistoryDao(),
                                        mediaAnalyzer = container.mediaAnalyzer,
                                        coordinator = container.downloadCoordinator
                                    )
                                )
                                BrowserScreen(
                                    viewModel = vm,
                                    onStreamUrl = { url, title ->
                                        navController.navigate(VortexRoutes.playerForStream(url, title))
                                    }
                                )
                            }

                            composable(VortexRoutes.DOWNLOADS) {
                                val vm: DownloadsViewModel = viewModel(
                                    factory = DownloadsViewModel.Factory(
                                        downloadDao = container.database.downloadDao(),
                                        coordinator = container.downloadCoordinator
                                    )
                                )
                                DownloadsScreen(
                                    viewModel = vm,
                                    initialTab = downloadsInitialTab,
                                    onOpenMedia = openDownloadedItem
                                )
                            }

                            composable(VortexRoutes.LIBRARY) {
                                val vm: LibraryViewModel = viewModel(
                                    factory = LibraryViewModel.Factory(
                                        downloadDao = container.database.downloadDao(),
                                        coordinator = container.downloadCoordinator,
                                        safeFileManager = container.safeFileManager
                                    )
                                )
                                LibraryScreen(
                                    viewModel = vm,
                                    safeFileManager = container.safeFileManager,
                                    initialCategory = libraryInitialCategory,
                                    onPlayMedia = openDownloadedItem
                                )
                            }

                            composable(VortexRoutes.SETTINGS) {
                                val vm: SettingsViewModel = viewModel(
                                    factory = SettingsViewModel.Factory(
                                        settingsRepository = container.settingsRepository,
                                        downloadDao = container.database.downloadDao(),
                                        browserHistoryDao = container.database.browserHistoryDao()
                                    )
                                )
                                SettingsScreen(viewModel = vm)
                            }

                            composable(
                                route = VortexRoutes.PLAYER,
                                arguments = listOf(
                                    navArgument("downloadId") { type = NavType.LongType },
                                    navArgument("streamUrl") { type = NavType.StringType },
                                    navArgument("title") { type = NavType.StringType }
                                )
                            ) { entry ->
                                val id = entry.arguments?.getLong("downloadId") ?: -1L
                                val stream = Uri.decode(entry.arguments?.getString("streamUrl").orEmpty())
                                val title = Uri.decode(entry.arguments?.getString("title").orEmpty())
                                VideoPlayerScreen(
                                    downloadId = id,
                                    streamUrl = stream,
                                    fallbackTitle = title,
                                    downloadDao = container.database.downloadDao(),
                                    onBack = { navController.popBackStack() }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TabIconWithBadge(
    tab: NavTabItem,
    selected: Boolean,
    activeDownloadsCount: Int
) {
    val icon = if (selected) tab.selectedIcon else tab.unselectedIcon
    if (tab.route == VortexRoutes.DOWNLOADS && activeDownloadsCount > 0) {
        BadgedBox(
            badge = {
                Badge(containerColor = MaterialTheme.colorScheme.tertiary) {
                    Text("$activeDownloadsCount")
                }
            }
        ) {
            Icon(icon, contentDescription = stringResource(tab.labelRes))
        }
    } else {
        Icon(icon, contentDescription = stringResource(tab.labelRes))
    }
}
