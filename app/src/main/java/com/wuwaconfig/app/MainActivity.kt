package com.wuwaconfig.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.scene.Scene
import androidx.navigation3.ui.NavDisplay
import com.wuwaconfig.app.nav.Backups
import com.wuwaconfig.app.nav.BattleStats
import com.wuwaconfig.app.nav.ConfigGen
import com.wuwaconfig.app.nav.History
import com.wuwaconfig.app.nav.Home
import com.wuwaconfig.app.nav.IniEditor
import com.wuwaconfig.app.nav.Logs
import com.wuwaconfig.app.nav.Navigator
import com.wuwaconfig.app.nav.Pity
import com.wuwaconfig.app.nav.Profile
import com.wuwaconfig.app.nav.ReviewTune
import com.wuwaconfig.app.nav.Settings
import com.wuwaconfig.app.nav.Setup
import com.wuwaconfig.app.nav.UserGuide
import com.wuwaconfig.app.nav.rememberNavigationState
import com.wuwaconfig.app.nav.startDestination
import com.wuwaconfig.app.nav.toEntries
import com.wuwaconfig.app.service.AdbConnectionService
import com.wuwaconfig.app.ui.BackupViewModel
import com.wuwaconfig.app.ui.DeployHistoryViewModel
import com.wuwaconfig.app.ui.GachaViewModel
import com.wuwaconfig.app.ui.IniEditorViewModel
import com.wuwaconfig.app.ui.LogInsightsViewModel
import com.wuwaconfig.app.ui.MainViewModel
import com.wuwaconfig.app.ui.ProfileViewModel
import com.wuwaconfig.app.ui.SettingsViewModel
import com.wuwaconfig.app.ui.components.BackgroundSettings
import com.wuwaconfig.app.ui.components.LocalBackgroundSettings
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
import com.wuwaconfig.app.ui.theme.WuWaConfigTheme
import com.wuwaconfig.app.ui.theme.setNeonSaturation
import kotlinx.coroutines.launch
import android.provider.Settings as AndroidSettings

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
    private val iniEditorViewModel: IniEditorViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // enableEdgeToEdge() (the ComponentActivity overload, not WindowCompat's) turns
        // isNavigationBarContrastEnforced ON, which paints a translucent grey scrim behind
        // 3-button navigation on API 29+. That scrim reads as a rendering bug here: every
        // screen draws its own GradientBackground all the way to the bottom edge, and
        // ReviewBottomBar/LogsScreen's FAB sit flush against the gesture pill. Disabling
        // it lets the app's own bottom-edge colour run to the system bar.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }
        // FLAG_SECURE blocks screenshots AND the recents-task thumbnail, and is off
        // by default: capturing a config screen is a normal thing to want to do.
        // It remains a build-time switch because a build that leaves it on is no
        // longer compatible with the release one — opt in with
        // `./gradlew assembleRelease -PsecureScreenshots=true`. App-wide rather than
        // per-screen so the guarantee cannot be forgotten on a new destination.
        if (BuildConfig.SECURE_SCREENSHOTS) {
            window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        } else {
            Log.i("MainActivity", "FLAG_SECURE off: screenshots and screencap are allowed in this build")
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
                            AndroidSettings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
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
                        AppNavigation(mainViewModel, deployHistoryViewModel, backupViewModel, logInsightsViewModel, settingsViewModel, gachaViewModel, profileViewModel, iniEditorViewModel)
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
                // Aliased, not shadowed: `android.provider.Settings` collides with the
                // nav destination named after this screen, and every destination in
                // nav/Destinations.kt is a bare domain noun, so renaming that one to
                // SettingsRoute would make it the odd one out for no benefit. The
                // platform class yields instead; it is an unrelated name collision
                // and is needed here for exactly two intent actions.
                val intent = Intent(AndroidSettings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
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
    iniEditorViewModel: IniEditorViewModel,
) {
    // Every ViewModel is passed in rather than obtained inside its entry, and
    // that is load-bearing under Navigation 3. navigation3-runtime ships no
    // ViewModelStoreOwner decorator - only SaveableStateHolderNavEntryDecorator -
    // so `viewModel()` called inside an `entry` resolves to whatever owner is in
    // scope, which here is the Activity. Navigation 2's LocalOwnersProvider DID
    // provide one per entry, so leaving IniEditorViewModel where it was would
    // have silently changed it from entry-scoped to Activity-scoped.
    //
    // The alternative, androidx.lifecycle:lifecycle-viewmodel-navigation3, is
    // published only against lifecycle 2.11.0 and would force this project off
    // 2.9.4. Hoisting costs nothing here: IniEditorViewModel takes no navigation
    // argument (readIniFile(fileName) is called on demand from the screen), so
    // entry scoping bought nothing, and it is now consistent with its seven
    // siblings.
    val setupDone by viewModel.isSetupDone.collectAsStateWithLifecycle()
    val navigationState = rememberNavigationState(startDestination(setupDone))
    val navigator = remember(navigationState) { Navigator(navigationState) }

    // Navigation 3 types transitions as one ContentTransform (enter togetherWith
    // exit) against a Scene receiver, where Navigation 2 had separate
    // EnterTransition?/ExitTransition? properties against a NavBackStackEntry
    // receiver. The values below are unchanged; only the pairing is different.
    val pushTransition: AnimatedContentTransitionScope<Scene<NavKey>>.() -> ContentTransform = {
        (
            slideInHorizontally(initialOffsetX = { it }, animationSpec = tween(300, easing = FastOutSlowInEasing)) +
                fadeIn(animationSpec = tween(300))
        )
            .togetherWith(
                slideOutHorizontally(targetOffsetX = { -it / 3 }, animationSpec = tween(250)) +
                    fadeOut(animationSpec = tween(250)),
            )
    }
    val popTransition: AnimatedContentTransitionScope<Scene<NavKey>>.() -> ContentTransform = {
        (
            slideInHorizontally(initialOffsetX = { -it }, animationSpec = tween(300, easing = FastOutSlowInEasing)) +
                fadeIn(animationSpec = tween(300))
        )
            .togetherWith(
                slideOutHorizontally(targetOffsetX = { it }, animationSpec = tween(250)) +
                    fadeOut(animationSpec = tween(250)),
            )
    }

    // Setup was the one destination with cross-fades instead of slides. Nav3's
    // entry() DSL has no per-entry transition parameters in 1.1.7 (transitions
    // travel as an untyped metadata map), so the override is selected at the
    // display level by inspecting the two scenes instead. Keyed on the scene
    // rather than on "is this the first composition" so it also applies when
    // Setup is popped back to.
    //
    // Both halves are named at each call site rather than each branch picking
    // the other's fallback: writing the push site as "else popTransition()"
    // compiles perfectly and silently slides every normal push backwards. That
    // inversion was in the first draft of this migration.
    fun setupAware(
        normal: AnimatedContentTransitionScope<Scene<NavKey>>.() -> ContentTransform,
        setup: AnimatedContentTransitionScope<Scene<NavKey>>.() -> ContentTransform,
    ): AnimatedContentTransitionScope<Scene<NavKey>>.() -> ContentTransform =
        {
            if (involvesSetup()) setup() else normal()
        }

    val pushTransitionSpec =
        setupAware(
            normal = pushTransition,
            setup = { fadeIn(animationSpec = tween(400)).togetherWith(fadeOut(animationSpec = tween(300))) },
        )
    val popTransitionSpec =
        setupAware(
            normal = popTransition,
            setup = { fadeIn(animationSpec = tween(300)).togetherWith(fadeOut(animationSpec = tween(300))) },
        )

    val entryProvider =
        entryProvider<NavKey> {
            entry<Setup> {
                SetupScreen(
                    viewModel = viewModel,
                    onComplete = { navigator.replaceAllWith(Home) },
                )
            }
            entry<Home> {
                HomeScreen(
                    viewModel = viewModel,
                    deployHistoryViewModel = deployHistoryViewModel,
                    backupViewModel = backupViewModel,
                    settingsViewModel = settingsViewModel,
                    onNavigateToBackups = { navigator.navigate(Backups) },
                    onNavigateToSettings = { navigator.navigate(Settings) },
                    onNavigateToConfigGen = { navigator.navigate(ConfigGen) },
                    onNavigateToPity = { navigator.navigate(Pity) },
                    onNavigateToProfile = { navigator.navigate(Profile) },
                    onNavigateToBattleStats = { navigator.navigate(BattleStats) },
                    onNavigateToLogs = { navigator.navigate(Logs) },
                    onNavigateToHistory = { navigator.navigate(History) },
                    onNavigateToIniEditor = { navigator.navigate(IniEditor) },
                )
            }
            entry<Backups> {
                BackupScreen(
                    viewModel = backupViewModel,
                    deployHistoryViewModel = deployHistoryViewModel,
                    onBack = { navigator.goBack() },
                )
            }
            entry<ConfigGen> {
                ConfigGenScreen(
                    viewModel = viewModel,
                    deployHistoryViewModel = deployHistoryViewModel,
                    insightsViewModel = insightsViewModel,
                    onBack = { navigator.goBack() },
                    onNavigateToReviewTune = {
                        navigator.navigate(ReviewTune)
                    },
                )
            }
            entry<ReviewTune> {
                val opts by viewModel.reviewTuneOptions.collectAsStateWithLifecycle()
                ReviewTuneScreen(
                    viewModel = viewModel,
                    deployHistoryViewModel = deployHistoryViewModel,
                    generatorOptions = opts,
                    onBack = { navigator.goBack() },
                    onDeploy = { ini, deployOpts ->
                        val accepted = deployHistoryViewModel.deployGeneratedConfigs(ini, deployOpts)
                        if (accepted) navigator.goBack()
                    },
                )
            }
            entry<Settings> {
                // detect() reads Build.* — remember so it runs once, not per recomposition.
                val chipsetInfo = remember { com.wuwaconfig.app.config.ChipsetDetector.detect() }
                val backendStatus by deployHistoryViewModel.backendStatus.collectAsStateWithLifecycle()
                SettingsScreen(
                    viewModel = settingsViewModel,
                    onBack = { navigator.goBack() },
                    onNavigateToUserGuide = { navigator.navigate(UserGuide) },
                    backendStatus = backendStatus,
                    chipsetInfo = chipsetInfo,
                    gameConfigDir = com.wuwaconfig.app.model.GamePaths.TARGET_DIR,
                    backupStorageDir = backupViewModel.backupStorageDir,
                    onChangeBackupDir = { newDir -> backupViewModel.changeBackupDir(newDir) },
                )
            }
            entry<UserGuide> {
                UserGuideScreen(
                    onBack = { navigator.goBack() },
                )
            }
            entry<Pity> {
                val backendStatus by deployHistoryViewModel.backendStatus.collectAsStateWithLifecycle()
                val isApplying by deployHistoryViewModel.isApplying.collectAsStateWithLifecycle()
                PityScreen(
                    viewModel = gachaViewModel,
                    onBack = { navigator.goBack() },
                    backendStatus = backendStatus,
                    isApplying = isApplying,
                )
            }
            entry<Profile> {
                val backendStatus by deployHistoryViewModel.backendStatus.collectAsStateWithLifecycle()
                ProfileScreen(
                    viewModel = profileViewModel,
                    onBack = { navigator.goBack() },
                    backendStatus = backendStatus,
                )
            }
            entry<BattleStats> {
                BattleStatsScreen(
                    viewModel = insightsViewModel,
                    onBack = { navigator.goBack() },
                )
            }
            entry<Logs> {
                LogsScreen(
                    viewModel = deployHistoryViewModel,
                    onBack = { navigator.goBack() },
                )
            }
            entry<History> {
                HistoryScreen(
                    viewModel = deployHistoryViewModel,
                    onBack = { navigator.goBack() },
                )
            }
            entry<IniEditor> {
                IniEditorScreen(
                    viewModel = iniEditorViewModel,
                    onBack = { navigator.goBack() },
                )
            }
        }

    NavDisplay(
        // `entries`, not `backStack`. Every recipe in the official Navigation 3
        // migration guide passes `backStack = backStack` to NavDisplay; there is no
        // such parameter in 1.1.7, and the guide's sample does not compile. Check
        // the artifact rather than the guide before copying a snippet.
        entries = navigationState.toEntries(entryProvider),
        // NavDisplay owns predictive back; this is what it calls once a back
        // gesture commits, replacing NavController.popBackStack().
        onBack = { navigator.goBack() },
        transitionSpec = pushTransitionSpec,
        popTransitionSpec = popTransitionSpec,
        modifier = Modifier,
    )
}

/** True when either side of the transition is the Setup destination. */
private fun AnimatedContentTransitionScope<Scene<NavKey>>.involvesSetup(): Boolean = (initialState as? Scene<*>)?.key is Setup || (targetState as? Scene<*>)?.key is Setup
