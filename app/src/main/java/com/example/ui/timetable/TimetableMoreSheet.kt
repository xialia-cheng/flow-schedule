package com.example.ui.timetable

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccessTime
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.FormatListBulleted
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.AppThemeMode
import com.example.data.CourseColorPalette
import com.example.data.CourseEntity
import com.example.data.DateTimeUtils
import com.example.data.DndLevel
import com.example.data.SectionTimeEntity
import com.example.data.TimetableEntity
import com.example.data.WeekStartDay
import com.example.ui.theme.FlowTheme
import com.example.ui.theme.MonoFontFamily
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimetableMoreSheet(
    state: TimetableUiState,
    onSelectWeek: (Int) -> Unit,
    onJumpCurrentWeek: () -> Unit,
    onSwitchTimetable: (Long) -> Unit,
    onCreateTimetable: (name: String, totalWeeks: Int) -> Unit,
    onDeleteTimetable: (TimetableEntity) -> Unit,
    onSaveSectionTimes: (List<SectionTimeEntity>) -> Unit,
    onUpdateTimetableSettings: (name: String, semesterStartMondayMillis: Long, totalWeeks: Int) -> Unit,
    onEditCourse: (CourseEntity) -> Unit,
    onDeleteCourse: (CourseEntity) -> Unit,
    onSetShowMorningExercise: (Boolean) -> Unit,
    onSetExtraBannerConfig: (title: String, startTime: String, endTime: String) -> Unit,
    onSetShowNonCurrentWeek: (Boolean) -> Unit,
    onSetShowWeekend: (Boolean) -> Unit,
    onSetWeekStartDay: (WeekStartDay) -> Unit,
    onSetTimetableRowHeightDp: (Int) -> Unit,
    onSetTimetableFontScale: (Float) -> Unit,
    onSetCourseReminderEnabled: (Boolean) -> Unit,
    onSetCourseReminderLeadMinutes: (Int) -> Unit,
    onSetDndLevel: (DndLevel) -> Unit,
    onSetFluidCloudDisplayOptions: (showLocation: Boolean, showTeacher: Boolean, showNotes: Boolean) -> Unit,
    onSetThemeMode: (AppThemeMode) -> Unit,
    onSetCompactMode: (Boolean) -> Unit,
    onOpenPermissions: () -> Unit,
    onTriggerFluidCloudTest: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val colorScheme = MaterialTheme.colorScheme
    val flowColors = FlowTheme.colors

    var showCreateTimetableDialog by remember { mutableStateOf(false) }
    var showSectionTimesDialog by remember { mutableStateOf(false) }
    var showTimetableSettingsDialog by remember { mutableStateOf(false) }
    var showAddedCoursesDialog by remember { mutableStateOf(false) }
    var showReminderDialog by remember { mutableStateOf(false) }

    val totalWeeks = (state.activeTimetable?.totalWeeks ?: 20).coerceAtLeast(1)

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
                .navigationBarsPadding(),
        ) {
            Text(
                text = "课表控制中心",
                style = MaterialTheme.typography.headlineSmall,
                color = colorScheme.onSurface,
            )

            Spacer(modifier = Modifier.height(14.dp))

            // 1. Week Slider Card
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = flowColors.cardElevated,
                border = BorderStroke(1.dp, colorScheme.outlineVariant),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                text = "周数快速切换",
                                style = MaterialTheme.typography.titleSmall,
                                color = colorScheme.onSurface,
                            )
                            Surface(
                                shape = CircleShape,
                                color = colorScheme.primaryContainer,
                            ) {
                                Text(
                                    text = "第 ${state.displayedWeek} / $totalWeeks 周",
                                    fontFamily = MonoFontFamily,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = colorScheme.onPrimaryContainer,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                )
                            }
                        }

                        if (!state.isViewingCurrentWeek) {
                            TextButton(
                                onClick = onJumpCurrentWeek,
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                            ) {
                                Text("回到本周", color = colorScheme.primary)
                            }
                        }
                    }

                    Slider(
                        value = state.displayedWeek.toFloat(),
                        onValueChange = { onSelectWeek(it.roundToInt()) },
                        valueRange = 1f..totalWeeks.toFloat().coerceAtLeast(2f),
                        steps = (totalWeeks - 2).coerceAtLeast(0),
                        modifier = Modifier.testTag("week_slider"),
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 2. Multi-Timetable Switcher & Creator
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = flowColors.cardElevated,
                border = BorderStroke(1.dp, colorScheme.outlineVariant),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = "多课表切换",
                            style = MaterialTheme.typography.titleSmall,
                            color = colorScheme.onSurface,
                        )
                        TextButton(
                            onClick = { showCreateTimetableDialog = true },
                            modifier = Modifier.testTag("create_timetable_button"),
                        ) {
                            Icon(Icons.Rounded.Add, null, Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("新建课表")
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        state.allTimetables.forEach { tt ->
                            val selected = tt.id == state.activeTimetable?.id
                            FilterChip(
                                selected = selected,
                                onClick = { onSwitchTimetable(tt.id) },
                                label = { Text(tt.name) },
                                shape = CircleShape,
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = colorScheme.primaryContainer,
                                    selectedLabelColor = colorScheme.onPrimaryContainer,
                                ),
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 3. 4 Quick Settings Entries (上课时间 / 课表设置 / 已添课程 / 上课提醒)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                MoreActionTile(
                    icon = Icons.Rounded.AccessTime,
                    title = "上课时间",
                    subtitle = "1-${state.sectionTimes.size.coerceIn(6, 16)} 节自由作息",
                    onClick = { showSectionTimesDialog = true },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("more_section_times_button"),
                )
                MoreActionTile(
                    icon = Icons.Rounded.Tune,
                    title = "课表设置",
                    subtitle = "开学日与显示项",
                    onClick = { showTimetableSettingsDialog = true },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("more_timetable_settings_button"),
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                MoreActionTile(
                    icon = Icons.Rounded.FormatListBulleted,
                    title = "已添课程",
                    subtitle = "共 ${state.courses.size} 门课程",
                    onClick = { showAddedCoursesDialog = true },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("more_added_courses_button"),
                )
                MoreActionTile(
                    icon = Icons.Rounded.NotificationsActive,
                    title = "上课提醒",
                    subtitle = if (state.settings.courseReminderEnabled) {
                        "提前 ${state.settings.courseReminderLeadMinutes} 分·流体云"
                    } else {
                        "已关闭"
                    },
                    onClick = { showReminderDialog = true },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("more_course_reminder_button"),
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 4. Night Mode & Compact Mode Controls
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = flowColors.cardElevated,
                border = BorderStroke(1.dp, colorScheme.outlineVariant),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "夜间模式与外观",
                        style = MaterialTheme.typography.titleSmall,
                        color = colorScheme.onSurface,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        AppThemeMode.entries.forEach { mode ->
                            val selected = state.settings.themeMode == mode
                            FilterChip(
                                selected = selected,
                                onClick = { onSetThemeMode(mode) },
                                label = { Text(mode.label) },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("more_theme_${mode.name.lowercase()}"),
                                shape = CircleShape,
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = colorScheme.primaryContainer,
                                    selectedLabelColor = colorScheme.onPrimaryContainer,
                                ),
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "简洁模式",
                                style = MaterialTheme.typography.titleSmall,
                                color = colorScheme.onSurface,
                            )
                            Text(
                                text = "隐藏早锻炼栏、非本周灰显与冗余信息，专注本周课程",
                                style = MaterialTheme.typography.bodySmall,
                                color = colorScheme.onSurfaceVariant,
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Switch(
                            checked = state.settings.compactMode,
                            onCheckedChange = onSetCompactMode,
                            modifier = Modifier.testTag("more_compact_mode_switch"),
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))
        }
    }

    if (showCreateTimetableDialog) {
        CreateTimetableDialog(
            onDismiss = { showCreateTimetableDialog = false },
            onCreate = { name, weeks ->
                onCreateTimetable(name, weeks)
                showCreateTimetableDialog = false
            },
        )
    }

    if (showSectionTimesDialog) {
        SectionTimesEditorSheet(
            initialTimes = state.sectionTimes,
            maxCourseSection = state.courses.maxOfOrNull { it.endSection } ?: 0,
            onDismiss = { showSectionTimesDialog = false },
            onSave = { updated ->
                onSaveSectionTimes(updated)
                showSectionTimesDialog = false
            },
        )
    }

    if (showTimetableSettingsDialog && state.activeTimetable != null) {
        TimetableSettingsDialog(
            timetable = state.activeTimetable,
            canDelete = state.allTimetables.size > 1,
            showMorningExercise = state.settings.showMorningExercise,
            extraBannerTitle = state.settings.extraBannerTitle,
            extraBannerStartTime = state.settings.extraBannerStartTime,
            extraBannerEndTime = state.settings.extraBannerEndTime,
            showNonCurrentWeek = state.settings.showNonCurrentWeekCourses,
            showWeekend = state.settings.showWeekend,
            weekStartDay = state.settings.weekStartDay,
            rowHeightDp = state.settings.timetableRowHeightDp,
            fontScale = state.settings.timetableFontScale,
            onSetShowMorningExercise = onSetShowMorningExercise,
            onSetExtraBannerConfig = onSetExtraBannerConfig,
            onSetShowNonCurrentWeek = onSetShowNonCurrentWeek,
            onSetShowWeekend = onSetShowWeekend,
            onSetWeekStartDay = onSetWeekStartDay,
            onSetRowHeightDp = onSetTimetableRowHeightDp,
            onSetFontScale = onSetTimetableFontScale,
            onSave = { name, startMonday, weeks ->
                onUpdateTimetableSettings(name, startMonday, weeks)
                showTimetableSettingsDialog = false
            },
            onDelete = {
                onDeleteTimetable(state.activeTimetable)
                showTimetableSettingsDialog = false
            },
            onDismiss = { showTimetableSettingsDialog = false },
        )
    }

    if (showAddedCoursesDialog) {
        AddedCoursesDialog(
            courses = state.courses,
            onEdit = { course ->
                showAddedCoursesDialog = false
                onDismiss()
                onEditCourse(course)
            },
            onDelete = onDeleteCourse,
            onDismiss = { showAddedCoursesDialog = false },
        )
    }

    if (showReminderDialog) {
        CourseReminderDialog(
            enabled = state.settings.courseReminderEnabled,
            leadMinutes = state.settings.courseReminderLeadMinutes,
            dndLevel = state.settings.dndLevel,
            showLocation = state.settings.fluidCloudShowLocation,
            showTeacher = state.settings.fluidCloudShowTeacher,
            showNotes = state.settings.fluidCloudShowNotes,
            onToggleEnabled = onSetCourseReminderEnabled,
            onSelectLeadMinutes = onSetCourseReminderLeadMinutes,
            onSelectDndLevel = onSetDndLevel,
            onSetFluidCloudDisplayOptions = onSetFluidCloudDisplayOptions,
            onOpenPermissions = {
                showReminderDialog = false
                onDismiss()
                onOpenPermissions()
            },
            onTriggerFluidCloudTest = {
                showReminderDialog = false
                onDismiss()
                onTriggerFluidCloudTest()
            },
            onDismiss = { showReminderDialog = false },
        )
    }
}

@Composable
private fun MoreActionTile(
    icon: ImageVector,
    title: String,
    subtitle: String,
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
                .padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(18.dp),
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun CreateTimetableDialog(
    onDismiss: () -> Unit,
    onCreate: (name: String, totalWeeks: Int) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var totalWeeks by remember { mutableIntStateOf(20) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("新建课表") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("课表名称") },
                    placeholder = { Text("例如：2027 春季学期") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "学期总周数（1-${CourseEntity.MAX_TOTAL_WEEKS} 周）",
                        style = MaterialTheme.typography.labelLarge,
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        FilledTonalButton(
                            onClick = { if (totalWeeks > 1) totalWeeks-- },
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                            modifier = Modifier.height(32.dp),
                        ) { Text("-") }
                        Text(
                            text = "$totalWeeks 周",
                            fontFamily = MonoFontFamily,
                            fontWeight = FontWeight.Bold,
                        )
                        FilledTonalButton(
                            onClick = { if (totalWeeks < CourseEntity.MAX_TOTAL_WEEKS) totalWeeks++ },
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                            modifier = Modifier.height(32.dp),
                        ) { Text("+") }
                    }
                }
                Slider(
                    value = totalWeeks.toFloat(),
                    onValueChange = { totalWeeks = it.roundToInt().coerceIn(1, CourseEntity.MAX_TOTAL_WEEKS) },
                    valueRange = 1f..CourseEntity.MAX_TOTAL_WEEKS.toFloat(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onCreate(name, totalWeeks) }) {
                Text("创建")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimetableSettingsDialog(
    timetable: TimetableEntity,
    canDelete: Boolean,
    showMorningExercise: Boolean,
    extraBannerTitle: String,
    extraBannerStartTime: String,
    extraBannerEndTime: String,
    showNonCurrentWeek: Boolean,
    showWeekend: Boolean,
    weekStartDay: WeekStartDay,
    rowHeightDp: Int,
    fontScale: Float,
    onSetShowMorningExercise: (Boolean) -> Unit,
    onSetExtraBannerConfig: (title: String, startTime: String, endTime: String) -> Unit,
    onSetShowNonCurrentWeek: (Boolean) -> Unit,
    onSetShowWeekend: (Boolean) -> Unit,
    onSetWeekStartDay: (WeekStartDay) -> Unit,
    onSetRowHeightDp: (Int) -> Unit,
    onSetFontScale: (Float) -> Unit,
    onSave: (name: String, semesterStartMondayMillis: Long, totalWeeks: Int) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember(timetable) { mutableStateOf(timetable.name) }
    var startMonday by remember(timetable) { mutableLongStateOf(timetable.semesterStartMondayMillis) }
    var totalWeeks by remember(timetable) { mutableIntStateOf(timetable.totalWeeks) }
    var bannerTitle by remember(extraBannerTitle) { mutableStateOf(extraBannerTitle) }
    var bannerStart by remember(extraBannerStartTime) { mutableStateOf(extraBannerStartTime) }
    var bannerEnd by remember(extraBannerEndTime) { mutableStateOf(extraBannerEndTime) }
    var showDatePicker by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("课表自由设置") },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 460.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("课表名称") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                // Week 1 start date
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.small)
                        .clickable { showDatePicker = true }
                        .testTag("settings_semester_start_picker"),
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Icon(Icons.Rounded.CalendarMonth, null, Modifier.size(18.dp))
                            Column {
                                Text("开学第一周日期", style = MaterialTheme.typography.labelMedium)
                                Text(
                                    text = DateTimeUtils.formatFullDate(startMonday),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                        Icon(Icons.Rounded.ChevronRight, null)
                    }
                }

                // Total weeks (1..52, no longer fixed at 20)
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("学期总周数", style = MaterialTheme.typography.labelMedium)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            FilledTonalButton(
                                onClick = { if (totalWeeks > 1) totalWeeks-- },
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                                modifier = Modifier
                                    .height(30.dp)
                                    .testTag("settings_total_weeks_minus"),
                            ) { Text("-") }
                            Text(
                                text = "$totalWeeks 周",
                                fontFamily = MonoFontFamily,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            FilledTonalButton(
                                onClick = { if (totalWeeks < CourseEntity.MAX_TOTAL_WEEKS) totalWeeks++ },
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                                modifier = Modifier
                                    .height(30.dp)
                                    .testTag("settings_total_weeks_plus"),
                            ) { Text("+") }
                        }
                    }
                    Slider(
                        value = totalWeeks.toFloat(),
                        onValueChange = { totalWeeks = it.roundToInt().coerceIn(1, CourseEntity.MAX_TOTAL_WEEKS) },
                        valueRange = 1f..CourseEntity.MAX_TOTAL_WEEKS.toFloat(),
                        modifier = Modifier.testTag("settings_total_weeks_slider"),
                    )
                }

                // Week start day (周一 / 周日)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("每周起始日", style = MaterialTheme.typography.bodyMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        WeekStartDay.entries.forEach { dayOpt ->
                            FilterChip(
                                selected = weekStartDay == dayOpt,
                                onClick = {
                                    onSetWeekStartDay(dayOpt)
                                    startMonday = DateTimeUtils.weekStartOfWeekMillis(startMonday, dayOpt)
                                },
                                label = { Text(dayOpt.label) },
                                shape = CircleShape,
                                modifier = Modifier.testTag("settings_week_start_${dayOpt.name.lowercase()}"),
                            )
                        }
                    }
                }

                SwitchSettingRow("显示周六、周日", showWeekend, onSetShowWeekend)
                SwitchSettingRow("显示非本周课程（灰显角标）", showNonCurrentWeek, onSetShowNonCurrentWeek)

                // Custom extra top time-slot banner (e.g. Morning Exercise)
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                ) {
                    Column(
                        modifier = Modifier.padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        SwitchSettingRow("显示顶部附加时段条", showMorningExercise, onSetShowMorningExercise)
                        if (showMorningExercise) {
                            OutlinedTextField(
                                value = bannerTitle,
                                onValueChange = { bannerTitle = it },
                                label = { Text("附加时段名称") },
                                placeholder = { Text("例如：早锻炼 / 早自习") },
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("settings_extra_banner_title"),
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                OutlinedTextField(
                                    value = bannerStart,
                                    onValueChange = { bannerStart = it },
                                    label = { Text("开始时间") },
                                    placeholder = { Text("06:40") },
                                    singleLine = true,
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("settings_extra_banner_start"),
                                )
                                OutlinedTextField(
                                    value = bannerEnd,
                                    onValueChange = { bannerEnd = it },
                                    label = { Text("结束时间") },
                                    placeholder = { Text("07:30") },
                                    singleLine = true,
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("settings_extra_banner_end"),
                                )
                            }
                        }
                    }
                }

                // Grid row height & card font size tuning
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                ) {
                    Column(
                        modifier = Modifier.padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("网格行高与字体大小", style = MaterialTheme.typography.labelLarge)
                            TextButton(
                                onClick = {
                                    onSetRowHeightDp(72)
                                    onSetFontScale(1.0f)
                                },
                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                            ) {
                                Text("恢复默认", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text("单节行高", style = MaterialTheme.typography.bodySmall)
                            Text(
                                text = "${rowHeightDp} dp",
                                fontFamily = MonoFontFamily,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                        Slider(
                            value = rowHeightDp.toFloat(),
                            onValueChange = { onSetRowHeightDp(it.roundToInt()) },
                            valueRange = 48f..108f,
                            modifier = Modifier.testTag("settings_row_height_slider"),
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text("课程卡片字体", style = MaterialTheme.typography.bodySmall)
                            Text(
                                text = "${(fontScale * 100).roundToInt()}%",
                                fontFamily = MonoFontFamily,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                        Slider(
                            value = fontScale,
                            onValueChange = { onSetFontScale(it) },
                            valueRange = 0.80f..1.35f,
                            modifier = Modifier.testTag("settings_font_scale_slider"),
                        )
                    }
                }

                if (canDelete) {
                    TextButton(onClick = onDelete) {
                        Icon(Icons.Rounded.DeleteOutline, null, tint = FlowTheme.colors.deleteAccent)
                        Spacer(Modifier.width(4.dp))
                        Text("删除当前课表", color = FlowTheme.colors.deleteAccent)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSetExtraBannerConfig(bannerTitle, bannerStart, bannerEnd)
                    onSave(name, startMonday, totalWeeks)
                },
                modifier = Modifier.testTag("settings_save_button"),
            ) {
                Text("保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )

    if (showDatePicker) {
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = startMonday)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        pickerState.selectedDateMillis?.let {
                            startMonday = DateTimeUtils.weekStartOfWeekMillis(it, weekStartDay)
                        }
                        showDatePicker = false
                    },
                ) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("取消") }
            },
        ) {
            DatePicker(state = pickerState)
        }
    }
}

