package com.example.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.ui.components.PermissionOnboardingSheet
import com.example.ui.components.SmartDraftPreviewSheet
import com.example.ui.components.SmartQuickCaptureSheet
import com.example.ui.mine.MineScreen
import com.example.ui.navigation.AppDestination
import com.example.ui.smart.SmartCaptureUiState
import com.example.ui.smart.SmartCaptureViewModel
import com.example.ui.study.StudyScreen
import com.example.ui.study.StudyViewModel
import com.example.ui.theme.FlowMotion
import com.example.ui.theme.FlowTheme
import com.example.ui.timetable.TimetableScreen
import com.example.ui.timetable.TimetableViewModel
import kotlinx.coroutines.launch

@Composable
fun AppRootScreen(
    scheduleViewModel: ScheduleViewModel,
    timetableViewModel: TimetableViewModel,
    studyViewModel: StudyViewModel,
    smartCaptureViewModel: SmartCaptureViewModel,
    deepLinkDestination: AppDestination?,
    onDeepLinkConsumed: () -> Unit,
    onRequestPostNotifications: () -> Unit,
    onOpenPromotedSettings: () -> Unit,
    onOpenExactAlarmSettings: () -> Unit,
    onOpenDndPolicySettings: () -> Unit,
) {
    // Timetable is the default home tab
    var currentTab by rememberSaveable { mutableStateOf(AppDestination.TIMETABLE) }
    val scheduleState by scheduleViewModel.uiState.collectAsStateWithLifecycle()
    val smartState by smartCaptureViewModel.uiState.collectAsStateWithLifecycle()
    val snackbarEvent by scheduleViewModel.snackbarEvent.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val colorScheme = MaterialTheme.colorScheme
    val flowColors = FlowTheme.colors
    val undoLabel = stringResource(R.string.action_undo)
    val testTitle = stringResource(R.string.test_schedule_title)
    val testNotes = stringResource(R.string.test_schedule_notes)

    LaunchedEffect(deepLinkDestination) {
        if (deepLinkDestination != null) {
            currentTab = deepLinkDestination
            onDeepLinkConsumed()
        }
    }

    LaunchedEffect(snackbarEvent?.id) {
        val event = snackbarEvent ?: return@LaunchedEffect
        val result = snackbarHostState.showSnackbar(
            message = event.message,
            actionLabel = if (event.canUndoDelete) undoLabel else null,
            duration = SnackbarDuration.Short,
        )
        if (result == SnackbarResult.ActionPerformed && event.canUndoDelete) {
            scheduleViewModel.undoDelete()
        }
        scheduleViewModel.consumeSnackbar()
    }

    // Back press on secondary tabs returns to the default Timetable tab
    BackHandler(enabled = currentTab != AppDestination.TIMETABLE) {
        currentTab = AppDestination.TIMETABLE
    }

    val showMessage: (String) -> Unit = { msg ->
        scope.launch {
            snackbarHostState.showSnackbar(
                message = msg,
                duration = SnackbarDuration.Short,
            )
        }
    }

    val triggerTestFluidCloud: () -> Unit = {
        scheduleViewModel.triggerFluidCloudTest(
            title = testTitle,
            notes = testNotes,
        )
    }

    // Subtle Gemini-inspired ambient blue-violet top atmospheric glow behind the transparent status bar
    val glowPrimary = flowColors.glowPrimary.copy(alpha = if (flowColors.isDark) 0.13f else 0.065f)
    val glowSecondary = flowColors.glowSecondary.copy(alpha = if (flowColors.isDark) 0.10f else 0.05f)

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(colorScheme.background)
            .drawBehind {
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(glowPrimary, Color.Transparent),
                        center = Offset(size.width * 0.18f, size.height * 0.04f),
                        radius = size.width * 0.85f,
                    ),
                    radius = size.width * 0.85f,
                    center = Offset(size.width * 0.18f, size.height * 0.04f),
                )
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(glowSecondary, Color.Transparent),
                        center = Offset(size.width * 0.88f, size.height * 0.08f),
                        radius = size.width * 0.72f,
                    ),
                    radius = size.width * 0.72f,
                    center = Offset(size.width * 0.88f, size.height * 0.08f),
                )
            },
    ) {
        val useNavigationRail = maxWidth >= 700.dp

        if (useNavigationRail) {
            Scaffold(
                modifier = Modifier.fillMaxSize(),
                containerColor = Color.Transparent,
                contentWindowInsets = WindowInsets.safeDrawing,
                snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            ) { innerPadding ->
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .consumeWindowInsets(innerPadding),
                ) {
                    NavigationRail(
                        containerColor = colorScheme.surfaceContainerLow,
                        modifier = Modifier.fillMaxHeight(),
                    ) {
                        AppDestination.entries.forEach { destination ->
                            val selected = currentTab == destination
                            NavigationRailItem(
                                selected = selected,
                                onClick = { currentTab = destination },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) destination.selectedIcon else destination.unselectedIcon,
                                        contentDescription = destination.label,
                                    )
                                },
                                label = {
                                    Text(
                                        text = destination.label,
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                    )
                                },
                                colors = NavigationRailItemDefaults.colors(
                                    selectedIconColor = colorScheme.onPrimaryContainer,
                                    selectedTextColor = colorScheme.primary,
                                    indicatorColor = colorScheme.primaryContainer,
                                    unselectedIconColor = colorScheme.onSurfaceVariant,
                                    unselectedTextColor = colorScheme.onSurfaceVariant,
                                ),
                                modifier = Modifier.testTag(destination.testTag),
                            )
                        }
                    }

                    VerticalDivider(color = colorScheme.outlineVariant.copy(alpha = 0.65f))

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                    ) {
                        TabContentHost(
                            currentTab = currentTab,
                            scheduleViewModel = scheduleViewModel,
                            timetableViewModel = timetableViewModel,
                            studyViewModel = studyViewModel,
                            smartCaptureViewModel = smartCaptureViewModel,
                            smartState = smartState,
                            onShowMessage = showMessage,
                            onOpenPermissions = { scheduleViewModel.openPermissionSheet() },
                            onOpenDndPolicySettings = onOpenDndPolicySettings,
                            onTriggerFluidCloudTest = triggerTestFluidCloud,
                        )
                    }
                }
            }
        } else {
            Scaffold(
                modifier = Modifier.fillMaxSize(),
                containerColor = Color.Transparent,
                contentWindowInsets = WindowInsets.safeDrawing,
                snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
                bottomBar = {
                    Column {
                        HorizontalDivider(
                            thickness = 0.75.dp,
                            color = colorScheme.outlineVariant.copy(alpha = 0.55f),
                        )
                        NavigationBar(
                            containerColor = colorScheme.surfaceContainerLow,
                            tonalElevation = 0.dp,
                            windowInsets = WindowInsets.navigationBars,
                        ) {
                            AppDestination.entries.forEach { destination ->
                                val selected = currentTab == destination
                                NavigationBarItem(
                                    selected = selected,
                                    onClick = { currentTab = destination },
                                    icon = {
                                        Icon(
                                            imageVector = if (selected) destination.selectedIcon else destination.unselectedIcon,
                                            contentDescription = destination.label,
                                        )
                                    },
                                    label = {
                                        Text(
                                            text = destination.label,
                                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                        )
                                    },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = colorScheme.onPrimaryContainer,
                                        selectedTextColor = colorScheme.primary,
                                        indicatorColor = colorScheme.primaryContainer,
                                        unselectedIconColor = colorScheme.onSurfaceVariant,
                                        unselectedTextColor = colorScheme.onSurfaceVariant,
                                    ),
                                    modifier = Modifier.testTag(destination.testTag),
                                )
                            }
                        }
                    }
                },
            ) { innerPadding ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .consumeWindowInsets(innerPadding),
                ) {
                    TabContentHost(
                        currentTab = currentTab,
                        scheduleViewModel = scheduleViewModel,
                        timetableViewModel = timetableViewModel,
                        studyViewModel = studyViewModel,
                        smartCaptureViewModel = smartCaptureViewModel,
                        smartState = smartState,
                        onShowMessage = showMessage,
                        onOpenPermissions = { scheduleViewModel.openPermissionSheet() },
                        onOpenDndPolicySettings = onOpenDndPolicySettings,
                        onTriggerFluidCloudTest = triggerTestFluidCloud,
                    )
                }
            }
        }
    }

    if (smartState.showInputSheet) {
        SmartQuickCaptureSheet(
            value = smartState.inputText,
            isParsing = smartState.isParsing,
            isCustomAiReady = smartState.customAiConfig.isReady,
            onValueChange = { smartCaptureViewModel.updateInput(it) },
            onSubmit = { smartCaptureViewModel.submitQuickInput(showMessage) },
            onDismiss = { smartCaptureViewModel.dismissInputSheet() },
        )
    }

    if (smartState.showPreviewSheet && smartState.drafts.isNotEmpty()) {
        SmartDraftPreviewSheet(
            drafts = smartState.drafts,
            parseSource = smartState.parseSource,
            parseNotice = smartState.parseNotice,
            isSaving = smartState.isSaving,
            onUpdateDraft = { smartCaptureViewModel.updateDraft(it) },
            onRemoveDraft = { smartCaptureViewModel.removeDraft(it) },
            onConfirm = { smartCaptureViewModel.confirmAndCommit(showMessage) },
            onDismiss = { smartCaptureViewModel.dismissPreview() },
        )
    }

    if (scheduleState.showPermissionSheet) {
        PermissionOnboardingSheet(
            status = scheduleState.permissionStatus,
            onRequestPostNotifications = onRequestPostNotifications,
            onOpenPromotedSettings = onOpenPromotedSettings,
            onOpenExactAlarmSettings = onOpenExactAlarmSettings,
            onOpenDndPolicySettings = onOpenDndPolicySettings,
            onDismiss = { scheduleViewModel.dismissPermissionSheet() },
        )
    }
}

