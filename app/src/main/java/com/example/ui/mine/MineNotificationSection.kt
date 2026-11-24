package com.example.ui.mine

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.BatterySaver
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.DoNotDisturbOn
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.SettingsApplications
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.AppSettings
import com.example.data.DndLevel
import com.example.notification.BackgroundKeepAliveHelper
import com.example.notification.LiveUpdatePermissionStatus
import com.example.ui.components.SelectablePillOption
import com.example.ui.components.SwitchSettingRow
import com.example.ui.components.rememberAppHaptics
import com.example.ui.theme.FlowTheme
import com.example.ui.theme.MonoFontFamily

@Composable
internal fun NotificationAndGuardSectionContent(
    settings: AppSettings,
    permissionStatus: LiveUpdatePermissionStatus,
    isGuardServiceActive: Boolean,
    isIgnoringBatteryOpt: Boolean,
    context: Context,
    onOpenPermissions: () -> Unit,
    onOpenDndPolicySettings: () -> Unit,
    onCourseReminderEnabledChange: (Boolean) -> Unit,
    onCourseReminderLeadMinutesChange: (Int) -> Unit,
    onDndLevelChange: (DndLevel) -> Unit,
    onFluidCloudDisplayOptionsChange: (Boolean, Boolean, Boolean) -> Unit,
    onFluidCloudLockscreenDetailsChange: (Boolean) -> Unit,
    onBackgroundGuardEnabledChange: (Boolean) -> Unit,
    onExcludeFromRecentsChange: (Boolean) -> Unit,
    onShowMessage: (String) -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme
    val flowColors = FlowTheme.colors
    val haptics = rememberAppHaptics()

    var displayDetailsExpanded by rememberSaveable { mutableStateOf(false) }

    // 1. Live Updates & Alarm Permission Diagnostics Row
    FluidCloudPermissionRow(
        status = permissionStatus,
        onOpenPermissions = onOpenPermissions,
    )

    HorizontalDivider(color = colorScheme.outlineVariant.copy(alpha = 0.7f))

    // 2. Auto Live Updates + Pre-class Lead Window
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SwitchSettingRow(
            title = "上课自动触发实况通知",
            subtitle = "课前与上课期间在状态栏实况胶囊或进度通知中实时展示课程倒计时",
            checked = settings.courseReminderEnabled,
            onCheckedChange = onCourseReminderEnabledChange,
            switchTestTag = "mine_switch_course_reminder",
            useLargeTitle = true,
        )

        AnimatedVisibility(
            visible = settings.courseReminderEnabled,
            enter = expandVertically(tween(220, easing = FastOutSlowInEasing)) + fadeIn(tween(180)),
            exit = shrinkVertically(tween(180, easing = FastOutSlowInEasing)) + fadeOut(tween(140)),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
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
                                onCourseReminderLeadMinutesChange(
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
                                onCourseReminderLeadMinutesChange(
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
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    listOf(5, 10, 15, 30).forEach { mins ->
                        SelectablePillOption(
                            label = "${mins}分钟",
                            selected = settings.courseReminderLeadMinutes == mins,
                            onClick = { onCourseReminderLeadMinutesChange(mins) },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("mine_lead_time_$mins"),
                        )
                    }
                }
            }
        }
    }

    HorizontalDivider(color = colorScheme.outlineVariant.copy(alpha = 0.7f))

    // 3. System DND Level & Lockscreen Privacy Toggle
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
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
                color = if (permissionStatus.hasDndPolicyAccess) {
                    flowColors.completedAccent.copy(alpha = 0.14f)
                } else {
                    flowColors.upcomingAccent.copy(alpha = 0.16f)
                },
                modifier = Modifier.testTag("mine_dnd_access_badge"),
            ) {
                Text(
                    text = if (permissionStatus.hasDndPolicyAccess) "已授权" else "去授权",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = if (permissionStatus.hasDndPolicyAccess) {
                        flowColors.completedAccent
                    } else {
                        flowColors.upcomingAccent
                    },
                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp),
                )
            }
        }
        Text(
            text = settings.dndLevel.subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = colorScheme.onSurfaceVariant,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            DndLevel.entries.forEach { level ->
                SelectablePillOption(
                    label = level.label,
                    selected = settings.dndLevel == level,
                    onClick = { onDndLevelChange(level) },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("mine_dnd_level_${level.name.lowercase()}"),
                )
            }
        }

        Spacer(modifier = Modifier.height(2.dp))

        SwitchSettingRow(
            title = "锁屏展示课程与日程详情",
            subtitle = "默认关闭；关闭时锁屏仅显示事件名称与倒计时，隐藏地点、教师与备注",
            checked = settings.fluidCloudLockscreenDetails,
            onCheckedChange = onFluidCloudLockscreenDetailsChange,
            switchTestTag = "mine_switch_fc_lockscreen",
            useLargeTitle = true,
        )

        // Low-frequency sub-panel: Fluid Cloud card field customization
        val activeFieldsSummary = buildList {
            if (settings.fluidCloudShowLocation) add("地点")
            if (settings.fluidCloudShowTeacher) add("教师")
            if (settings.fluidCloudShowNotes) add("备注")
        }.ifEmpty { listOf("仅事件名称") }.joinToString(" · ")

        InlineSubDisclosureCard(
            title = "实况卡片展示字段定制",
            summary = "当前展示：$activeFieldsSummary",
            expanded = displayDetailsExpanded,
            onToggle = { displayDetailsExpanded = !displayDetailsExpanded },
            testTag = "mine_fc_fields_toggle",
        ) {
            SwitchSettingRow(
                title = "展示地点 / 教室",
                subtitle = "在实况通知标题与展开卡片中显示 @教室 或地点",
                checked = settings.fluidCloudShowLocation,
                onCheckedChange = { checked ->
                    onFluidCloudDisplayOptionsChange(
                        checked,
                        settings.fluidCloudShowTeacher,
                        settings.fluidCloudShowNotes,
                    )
                },
                switchTestTag = "mine_switch_fc_location",
            )
            SwitchSettingRow(
                title = "展示授课教师",
                subtitle = "在课程实况通知展开详情中附带授课教师姓名",
                checked = settings.fluidCloudShowTeacher,
                onCheckedChange = { checked ->
                    onFluidCloudDisplayOptionsChange(
                        settings.fluidCloudShowLocation,
                        checked,
                        settings.fluidCloudShowNotes,
                    )
                },
                switchTestTag = "mine_switch_fc_teacher",
            )
            SwitchSettingRow(
                title = "展示备注信息",
                subtitle = "在课程与日程实况通知展开卡片中附带备注内容",
                checked = settings.fluidCloudShowNotes,
                onCheckedChange = { checked ->
                    onFluidCloudDisplayOptionsChange(
                        settings.fluidCloudShowLocation,
                        settings.fluidCloudShowTeacher,
                        checked,
                    )
                },
                switchTestTag = "mine_switch_fc_notes",
            )
        }
    }

    HorizontalDivider(color = colorScheme.outlineVariant.copy(alpha = 0.7f))

    // 4. All-day Background Guard + Exclude From Recents + OEM Keep-Alive
    BackgroundGuardSettingsBlock(
        enabled = settings.backgroundGuardEnabled,
        excludeFromRecents = settings.excludeFromRecents,
        isServiceRunning = isGuardServiceActive,
        isIgnoringBatteryOptimizations = isIgnoringBatteryOpt,
        onToggle = onBackgroundGuardEnabledChange,
        onToggleExcludeFromRecents = onExcludeFromRecentsChange,
        onOpenAutoStartSettings = {
            if (!BackgroundKeepAliveHelper.openAutoStartOrBackgroundSettings(context)) {
                onShowMessage("无法打开系统自启动或应用详情页")
            }
        },
        onOpenAppDetails = {
            val intent = BackgroundKeepAliveHelper.buildAppDetailsIntent(context)
            runCatching { context.startActivity(intent) }.onFailure {
                onShowMessage("无法打开系统应用详情页")
            }
        },
        onOpenBatterySettings = {
            if (!BackgroundKeepAliveHelper.openBatteryOptimizationSettings(context)) {
                onShowMessage("无法打开系统电池优化设置")
            }
        },
    )
}

