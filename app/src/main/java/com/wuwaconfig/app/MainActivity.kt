package com.wuwaconfig.app

import android.util.Log
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import android.os.Environment
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.launch
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavBackStackEntry
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.wuwaconfig.app.service.AdbConnectionService
import com.wuwaconfig.app.ui.BackupViewModel
import com.wuwaconfig.app.ui.DeployHistoryViewModel
import com.wuwaconfig.app.ui.GachaViewModel
import com.wuwaconfig.app.ui.IniEditorViewModel
import com.wuwaconfig.app.ui.LogInsightsViewModel
import com.wuwaconfig.app.ui.MainViewModel
import com.wuwaconfig.app.ui.ProfileViewModel
import com.wuwaconfig.app.ui.SettingsViewModel
import com.wuwaconfig.app.ui.screens.BackupScreen
import com.wuwaconfig.app.ui.screens.BattleStatsScreen
import com.wuwaconfig.app.ui.screens.ConfigGenScreen
import com.wuwaconfig.app.ui.screens.HistoryScreen
import com.wuwaconfig.app.ui.screens.HomeScreen
import com.wuwaconfig.app.ui.screens.IniEditorScreen
import com.wuwaconfig.app.ui.screens.LogsScreen
import com.wuwaconfig.app.ui.screens.PityScreen
import com.wuwaconfig.app.ui.screens.ProfileScreen
import com.wuwaconfig.app.ui.screens.ReviewTuneScreen
import com.wuwaconfig.app.ui.screens.SettingsScreen
import com.wuwaconfig.app.ui.screens.SetupScreen
import com.wuwaconfig.app.ui.screens.TermsScreen
import com.wuwaconfig.app.ui.screens.UserGuideScreen
import com.wuwaconfig.app.ui.components.BackgroundSettings
import com.wuwaconfig.app.ui.components.LocalBackgroundSettings
import com.wuwaconfig.app.ui.theme.WuWaConfigTheme
import com.wuwaconfig.app.ui.theme.setNeonSaturation

