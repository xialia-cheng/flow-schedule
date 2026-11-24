package com.example.ui.mine

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.BatterySaver
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.DoNotDisturbOn
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.FileUpload
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Label
import androidx.compose.material.icons.rounded.NetworkCheck
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.SettingsApplications
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.SystemUpdateAlt
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Update
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.BuildConfig
import com.example.R
import com.example.data.AppThemeMode
import com.example.data.BackupRestoreMode
import com.example.data.CustomAiConfig
import com.example.data.CustomScheduleTag
import com.example.data.DndLevel
import com.example.data.FullBackupPayload
import com.example.data.ScheduleCategory
import com.example.data.TagIconCatalog
import com.example.data.TimetableImportExport
import com.example.data.WeekStartDay
import com.example.notification.LiveUpdatePermissionStatus
import com.example.notification.ScheduleGuardService
import com.example.smart.OpenAiCompatibleClient
import com.example.ui.ScheduleViewModel
import com.example.ui.components.AppHaptics
import com.example.ui.components.CustomTagManagerDialog
import com.example.ui.components.rememberAppHaptics
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
    val haptics = rememberAppHaptics()
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

    Box(
        modifier = modifier
            .fillMaxSize()
            .testTag("mine_screen"),
        contentAlignment = Alignment.TopCenter,
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 640.dp),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item(key = "mine_header") {
                Text(
                    text = "我的",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = colorScheme.onBackground,
                )
            }

            // 1. Offline stats overview card
            item(key = "offline_overview_card") {
                OfflineOverviewCard(
                    activeTimetableName = ttState.activeTimetable?.name ?: "默认课表",
                    courseCount = ttState.courses.size,
                    todayPendingCount = scheduleState.todayPendingCount,
                    totalFocusMinutes = settings.totalFocusMinutes,
                )
            }

            // 2. Fluid Cloud permission diagnostics & collapsible debug test area
            item(key = "fluid_cloud_section") {
                var debugExpanded by rememberSaveable { mutableStateOf(false) }

                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "实况通知 · Live Updates",
                        style = MaterialTheme.typography.titleSmall,
                        color = colorScheme.onSurfaceVariant,
                    )

                    FluidCloudPermissionCard(
                        status = scheduleState.permissionStatus,
                        onOpenPermissions = onOpenPermissions,
                    )

                    // Collapsible debug section containing "测试实况通知效果"
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.large,
                        color = flowColors.cardElevated,
                        border = BorderStroke(1.dp, colorScheme.outlineVariant),
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        haptics.tick()
                                        debugExpanded = !debugExpanded
                                    }
                                    .padding(horizontal = 16.dp, vertical = 14.dp)
                                    .testTag("mine_fluid_cloud_debug_toggle"),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    modifier = Modifier.weight(1f),
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.BugReport,
                                        contentDescription = null,
                                        tint = colorScheme.primary,
                                        modifier = Modifier.size(20.dp),
                                    )
                                    Column {
                                        Text(
                                            text = "实况通知调试与效果演练",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.SemiBold,
                                            color = colorScheme.onSurface,
                                        )
                                        Text(
                                            text = if (debugExpanded) "点击收起调试面板" else "展开以手动触发 1 分钟状态栏实况胶囊或实时进度通知测试",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                                Icon(
                                    imageVector = if (debugExpanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                                    contentDescription = if (debugExpanded) "收起调试区" else "展开调试区",
                                    tint = colorScheme.onSurfaceVariant,
                                )
                            }

                            AnimatedVisibility(
                                visible = debugExpanded,
                                enter = expandVertically() + fadeIn(),
                                exit = shrinkVertically() + fadeOut(),
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
                                ) {
                                    FluidCloudTestCard(
                                        hasActiveTest = scheduleState.hasActiveTestSchedule,
                                        onTriggerTest = onTriggerFluidCloudTest,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 2.5 Notification, DND Level & Fluid Cloud Display Settings
            item(key = "notification_dnd_section") {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "通知与免打扰设置",
                        style = MaterialTheme.typography.titleSmall,
                        color = colorScheme.onSurfaceVariant,
                    )

                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("mine_notification_dnd_card"),
                        shape = MaterialTheme.shapes.large,
                        color = flowColors.cardElevated,
                        border = BorderStroke(1.dp, colorScheme.outlineVariant),
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            MineSwitchRow(
                                title = "上课自动触发实况通知",
                                subtitle = "课前提醒窗口与上课期间在状态栏实况胶囊或进度通知中实时显示课程倒计时",
                                checked = settings.courseReminderEnabled,
                                onCheckedChange = { timetableViewModel.setCourseReminderEnabled(it) },
                                testTag = "mine_switch_course_reminder",
                            )

                            if (settings.courseReminderEnabled) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Text(
                                        text = "课前提前提醒窗口（0–120 分钟）",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = colorScheme.onSurfaceVariant,
                                    )
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                                    ) {
                                        IconButton(
                                            onClick = {
                                                haptics.tick()
                                                timetableViewModel.setCourseReminderLeadMinutes(
                                                    (settings.courseReminderLeadMinutes - 1).coerceAtLeast(0),
                                                )
                                            },
                                            enabled = settings.courseReminderLeadMinutes > 0,
                                            modifier = Modifier.size(32.dp),
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.Remove,
                                                contentDescription = "减少提醒分钟",
                                                modifier = Modifier.size(16.dp),
                                            )
                                        }
                                        Text(
                                            text = if (settings.courseReminderLeadMinutes == 0) {
                                                "准点"
                                            } else {
                                                "${settings.courseReminderLeadMinutes}分"
                                            },
                                            fontFamily = MonoFontFamily,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp,
                                            color = colorScheme.primary,
                                        )
                                        IconButton(
                                            onClick = {
                                                haptics.tick()
                                                timetableViewModel.setCourseReminderLeadMinutes(
                                                    (settings.courseReminderLeadMinutes + 1).coerceAtMost(120),
                                                )
                                            },
                                            enabled = settings.courseReminderLeadMinutes < 120,
                                            modifier = Modifier.size(32.dp),
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.Add,
                                                contentDescription = "增加提醒分钟",
                                                modifier = Modifier.size(16.dp),
                                            )
                                        }
                                    }
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    listOf(5, 10, 15, 30).forEach { mins ->
                                        SelectablePillOption(
                                            label = "${mins}分钟",
                                            selected = settings.courseReminderLeadMinutes == mins,
                                            onClick = { timetableViewModel.setCourseReminderLeadMinutes(mins) },
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                                            modifier = Modifier
                                                .weight(1f)
                                                .testTag("mine_lead_time_$mins"),
                                        )
                                    }
                                }
                            }

                            HorizontalDivider(
                                modifier = Modifier.padding(vertical = 12.dp),
                                color = colorScheme.outlineVariant,
                            )

                            BackgroundGuardSettingsBlock(
                                enabled = settings.backgroundGuardEnabled,
                                excludeFromRecents = settings.excludeFromRecents,
                                isServiceRunning = isGuardServiceActive,
                                isIgnoringBatteryOptimizations = isIgnoringBatteryOpt,
                                onToggle = { checked ->
                                    timetableViewModel.setBackgroundGuardEnabled(checked, context)
                                    isGuardServiceActive = checked
                                },
                                onToggleExcludeFromRecents = { checked ->
                                    timetableViewModel.setExcludeFromRecents(checked, context)
                                },
                                onOpenAutoStartSettings = {
                                    if (!com.example.notification.BackgroundKeepAliveHelper.openAutoStartOrBackgroundSettings(context)) {
                                        onShowMessage("无法打开系统自启动或应用详情页")
                                    }
                                },
                                onOpenAppDetails = {
                                    val intent = com.example.notification.BackgroundKeepAliveHelper.buildAppDetailsIntent(context)
                                    runCatching { context.startActivity(intent) }.onFailure {
                                        onShowMessage("无法打开系统应用详情页")
                                    }
                                },
                                onOpenBatterySettings = {
                                    if (!com.example.notification.BackgroundKeepAliveHelper.openBatteryOptimizationSettings(context)) {
                                        onShowMessage("无法打开系统电池优化设置")
                                    }
                                },
                            )

                            HorizontalDivider(
                                modifier = Modifier.padding(vertical = 12.dp),
                                color = colorScheme.outlineVariant,
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.DoNotDisturbOn,
                                        contentDescription = null,
                                        tint = colorScheme.primary,
                                        modifier = Modifier.size(18.dp),
                                    )
                                    Text(
                                        text = "系统免打扰级别",
                                        style = MaterialTheme.typography.titleSmall,
                                        color = colorScheme.onSurface,
                                    )
                                }
                                Surface(
                                    onClick = {
                                        haptics.tick()
                                        onOpenDndPolicySettings()
                                    },
                                    shape = CircleShape,
                                    color = if (scheduleState.permissionStatus.hasDndPolicyAccess) {
                                        flowColors.completedAccent.copy(alpha = 0.14f)
                                    } else {
                                        flowColors.upcomingAccent.copy(alpha = 0.16f)
                                    },
                                    modifier = Modifier.testTag("mine_dnd_access_badge"),
                                ) {
                                    Text(
                                        text = if (scheduleState.permissionStatus.hasDndPolicyAccess) "已授权" else "去授权",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (scheduleState.permissionStatus.hasDndPolicyAccess) {
                                            flowColors.completedAccent
                                        } else {
                                            flowColors.upcomingAccent
                                        },
                                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp),
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = settings.dndLevel.subtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = colorScheme.onSurfaceVariant,
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                DndLevel.entries.forEach { level ->
                                    SelectablePillOption(
                                        label = level.label,
                                        selected = settings.dndLevel == level,
                                        onClick = { timetableViewModel.setDndLevel(level) },
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("mine_dnd_level_${level.name.lowercase()}"),
                                    )
                                }
                            }

                            HorizontalDivider(
                                modifier = Modifier.padding(vertical = 12.dp),
                                color = colorScheme.outlineVariant,
                            )

                            Text(
                                text = "实况通知卡片展示内容",
                                style = MaterialTheme.typography.titleSmall,
                                color = colorScheme.onSurface,
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            MineSwitchRow(
                                title = "展示地点 / 教室",
                                subtitle = "在实况通知标题与展开卡片中显示 @教室 或地点",
                                checked = settings.fluidCloudShowLocation,
                                onCheckedChange = { checked ->
                                    timetableViewModel.setFluidCloudDisplayOptions(
                                        showLocation = checked,
                                        showTeacher = settings.fluidCloudShowTeacher,
                                        showNotes = settings.fluidCloudShowNotes,
                                    )
                                },
                                testTag = "mine_switch_fc_location",
                            )
                            MineSwitchRow(
                                title = "展示授课教师",
                                subtitle = "在课程实况通知展开详情中附带授课教师姓名",
                                checked = settings.fluidCloudShowTeacher,
                                onCheckedChange = { checked ->
                                    timetableViewModel.setFluidCloudDisplayOptions(
                                        showLocation = settings.fluidCloudShowLocation,
                                        showTeacher = checked,
                                        showNotes = settings.fluidCloudShowNotes,
                                    )
                                },
                                testTag = "mine_switch_fc_teacher",
                            )
                            MineSwitchRow(
                                title = "展示备注信息",
                                subtitle = "在课程与日程实况通知展开卡片中附带备注内容",
                                checked = settings.fluidCloudShowNotes,
                                onCheckedChange = { checked ->
                                    timetableViewModel.setFluidCloudDisplayOptions(
                                        showLocation = settings.fluidCloudShowLocation,
                                        showTeacher = settings.fluidCloudShowTeacher,
                                        showNotes = checked,
                                    )
                                },
                                testTag = "mine_switch_fc_notes",
                            )
                            MineSwitchRow(
                                title = "锁屏展示课程与日程详情",
                                subtitle = "默认关闭；关闭时锁屏仅显示事件名称与倒计时，隐藏地点、教师与备注",
                                checked = settings.fluidCloudLockscreenDetails,
                                onCheckedChange = { checked ->
                                    timetableViewModel.setFluidCloudLockscreenDetails(checked)
                                },
                                testTag = "mine_switch_fc_lockscreen",
                            )
                        }
                    }
                }
            }

            // 3. Appearance & Timetable Preferences
            item(key = "preferences_section") {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "外观与课表偏好",
                        style = MaterialTheme.typography.titleSmall,
                        color = colorScheme.onSurfaceVariant,
                    )

                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.large,
                        color = flowColors.cardElevated,
                        border = BorderStroke(1.dp, colorScheme.outlineVariant),
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "外观主题",
                                style = MaterialTheme.typography.titleSmall,
                                color = colorScheme.onSurface,
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                AppThemeMode.entries.forEach { mode ->
                                    SelectablePillOption(
                                        label = mode.label,
                                        selected = settings.themeMode == mode,
                                        onClick = { timetableViewModel.setThemeMode(mode) },
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("mine_theme_${mode.name.lowercase()}"),
                                    )
                                }
                            }

                            HorizontalDivider(
                                modifier = Modifier.padding(vertical = 12.dp),
                                color = colorScheme.outlineVariant,
                            )

                            Text(
                                text = "每周起始日（课表与日程日期条同步生效）",
                                style = MaterialTheme.typography.titleSmall,
                                color = colorScheme.onSurface,
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                WeekStartDay.entries.forEach { startDay ->
                                    SelectablePillOption(
                                        label = "从${startDay.label}开始",
                                        selected = settings.weekStartDay == startDay,
                                        onClick = { timetableViewModel.setWeekStartDay(startDay) },
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("mine_week_start_${startDay.name.lowercase()}"),
                                    )
                                }
                            }

                            HorizontalDivider(
                                modifier = Modifier.padding(vertical = 12.dp),
                                color = colorScheme.outlineVariant,
                            )

                            MineSwitchRow(
                                title = "系统壁纸动态取色（Dynamic Color）",
                                subtitle = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                    "开启后跟随系统壁纸色系；关闭则使用深邃蓝紫微光默认主题"
                                } else {
                                    "需 Android 12 及以上系统，当前自动使用深邃蓝紫微光主题"
                                },
                                checked = settings.useDynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S,
                                onCheckedChange = { timetableViewModel.setUseDynamicColor(it) },
                                testTag = "mine_switch_dynamic_color",
                            )

                            MineSwitchRow(
                                title = "简洁模式",
                                subtitle = "隐藏课表非本周灰显课程、附加时段条与下课时间",
                                checked = settings.compactMode,
                                onCheckedChange = { timetableViewModel.setCompactMode(it) },
                                testTag = "mine_switch_compact",
                            )
                            MineSwitchRow(
                                title = "应用内触觉反馈",
                                subtitle = "在切换标签、勾选完成与点击微调时提供清脆触感（尊重系统全局开关）",
                                checked = settings.hapticFeedbackEnabled,
                                onCheckedChange = { enabled ->
                                    if (enabled && !settings.hapticFeedbackEnabled) {
                                        view.performHapticFeedback(AppHaptics.resolveTickConstant())
                                    }
                                    timetableViewModel.setHapticFeedbackEnabled(enabled)
                                },
                                testTag = "mine_switch_haptic_feedback",
                            )
                            MineSwitchRow(
                                title = if (settings.extraBannerTitle.isNotBlank()) {
                                    "顶部附加时段条（${settings.extraBannerTitle}）"
                                } else {
                                    "顶部附加时段条"
                                },
                                subtitle = if (settings.showMorningExercise) {
                                    "当前时段 ${settings.extraBannerStartTime} - ${settings.extraBannerEndTime}，可在课表设置中自定义"
                                } else {
                                    "默认关闭；开启后在周视图顶部显示自定义打卡时段"
                                },
                                checked = settings.showMorningExercise,
                                onCheckedChange = { timetableViewModel.setShowMorningExercise(it) },
                                testTag = "mine_switch_morning",
                            )
                            MineSwitchRow(
                                title = "课间午休分隔带（${settings.lunchBreakTitle.ifBlank { "午休" }}）",
                                subtitle = if (settings.showLunchBreak) {
                                    "当前显示在第 ${settings.lunchAfterSection} 节后，可在课表设置中自定义"
                                } else {
                                    "默认关闭；开启后在指定节次之间插入午休分隔带"
                                },
                                checked = settings.showLunchBreak,
                                onCheckedChange = { timetableViewModel.setShowLunchBreak(it) },
                                testTag = "mine_switch_lunch_break",
                            )
                            MineSwitchRow(
                                title = "显示周末列（周六、周日）",
                                subtitle = "关闭后仅展示周一至周五课表",
                                checked = settings.showWeekend,
                                onCheckedChange = { timetableViewModel.setShowWeekend(it) },
                                testTag = "mine_switch_weekend",
                            )
                            MineSwitchRow(
                                title = "显示非本周课程（灰显角标）",
                                subtitle = "半透明展示非本周课程并在底部标注“非本周”",
                                checked = settings.showNonCurrentWeekCourses,
                                onCheckedChange = { timetableViewModel.setShowNonCurrentWeekCourses(it) },
                                testTag = "mine_switch_non_current",
                            )
                        }
                    }
                }
            }

            // 3.2 Schedule Tag Management Panel
            item(key = "schedule_tags_section") {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "日程分类与标签",
                        style = MaterialTheme.typography.titleSmall,
                        color = colorScheme.onSurfaceVariant,
                    )

                    ScheduleTagManagementCard(
                        customTags = settings.customScheduleTags,
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

            // 3.5 Optional Custom OpenAI-compatible AI API Config (Local Keystore encrypted)
            item(key = "custom_ai_section") {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "自定义 AI 接口配置（可选）",
                        style = MaterialTheme.typography.titleSmall,
                        color = colorScheme.onSurfaceVariant,
                    )

                    CustomAiSettingsCard(
                        savedConfig = smartState.customAiConfig,
                        isTesting = smartState.isTestingAi,
                        testFeedback = smartState.aiTestFeedback,
                        onSave = { enabled, baseUrl, apiKey, model ->
                            smartCaptureViewModel.saveCustomAiConfig(
                                enabled = enabled,
                                baseUrl = baseUrl,
                                apiKey = apiKey,
                                model = model,
                                onResult = onShowMessage,
                            )
                        },
                        onTestConnection = { baseUrl, apiKey, model ->
                            smartCaptureViewModel.testCustomAiConnection(
                                baseUrl = baseUrl,
                                apiKey = apiKey,
                                model = model,
                            )
                        },
                        onClear = {
                            smartCaptureViewModel.clearCustomAiConfig(onShowMessage)
                        },
                    )
                }
            }

            // 4. Local JSON Backup & Restore
            item(key = "backup_section") {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "本地数据备份与迁移",
                        style = MaterialTheme.typography.titleSmall,
                        color = colorScheme.onSurfaceVariant,
                    )

                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.large,
                        color = flowColors.cardElevated,
                        border = BorderStroke(1.dp, colorScheme.outlineVariant),
                    ) {
                        Column {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        haptics.tick()
                                        exportBackupLauncher.launch("ripple_plan_timetable_backup.json")
                                    }
                                    .padding(horizontal = 16.dp, vertical = 14.dp)
                                    .testTag("mine_export_json_button"),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.FileUpload,
                                        contentDescription = null,
                                        tint = colorScheme.primary,
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "导出本地 JSON 备份",
                                            style = MaterialTheme.typography.titleSmall,
                                            color = colorScheme.onSurface,
                                        )
                                        Text(
                                            text = "明文导出全部多课表、节次与全部日程（含已完成），不含 API Key，请妥善保管",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                                Icon(
                                    imageVector = Icons.Rounded.ChevronRight,
                                    contentDescription = null,
                                    tint = colorScheme.onSurfaceVariant,
                                )
                            }

                            HorizontalDivider(
                                modifier = Modifier.padding(horizontal = 16.dp),
                                color = colorScheme.outlineVariant,
                            )

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        haptics.tick()
                                        importBackupLauncher.launch(arrayOf("application/json", "text/plain", "*/*"))
                                    }
                                    .padding(horizontal = 16.dp, vertical = 14.dp)
                                    .testTag("mine_import_json_button"),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Row(
                                    modifier = Modifier.weight(1f),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.FileDownload,
                                        contentDescription = null,
                                        tint = flowColors.liveAccent,
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "从本地 JSON 文件恢复",
                                            style = MaterialTheme.typography.titleSmall,
                                            color = colorScheme.onSurface,
                                        )
                                        Text(
                                            text = "先校验文件结构再选择「智能合并」或「清空覆盖」，无需存储权限",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                                Icon(
                                    imageVector = Icons.Rounded.ChevronRight,
                                    contentDescription = null,
                                    tint = colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }

            // 5. App Updates & About App, Help & Open Source License
            item(key = "about_section") {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "应用更新与信息",
                        style = MaterialTheme.typography.titleSmall,
                        color = colorScheme.onSurfaceVariant,
                    )

                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.large,
                        color = flowColors.cardElevated,
                        border = BorderStroke(1.dp, colorScheme.outlineVariant),
                    ) {
                        Column {
                            // Check Updates Button
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        haptics.tick()
                                        if (updateState.availableRelease != null && !updateState.isChecking) {
                                            appUpdateViewModel.openUpdateDialog()
                                        } else {
                                            appUpdateViewModel.checkForUpdatesManually(onShowMessage)
                                        }
                                    }
                                    .padding(horizontal = 16.dp, vertical = 14.dp)
                                    .testTag("mine_check_update_button"),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Row(
                                    modifier = Modifier.weight(1f),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    Box {
                                        Icon(
                                            imageVector = Icons.Rounded.SystemUpdateAlt,
                                            contentDescription = null,
                                            tint = colorScheme.primary,
                                        )
                                        if (updateState.availableRelease != null) {
                                            Box(
                                                modifier = Modifier
                                                    .size(8.dp)
                                                    .align(Alignment.TopEnd)
                                                    .background(colorScheme.error, CircleShape)
                                                    .testTag("mine_check_update_red_dot"),
                                            )
                                        }
                                    }
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        ) {
                                            Text(
                                                text = "检查更新",
                                                style = MaterialTheme.typography.titleSmall,
                                                color = colorScheme.onSurface,
                                            )
                                            Surface(
                                                shape = CircleShape,
                                                color = if (updateState.availableRelease != null) {
                                                    colorScheme.errorContainer
                                                } else {
                                                    colorScheme.surfaceContainerHighest
                                                },
                                            ) {
                                                Text(
                                                    text = if (updateState.availableRelease != null) {
                                                        "新版本 v${updateState.availableRelease!!.versionName}"
                                                    } else {
                                                        "v${BuildConfig.VERSION_NAME}"
                                                    },
                                                    fontFamily = MonoFontFamily,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = if (updateState.availableRelease != null) {
                                                        colorScheme.onErrorContainer
                                                    } else {
                                                        colorScheme.onSurfaceVariant
                                                    },
                                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                                                )
                                            }
                                        }
                                        Text(
                                            text = when {
                                                updateState.isChecking -> "正在从 GitHub Releases 检查最新版本..."
                                                updateState.availableRelease != null ->
                                                    "点击查看更新日志并前往浏览器下载新版本"
                                                !updateState.lastStatusHint.isNullOrBlank() ->
                                                    updateState.lastStatusHint!!
                                                else -> "仅向 GitHub Releases 发起只读检查，不上传个人数据"
                                            },
                                            style = MaterialTheme.typography.bodySmall,
                                            color = colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                                if (updateState.isChecking) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(18.dp),
                                        strokeWidth = 2.dp,
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Rounded.ChevronRight,
                                        contentDescription = null,
                                        tint = colorScheme.onSurfaceVariant,
                                    )
                                }
                            }

                            HorizontalDivider(
                                modifier = Modifier.padding(horizontal = 16.dp),
                                color = colorScheme.outlineVariant,
                            )

                            // Auto-check on startup switch
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        haptics.tick()
                                        appUpdateViewModel.setAutoCheckUpdates(!updateState.autoCheckUpdates)
                                    }
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Row(
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(end = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Update,
                                        contentDescription = null,
                                        tint = flowColors.liveAccent,
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "启动时自动检查更新",
                                            style = MaterialTheme.typography.titleSmall,
                                            color = colorScheme.onSurface,
                                        )
                                        Text(
                                            text = "每 24 小时最多检查一次；关闭后可保持完全零联网运行",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                                Switch(
                                    checked = updateState.autoCheckUpdates,
                                    onCheckedChange = {
                                        haptics.tick()
                                        appUpdateViewModel.setAutoCheckUpdates(it)
                                    },
                                    modifier = Modifier.testTag("mine_auto_check_updates_switch"),
                                )
                            }

                            HorizontalDivider(
                                modifier = Modifier.padding(horizontal = 16.dp),
                                color = colorScheme.outlineVariant,
                            )

                            // About App, Help & License
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        haptics.tick()
                                        showAboutSheet = true
                                    }
                                    .padding(horizontal = 16.dp, vertical = 14.dp)
                                    .testTag("mine_about_help_entry"),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Row(
                                    modifier = Modifier.weight(1f),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Info,
                                        contentDescription = null,
                                        tint = colorScheme.primary,
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "关于应用、帮助与开源协议",
                                            style = MaterialTheme.typography.titleSmall,
                                            color = colorScheme.onSurface,
                                        )
                                        Text(
                                            text = "v${BuildConfig.VERSION_NAME} · 作者、权限指南、隐私与网络说明及 MIT 协议",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                                Icon(
                                    imageVector = Icons.Rounded.ChevronRight,
                                    contentDescription = null,
                                    tint = colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
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
        AlertDialog(
            onDismissRequest = { pendingBackupPreview = null },
            title = {
                Text(
                    text = "选择 JSON 备份恢复方式",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "已解析到 ${payload.timetables.size} 张课表、${payload.courses.size} 门课程、${payload.schedules.size} 条日程（含已完成记录）。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colorScheme.onSurface,
                    )
                    Text(
                        text = "• 智能合并（推荐）：保留现有本地数据并自动跳过完全重复的课程与日程。\n• 清空覆盖：先清空当前所有课表、节次与日程，再按备份完全还原。",
                        style = MaterialTheme.typography.bodySmall,
                        color = colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        haptics.confirm()
                        timetableViewModel.restoreFullBackupJson(rawJson, BackupRestoreMode.SMART_MERGE)
                        pendingBackupPreview = null
                    },
                    modifier = Modifier.testTag("mine_backup_restore_merge"),
                ) {
                    Text("智能合并")
                }
            },
            dismissButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    TextButton(
                        onClick = {
                            haptics.tick()
                            pendingBackupPreview = null
                        },
                        modifier = Modifier.testTag("mine_backup_restore_cancel"),
                    ) {
                        Text("取消")
                    }
                    TextButton(
                        onClick = {
                            haptics.tick()
                            timetableViewModel.restoreFullBackupJson(rawJson, BackupRestoreMode.OVERWRITE_ALL)
                            pendingBackupPreview = null
                        },
                        colors = ButtonDefaults.textButtonColors(contentColor = colorScheme.error),
                        modifier = Modifier.testTag("mine_backup_restore_overwrite"),
                    ) {
                        Text("清空覆盖")
                    }
                }
            },
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
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.app_name),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = colorScheme.onSurface,
                        )
                        Surface(
                            shape = CircleShape,
                            color = colorScheme.primaryContainer,
                        ) {
                            Text(
                                text = "v${BuildConfig.VERSION_NAME}",
                                fontFamily = MonoFontFamily,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = colorScheme.onPrimaryContainer,
                                modifier = Modifier
                                    .padding(horizontal = 7.dp, vertical = 2.dp)
                                    .testTag("mine_app_version_badge"),
                            )
                        }
                    }
                    Text(
                        text = "当前课表：$activeTimetableName · Build ${BuildConfig.VERSION_CODE}",
                        style = MaterialTheme.typography.bodySmall,
                        color = colorScheme.onSurfaceVariant,
                    )
                }
                Surface(
                    shape = CircleShape,
                    color = colorScheme.secondaryContainer,
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Shield,
                            contentDescription = null,
                            tint = colorScheme.onSecondaryContainer,
                            modifier = Modifier.size(14.dp),
                        )
                        Text(
                            text = "本地优先 · 零数据收集",
                            style = MaterialTheme.typography.labelSmall,
                            color = colorScheme.onSecondaryContainer,
                        )
                    }
                }
            }

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