@Composable
private fun FluidCloudPermissionRow(
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
            .clip(MaterialTheme.shapes.medium)
            .clickable {
                haptics.tick()
                onOpenPermissions()
            }
            .testTag("mine_permissions_button"),
        shape = MaterialTheme.shapes.medium,
        color = colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, colorScheme.outlineVariant.copy(alpha = 0.75f)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                imageVector = if (ready) Icons.Rounded.NotificationsActive else Icons.Rounded.NotificationsOff,
                contentDescription = null,
                tint = badgeColor,
                modifier = Modifier.size(20.dp),
            )
            Column(modifier = Modifier.weight(1f)) {
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
            Surface(
                shape = CircleShape,
                color = badgeColor.copy(alpha = 0.14f),
            ) {
                Text(
                    text = if (ready) "已就绪" else "去开启",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = badgeColor,
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                )
            }
            Icon(
                imageVector = Icons.Rounded.ChevronRight,
                contentDescription = null,
                tint = colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
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
    val oemBrand = remember { BackgroundKeepAliveHelper.detectOemBrand() }
    var keepAliveGuideExpanded by rememberSaveable { mutableStateOf(false) }

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
                        text = "开启后多任务卡片会自动隐藏本应用，避免一键清理误杀守护服务；可通过桌面图标、快捷瓷贴或通知栏随时回到应用",
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

        InlineSubDisclosureCard(
            title = "系统保活建议与自启动跳转",
            summary = "${oemBrand.displayName} · ${if (isIgnoringBatteryOptimizations) "后台无限制已开启" else "建议关闭电池优化"}",
            expanded = keepAliveGuideExpanded,
            onToggle = { keepAliveGuideExpanded = !keepAliveGuideExpanded },
            testTag = "mine_guard_keepalive_toggle",
        ) {
            Text(
                text = BackgroundKeepAliveHelper.oemKeepAliveGuideText(oemBrand),
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

@Composable
private fun InlineSubDisclosureCard(
    title: String,
    summary: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    testTag: String,
    content: @Composable () -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme
    val haptics = rememberAppHaptics()
    val rotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing),
        label = "inline_sub_disclosure_arrow",
    )

    Surface(
        shape = MaterialTheme.shapes.medium,
        color = colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, colorScheme.outlineVariant.copy(alpha = 0.75f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 48.dp)
                    .clickable {
                        haptics.tick()
                        onToggle()
                    }
                    .testTag(testTag)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = summary,
                        style = MaterialTheme.typography.bodySmall,
                        color = colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Icon(
                    imageVector = Icons.Rounded.KeyboardArrowDown,
                    contentDescription = if (expanded) "收起$title" else "展开$title",
                    tint = colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .size(20.dp)
                        .rotate(rotation),
                )
            }

            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(tween(220, easing = FastOutSlowInEasing)) + fadeIn(tween(180)),
                exit = shrinkVertically(tween(180, easing = FastOutSlowInEasing)) + fadeOut(tween(140)),
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    HorizontalDivider(color = colorScheme.outlineVariant.copy(alpha = 0.6f))
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        content()
                    }
                }
            }
        }
    }
}
