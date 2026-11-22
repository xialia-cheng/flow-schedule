package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccessTime
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CalendarToday
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.CustomScheduleTag
import com.example.data.DateTimeUtils
import com.example.data.RepeatRule
import com.example.data.ScheduleCategory
import com.example.data.ScheduleEntity
import com.example.ui.theme.FlowTheme
import com.example.ui.theme.MonoFontFamily
import java.util.Calendar

private val ReminderOptions = listOf(
    0 to "不提前",
    5 to "5 分钟",
    10 to "10 分钟",
    15 to "15 分钟",
    30 to "30 分钟",
    60 to "1 小时",
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ScheduleEditorSheet(
    editingSchedule: ScheduleEntity?,
    customTags: List<CustomScheduleTag> = emptyList(),
    onAddCustomTag: (name: String, colorHex: String) -> Unit = { _, _ -> },
    onUpdateCustomTag: (id: String, name: String, colorHex: String) -> Unit = { _, _, _ -> },
    onDeleteCustomTag: (id: String) -> Unit = {},
    onDismiss: () -> Unit,
    onSave: (
        existingId: Long,
        title: String,
        notes: String,
        startTimeMillis: Long,
        endTimeMillis: Long,
        repeatRule: RepeatRule,
        reminderMinutesBefore: Int,
        category: ScheduleCategory,
        customTagId: String,
        customTagName: String,
        customTagColorHex: String,
    ) -> Unit,
    onDelete: ((ScheduleEntity) -> Unit)? = null,
    onOpenSmartCapture: (() -> Unit)? = null,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val colorScheme = MaterialTheme.colorScheme
    val flowColors = FlowTheme.colors

    val defaultStartMillis = remember(editingSchedule) {
        if (editingSchedule != null) {
            editingSchedule.startTimeMillis
        } else {
            val cal = Calendar.getInstance().apply {
                add(Calendar.MINUTE, 15)
                val minute = get(Calendar.MINUTE)
                val rounded = ((minute + 4) / 5) * 5
                set(Calendar.MINUTE, rounded)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            cal.timeInMillis
        }
    }
    val defaultEndMillis = remember(editingSchedule) {
        editingSchedule?.endTimeMillis ?: (defaultStartMillis + 45 * 60_000L)
    }

    var title by remember(editingSchedule) { mutableStateOf(editingSchedule?.title ?: "") }
    var notes by remember(editingSchedule) { mutableStateOf(editingSchedule?.notes ?: "") }
    var dateMillis by remember(editingSchedule) {
        mutableLongStateOf(DateTimeUtils.startOfDayMillis(defaultStartMillis))
    }
    val (initStartH, initStartM) = remember(editingSchedule) {
        DateTimeUtils.extractHourMinute(defaultStartMillis)
    }
    val (initEndH, initEndM) = remember(editingSchedule) {
        DateTimeUtils.extractHourMinute(defaultEndMillis)
    }
    var startHour by remember(editingSchedule) { mutableIntStateOf(initStartH) }
    var startMinute by remember(editingSchedule) { mutableIntStateOf(initStartM) }
    var endHour by remember(editingSchedule) { mutableIntStateOf(initEndH) }
    var endMinute by remember(editingSchedule) { mutableIntStateOf(initEndM) }
    var repeatRule by remember(editingSchedule) {
        mutableStateOf(editingSchedule?.repeatRule ?: RepeatRule.NONE)
    }
    var reminderMinutes by remember(editingSchedule) {
        mutableIntStateOf(editingSchedule?.reminderMinutesBefore ?: 15)
    }
    var category by remember(editingSchedule) {
        mutableStateOf(
            if (editingSchedule != null && editingSchedule.category != ScheduleCategory.TEST) {
                editingSchedule.category
            } else {
                ScheduleCategory.WORK
            },
        )
    }
    var selectedCustomTagId by remember(editingSchedule) {
        mutableStateOf(editingSchedule?.customTagId?.takeIf { it.isNotBlank() })
    }
    var titleError by remember { mutableStateOf(false) }

    var showDatePicker by remember { mutableStateOf(false) }
    var showStartTimePicker by remember { mutableStateOf(false) }
    var showEndTimePicker by remember { mutableStateOf(false) }
    var showTagManagerDialog by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = colorScheme.surface,
        contentColor = colorScheme.onSurface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
                .imePadding(),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = if (editingSchedule == null) {
                        stringResource(R.string.action_add_schedule)
                    } else {
                        stringResource(R.string.action_edit_schedule)
                    },
                    style = MaterialTheme.typography.headlineSmall,
                    color = colorScheme.onSurface,
                )

                if (editingSchedule != null && onDelete != null) {
                    IconButton(
                        onClick = {
                            onDelete(editingSchedule)
                            onDismiss()
                        },
                        modifier = Modifier.testTag("editor_delete_button"),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.DeleteOutline,
                            contentDescription = stringResource(R.string.cd_delete_schedule),
                            tint = flowColors.deleteAccent,
                        )
                    }
                } else if (editingSchedule == null && onOpenSmartCapture != null) {
                    Surface(
                        onClick = {
                            onDismiss()
                            onOpenSmartCapture()
                        },
                        shape = CircleShape,
                        color = colorScheme.secondaryContainer,
                        modifier = Modifier.testTag("editor_smart_capture_button"),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 11.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.AutoAwesome,
                                contentDescription = "一句话智能创建日程",
                                tint = colorScheme.onSecondaryContainer,
                                modifier = Modifier.size(15.dp),
                            )
                            Text(
                                text = "一句话输入",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = colorScheme.onSecondaryContainer,
                                maxLines = 1,
                                softWrap = false,
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            OutlinedTextField(
                value = title,
                onValueChange = {
                    title = it
                    if (titleError && it.isNotBlank()) titleError = false
                },
                label = { Text("日程标题") },
                placeholder = { Text("例如：产品方案评审 / 晚间慢跑") },
                singleLine = true,
                isError = titleError,
                supportingText = if (titleError) {
                    { Text("请输入日程标题") }
                } else {
                    null
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("schedule_title_input"),
                shape = MaterialTheme.shapes.medium,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = colorScheme.primary,
                    unfocusedBorderColor = colorScheme.outline,
                    focusedLabelColor = colorScheme.primary,
                ),
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "分类标签",
                style = MaterialTheme.typography.labelMedium,
                color = colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ScheduleCategory.entries
                    .filter { it != ScheduleCategory.TEST }
                    .forEach { cat ->
                        val selected = selectedCustomTagId == null && category == cat
                        FilterChip(
                            selected = selected,
                            onClick = {
                                selectedCustomTagId = null
                                category = cat
                            },
                            label = { Text(cat.label) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = cat.accentColor.copy(
                                    alpha = if (flowColors.isDark) 0.22f else 0.14f,
                                ),
                                selectedLabelColor = cat.accentColor,
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true,
                                selected = selected,
                                borderColor = colorScheme.outlineVariant,
                                selectedBorderColor = cat.accentColor.copy(alpha = 0.6f),
                            ),
                        )
                    }

                customTags.forEach { tag ->
                    val tagColor = tag.resolvedColor()
                    val selected = selectedCustomTagId == tag.id
                    FilterChip(
                        selected = selected,
                        onClick = { selectedCustomTagId = tag.id },
                        leadingIcon = {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(tagColor),
                            )
                        },
                        label = { Text(tag.name) },
                        modifier = Modifier.testTag("editor_custom_tag_${tag.id}"),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = tagColor.copy(
                                alpha = if (flowColors.isDark) 0.24f else 0.15f,
                            ),
                            selectedLabelColor = tagColor,
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = selected,
                            borderColor = colorScheme.outlineVariant,
                            selectedBorderColor = tagColor.copy(alpha = 0.65f),
                        ),
                    )
                }

                FilterChip(
                    selected = false,
                    onClick = { showTagManagerDialog = true },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Rounded.Add,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                    },
                    label = { Text("自定义标签") },
                    modifier = Modifier.testTag("editor_manage_custom_tags_button"),
                    colors = FilterChipDefaults.filterChipColors(
                        containerColor = colorScheme.secondaryContainer.copy(alpha = 0.65f),
                        labelColor = colorScheme.onSecondaryContainer,
                        iconColor = colorScheme.onSecondaryContainer,
                    ),
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = "日期与时间",
                style = MaterialTheme.typography.labelMedium,
                color = colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(8.dp))

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.medium)
                    .clickable { showDatePicker = true }
                    .testTag("editor_date_picker_button"),
                shape = MaterialTheme.shapes.medium,
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
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.CalendarToday,
                            contentDescription = null,
                            tint = colorScheme.primary,
                            modifier = Modifier.size(18.dp),
                        )
                        Text(
                            text = DateTimeUtils.formatFullDate(dateMillis),
                            style = MaterialTheme.typography.titleSmall,
                            color = colorScheme.onSurface,
                        )
                    }
                    Text(
                        text = when {
                            DateTimeUtils.isToday(dateMillis) -> "今天"
                            DateTimeUtils.isTomorrow(dateMillis) -> "明天"
                            else -> "点击更改"
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = colorScheme.primary,
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                TimeSelectorCard(
                    label = "开始时间",
                    timeText = String.format("%02d:%02d", startHour, startMinute),
                    onClick = { showStartTimePicker = true },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("editor_start_time_button"),
                )
                TimeSelectorCard(
                    label = "结束时间",
                    timeText = String.format("%02d:%02d", endHour, endMinute),
                    onClick = { showEndTimePicker = true },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("editor_end_time_button"),
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "重复规则",
                style = MaterialTheme.typography.labelMedium,
                color = colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(6.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                RepeatRule.entries.forEach { rule ->
                    val selected = repeatRule == rule
                    FilterChip(
                        selected = selected,
                        onClick = { repeatRule = rule },
                        label = { Text(rule.label) },
                        modifier = Modifier.testTag("repeat_chip_${rule.name.lowercase()}"),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = colorScheme.primaryContainer,
                            selectedLabelColor = colorScheme.onPrimaryContainer,
                        ),
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "提前提醒（进入流体云实时胶囊窗口）",
                    style = MaterialTheme.typography.labelMedium,
                    color = colorScheme.onSurfaceVariant,
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    IconButton(
                        onClick = { reminderMinutes = (reminderMinutes - 1).coerceAtLeast(0) },
                        enabled = reminderMinutes > 0,
                        modifier = Modifier.size(30.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Remove,
                            contentDescription = "减少提前提醒分钟",
                            modifier = Modifier.size(16.dp),
                        )
                    }
                    Text(
                        text = if (reminderMinutes == 0) "不提前" else "${reminderMinutes}分",
                        fontFamily = MonoFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = colorScheme.primary,
                    )
                    IconButton(
                        onClick = { reminderMinutes = (reminderMinutes + 1).coerceAtMost(120) },
                        enabled = reminderMinutes < 120,
                        modifier = Modifier.size(30.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Add,
                            contentDescription = "增加提前提醒分钟",
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                ReminderOptions.forEach { (minutes, label) ->
                    val selected = reminderMinutes == minutes
                    FilterChip(
                        selected = selected,
                        onClick = { reminderMinutes = minutes },
                        label = { Text(label) },
                        modifier = Modifier.testTag("reminder_chip_$minutes"),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = colorScheme.primaryContainer,
                            selectedLabelColor = colorScheme.onPrimaryContainer,
                        ),
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                label = { Text("备注（可选）") },
                placeholder = { Text("会议链接、地点或关键事项...") },
                minLines = 2,
                maxLines = 4,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("schedule_notes_input"),
                shape = MaterialTheme.shapes.medium,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = colorScheme.primary,
                    unfocusedBorderColor = colorScheme.outline,
                    focusedLabelColor = colorScheme.primary,
                ),
            )

            Spacer(modifier = Modifier.height(20.dp))

            Button(
                onClick = {
                    if (title.isBlank()) {
                        titleError = true
                        return@Button
                    }
                    val startMillis = DateTimeUtils.combineDateAndTime(dateMillis, startHour, startMinute)
                    var endMillis = DateTimeUtils.combineDateAndTime(dateMillis, endHour, endMinute)
                    if (endMillis <= startMillis) {
                        endMillis = startMillis + 30 * 60_000L
                    }
                    val matchedCustomTag = selectedCustomTagId?.let { id ->
                        customTags.firstOrNull { it.id == id }
                    }
                    onSave(
                        editingSchedule?.id ?: 0L,
                        title,
                        notes,
                        startMillis,
                        endMillis,
                        repeatRule,
                        reminderMinutes,
                        category,
                        matchedCustomTag?.id ?: "",
                        matchedCustomTag?.name ?: "",
                        matchedCustomTag?.colorHex ?: "",
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("save_schedule_button"),
                shape = MaterialTheme.shapes.medium,
                colors = ButtonDefaults.buttonColors(
                    containerColor = colorScheme.primary,
                    contentColor = colorScheme.onPrimary,
                ),
            ) {
                Text(
                    text = stringResource(R.string.action_save),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = dateMillis)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { selected ->
                            dateMillis = DateTimeUtils.startOfDayMillis(selected)
                        }
                        showDatePicker = false
                    },
                ) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("取消") }
            },
        ) {
            DatePicker(state = datePickerState)
        }
    }

    if (showStartTimePicker) {
        val timeState = rememberTimePickerState(
            initialHour = startHour,
            initialMinute = startMinute,
            is24Hour = true,
        )
        TimePickerDialogWrapper(
            title = "选择开始时间",
            onDismiss = { showStartTimePicker = false },
            onConfirm = {
                startHour = timeState.hour
                startMinute = timeState.minute
                val startTotal = startHour * 60 + startMinute
                val endTotal = endHour * 60 + endMinute
                if (endTotal <= startTotal) {
                    val adjusted = (startTotal + 30).coerceAtMost(23 * 60 + 59)
                    endHour = adjusted / 60
                    endMinute = adjusted % 60
                }
                showStartTimePicker = false
            },
        ) {
            TimePicker(state = timeState)
        }
    }

    if (showEndTimePicker) {
        val timeState = rememberTimePickerState(
            initialHour = endHour,
            initialMinute = endMinute,
            is24Hour = true,
        )
        TimePickerDialogWrapper(
            title = "选择结束时间",
            onDismiss = { showEndTimePicker = false },
            onConfirm = {
                endHour = timeState.hour
                endMinute = timeState.minute
                showEndTimePicker = false
            },
        ) {
            TimePicker(state = timeState)
        }
    }

    if (showTagManagerDialog) {
        CustomTagManagerDialog(
            customTags = customTags,
            onAddTag = onAddCustomTag,
            onUpdateTag = onUpdateCustomTag,
            onDeleteTag = { deletedId ->
                if (selectedCustomTagId == deletedId) {
                    selectedCustomTagId = null
                }
                onDeleteCustomTag(deletedId)
            },
            onSelectTag = { picked ->
                selectedCustomTagId = picked.id
                showTagManagerDialog = false
            },
            onDismiss = { showTagManagerDialog = false },
        )
    }
}

@Composable
private fun TimeSelectorCard(
    label: String,
    timeText: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colorScheme = MaterialTheme.colorScheme
    val flowColors = FlowTheme.colors

    Surface(
        modifier = modifier
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClick = onClick),
        shape = MaterialTheme.shapes.medium,
        color = flowColors.cardElevated,
        border = BorderStroke(1.dp, colorScheme.outlineVariant),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodySmall,
                    color = colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = timeText,
                    fontFamily = MonoFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = colorScheme.onSurface,
                )
            }
            Icon(
                imageVector = Icons.Rounded.AccessTime,
                contentDescription = null,
                tint = colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun TimePickerDialogWrapper(
    title: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    content: @Composable () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
            )
        },
        text = { content() },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("确定", color = MaterialTheme.colorScheme.primary)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        },
    )
}