@Composable
private fun FluidCloudTestCard(
    hasActiveTest: Boolean,
    onTriggerTest: () -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme
    val flowColors = FlowTheme.colors
    val haptics = rememberAppHaptics()
    val shape = MaterialTheme.shapes.large

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .clickable {
                haptics.tick()
                onTriggerTest()
            }
            .testTag("mine_test_fluid_cloud_button"),
        shape = shape,
        color = flowColors.cardElevated,
        border = BorderStroke(
            width = 1.dp,
            brush = Brush.horizontalGradient(
                colors = listOf(
                    flowColors.secondaryAccent.copy(alpha = 0.55f),
                    flowColors.liveAccent.copy(alpha = 0.55f),
                ),
            ),
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            flowColors.secondaryAccent.copy(alpha = if (flowColors.isDark) 0.14f else 0.08f),
                            flowColors.liveAccent.copy(alpha = if (flowColors.isDark) 0.10f else 0.07f),
                        ),
                    ),
                )
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(colorScheme.primary.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.AutoAwesome,
                        contentDescription = null,
                        tint = colorScheme.primary,
                    )
                }
                Column {
                    Text(
                        text = stringResource(R.string.test_fluid_cloud_btn),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = colorScheme.onSurface,
                    )
                    Text(
                        text = if (hasActiveTest) {
                            "1 分钟测试正在状态栏实况胶囊或实时进度通知中倒计时，点击可重新发起"
                        } else {
                            "触发 1 分钟倒计时（含 @地点、分段进度条与切换免打扰 / 立即结束）"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(colorScheme.primary),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.PlayArrow,
                    contentDescription = "发起实况通知测试",
                    tint = colorScheme.onPrimary,
                )
            }
        }
    }
}

