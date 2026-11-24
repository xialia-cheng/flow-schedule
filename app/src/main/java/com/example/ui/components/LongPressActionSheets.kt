package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.AlarmOff
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircleOutline
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Label
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.CourseColorPalette
import com.example.data.CourseEntity
import com.example.data.CustomScheduleTag
import com.example.data.DateTimeUtils
import com.example.data.ScheduleCategory
import com.example.data.ScheduleEntity
import com.example.data.TagIconCatalog
import com.example.ui.theme.FlowTheme
import com.example.ui.theme.MonoFontFamily
import java.util.Calendar
import java.util.TimeZone

private val QuickLeadOptions = listOf(0 to "关闭", 5 to "5分", 10 to "10分", 15 to "15分", 30 to "30分")
private val QuickSnoozeOptions = listOf(5 to "5分钟后", 10 to "10分钟后", 30 to "30分钟后", 60 to "1小时后")

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ScheduleLongPressSheet(
    schedule: ScheduleEntity,
    nowMillis: Long,
    customTags: List<CustomScheduleTag>,
    activeReReminderMillis: Long?,
    onDismiss: () -> Unit,
    onMoveToDate: (Long) -> Unit,
    onSelectTag: (ScheduleCategory, CustomScheduleTag?) -> Unit,
    onSetReminderLead: (Int) -> Unit,
    onScheduleReReminder: (Int) -> Unit,
    onCancelReReminder: () -> Unit,
    onToggleComplete: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val colorScheme = MaterialTheme.colorScheme
    val flowColors = FlowTheme.colors
    val haptics = rememberAppHaptics()

    var showDatePicker by remember { mutableStateOf(false) }
    var showCustomMinutesDialog by remember { mutableStateOf(false) }

    val tagLabel = schedule.resolvedTagLabel(customTags)
    val tagColor = schedule.resolvedTagColor(customTags)
    val tagIcon = schedule.resolvedTagIcon(customTags)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = colorScheme.surfaceContainerLowest,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 20.dp)
                .testTag("schedule_long_press_sheet"),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Header summary
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = schedule.title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Surface(
                        shape = CircleShape,
                        color = tagColor.copy(alpha = if (flowColors.isDark) 0.18f else 0.12f),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Icon(tagIcon, null, tint = tagColor, modifier = Modifier.size(13.dp))
                            Text(
                                text = tagLabel,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = tagColor,
                            )
                        }
                    }
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = "${DateTimeUtils.formatDateShort(schedule.startTimeMillis)} · ${DateTimeUtils.formatTimeRange(schedule.startTimeMillis, schedule.endTimeMillis)}",
                        fontFamily = MonoFontFamily,
                        style = MaterialTheme.typography.bodySmall,
                        color = colorScheme.onSurfaceVariant,
                    )
                    if (activeReReminderMillis != null) {
                        ActiveReReminderPill(triggerMillis = activeReReminderMillis)
                    }
                }
            }

            HorizontalDivider(color = colorScheme.outlineVariant.copy(alpha = 0.6f))

            // 1. Move to Date
            QuickSectionHeader(icon = Icons.Rounded.CalendarMonth, title = "快速移动到特定日期")
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val today = DateTimeUtils.startOfDayMillis(nowMillis)
                val tomorrow = DateTimeUtils.addDays(nowMillis, 1)
                val afterTomorrow = DateTimeUtils.addDays(nowMillis, 2)
                val plusOneDay = DateTimeUtils.addDays(schedule.startTimeMillis, 1)
                val nextWeek = DateTimeUtils.addDays(schedule.startTimeMillis, 7)

                val datePresets = listOf(
                    Triple("今天", today, "quick_move_today"),
                    Triple("明天", tomorrow, "quick_move_tomorrow"),
                    Triple("后天", afterTomorrow, "quick_move_after_tomorrow"),
                    Triple("推迟1天", plusOneDay, "quick_move_plus_1d"),
                    Triple("下周同天", nextWeek, "quick_move_next_week"),
                )
                datePresets.forEach { (label, targetMs, tag) ->
                    val isSame = DateTimeUtils.isSameDay(schedule.startTimeMillis, targetMs)
                    ActionPillChip(
                        label = label,
                        selected = isSame,
                        accent = colorScheme.primary,
                        testTag = tag,
                        onClick = {
                            haptics.confirm()
                            onMoveToDate(targetMs)
                        },
                    )
                }
                ActionPillChip(
                    label = "选择日期…",
                    selected = false,
                    accent = colorScheme.primary,
                    testTag = "quick_move_pick_date",
                    onClick = {
                        haptics.tick()
                        showDatePicker = true
                    },
                )
            }

            // 2. Quick Tag Change
            QuickSectionHeader(icon = Icons.Rounded.Label, title = "快速更改标签")
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                customTags.forEach { tag ->
                    val isSelected = schedule.customTagId == tag.id
                    val accent = tag.resolvedColor()
                    ActionPillChip(
                        label = tag.name,
                        icon = tag.resolvedIcon(),
                        selected = isSelected,
                        accent = accent,
                        testTag = "quick_tag_custom_${tag.id}",
                        onClick = {
                            haptics.confirm()
                            onSelectTag(schedule.category, tag)
                        },
                    )
                }
                ScheduleCategory.entries
                    .filter { it != ScheduleCategory.TEST }
                    .forEach { category ->
                        val isSelected = schedule.customTagId.isBlank() && schedule.category == category
                        ActionPillChip(
                            label = category.label,
                            icon = TagIconCatalog.iconForCategory(category),
                            selected = isSelected,
                            accent = category.accentColor,
                            testTag = "quick_tag_${category.name.lowercase()}",
                            onClick = {
                                haptics.confirm()
                                onSelectTag(category, null)
                            },
                        )
                    }
            }

            // 3. Alarm Re-Reminder & Lead Time
            QuickSectionHeader(icon = Icons.Rounded.Alarm, title = "设置闹钟重提醒")
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "预约重提醒闹钟（到点响铃弹窗）",
                    style = MaterialTheme.typography.labelSmall,
                    color = colorScheme.onSurfaceVariant,
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    QuickSnoozeOptions.forEach { (minutes, label) ->
                        ActionPillChip(
                            label = label,
                            selected = false,
                            accent = flowColors.liveAccent,
                            testTag = "quick_reremind_${minutes}m",
                            onClick = {
                                haptics.confirm()
                                onScheduleReReminder(minutes)
                            },
                        )
                    }
                    ActionPillChip(
                        label = "自定义分钟…",
                        selected = false,
                        accent = flowColors.liveAccent,
                        testTag = "quick_reremind_custom",
                        onClick = {
                            haptics.tick()
                            showCustomMinutesDialog = true
                        },
                    )
                    if (activeReReminderMillis != null) {
                        ActionPillChip(
                            label = "取消重提醒",
                            icon = Icons.Rounded.AlarmOff,
                            selected = true,
                            accent = flowColors.deleteAccent,
                            testTag = "quick_reremind_cancel",
                            onClick = {
                                haptics.tick()
                                onCancelReReminder()
                            },
                        )
                    }
                }

                Text(
                    text = "日程开始前提前提醒",
                    style = MaterialTheme.typography.labelSmall,
                    color = colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    QuickLeadOptions.forEach { (minutes, label) ->
                        ActionPillChip(
                            label = label,
                            selected = schedule.reminderMinutesBefore == minutes,
                            accent = colorScheme.primary,
                            testTag = "quick_lead_${minutes}m",
                            onClick = {
                                haptics.confirm()
                                onSetReminderLead(minutes)
                            },
                        )
                    }
                }
            }

            HorizontalDivider(color = colorScheme.outlineVariant.copy(alpha = 0.6f))

            // Footer actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SheetFooterAction(
                    icon = Icons.Rounded.CheckCircleOutline,
                    label = if (schedule.isCompleted) "标为未完成" else "标为完成",
                    tint = flowColors.completedAccent,
                    testTag = "quick_action_toggle_complete",
                    modifier = Modifier.weight(1f),
                    onClick = {
                        haptics.confirm()
                        onToggleComplete()
                        onDismiss()
                    },
                )
                SheetFooterAction(
                    icon = Icons.Rounded.Edit,
                    label = "编辑详情",
                    tint = colorScheme.primary,
                    testTag = "quick_action_edit",
                    modifier = Modifier.weight(1f),
                    onClick = {
                        haptics.tick()
                        onDismiss()
                        onEdit()
                    },
                )
                SheetFooterAction(
                    icon = Icons.Rounded.DeleteOutline,
                    label = "删除",
                    tint = flowColors.deleteAccent,
                    testTag = "quick_action_delete",
                    modifier = Modifier.weight(1f),
                    onClick = {
                        haptics.tick()
                        onDismiss()
                        onDelete()
                    },
                )
            }
        }
    }

    if (showDatePicker) {
        LocalDatePickerModal(
            initialDateMillis = schedule.startTimeMillis,
            onDismiss = { showDatePicker = false },
            onConfirm = { pickedLocalMidnight ->
                showDatePicker = false
                onMoveToDate(pickedLocalMidnight)
            },
        )
    }

    if (showCustomMinutesDialog) {
        CustomMinutesInputDialog(
            onDismiss = { showCustomMinutesDialog = false },
            onConfirm = { mins ->
                showCustomMinutesDialog = false
                onScheduleReReminder(mins)
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CourseLongPressSheet(
    course: CourseEntity,
    sectionLabel: String,
    courseReminderEnabled: Boolean,
    courseReminderLeadMinutes: Int,
    activeReReminderMillis: Long?,
    onDismiss: () -> Unit,
    onMoveToDay: (Int) -> Unit,
    onMoveToDate: (Long) -> Unit,
    onSelectColor: (Int) -> Unit,
    onScheduleReReminder: (Int) -> Unit,
    onCancelReReminder: () -> Unit,
    onSetCourseReminderLead: (Int) -> Unit,
    onDisableCourseReminder: () -> Unit,
    onInspect: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val colorScheme = MaterialTheme.colorScheme
    val flowColors = FlowTheme.colors
    val haptics = rememberAppHaptics()

    var showDatePicker by remember { mutableStateOf(false) }
    var showCustomMinutesDialog by remember { mutableStateOf(false) }

    val style = course.resolvedColorStyle()
    val accentColor = if (flowColors.isDark) style.darkAccent else style.lightAccent

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = colorScheme.surfaceContainerLowest,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 20.dp)
                .testTag("course_long_press_sheet"),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Header
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(accentColor),
                    )
                    Text(
                        text = course.name,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Surface(
                        shape = CircleShape,
                        color = accentColor.copy(alpha = if (flowColors.isDark) 0.18f else 0.12f),
                    ) {
                        Text(
                            text = "${DateTimeUtils.dayOfWeekFullChinese(course.dayOfWeek)} $sectionLabel",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = accentColor,
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                        )
                    }
                }
                val metaParts = buildList {
                    course.formattedLocation().takeIf { it.isNotEmpty() }?.let { add(it) }
                    course.teacher.takeIf { it.isNotBlank() }?.let { add(it) }
                    add(course.weekRangeLabel())
                }.joinToString(" · ")
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = metaParts,
                        style = MaterialTheme.typography.bodySmall,
                        color = colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (activeReReminderMillis != null) {
                        ActiveReReminderPill(triggerMillis = activeReReminderMillis)
                    }
                }
            }

            HorizontalDivider(color = colorScheme.outlineVariant.copy(alpha = 0.6f))

            // 1. Move to Weekday / Specific Date
            QuickSectionHeader(icon = Icons.Rounded.CalendarMonth, title = "快速调课到指定星期 / 日期")
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    (1..7).forEach { isoDay ->
                        val isCurrent = course.dayOfWeek == isoDay
                        Surface(
                            onClick = {
                                haptics.confirm()
                                onMoveToDay(isoDay)
                                onDismiss()
                            },
                            shape = RoundedCornerShape(10.dp),
                            color = if (isCurrent) {
                                colorScheme.primaryContainer
                            } else {
                                flowColors.cardElevated
                            },
                            border = BorderStroke(
                                width = 1.dp,
                                color = if (isCurrent) colorScheme.primary else colorScheme.outlineVariant,
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .defaultMinSize(minHeight = 42.dp)
                                .testTag("course_move_day_$isoDay"),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = DateTimeUtils.dayOfWeekShortChinese(isoDay),
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isCurrent) colorScheme.onPrimaryContainer else colorScheme.onSurface,
                                )
                            }
                        }
                    }
                }
                ActionPillChip(
                    label = "按日历选择具体日期…",
                    icon = Icons.Rounded.Schedule,
                    selected = false,
                    accent = colorScheme.primary,
                    testTag = "course_move_pick_date",
                    onClick = {
                        haptics.tick()
                        showDatePicker = true
                    },
                )
            }

            // 2. Quick Color/Label Style
            QuickSectionHeader(icon = Icons.Rounded.Palette, title = "快速更改课程颜色标签")
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CourseColorPalette.styles.forEachIndexed { idx, paletteStyle ->
                    val swatchColor = if (flowColors.isDark) paletteStyle.darkAccent else paletteStyle.lightAccent
                    val isSelected = course.customColorHex.isBlank() &&
                        Math.floorMod(course.colorIndex, CourseColorPalette.styles.size) == idx
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(swatchColor)
                            .border(
                                width = if (isSelected) 2.5.dp else 1.dp,
                                color = if (isSelected) colorScheme.onSurface else Color.White.copy(alpha = 0.35f),
                                shape = CircleShape,
                            )
                            .clickable {
                                haptics.confirm()
                                onSelectColor(idx)
                                onDismiss()
                            }
                            .testTag("course_quick_color_$idx"),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (isSelected) {
                            Icon(
                                imageVector = Icons.Rounded.Check,
                                contentDescription = paletteStyle.name,
                                tint = Color.White,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }
                }
            }

            // 3. Alarm Re-Reminder & Global Course Reminder Lead
            QuickSectionHeader(icon = Icons.Rounded.Alarm, title = "设置闹钟重提醒")
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "预约重提醒闹钟（到点响铃提醒）",
                    style = MaterialTheme.typography.labelSmall,
                    color = colorScheme.onSurfaceVariant,
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    QuickSnoozeOptions.forEach { (minutes, label) ->
                        ActionPillChip(
                            label = label,
                            selected = false,
                            accent = flowColors.liveAccent,
                            testTag = "course_reremind_${minutes}m",
                            onClick = {
                                haptics.confirm()
                                onScheduleReReminder(minutes)
                                onDismiss()
                            },
                        )
                    }
                    ActionPillChip(
                        label = "自定义分钟…",
                        selected = false,
                        accent = flowColors.liveAccent,
                        testTag = "course_reremind_custom",
                        onClick = {
                            haptics.tick()
                            showCustomMinutesDialog = true
                        },
                    )
                    if (activeReReminderMillis != null) {
                        ActionPillChip(
                            label = "取消重提醒",
                            icon = Icons.Rounded.AlarmOff,
                            selected = true,
                            accent = flowColors.deleteAccent,
                            testTag = "course_reremind_cancel",
                            onClick = {
                                haptics.tick()
                                onCancelReReminder()
                            },
                        )
                    }
                }

                Text(
                    text = "上课提前提醒（作用于课表通知）",
                    style = MaterialTheme.typography.labelSmall,
                    color = colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    QuickLeadOptions.forEach { (minutes, label) ->
                        val isSelected = if (minutes == 0) {
                            !courseReminderEnabled || courseReminderLeadMinutes == 0
                        } else {
                            courseReminderEnabled && courseReminderLeadMinutes == minutes
                        }
                        ActionPillChip(
                            label = label,
                            selected = isSelected,
                            accent = colorScheme.primary,
                            testTag = "course_lead_${minutes}m",
                            onClick = {
                                haptics.confirm()
                                if (minutes == 0) onDisableCourseReminder() else onSetCourseReminderLead(minutes)
                            },
                        )
                    }
                }
            }

            HorizontalDivider(color = colorScheme.outlineVariant.copy(alpha = 0.6f))

            // Footer actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SheetFooterAction(
                    icon = Icons.Rounded.Info,
                    label = "查看详情",
                    tint = colorScheme.onSurface,
                    testTag = "course_quick_inspect",
                    modifier = Modifier.weight(1f),
                    onClick = {
                        haptics.tick()
                        onDismiss()
                        onInspect()
                    },
                )
                SheetFooterAction(
                    icon = Icons.Rounded.Edit,
                    label = "编辑课程",
                    tint = colorScheme.primary,
                    testTag = "course_quick_edit",
                    modifier = Modifier.weight(1f),
                    onClick = {
                        haptics.tick()
                        onDismiss()
                        onEdit()
                    },
                )
                SheetFooterAction(
                    icon = Icons.Rounded.DeleteOutline,
                    label = "删除课程",
                    tint = flowColors.deleteAccent,
                    testTag = "course_quick_delete",
                    modifier = Modifier.weight(1f),
                    onClick = {
                        haptics.tick()
                        onDismiss()
                        onDelete()
                    },
                )
            }
        }
    }

    if (showDatePicker) {
        LocalDatePickerModal(
            initialDateMillis = System.currentTimeMillis(),
            onDismiss = { showDatePicker = false },
            onConfirm = { pickedLocalMidnight ->
                showDatePicker = false
                onMoveToDate(pickedLocalMidnight)
                onDismiss()
            },
        )
    }

    if (showCustomMinutesDialog) {
        CustomMinutesInputDialog(
            onDismiss = { showCustomMinutesDialog = false },
            onConfirm = { mins ->
                showCustomMinutesDialog = false
                onScheduleReReminder(mins)
                onDismiss()
            },
        )
    }
}

