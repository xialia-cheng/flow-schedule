package com.example.ui.mine

import android.content.Context
import android.net.Uri
import android.os.PowerManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.BuildConfig
import com.example.R
import com.example.data.FullBackupPayload
import com.example.data.TimetableImportExport
import com.example.notification.ScheduleGuardService
import com.example.ui.ScheduleViewModel
import com.example.ui.components.BackupRestoreConfirmDialog
import com.example.ui.components.CustomTagManagerDialog
import com.example.ui.components.ExpandableSettingSection
import com.example.ui.smart.SmartCaptureViewModel
import com.example.ui.theme.FlowTheme
import com.example.ui.theme.MonoFontFamily
import com.example.ui.timetable.TimetableViewModel
import com.example.ui.update.AppUpdateViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun MineScreen(
    timetableViewModel: TimetableViewModel,
    scheduleViewModel: ScheduleViewModel,
    smartCaptureViewModel: SmartCaptureViewModel,
    appUpdateViewModel: AppUpdateViewModel,
    onOpenPermissions: () -> Unit,
    onOpenDndPolicySettings: () -> Unit = {},
    onTriggerFluidCloudTest: () -> Unit,
    onStopFluidCloudTest: () -> Unit = {},
    onShowMessage: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val ttState by timetableViewModel.uiState.collectAsStateWithLifecycle()
    val scheduleState by scheduleViewModel.uiState.collectAsStateWithLifecycle()
    val smartState by smartCaptureViewModel.uiState.collectAsStateWithLifecycle()
    val updateState by appUpdateViewModel.uiState.collectAsStateWithLifecycle()
    val settings = ttState.settings
    val colorScheme = MaterialTheme.colorScheme
    val flowColors = FlowTheme.colors
    val context = LocalContext.current
    val view = LocalView.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

    var isIgnoringBatteryOpt by remember {
        mutableStateOf(queryIgnoringBatteryOptimizations(context))
    }
    var isGuardServiceActive by remember {
        mutableStateOf(ScheduleGuardService.isRunning)
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                isIgnoringBatteryOpt = queryIgnoringBatteryOptimizations(context)
                isGuardServiceActive = ScheduleGuardService.isRunning
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(settings.backgroundGuardEnabled) {
        isGuardServiceActive = ScheduleGuardService.isRunning
    }

    val exportBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json"),
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                val success = withContext(Dispatchers.IO) {
                    runCatching {
                        val json = timetableViewModel.generateFullBackupJson()
                        context.contentResolver.openOutputStream(uri)?.use { out ->
                            out.write(json.toByteArray(Charsets.UTF_8))
                        }
                        true
                    }.getOrDefault(false)
                }
                onShowMessage(if (success) "已导出本地 JSON 备份" else "导出备份失败")
            }
        }
    }

    var pendingBackupPreview by remember { mutableStateOf<Pair<String, FullBackupPayload>?>(null) }
    var showAboutSheet by rememberSaveable { mutableStateOf(false) }
    var showTagManagerDialog by remember { mutableStateOf(false) }
    var editingTagId by remember { mutableStateOf<String?>(null) }

    // Group expand/collapse states (high-frequency Group 1 expanded, low-frequency Groups 2-4 collapsed)
    var notificationExpanded by rememberSaveable { mutableStateOf(true) }
    var appearanceExpanded by rememberSaveable { mutableStateOf(false) }
    var aiBackupExpanded by rememberSaveable { mutableStateOf(false) }
    var advancedExpanded by rememberSaveable { mutableStateOf(false) }

    val importBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                val jsonText = withContext(Dispatchers.IO) {
                    runCatching {
                        context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                    }.getOrNull()
                }
                if (jsonText.isNullOrBlank()) {
                    onShowMessage("无法读取该 JSON 备份文件")
                } else {
                    val parsed = TimetableImportExport.parseFullBackupJson(jsonText)
                    if (parsed == null) {
                        onShowMessage("JSON 备份格式无效，原有数据未作任何改动")
                    } else {
                        pendingBackupPreview = jsonText to parsed
                    }
                }
            }
        }
    }

    val permissionsReady = scheduleState.permissionStatus.allRecommendedGranted
    val notificationSummary = buildString {
        append(if (permissionsReady) "已就绪" else "待授权")
        append(" · ")
        append(
            if (settings.courseReminderEnabled) {
                if (settings.courseReminderLeadMinutes == 0) "准点提醒" else "提前${settings.courseReminderLeadMinutes}分钟"
            } else {
                "实况已关"
            },
        )
        if (settings.backgroundGuardEnabled) {
            append(" · 守护中")
        }
    }
    val appearanceSummary = "${settings.themeMode.label} · ${if (settings.hapticFeedbackEnabled) "触感已开启" else "触感已关闭"}"
    val aiBackupSummary = if (smartState.customAiConfig.isReady) {
        "自定义 AI (${smartState.customAiConfig.targetHost}) · JSON 备份"
    } else {
        "纯本地离线解析 · JSON 备份"
    }
    val advancedSummary = if (updateState.availableRelease != null) {
        "发现新版本 v${updateState.availableRelease!!.versionName} · 使用教程与开源协议"
    } else {
        "v${BuildConfig.VERSION_NAME} · 使用教程、实况演练与检查更新"
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .testTag("mine_screen"),
        contentAlignment = Alignment.TopCenter,
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 640.dp)
                .testTag("mine_lazy_list"),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "mine_header") {
                Text(
                    text = "我的",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = colorScheme.onBackground,
                )
            }

            // Top Overview Card
            item(key = "offline_overview_card") {
                OfflineOverviewCard(
                    activeTimetableName = ttState.activeTimetable?.name ?: "默认课表",
                    courseCount = ttState.courses.size,
                    todayPendingCount = scheduleState.todayPendingCount,
                    totalFocusMinutes = settings.totalFocusMinutes,
                )
            }

            // Group 1: Notifications, Live Updates & Background Guard (expanded by default)
            item(key = "group_notification_guard") {
                val permBadgeColor = if (permissionsReady) flowColors.completedAccent else flowColors.upcomingAccent
                ExpandableSettingSection(
                    icon = Icons.Rounded.NotificationsActive,
                    title = "通知、实况与后台守护",
                    summary = notificationSummary,
                    expanded = notificationExpanded,
                    onToggle = { notificationExpanded = !notificationExpanded },
                    headerTestTag = "mine_group_notification_header",
                    trailingBadge = {
                        Surface(
                            shape = CircleShape,
                            color = permBadgeColor.copy(alpha = 0.14f),
                        ) {
                            Text(
                                text = if (permissionsReady) "已就绪" else "待开启",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = permBadgeColor,
                                maxLines = 1,
                                softWrap = false,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            )
                        }
                    },
                ) {
                    NotificationAndGuardSectionContent(
                        settings = settings,
                        permissionStatus = scheduleState.permissionStatus,
                        isGuardServiceActive = isGuardServiceActive,
                        isIgnoringBatteryOpt = isIgnoringBatteryOpt,
                        context = context,
                        onOpenPermissions = onOpenPermissions,
                        onOpenDndPolicySettings = onOpenDndPolicySettings,
                        onCourseReminderEnabledChange = { timetableViewModel.setCourseReminderEnabled(it) },
                        onCourseReminderLeadMinutesChange = { timetableViewModel.setCourseReminderLeadMinutes(it) },
                        onDndLevelChange = { timetableViewModel.setDndLevel(it) },
                        onFluidCloudDisplayOptionsChange = { loc, teacher, notes ->
                            timetableViewModel.setFluidCloudDisplayOptions(
                                showLocation = loc,
                                showTeacher = teacher,
                                showNotes = notes,
                            )
                        },
                        onFluidCloudLockscreenDetailsChange = { timetableViewModel.setFluidCloudLockscreenDetails(it) },
                        onBackgroundGuardEnabledChange = { checked ->
                            timetableViewModel.setBackgroundGuardEnabled(checked, context)
                            isGuardServiceActive = checked
                        },
                        onExcludeFromRecentsChange = { checked ->
                            timetableViewModel.setExcludeFromRecents(checked, context)
                        },
                        onShowMessage = onShowMessage,
                    )
                }
            }

            // Group 2: Appearance & Interaction (collapsed by default)
            item(key = "group_appearance_interaction") {
                ExpandableSettingSection(
                    icon = Icons.Rounded.Palette,
                    title = "外观与交互",
                    summary = appearanceSummary,
                    expanded = appearanceExpanded,
                    onToggle = { appearanceExpanded = !appearanceExpanded },
                    headerTestTag = "mine_group_appearance_header",
                ) {
                    AppearanceAndInteractionSectionContent(
                        settings = settings,
                        view = view,
                        onThemeModeChange = { timetableViewModel.setThemeMode(it) },
                        onUseDynamicColorChange = { timetableViewModel.setUseDynamicColor(it) },
                        onHapticFeedbackEnabledChange = { timetableViewModel.setHapticFeedbackEnabled(it) },
                        onWeekStartDayChange = { timetableViewModel.setWeekStartDay(it) },
                        onCompactModeChange = { timetableViewModel.setCompactMode(it) },
                        onShowMorningExerciseChange = { timetableViewModel.setShowMorningExercise(it) },
                        onShowLunchBreakChange = { timetableViewModel.setShowLunchBreak(it) },
                        onShowWeekendChange = { timetableViewModel.setShowWeekend(it) },
                        onShowNonCurrentWeekCoursesChange = { timetableViewModel.setShowNonCurrentWeekCourses(it) },
                        onAddTagClick = {
                            editingTagId = null
                            showTagManagerDialog = true
                        },
                        onEditTagClick = { tagId ->
                            editingTagId = tagId
                            showTagManagerDialog = true
                        },
                        onDeleteTagClick = { tagId ->
                            scheduleViewModel.deleteCustomTag(tagId)
                        },
                    )
                }
            }

            // Group 3: Smart Parsing & Data Backup (collapsed by default)
            item(key = "group_ai_backup") {
                ExpandableSettingSection(
                    icon = Icons.Rounded.Psychology,
                    title = "智能解析与数据备份",
                    summary = aiBackupSummary,
                    expanded = aiBackupExpanded,
                    onToggle = { aiBackupExpanded = !aiBackupExpanded },
                    headerTestTag = "mine_group_ai_backup_header",
                ) {
                    AiAndBackupSectionContent(
                        savedConfig = smartState.customAiConfig,
                        isTesting = smartState.isTestingAi,
                        testFeedback = smartState.aiTestFeedback,
                        onSaveAiConfig = { enabled, baseUrl, apiKey, model ->
                            smartCaptureViewModel.saveCustomAiConfig(
                                enabled = enabled,
                                baseUrl = baseUrl,
                                apiKey = apiKey,
                                model = model,
                                onResult = onShowMessage,
                            )
                        },
                        onTestAiConnection = { baseUrl, apiKey, model ->
                            smartCaptureViewModel.testCustomAiConnection(
                                baseUrl = baseUrl,
                                apiKey = apiKey,
                                model = model,
                            )
                        },
                        onClearAiConfig = {
                            smartCaptureViewModel.clearCustomAiConfig(onShowMessage)
                        },
                        onExportJsonBackup = {
                            exportBackupLauncher.launch("ripple_plan_timetable_backup.json")
                        },
                        onImportJsonBackup = {
                            importBackupLauncher.launch(arrayOf("application/json", "text/plain", "*/*"))
                        },
                    )
                }
            }

            // Group 4: Advanced Debugging & About (collapsed by default)
            item(key = "group_advanced_about") {
                ExpandableSettingSection(
                    icon = Icons.Rounded.Tune,
                    title = "高级调试与关于",
                    summary = advancedSummary,
                    expanded = advancedExpanded,
                    onToggle = { advancedExpanded = !advancedExpanded },
                    headerTestTag = "mine_group_advanced_header",
                    trailingBadge = if (updateState.availableRelease != null) {
                        {
                            Surface(
                                shape = CircleShape,
                                color = colorScheme.errorContainer,
                            ) {
                                Text(
                                    text = "新版本",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = colorScheme.onErrorContainer,
                                    maxLines = 1,
                                    softWrap = false,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                )
                            }
                        }
                    } else {
                        null
                    },
                ) {
                    AdvancedAndAboutSectionContent(
                        hasActiveTestSchedule = scheduleState.hasActiveTestSchedule,
                        updateState = updateState,
                        onTriggerFluidCloudTest = onTriggerFluidCloudTest,
                        onStopFluidCloudTest = onStopFluidCloudTest,
                        onCheckUpdatesClick = {
                            if (updateState.availableRelease != null && !updateState.isChecking) {
                                appUpdateViewModel.openUpdateDialog()
                            } else {
                                appUpdateViewModel.checkForUpdatesManually(onShowMessage)
                            }
                        },
                        onSelectMirrorSource = { appUpdateViewModel.setUpdateMirrorSource(it) },
                        onAutoCheckUpdatesChange = { appUpdateViewModel.setAutoCheckUpdates(it) },
                        onOpenAboutSheet = { showAboutSheet = true },
                    )
                }
            }

            item(key = "mine_bottom_spacer") {
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }

    if (showAboutSheet) {
        AboutHelpLicenseSheet(
            updateState = updateState,
            onCheckUpdates = {
                if (updateState.availableRelease != null && !updateState.isChecking) {
                    appUpdateViewModel.openUpdateDialog()
                } else {
                    appUpdateViewModel.checkForUpdatesManually(onShowMessage)
                }
            },
            onDismiss = { showAboutSheet = false },
            onShowMessage = onShowMessage,
        )
    }

    if (showTagManagerDialog) {
        CustomTagManagerDialog(
            customTags = settings.customScheduleTags,
            initialEditingTagId = editingTagId,
            onAddTag = { name, colorHex, iconKey ->
                scheduleViewModel.addCustomTag(name, colorHex, iconKey)
            },
            onUpdateTag = { id, name, colorHex, iconKey ->
                scheduleViewModel.updateCustomTag(id, name, colorHex, iconKey)
            },
            onDeleteTag = { id ->
                scheduleViewModel.deleteCustomTag(id)
            },
            onDismiss = {
                showTagManagerDialog = false
                editingTagId = null
            },
        )
    }

    pendingBackupPreview?.let { (rawJson, payload) ->
        BackupRestoreConfirmDialog(
            payload = payload,
            testTagPrefix = "mine_backup_restore_",
            onSelectMode = { mode ->
                timetableViewModel.restoreFullBackupJson(rawJson, mode)
                pendingBackupPreview = null
            },
            onDismiss = { pendingBackupPreview = null },
        )
    }
}