@Composable
private fun FluidCloudPermissionCard(
    status: LiveUpdatePermissionStatus,
    onOpenPermissions: () -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme
    val flowColors = FlowTheme.colors
    val haptics = rememberAppHaptics()
    val ready = status.allRecommendedGranted
    val badgeColor = if (ready) flowColors.completedAccent else flowColors.upcomingAccent

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .clickable {
                haptics.tick()
                onOpenPermissions()
            }
            .testTag("mine_permissions_button"),
        shape = MaterialTheme.shapes.large,
        color = flowColors.cardElevated,
        border = BorderStroke(1.dp, colorScheme.outlineVariant),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(
                    imageVector = if (ready) Icons.Rounded.NotificationsActive else Icons.Rounded.NotificationsOff,
                    contentDescription = null,
                    tint = badgeColor,
                )
                Column {
                    Text(
                        text = "实况通知与闹钟权限诊断",
                        style = MaterialTheme.typography.titleSmall,
                        color = colorScheme.onSurface,
                    )
                    Text(
                        text = if (ready) {
                            "通知、实时活动与准时闹钟均已就绪"
                        } else {
                            "点击引导开启通知、实时活动与准时闹钟权限"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = colorScheme.onSurfaceVariant,
                    )
                }
            }
            Surface(
                shape = CircleShape,
                color = badgeColor.copy(alpha = 0.14f),
            ) {
                Text(
                    text = if (ready) "已就绪" else "去设置",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = badgeColor,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun MineSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    testTag: String,
) {
    val colorScheme = MaterialTheme.colorScheme
    val haptics = rememberAppHaptics()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = colorScheme.onSurface,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = colorScheme.onSurfaceVariant,
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = {
                haptics.tick()
                onCheckedChange(it)
            },
            modifier = Modifier.testTag(testTag),
        )
    }
}