@Composable
private fun QuickSectionHeader(icon: ImageVector, title: String) {
    val colorScheme = MaterialTheme.colorScheme
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(icon, null, tint = colorScheme.primary, modifier = Modifier.size(16.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = colorScheme.onSurface,
        )
    }
}

@Composable
private fun ActiveReReminderPill(triggerMillis: Long) {
    val flowColors = FlowTheme.colors
    Surface(
        shape = CircleShape,
        color = flowColors.liveAccent.copy(alpha = if (flowColors.isDark) 0.20f else 0.12f),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(
                imageVector = Icons.Rounded.NotificationsActive,
                contentDescription = null,
                tint = flowColors.liveAccent,
                modifier = Modifier.size(12.dp),
            )
            Text(
                text = "已设 ${DateTimeUtils.formatTime(triggerMillis)} 重提醒",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = flowColors.liveAccent,
            )
        }
    }
}

@Composable
private fun ActionPillChip(
    label: String,
    selected: Boolean,
    accent: Color,
    testTag: String,
    icon: ImageVector? = null,
    onClick: () -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme
    val flowColors = FlowTheme.colors
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = if (selected) {
            accent.copy(alpha = if (flowColors.isDark) 0.20f else 0.13f)
        } else {
            flowColors.cardElevated
        },
        border = BorderStroke(
            width = 1.dp,
            color = if (selected) accent.copy(alpha = 0.6f) else colorScheme.outlineVariant,
        ),
        modifier = Modifier
            .defaultMinSize(minHeight = 38.dp)
            .testTag(testTag),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (selected) accent else colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(14.dp),
                )
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                color = if (selected) accent else colorScheme.onSurface,
                maxLines = 1,
                softWrap = false,
            )
        }
    }
}

