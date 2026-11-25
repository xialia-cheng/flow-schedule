package com.example

import android.Manifest
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.example.data.AppThemeMode
import com.example.notification.BackgroundKeepAliveHelper
import com.example.notification.LiveActivitySourceType
import com.example.notification.LiveUpdateManager
import com.example.notification.ScheduleGuardService
import com.example.ui.AppRootScreen
import com.example.ui.ScheduleViewModel
import com.example.ui.navigation.AppDestination
import com.example.ui.smart.SmartCaptureViewModel
import com.example.ui.study.StudyViewModel
import com.example.ui.theme.FlowScheduleTheme
import com.example.ui.timetable.TimetableViewModel
import com.example.ui.update.AppUpdateViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val scheduleViewModel: ScheduleViewModel by viewModels {
        val container = (application as ScheduleApp).container
        ScheduleViewModel.Factory(
            repository = container.scheduleRepository,
            preferencesRepository = container.preferencesRepository,
            liveUpdateManager = container.liveUpdateManager,
        )
    }

    private val timetableViewModel: TimetableViewModel by viewModels {
        val container = (application as ScheduleApp).container
        TimetableViewModel.Factory(
            timetableRepository = container.timetableRepository,
            preferencesRepository = container.preferencesRepository,
            liveUpdateCoordinator = container.liveUpdateCoordinator,
        )
    }

    private val studyViewModel: StudyViewModel by viewModels {
        val container = (application as ScheduleApp).container
        StudyViewModel.Factory(
            timetableRepository = container.timetableRepository,
            preferencesRepository = container.preferencesRepository,
            liveUpdateManager = container.liveUpdateManager,
            liveUpdateCoordinator = container.liveUpdateCoordinator,
        )
    }

    private val smartCaptureViewModel: SmartCaptureViewModel by viewModels {
        val container = (application as ScheduleApp).container
        SmartCaptureViewModel.Factory(
            orchestrator = container.smartCaptureOrchestrator,
            preferencesRepository = container.preferencesRepository,
        )
    }

    private val appUpdateViewModel: AppUpdateViewModel by viewModels {
        val container = (application as ScheduleApp).container
        AppUpdateViewModel.Factory(
            preferencesRepository = container.preferencesRepository,
        )
    }

    private val deepLinkTab = MutableStateFlow<AppDestination?>(null)
    private val pendingShortcutAction = MutableStateFlow<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
            window.isStatusBarContrastEnforced = false
        }
        handleDeepLinkIntent(intent)

        val container = (application as ScheduleApp).container
        val liveUpdateManager = container.liveUpdateManager
        lifecycleScope.launch {
            val initialExclude = runCatching {
                container.preferencesRepository.settings.first().excludeFromRecents
            }.getOrDefault(false)
            BackgroundKeepAliveHelper.applyExcludeFromRecents(this@MainActivity, initialExclude)
        }

        setContent {
            val themeMode by scheduleViewModel.themeMode.collectAsStateWithLifecycle()
            val useDynamicColor by scheduleViewModel.useDynamicColor.collectAsStateWithLifecycle()
            val timetableState by timetableViewModel.uiState.collectAsStateWithLifecycle()
            val pendingDeepLink by deepLinkTab.collectAsStateWithLifecycle()
            val shortcutAction by pendingShortcutAction.collectAsStateWithLifecycle()
            val systemDark = isSystemInDarkTheme()
            val useDarkTheme = when (themeMode) {
                AppThemeMode.SYSTEM -> systemDark
                AppThemeMode.LIGHT -> false
                AppThemeMode.DARK -> true
            }

            // Full Edge-to-Edge transparent status & navigation bars synced with Light/Dark mode
            DisposableEffect(useDarkTheme) {
                enableEdgeToEdge(
                    statusBarStyle = if (useDarkTheme) {
                        SystemBarStyle.dark(Color.TRANSPARENT)
                    } else {
                        SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                    },
                    navigationBarStyle = if (useDarkTheme) {
                        SystemBarStyle.dark(Color.TRANSPARENT)
                    } else {
                        SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                    },
                )
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    window.isNavigationBarContrastEnforced = false
                    window.isStatusBarContrastEnforced = false
                }
                onDispose {}
            }

            FlowScheduleTheme(
                darkTheme = useDarkTheme,
                dynamicColor = useDynamicColor,
            ) {
                val context = LocalContext.current
                val lifecycleOwner = LocalLifecycleOwner.current

                val notificationPermissionLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestPermission(),
                ) {
                    scheduleViewModel.onResumeRefresh()
                }

                LaunchedEffect(timetableState.settings.excludeFromRecents) {
                    BackgroundKeepAliveHelper.applyExcludeFromRecents(
                        this@MainActivity,
                        timetableState.settings.excludeFromRecents,
                    )
                }

                DisposableEffect(lifecycleOwner) {
                    val observer = LifecycleEventObserver { _, event ->
                        if (event == Lifecycle.Event.ON_RESUME) {
                            scheduleViewModel.onResumeRefresh()
                            val currentSettings = timetableViewModel.uiState.value.settings
                            BackgroundKeepAliveHelper.applyExcludeFromRecents(
                                this@MainActivity,
                                currentSettings.excludeFromRecents,
                            )
                            if (currentSettings.backgroundGuardEnabled && !ScheduleGuardService.isRunning) {
                                ScheduleGuardService.tryStart(context)
                            }
                        }
                    }
                    lifecycleOwner.lifecycle.addObserver(observer)
                    onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
                }

                AppRootScreen(
                    scheduleViewModel = scheduleViewModel,
                    timetableViewModel = timetableViewModel,
                    studyViewModel = studyViewModel,
                    smartCaptureViewModel = smartCaptureViewModel,
                    appUpdateViewModel = appUpdateViewModel,
                    deepLinkDestination = pendingDeepLink,
                    onDeepLinkConsumed = { deepLinkTab.value = null },
                    pendingShortcutAction = shortcutAction,
                    onShortcutActionConsumed = { pendingShortcutAction.value = null },
                    onRequestPostNotifications = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            scheduleViewModel.onResumeRefresh()
                        }
                    },
                    onOpenPromotedSettings = {
                        runCatching {
                            context.startActivity(liveUpdateManager.buildPromotedNotificationsSettingsIntent())
                        }
                    },
                    onOpenExactAlarmSettings = {
                        runCatching {
                            context.startActivity(liveUpdateManager.buildExactAlarmSettingsIntent())
                        }
                    },
                    onOpenDndPolicySettings = {
                        runCatching {
                            context.startActivity(liveUpdateManager.buildDndPolicySettingsIntent())
                        }
                    },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleDeepLinkIntent(intent)
    }

    private fun handleDeepLinkIntent(intent: Intent?) {
        when (intent?.action) {
            ACTION_SHORTCUT_SMART_CAPTURE -> {
                pendingShortcutAction.value = ACTION_SHORTCUT_SMART_CAPTURE
                return
            }
            ACTION_SHORTCUT_NEW_SCHEDULE -> {
                deepLinkTab.value = AppDestination.SCHEDULE
                pendingShortcutAction.value = ACTION_SHORTCUT_NEW_SCHEDULE
                return
            }
            ACTION_SHORTCUT_TODAY_TIMETABLE -> {
                deepLinkTab.value = AppDestination.TIMETABLE
                return
            }
        }
        val sourceType = intent?.getStringExtra(LiveUpdateManager.EXTRA_SOURCE_TYPE) ?: return
        deepLinkTab.value = when (sourceType) {
            LiveActivitySourceType.COURSE.name -> AppDestination.TIMETABLE
            LiveActivitySourceType.SCHEDULE.name -> AppDestination.SCHEDULE
            LiveActivitySourceType.FOCUS.name -> AppDestination.STUDY
            else -> null
        }
    }

    companion object {
        const val ACTION_SHORTCUT_SMART_CAPTURE = "com.example.action.SHORTCUT_SMART_CAPTURE"
        const val ACTION_SHORTCUT_NEW_SCHEDULE = "com.example.action.SHORTCUT_NEW_SCHEDULE"
        const val ACTION_SHORTCUT_TODAY_TIMETABLE = "com.example.action.SHORTCUT_TODAY_TIMETABLE"
    }
}