@Composable
private fun CustomAiSettingsCard(
    savedConfig: CustomAiConfig,
    isTesting: Boolean,
    testFeedback: String?,
    onSave: (enabled: Boolean, baseUrl: String, apiKey: String, model: String) -> Unit,
    onTestConnection: (baseUrl: String, apiKey: String, model: String) -> Unit,
    onClear: () -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme
    val flowColors = FlowTheme.colors
    val haptics = rememberAppHaptics()

    var enabled by remember { mutableStateOf(savedConfig.enabled) }
    var baseUrl by remember { mutableStateOf(savedConfig.baseUrl) }
    var apiKey by remember { mutableStateOf(savedConfig.apiKey) }
    var model by remember { mutableStateOf(savedConfig.model) }
    var showApiKey by remember { mutableStateOf(false) }

    LaunchedEffect(savedConfig) {
        enabled = savedConfig.enabled
        baseUrl = savedConfig.baseUrl
        apiKey = savedConfig.apiKey
        model = savedConfig.model
    }

    val isUrlValidHttps = baseUrl.isBlank() || OpenAiCompatibleClient.isValidHttpsUrl(baseUrl)
    val isConfiguredReady = enabled &&
        OpenAiCompatibleClient.isValidHttpsUrl(baseUrl) &&
        apiKey.isNotBlank() &&
        model.isNotBlank()
    val targetHostLabel = OpenAiCompatibleClient.extractHostOrNull(baseUrl) ?: "待填写 HTTPS 接口域名"

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("custom_ai_settings_card"),
        shape = MaterialTheme.shapes.large,
        color = flowColors.cardElevated,
        border = BorderStroke(1.dp, colorScheme.outlineVariant),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Psychology,
                        contentDescription = null,
                        tint = colorScheme.primary,
                    )
                    Column {
                        Text(
                            text = "OpenAI 兼容自然语言解析",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = colorScheme.onSurface,
                        )
                        Text(
                            text = "兼容 DeepSeek、通义千问等 /v1/chat/completions；未配置或断网时自动回退本地离线解析",
                            style = MaterialTheme.typography.bodySmall,
                            color = colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Switch(
                    checked = enabled,
                    onCheckedChange = {
                        haptics.tick()
                        enabled = it
                    },
                    modifier = Modifier.testTag("ai_enable_switch"),
                )
            }

            if (savedConfig.requiresLegacyKeyReentry) {
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = colorScheme.errorContainer,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("ai_legacy_key_warning"),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.WarningAmber,
                            contentDescription = null,
                            tint = colorScheme.error,
                            modifier = Modifier.size(18.dp),
                        )
                        Text(
                            text = "检测到旧版兼容格式密钥。为确保硬件级 Android Keystore 安全加密，请重新输入您的 API Key 并保存。",
                            style = MaterialTheme.typography.bodySmall,
                            color = colorScheme.onErrorContainer,
                        )
                    }
                }
            }

            // Dedicated full-width status badge row directly beneath title
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = CircleShape,
                color = if (isConfiguredReady) colorScheme.primaryContainer else colorScheme.secondaryContainer,
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(
                                if (isConfiguredReady) colorScheme.primary else colorScheme.onSecondaryContainer,
                            ),
                    )
                    Text(
                        text = if (isConfiguredReady) "当前：优先自定义 AI ($targetHostLabel) · 断网回退离线" else "当前：纯本地离线解析",
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isConfiguredReady) colorScheme.onPrimaryContainer else colorScheme.onSecondaryContainer,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            // Explicit data-egress scope disclosure
            Surface(
                shape = MaterialTheme.shapes.small,
                color = colorScheme.surfaceContainerLow,
                border = BorderStroke(1.dp, colorScheme.outlineVariant.copy(alpha = 0.7f)),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = "隐私与外发范围声明：仅支持 https:// 安全连接。开启后在“一句话创建”触发解析时，仅将当前输入框文本、当前日期时间与默认学期总周数发送至「$targetHostLabel」，绝不会上传本地已有课表、日程或其它配置。",
                    style = MaterialTheme.typography.bodySmall,
                    color = colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                )
            }

            // Uniform horizontal preset buttons row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SelectablePillOption(
                    label = "DeepSeek 预设",
                    selected = baseUrl.contains("deepseek", ignoreCase = true),
                    showCheckIcon = true,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                    onClick = {
                        enabled = true
                        baseUrl = "https://api.deepseek.com/v1"
                        model = "deepseek-chat"
                    },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("ai_preset_deepseek"),
                )

                SelectablePillOption(
                    label = "通义千问预设",
                    selected = baseUrl.contains("dashscope", ignoreCase = true),
                    showCheckIcon = true,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                    onClick = {
                        enabled = true
                        baseUrl = "https://dashscope.aliyuncs.com/compatible-mode/v1"
                        model = "qwen-plus"
                    },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("ai_preset_qwen"),
                )
            }

            OutlinedTextField(
                value = baseUrl,
                onValueChange = { baseUrl = it },
                label = { Text("Base URL (仅限 https://)", maxLines = 1, softWrap = false) },
                placeholder = { Text("https://api.deepseek.com/v1", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                isError = !isUrlValidHttps,
                supportingText = if (!isUrlValidHttps) {
                    {
                        Text(
                            text = "为保护 API Key 与隐私数据，仅允许使用 https:// 协议接口地址",
                            color = colorScheme.error,
                        )
                    }
                } else {
                    null
                },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("ai_base_url_input"),
                shape = MaterialTheme.shapes.small,
            )

            OutlinedTextField(
                value = apiKey,
                onValueChange = { apiKey = it },
                label = { Text("API Key", maxLines = 1, softWrap = false) },
                placeholder = { Text("sk-...", maxLines = 1) },
                supportingText = {
                    Text(
                        text = "由 Android Keystore AES-256 硬件级加密保存，不包含在导出备份中",
                        style = MaterialTheme.typography.labelSmall,
                        color = colorScheme.onSurfaceVariant,
                    )
                },
                singleLine = true,
                visualTransformation = if (showApiKey) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = {
                        haptics.tick()
                        showApiKey = !showApiKey
                    }) {
                        Icon(
                            imageVector = if (showApiKey) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                            contentDescription = if (showApiKey) "隐藏密钥" else "显示密钥",
                        )
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("ai_api_key_input"),
                shape = MaterialTheme.shapes.small,
            )

            OutlinedTextField(
                value = model,
                onValueChange = { model = it },
                label = { Text("模型名称（Model）", maxLines = 1, softWrap = false) },
                placeholder = { Text("deepseek-chat / qwen-plus", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("ai_model_input"),
                shape = MaterialTheme.shapes.small,
            )

            if (!testFeedback.isNullOrBlank()) {
                val isError = testFeedback.startsWith("连接失败")
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = if (isError) colorScheme.errorContainer else colorScheme.primaryContainer,
                ) {
                    Text(
                        text = testFeedback,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isError) colorScheme.onErrorContainer else colorScheme.onPrimaryContainer,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = {
                        haptics.confirm()
                        onSave(enabled, baseUrl, apiKey, model)
                    },
                    enabled = isUrlValidHttps,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("ai_save_config_button"),
                    shape = MaterialTheme.shapes.medium,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Save,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("保存配置", fontWeight = FontWeight.SemiBold)
                }

                FilledTonalButton(
                    onClick = {
                        haptics.tick()
                        onTestConnection(baseUrl, apiKey, model)
                    },
                    enabled = !isTesting &&
                        OpenAiCompatibleClient.isValidHttpsUrl(baseUrl) &&
                        apiKey.isNotBlank() &&
                        model.isNotBlank(),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("ai_test_connection_button"),
                    shape = MaterialTheme.shapes.medium,
                ) {
                    if (isTesting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("测试中...")
                    } else {
                        Icon(
                            imageVector = Icons.Rounded.NetworkCheck,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("测试接口")
                    }
                }
            }

            if (savedConfig.baseUrl.isNotBlank() || savedConfig.apiKey.isNotBlank() || savedConfig.model.isNotBlank()) {
                OutlinedButton(
                    onClick = {
                        haptics.tick()
                        enabled = false
                        baseUrl = ""
                        apiKey = ""
                        model = ""
                        onClear()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .testTag("ai_clear_config_button"),
                    shape = MaterialTheme.shapes.medium,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.DeleteOutline,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("清空配置并恢复纯本地离线模式")
                }
            }
        }
    }
}

@Composable
private fun SelectablePillOption(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    showCheckIcon: Boolean = false,
    contentPadding: PaddingValues = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
) {
    val colorScheme = MaterialTheme.colorScheme
    val haptics = rememberAppHaptics()
    val containerColor = if (selected) {
        colorScheme.primaryContainer
    } else {
        colorScheme.surfaceContainerLow
    }
    val contentColor = if (selected) {
        colorScheme.onPrimaryContainer
    } else {
        colorScheme.onSurfaceVariant
    }
    val borderColor = if (selected) {
        colorScheme.primary.copy(alpha = 0.55f)
    } else {
        colorScheme.outlineVariant
    }

    Surface(
        onClick = {
            haptics.tick()
            onClick()
        },
        modifier = modifier.height(38.dp),
        shape = CircleShape,
        color = containerColor,
        contentColor = contentColor,
        border = BorderStroke(1.dp, borderColor),
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            if (showCheckIcon && selected) {
                Icon(
                    imageVector = Icons.Rounded.Check,
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(14.dp),
                )
                Spacer(modifier = Modifier.width(4.dp))
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontSize = 12.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                color = contentColor,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Clip,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun BackgroundGuardSettingsBlock(
    enabled: Boolean,
    excludeFromRecents: Boolean,
    isServiceRunning: Boolean,
    isIgnoringBatteryOptimizations: Boolean,
    onToggle: (Boolean) -> Unit,
    onToggleExcludeFromRecents: (Boolean) -> Unit,
    onOpenAutoStartSettings: () -> Unit,
    onOpenAppDetails: () -> Unit,
    onOpenBatterySettings: () -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme
    val flowColors = FlowTheme.colors
    val haptics = rememberAppHaptics()
    val oemBrand = remember { com.example.notification.BackgroundKeepAliveHelper.detectOemBrand() }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.Security,
                    contentDescription = null,
                    tint = colorScheme.primary,
                    modifier = Modifier.size(18.dp),
                )
                Column {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            text = "全天后台常驻守护",
                            style = MaterialTheme.typography.titleSmall,
                            color = colorScheme.onSurface,
                        )
                        Surface(
                            shape = CircleShape,
                            color = if (enabled && isServiceRunning) {
                                flowColors.completedAccent.copy(alpha = 0.14f)
                            } else if (enabled) {
                                flowColors.upcomingAccent.copy(alpha = 0.16f)
                            } else {
                                colorScheme.surfaceContainerHighest
                            },
                        ) {
                            Text(
                                text = when {
                                    enabled && isServiceRunning -> "守护运行中"
                                    enabled -> "巡检就绪"
                                    else -> "未开启"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = when {
                                    enabled && isServiceRunning -> flowColors.completedAccent
                                    enabled -> flowColors.upcomingAccent
                                    else -> colorScheme.onSurfaceVariant
                                },
                                modifier = Modifier
                                    .padding(horizontal = 7.dp, vertical = 2.dp)
                                    .testTag("mine_guard_status_badge"),
                            )
                        }
                    }
                    Text(
                        text = "开启静音常驻前台通知与 15 分钟周期巡检，滑掉多任务卡片或跨过零点也能持续刷新实况通知与准时闹钟",
                        style = MaterialTheme.typography.bodySmall,
                        color = colorScheme.onSurfaceVariant,
                    )
                }
            }
            Switch(
                checked = enabled,
                onCheckedChange = { checked ->
                    haptics.tick()
                    onToggle(checked)
                },
                modifier = Modifier.testTag("mine_switch_background_guard"),
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.VisibilityOff,
                    contentDescription = null,
                    tint = colorScheme.secondary,
                    modifier = Modifier.size(18.dp),
                )
                Column {
                    Text(
                        text = "从最近任务隐藏（防误划清理）",
                        style = MaterialTheme.typography.titleSmall,
                        color = colorScheme.onSurface,
                    )
                    Text(
                        text = "开启后多任务卡片会自动隐藏本应用，避免一键清理误杀守护服务；开启后可通过桌面图标、快捷瓷贴或通知栏随时回到应用",
                        style = MaterialTheme.typography.bodySmall,
                        color = colorScheme.onSurfaceVariant,
                    )
                }
            }
            Switch(
                checked = excludeFromRecents,
                onCheckedChange = { checked ->
                    haptics.tick()
                    onToggleExcludeFromRecents(checked)
                },
                modifier = Modifier.testTag("mine_switch_exclude_from_recents"),
            )
        }

        Surface(
            shape = MaterialTheme.shapes.medium,
            color = colorScheme.surfaceContainerLow,
            border = BorderStroke(1.dp, colorScheme.outlineVariant.copy(alpha = 0.75f)),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                        Text(
                            text = "系统保活建议（防误杀）",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = colorScheme.onSurface,
                        )
                        Text(
                            text = oemBrand.displayName,
                            style = MaterialTheme.typography.labelSmall,
                            color = colorScheme.primary,
                        )
                    }
                    Surface(
                        shape = CircleShape,
                        color = if (isIgnoringBatteryOptimizations) {
                            flowColors.completedAccent.copy(alpha = 0.14f)
                        } else {
                            flowColors.upcomingAccent.copy(alpha = 0.16f)
                        },
                    ) {
                        Text(
                            text = if (isIgnoringBatteryOptimizations) "后台无限制已开启" else "建议关闭电池优化",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = if (isIgnoringBatteryOptimizations) {
                                flowColors.completedAccent
                            } else {
                                flowColors.upcomingAccent
                            },
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        )
                    }
                }
                Text(
                    text = com.example.notification.BackgroundKeepAliveHelper.oemKeepAliveGuideText(oemBrand),
                    style = MaterialTheme.typography.bodySmall,
                    color = colorScheme.onSurfaceVariant,
                )
                FilledTonalButton(
                    onClick = {
                        haptics.tick()
                        onOpenAutoStartSettings()
                    },
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("mine_guard_autostart_button"),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Tune,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "一键前往自启动 / 后台管理",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        softWrap = false,
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(
                        onClick = {
                            haptics.tick()
                            onOpenAppDetails()
                        },
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("mine_guard_app_details_button"),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.SettingsApplications,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "应用详情页",
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 1,
                            softWrap = false,
                        )
                    }
                    OutlinedButton(
                        onClick = {
                            haptics.tick()
                            onOpenBatterySettings()
                        },
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("mine_guard_battery_button"),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.BatterySaver,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "电池优化设置",
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 1,
                            softWrap = false,
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ScheduleTagManagementCard(
    customTags: List<CustomScheduleTag>,
    onAddTagClick: () -> Unit,
    onEditTagClick: (String) -> Unit,
    onDeleteTagClick: (String) -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme
    val flowColors = FlowTheme.colors
    val haptics = rememberAppHaptics()

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("mine_tag_management_card"),
        shape = MaterialTheme.shapes.large,
        color = flowColors.cardElevated,
        border = BorderStroke(1.dp, colorScheme.outlineVariant),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Label,
                        contentDescription = null,
                        tint = colorScheme.primary,
                        modifier = Modifier.size(18.dp),
                    )
                    Column {
                        Text(
                            text = "标签管理",
                            style = MaterialTheme.typography.titleSmall,
                            color = colorScheme.onSurface,
                        )
                        Text(
                            text = "5 个默认分类 · ${customTags.size} 个自定义标签",
                            style = MaterialTheme.typography.bodySmall,
                            color = colorScheme.onSurfaceVariant,
                        )
                    }
                }
                FilledTonalButton(
                    onClick = {
                        haptics.tick()
                        onAddTagClick()
                    },
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    modifier = Modifier.testTag("mine_add_tag_button"),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Add,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "新建标签",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        softWrap = false,
                    )
                }
            }

            // Quiet reference strip for built-in categories
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                ScheduleCategory.entries
                    .filter { it != ScheduleCategory.TEST }
                    .forEach { cat ->
                        Surface(
                            shape = CircleShape,
                            color = colorScheme.surfaceContainerLow,
                            border = BorderStroke(1.dp, colorScheme.outlineVariant.copy(alpha = 0.7f)),
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Icon(
                                    imageVector = TagIconCatalog.iconForCategory(cat),
                                    contentDescription = null,
                                    tint = cat.accentColor.copy(alpha = 0.85f),
                                    modifier = Modifier.size(12.dp),
                                )
                                Text(
                                    text = cat.label,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    softWrap = false,
                                )
                            }
                        }
                    }
            }

            HorizontalDivider(color = colorScheme.outlineVariant.copy(alpha = 0.7f))

            if (customTags.isEmpty()) {
                Text(
                    text = "暂未添加自定义标签；可点击右上角「新建标签」自由定制名称、颜色与图标，并在日程页一键筛选。",
                    style = MaterialTheme.typography.bodySmall,
                    color = colorScheme.onSurfaceVariant,
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    customTags.forEach { tag ->
                        val tagColor = tag.resolvedColor()
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = colorScheme.surfaceContainerLow,
                            border = BorderStroke(1.dp, colorScheme.outlineVariant.copy(alpha = 0.75f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("mine_tag_row_${tag.id}"),
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 12.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Row(
                                    modifier = Modifier.weight(1f),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(30.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(tagColor.copy(alpha = if (flowColors.isDark) 0.18f else 0.11f)),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Icon(
                                            imageVector = tag.resolvedIcon(),
                                            contentDescription = null,
                                            tint = tagColor,
                                            modifier = Modifier.size(16.dp),
                                        )
                                    }
                                    Column {
                                        Text(
                                            text = tag.name,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            color = colorScheme.onSurface,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                        Text(
                                            text = tag.colorHex,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(
                                        onClick = {
                                            haptics.tick()
                                            onEditTagClick(tag.id)
                                        },
                                        modifier = Modifier.testTag("mine_tag_edit_${tag.id}"),
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Edit,
                                            contentDescription = "编辑标签 ${tag.name}",
                                            tint = colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(18.dp),
                                        )
                                    }
                                    IconButton(
                                        onClick = {
                                            haptics.tick()
                                            onDeleteTagClick(tag.id)
                                        },
                                        modifier = Modifier.testTag("mine_tag_delete_${tag.id}"),
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.DeleteOutline,
                                            contentDescription = "删除标签 ${tag.name}",
                                            tint = colorScheme.error,
                                            modifier = Modifier.size(18.dp),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun queryIgnoringBatteryOptimizations(context: Context): Boolean {
    val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return false
    return runCatching { pm.isIgnoringBatteryOptimizations(context.packageName) }.getOrDefault(false)
}