@Composable
private fun OfflineOverviewCard(
    activeTimetableName: String,
    courseCount: Int,
    todayPendingCount: Int,
    totalFocusMinutes: Int,
) {
    val colorScheme = MaterialTheme.colorScheme
    val flowColors = FlowTheme.colors
    val cardShape = MaterialTheme.shapes.large

    val cardModifier = if (!flowColors.isDark) {
        Modifier
            .fillMaxWidth()
            .shadow(
                elevation = 4.dp,
                shape = cardShape,
                ambientColor = flowColors.cardShadowColor,
                spotColor = flowColors.cardShadowColor,
            )
    } else {
        Modifier.fillMaxWidth()
    }

    Surface(
        modifier = cardModifier,
        shape = cardShape,
        color = flowColors.cardElevated,
        border = BorderStroke(1.dp, colorScheme.outlineVariant),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Row 1: App Name + Version & Build pill (prevents crowding & wrapping)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Surface(
                    shape = CircleShape,
                    color = colorScheme.primaryContainer,
                ) {
                    Text(
                        text = "v${BuildConfig.VERSION_NAME} · Build ${BuildConfig.VERSION_CODE}",
                        fontFamily = MonoFontFamily,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = colorScheme.onPrimaryContainer,
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                            .testTag("mine_app_version_badge"),
                    )
                }
            }

            // Row 2: Active timetable on left + Local-first privacy badge on right
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = "当前课表：$activeTimetableName",
                    style = MaterialTheme.typography.bodySmall,
                    color = colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("mine_active_timetable_label"),
                )
                Surface(
                    shape = CircleShape,
                    color = colorScheme.secondaryContainer,
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Shield,
                            contentDescription = null,
                            tint = colorScheme.onSecondaryContainer,
                            modifier = Modifier.size(13.dp),
                        )
                        Text(
                            text = "本地优先 · 零数据收集",
                            style = MaterialTheme.typography.labelSmall,
                            color = colorScheme.onSecondaryContainer,
                            maxLines = 1,
                            softWrap = false,
                        )
                    }
                }
            }

            // Row 3: Quick stats summary
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                StatMetricColumn(value = "$courseCount", label = "当前课程")
                StatMetricColumn(value = "$todayPendingCount", label = "今日待办")
                StatMetricColumn(value = "${totalFocusMinutes}m", label = "累计专注")
            }
        }
    }
}

@Composable
private fun StatMetricColumn(
    value: String,
    label: String,
) {
    val colorScheme = MaterialTheme.colorScheme
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            fontFamily = MonoFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 22.sp,
            color = colorScheme.primary,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = colorScheme.onSurfaceVariant,
        )
    }
}

private fun queryIgnoringBatteryOptimizations(context: Context): Boolean {
    val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return false
    return runCatching { pm.isIgnoringBatteryOptimizations(context.packageName) }.getOrDefault(false)
}