@Composable
private fun TabContentHost(
    currentTab: AppDestination,
    scheduleViewModel: ScheduleViewModel,
    timetableViewModel: TimetableViewModel,
    studyViewModel: StudyViewModel,
    smartCaptureViewModel: SmartCaptureViewModel,
    smartState: SmartCaptureUiState,
    onShowMessage: (String) -> Unit,
    onOpenPermissions: () -> Unit,
    onOpenDndPolicySettings: () -> Unit,
    onTriggerFluidCloudTest: () -> Unit,
) {
    AnimatedContent(
        targetState = currentTab,
        transitionSpec = {
            (fadeIn(animationSpec = tween(durationMillis = 210)) +
                scaleIn(
                    initialScale = 0.985f,
                    animationSpec = FlowMotion.standardSpring(),
                )) togetherWith fadeOut(animationSpec = tween(durationMillis = 140))
        },
        label = "root_tab_transition",
    ) { tab ->
        when (tab) {
            AppDestination.SCHEDULE -> HomeScreen(
                viewModel = scheduleViewModel,
                onOpenSmartCapture = { smartCaptureViewModel.openInputSheet() },
            )

            AppDestination.TIMETABLE -> {
                val scheduleUiState = scheduleViewModel.uiState.collectAsStateWithLifecycle().value
                TimetableScreen(
                    viewModel = timetableViewModel,
                    schedules = scheduleUiState.allSchedules,
                    activeLiveActivity = scheduleUiState.activeLiveActivity,
                    permissionStatus = scheduleUiState.permissionStatus,
                    onToggleScheduleComplete = { scheduleViewModel.toggleComplete(it) },
                    onEditSchedule = { scheduleViewModel.openEditScheduleEditor(it) },
                    onOpenSmartCapture = { smartCaptureViewModel.openInputSheet() },
                    onShowMessage = onShowMessage,
                    onOpenPermissions = onOpenPermissions,
                    onTriggerFluidCloudTest = onTriggerFluidCloudTest,
                )
            }

            AppDestination.STUDY -> StudyScreen(
                viewModel = studyViewModel,
                onShowMessage = onShowMessage,
                onOpenDndPolicySettings = onOpenDndPolicySettings,
            )

            AppDestination.MINE -> MineScreen(
                timetableViewModel = timetableViewModel,
                scheduleViewModel = scheduleViewModel,
                smartCaptureViewModel = smartCaptureViewModel,
                onOpenPermissions = onOpenPermissions,
                onOpenDndPolicySettings = onOpenDndPolicySettings,
                onTriggerFluidCloudTest = onTriggerFluidCloudTest,
                onShowMessage = onShowMessage,
            )
        }
    }
}
