package com.wuwaconfig.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavBackStackEntry
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.wuwaconfig.app.ui.MainViewModel
import com.wuwaconfig.app.ui.screens.BackupScreen
import com.wuwaconfig.app.ui.screens.BattleStatsScreen
import com.wuwaconfig.app.ui.screens.ConfigGenScreen
import com.wuwaconfig.app.ui.screens.HistoryScreen
import com.wuwaconfig.app.ui.screens.HomeScreen
import com.wuwaconfig.app.ui.screens.IniEditorScreen
import com.wuwaconfig.app.ui.screens.LogsScreen
import com.wuwaconfig.app.ui.screens.PityScreen
import com.wuwaconfig.app.ui.screens.ProfileScreen
import com.wuwaconfig.app.ui.screens.SettingsScreen
import com.wuwaconfig.app.ui.screens.SetupScreen
import com.wuwaconfig.app.ui.screens.TermsScreen
import com.wuwaconfig.app.ui.screens.UserGuideScreen
import com.wuwaconfig.app.ui.theme.WuWaConfigTheme

class MainActivity : ComponentActivity() {
    private val manageStorageLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            initExternalBackupDir()
        }

    override fun onDestroy() {
        super.onDestroy()
        if (isFinishing) {
            WuWaConfigApp.instance.backend.disconnect()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            val viewModel: MainViewModel = viewModel()
            val themeMode by viewModel.themeMode.collectAsState()
            var showTerms by remember { mutableStateOf(viewModel.needsTermsAccept()) }

            WuWaConfigTheme(themeMode = themeMode) {
                if (showTerms) {
                    TermsScreen(
                        onAccept = {
                            viewModel.acceptTerms()
                            viewModel.postAcceptInit()
                            showTerms = false
                            this@MainActivity.requestStoragePermissions()
                            this@MainActivity.initExternalBackupDir()
                        },
                    )
                } else {
                    AppNavigation(viewModel)
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
        val vm = ViewModelProvider(this)[MainViewModel::class.java]
        vm.initDownloadBackupDir()
    }

    private val permissionsLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            initExternalBackupDir()
        }

    private fun requestStoragePermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                intent.data = android.net.Uri.parse("package:$packageName")
                manageStorageLauncher.launch(intent)
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
fun AppNavigation(viewModel: MainViewModel) {
    val navController = rememberNavController()
    val startDest = if (viewModel.isSetupDone) "home" else "setup"

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
                viewModel = viewModel,
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
                onBack = { navController.popBackStack() },
            )
        }
        composable(
            "settings",
            enterTransition = navEnter,
            exitTransition = navExit,
            popEnterTransition = popEnter,
            popExitTransition = popExit,
        ) {
            SettingsScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
                onNavigateToUserGuide = { navController.navigate("userguide") },
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
            PityScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
            )
        }
        composable(
            "profile",
            enterTransition = navEnter,
            exitTransition = navExit,
            popEnterTransition = popEnter,
            popExitTransition = popExit,
        ) {
            ProfileScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
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
                viewModel = viewModel,
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
                viewModel = viewModel,
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
                viewModel = viewModel,
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
            IniEditorScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
            )
        }
    }
}