@Composable
private fun SwitchSettingRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun AddedCoursesDialog(
    courses: List<CourseEntity>,
    onEdit: (CourseEntity) -> Unit,
    onDelete: (CourseEntity) -> Unit,
    onDismiss: () -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme
    val flowColors = FlowTheme.colors

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("已添课程（${courses.size}）") },
        text = {
            if (courses.isEmpty()) {
                Text(
                    text = "当前课表还没有课程，点击右上角“+”添加或通过“导入”快速导入。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colorScheme.onSurfaceVariant,
                )
            } else {
                LazyColumn(
                    modifier = Modifier.heightIn(max = 360.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(courses, key = { it.id }) { course ->
                        val style = CourseColorPalette.styleFor(course.colorIndex)
                        val dotColor = if (flowColors.isDark) style.darkAccent else style.lightAccent
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(MaterialTheme.shapes.small)
                                .clickable { onEdit(course) },
                            shape = MaterialTheme.shapes.small,
                            color = colorScheme.surfaceContainerLow,
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
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
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(dotColor),
                                    )
                                    Column {
                                        Text(
                                            text = course.name,
                                            style = MaterialTheme.typography.titleSmall,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                        Text(
                                            text = "${DateTimeUtils.dayOfWeekFullChinese(course.dayOfWeek)} ${course.startSection}-${course.endSection}节 · ${course.formattedLocation().ifEmpty { "未填地点" }}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                }
                                IconButton(onClick = { onDelete(course) }) {
                                    Icon(
                                        imageVector = Icons.Rounded.DeleteOutline,
                                        contentDescription = "删除课程",
                                        tint = flowColors.deleteAccent,
                                        modifier = Modifier.size(18.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("完成") }
        },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CourseReminderDialog(
    enabled: Boolean,
    leadMinutes: Int,
    dndLevel: DndLevel,
    showLocation: Boolean,
    showTeacher: Boolean,
    showNotes: Boolean,
    onToggleEnabled: (Boolean) -> Unit,
    onSelectLeadMinutes: (Int) -> Unit,
    onSelectDndLevel: (DndLevel) -> Unit,
    onSetFluidCloudDisplayOptions: (showLocation: Boolean, showTeacher: Boolean, showNotes: Boolean) -> Unit,
    onOpenPermissions: () -> Unit,
    onTriggerFluidCloudTest: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("通知、流体云与免打扰") },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 440.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                SwitchSettingRow("开启课前流体云实时活动", enabled, onToggleEnabled)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "课前提前提醒（0-120分）",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        FilledTonalButton(
                            onClick = { onSelectLeadMinutes((leadMinutes - 5).coerceAtLeast(0)) },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                            modifier = Modifier.height(28.dp),
                        ) { Text("-5") }
                        Text(
                            text = "${leadMinutes}分",
                            fontFamily = MonoFontFamily,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        FilledTonalButton(
                            onClick = { onSelectLeadMinutes((leadMinutes + 5).coerceAtMost(120)) },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                            modifier = Modifier.height(28.dp),
                        ) { Text("+5") }
                    }
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(0, 5, 10, 15, 20, 30, 45).forEach { mins ->
                        FilterChip(
                            selected = leadMinutes == mins,
                            onClick = { onSelectLeadMinutes(mins) },
                            label = { Text(if (mins == 0) "准点" else "${mins}分") },
                            shape = CircleShape,
                        )
                    }
                }

                // System DND interruption level selector
                Text(
                    text = "系统免打扰级别",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DndLevel.entries.forEach { level ->
                        FilterChip(
                            selected = dndLevel == level,
                            onClick = { onSelectDndLevel(level) },
                            label = { Text(level.label) },
                            shape = CircleShape,
                        )
                    }
                }
                Text(
                    text = dndLevel.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                // Fluid Cloud card metadata display toggles
                Text(
                    text = "流体云卡片展示内容",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                SwitchSettingRow("展示地点 / 教室", showLocation) {
                    onSetFluidCloudDisplayOptions(it, showTeacher, showNotes)
                }
                SwitchSettingRow("展示授课教师", showTeacher) {
                    onSetFluidCloudDisplayOptions(showLocation, it, showNotes)
                }
                SwitchSettingRow("展示备注信息", showNotes) {
                    onSetFluidCloudDisplayOptions(showLocation, showTeacher, it)
                }

                Button(
                    onClick = onTriggerFluidCloudTest,
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                ) {
                    Icon(Icons.Rounded.AutoAwesome, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("测试流体云效果（1 分钟）")
                }

                TextButton(
                    onClick = onOpenPermissions,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("检查通知、实时活动与精确闹钟权限")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("关闭") }
        },
    )
}