class MainActivity : ComponentActivity() {
    private val manageStorageLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            initExternalBackupDir()
        }

    // The self-updater needs the per-app "allow from this source" grant on API 26+.
    // SettingsViewModel cannot call startActivity, so it emits a one-shot event and
    // retries the install itself once the user comes back from this screen.
    private val installPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            settingsViewModel.onInstallPermissionResult()
        }

    override fun onDestroy() {
        super.onDestroy()
        if (isFinishing) {
            // Only stop the foreground service here — DeployHistoryViewModel.onCleared
            // already disconnects the backend, and that teardown is dispatched onto
            // its own IO scope (onCleared() itself runs on the main thread, and for
            // AdbBackend disconnect() is a blocking socket close).
            try {
                stopService(Intent(this, AdbConnectionService::class.java))
            } catch (_: Exception) {
            }
        }
    }

    private val deployHistoryViewModel: DeployHistoryViewModel by viewModels()
    private val backupViewModel: BackupViewModel by viewModels()
    private val logInsightsViewModel: LogInsightsViewModel by viewModels()
    private val settingsViewModel: SettingsViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // FLAG_SECURE: block screenshots AND the recents-task thumbnail.
        //
        // Without it, SystemUI captures every screen and (on some OEM builds) mirrors
        // that thumbnail to cloud recents-sync. The screens render a gacha Convene URL
        // whose fragment carries `record_id` (a bearer credential for the
        // gacha-history endpoint), the player UID/region/level, shell command strings,
        // and the wireless-ADB host:port. App-wide rather than per-screen so the
        // guarantee cannot be forgotten on a new destination.
        if (BuildConfig.SECURE_SCREENSHOTS) {
            window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        } else {
            // Screenshot-enabled build (./gradlew -PsecureScreenshots=false). The flag
            // blocks `adb shell screencap` as well as user screenshots, which makes UI
            // work impossible, so it is a build-time switch rather than a hardcoded
            // always-on. Release builds default to protected.
            Log.w("MainActivity", "FLAG_SECURE disabled for this build (secureScreenshots=false)")
        }
        // Seed the neon palette BEFORE the first composition. Doing it from a
        // LaunchedEffect made the first frame render at the previous process's
        // saturation and then visibly snap.
        requestNotificationPermissionIfNeeded()
        setNeonSaturation(settingsViewModel.colorSaturation.value)
        // Device mutations (deploys, auto-backups) refresh the backup list.
        deployHistoryViewModel.onDeviceMutated = { backupViewModel.refreshBackups() }

        // Route the install-permission request to the system screen. Collected in
        // lifecycleScope (not the composition) because it is a navigation side effect
        // that must survive a configuration change without re-firing.
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                settingsViewModel.installPermissionRequest.collect {
                    val intent =
                        Intent(
                            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                            android.net.Uri.parse("package:$packageName"),
                        )
                    if (intent.resolveActivity(packageManager) == null) return@collect
                    try {
                        installPermissionLauncher.launch(intent)
                    } catch (_: Exception) {
                        // Some ROMs throw despite resolveActivity() succeeding.
                    }
                }
            }
        }

        setContent {
            val mainViewModel: MainViewModel = viewModel()
            val gachaViewModel: GachaViewModel = viewModel()
            val profileViewModel: ProfileViewModel = viewModel()
            val themeMode by settingsViewModel.themeMode.collectAsStateWithLifecycle()
            val textOpacity by settingsViewModel.textOpacity.collectAsStateWithLifecycle()
            val fontFamilyName by settingsViewModel.fontFamilyName.collectAsStateWithLifecycle()
            val fontScale by settingsViewModel.fontScale.collectAsStateWithLifecycle()
            val colorSaturation by settingsViewModel.colorSaturation.collectAsStateWithLifecycle()
            var showTerms by rememberSaveable { mutableStateOf(mainViewModel.needsTermsAccept()) }

            // Keeps the palette in step with the saturation slider. The first
            // value is applied in onCreate() so frame 1 is already correct.
            LaunchedEffect(colorSaturation) {
                setNeonSaturation(colorSaturation)
            }
            val backgroundImageUri by settingsViewModel.backgroundImageUri.collectAsStateWithLifecycle()
            val backgroundVideoUri by settingsViewModel.backgroundVideoUri.collectAsStateWithLifecycle()
            val backgroundOpacity by settingsViewModel.backgroundOpacity.collectAsStateWithLifecycle()
            CompositionLocalProvider(
                LocalBackgroundSettings provides BackgroundSettings(backgroundImageUri, backgroundVideoUri, backgroundOpacity),
            ) {
                WuWaConfigTheme(
                    themeMode = themeMode,
                    textOpacity = textOpacity,
                    fontFamilyName = fontFamilyName,
                    fontScale = fontScale,
                    colorSaturation = colorSaturation,
                ) {
                    if (showTerms) {
                        TermsScreen(
                            onAccept = {
                                mainViewModel.acceptTerms()
                                mainViewModel.postAcceptInit()
                                backupViewModel.initDownloadBackupDir()
                                showTerms = false
                                this@MainActivity.requestStoragePermissions()
                                this@MainActivity.initExternalBackupDir()
                            },
                        )
                    } else {
                        AppNavigation(mainViewModel, deployHistoryViewModel, backupViewModel, logInsightsViewModel, settingsViewModel, gachaViewModel, profileViewModel)
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        initExternalBackupDir()
    }

    private fun initExternalBackupDir() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !Environment.isExternalStorageManager()) return
        backupViewModel.initDownloadBackupDir()
    }

    private val permissionsLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            initExternalBackupDir()
        }

    /**
     * POST_NOTIFICATIONS is declared but was never requested, so on API 33+ the
     * foreground-service notification for a live wireless-ADB session was silently
     * suppressed while a wake lock and a shell-privileged socket were held. Ask for it
     * once, at startup, and treat refusal as non-fatal.
     */
    private val notificationLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* best-effort */ }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted =
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
        if (granted) return
        try {
            notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } catch (_: Exception) {
            // Some ROMs throw; the app must still work, it just will not show the
            // ongoing-connection notification.
        }
    }

    private fun requestStoragePermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                intent.data = android.net.Uri.parse("package:$packageName")
                // Kiosk / stripped / some Chinese ROMs may ship no Settings
                // handler for this action — fail silently instead of crashing
                // with ActivityNotFoundException (same guard as UpdateManager).
                if (intent.resolveActivity(packageManager) == null) return
                try {
                    manageStorageLauncher.launch(intent)
                } catch (_: Exception) {
                }
            }
        } else {
            val permissions = mutableListOf<String>()
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED
            ) {
                permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.Q &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED
            ) {
                permissions.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
            if (permissions.isNotEmpty()) {
                permissionsLauncher.launch(permissions.toTypedArray())
            } else {
                initExternalBackupDir()
            }
        }
    }
}