@Composable
private fun SheetFooterAction(
    icon: ImageVector,
    label: String,
    tint: Color,
    testTag: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val flowColors = FlowTheme.colors
    val colorScheme = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = flowColors.cardElevated,
        border = BorderStroke(1.dp, colorScheme.outlineVariant),
        modifier = modifier
            .defaultMinSize(minHeight = 48.dp)
            .testTag(testTag),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(5.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = tint,
                maxLines = 1,
                softWrap = false,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LocalDatePickerModal(
    initialDateMillis: Long,
    onDismiss: () -> Unit,
    onConfirm: (Long) -> Unit,
) {
    val haptics = rememberAppHaptics()
    val initialUtcMillis = remember(initialDateMillis) {
        val localCal = Calendar.getInstance().apply { timeInMillis = initialDateMillis }
        Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            clear()
            set(localCal.get(Calendar.YEAR), localCal.get(Calendar.MONTH), localCal.get(Calendar.DAY_OF_MONTH))
        }.timeInMillis
    }
    val datePickerState = rememberDatePickerState(initialSelectedDateMillis = initialUtcMillis)
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    haptics.confirm()
                    val pickedUtc = datePickerState.selectedDateMillis
                    if (pickedUtc != null) {
                        val utcCal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
                            timeInMillis = pickedUtc
                        }
                        val localMidnight = Calendar.getInstance().apply {
                            clear()
                            set(
                                utcCal.get(Calendar.YEAR),
                                utcCal.get(Calendar.MONTH),
                                utcCal.get(Calendar.DAY_OF_MONTH),
                            )
                        }.timeInMillis
                        onConfirm(localMidnight)
                    } else {
                        onDismiss()
                    }
                },
            ) { Text("确定") }
        },
        dismissButton = {
            TextButton(onClick = {
                haptics.tick()
                onDismiss()
            }) { Text("取消") }
        },
    ) {
        DatePicker(state = datePickerState)
    }
}

@Composable
private fun CustomMinutesInputDialog(
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit,
) {
    val haptics = rememberAppHaptics()
    var minutesInput by remember { mutableStateOf("15") }
    val parsed = minutesInput.trim().toIntOrNull()
    val isValid = parsed != null && parsed in 1..720

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("自定义重提醒时间", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "输入多少分钟后再次响铃提醒（1–720 分钟）：",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = minutesInput,
                    onValueChange = { raw ->
                        minutesInput = raw.filter { it.isDigit() }.take(3)
                    },
                    singleLine = true,
                    label = { Text("分钟数") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    isError = minutesInput.isNotEmpty() && !isValid,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = isValid,
                onClick = {
                    haptics.confirm()
                    parsed?.let { onConfirm(it) }
                },
            ) { Text("设置闹钟") }
        },
        dismissButton = {
            TextButton(onClick = {
                haptics.tick()
                onDismiss()
            }) { Text("取消") }
        },
    )
}