@Composable
fun AppNavigation(
    viewModel: MainViewModel,
    deployHistoryViewModel: DeployHistoryViewModel,
    backupViewModel: BackupViewModel,
    insightsViewModel: LogInsightsViewModel,
    settingsViewModel: SettingsViewModel,
    gachaViewModel: GachaViewModel,
    profileViewModel: ProfileViewModel,
) {
    val navController = rememberNavController()
    val setupDone by viewModel.isSetupDone.collectAsStateWithLifecycle()
    val startDest = if (setupDone) "home" else "setup"

    val navEnter: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition? = {
        slideInHorizontally(initialOffsetX = { it }, animationSpec = tween(300, easing = FastOutSlowInEasing)) +
            fadeIn(animationSpec = tween(300))
    }
    val navExit: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition? = {
        slideOutHorizontally(targetOffsetX = { -it / 3 }, animationSpec = tween(250)) +
            fadeOut(animationSpec = tween(250))
    }
    val popEnter: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition? = {
        slideInHorizontally(initialOffsetX = { -it }, animationSpec = tween(300, easing = FastOutSlowInEasing)) +
            fadeIn(animationSpec = tween(300))
    }
    val popExit: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition? = {
        slideOutHorizontally(targetOffsetX = { it }, animationSpec = tween(250)) +
            fadeOut(animationSpec = tween(250))
    }

    NavHost(
        navController = navController,
        startDestination = startDest,
        modifier = Modifier,
    ) {
        composable(
            "setup",
            enterTransition = { fadeIn(animationSpec = tween(400)) },
            exitTransition = { fadeOut(animationSpec = tween(300)) },
            popEnterTransition = { fadeIn(animationSpec = tween(300)) },
            popExitTransition = { fadeOut(animationSpec = tween(300)) },
        ) {
            SetupScreen(
                viewModel = viewModel,
                onComplete = {
                    navController.navigate("home") {
                        popUpTo("setup") { inclusive = true }
                    }
                },
            )
        }
        composable(
            "home",
            enterTransition = navEnter,
            exitTransition = navExit,
            popEnterTransition = popEnter,
            popExitTransition = popExit,
        ) {
            HomeScreen(
                viewModel = viewModel,
                deployHistoryViewModel = deployHistoryViewModel,
                backupViewModel = backupViewModel,
                settingsViewModel = settingsViewModel,
                onNavigateToBackups = { navController.navigate("backups") },
                onNavigateToSettings = { navController.navigate("settings") },
                onNavigateToConfigGen = { navController.navigate("configgen") },
                onNavigateToPity = { navController.navigate("pity") },
                onNavigateToProfile = { navController.navigate("profile") },
                onNavigateToBattleStats = { navController.navigate("battlestats") },
                onNavigateToLogs = { navController.navigate("logs") },
                onNavigateToHistory = { navController.navigate("history") },
                onNavigateToIniEditor = { navController.navigate("inieditor") },
            )
        }
        composable(
            "backups",
            enterTransition = navEnter,
            exitTransition = navExit,
            popEnterTransition = popEnter,
            popExitTransition = popExit,
        ) {
            BackupScreen(
                viewModel = backupViewModel,
                deployHistoryViewModel = deployHistoryViewModel,
                onBack = { navController.popBackStack() },
            )
        }
        composable(
            "configgen",
            enterTransition = navEnter,
            exitTransition = navExit,
            popEnterTransition = popEnter,
            popExitTransition = popExit,
        ) {
            ConfigGenScreen(
                viewModel = viewModel,
                deployHistoryViewModel = deployHistoryViewModel,
                insightsViewModel = insightsViewModel,
                onBack = { navController.popBackStack() },
                onNavigateToReviewTune = {
                    navController.navigate("reviewtune")
                },
            )
        }
        composable(
            "reviewtune",
            enterTransition = navEnter,
            exitTransition = navExit,
            popEnterTransition = popEnter,
            popExitTransition = popExit,
        ) {
            val opts by viewModel.reviewTuneOptions.collectAsStateWithLifecycle()
            ReviewTuneScreen(
                viewModel = viewModel,
                deployHistoryViewModel = deployHistoryViewModel,
                generatorOptions = opts,
                onBack = { navController.popBackStack() },
                onDeploy = { ini, deployOpts ->
                    val accepted = deployHistoryViewModel.deployGeneratedConfigs(ini, deployOpts)
                    if (accepted) navController.popBackStack()
                },
            )
        }
        composable(
            "settings",
            enterTransition = navEnter,
            exitTransition = navExit,
            popEnterTransition = popEnter,
            popExitTransition = popExit,
        ) {
            // detect() reads Build.* — remember so it runs once, not per recomposition.
            val chipsetInfo = remember { com.wuwaconfig.app.config.ChipsetDetector.detect() }
            val backendStatus by deployHistoryViewModel.backendStatus.collectAsStateWithLifecycle()
            SettingsScreen(
                viewModel = settingsViewModel,
                onBack = { navController.popBackStack() },
                onNavigateToUserGuide = { navController.navigate("userguide") },
                backendStatus = backendStatus,
                chipsetInfo = chipsetInfo,
                gameConfigDir = com.wuwaconfig.app.model.GamePaths.TARGET_DIR,
                backupStorageDir = backupViewModel.backupStorageDir,
                onChangeBackupDir = { newDir -> backupViewModel.changeBackupDir(newDir) },
            )
        }
        composable(
            "userguide",
            enterTransition = navEnter,
            exitTransition = navExit,
            popEnterTransition = popEnter,
            popExitTransition = popExit,
        ) {
            UserGuideScreen(
                onBack = { navController.popBackStack() },
            )
        }
        composable(
            "pity",
            enterTransition = navEnter,
            exitTransition = navExit,
            popEnterTransition = popEnter,
            popExitTransition = popExit,
        ) {
            val backendStatus by deployHistoryViewModel.backendStatus.collectAsStateWithLifecycle()
            val isApplying by deployHistoryViewModel.isApplying.collectAsStateWithLifecycle()
            PityScreen(
                viewModel = gachaViewModel,
                onBack = { navController.popBackStack() },
                backendStatus = backendStatus,
                isApplying = isApplying,
            )
        }
        composable(
            "profile",
            enterTransition = navEnter,
            exitTransition = navExit,
            popEnterTransition = popEnter,
            popExitTransition = popExit,
        ) {
            val backendStatus by deployHistoryViewModel.backendStatus.collectAsStateWithLifecycle()
            ProfileScreen(
                viewModel = profileViewModel,
                onBack = { navController.popBackStack() },
                backendStatus = backendStatus,
            )
        }
        composable(
            "battlestats",
            enterTransition = navEnter,
            exitTransition = navExit,
            popEnterTransition = popEnter,
            popExitTransition = popExit,
        ) {
            BattleStatsScreen(
                viewModel = insightsViewModel,
                onBack = { navController.popBackStack() },
            )
        }
        composable(
            "logs",
            enterTransition = navEnter,
            exitTransition = navExit,
            popEnterTransition = popEnter,
            popExitTransition = popExit,
        ) {
            LogsScreen(
                viewModel = deployHistoryViewModel,
                onBack = { navController.popBackStack() },
            )
        }
        composable(
            "history",
            enterTransition = navEnter,
            exitTransition = navExit,
            popEnterTransition = popEnter,
            popExitTransition = popExit,
        ) {
            HistoryScreen(
                viewModel = deployHistoryViewModel,
                onBack = { navController.popBackStack() },
            )
        }
        composable(
            "inieditor",
            enterTransition = navEnter,
            exitTransition = navExit,
            popEnterTransition = popEnter,
            popExitTransition = popExit,
        ) {
            val iniEditorViewModel: IniEditorViewModel = viewModel()
            IniEditorScreen(
                viewModel = iniEditorViewModel,
                onBack = { navController.popBackStack() },
            )
        }
    }
}
