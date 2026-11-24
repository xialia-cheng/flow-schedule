package com.example.ui.timetable

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.FormatListBulleted
import androidx.compose.material.icons.rounded.ImportExport
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
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
    onSetShowLunchBreak: (Boolean) -> Unit,
    onSetLunchBreakConfig: (title: String, afterSection: Int, startTime: String, endTime: String) -> Unit,
    onSetShowNonCurrentWeek: (Boolean) -> Unit,
    onSetShowWeekend: (Boolean) -> Unit,
    onSetWeekStartDay: (WeekStartDay) -> Unit,
    onSetTimetableRowHeightDp: (Int) -> Unit,
    onSetTimetableFontScale: (Float) -> Unit,
    onSetCourseReminderEnabled: (Boolean) -> Unit,
    onSetCourseReminderLeadMinutes: (Int) -> Unit,
    onSetDndLevel: (DndLevel) -> Unit,
    onSetFluidCloudDisplayOptions: (showLocation: Boolean, showTeacher: Boolean, showNotes: Boolean) -> Unit,
    onSetFluidCloudLockscreenDetails: (Boolean) -> Unit,
    onSetThemeMode: (AppThemeMode) -> Unit,
    onSetCompactMode: (Boolean) -> Unit,
    onOpenPermissions: () -> Unit,
    onOpenImportExport: () -> Unit,
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
                        "提前 ${state.settings.courseReminderLeadMinutes} 分·实况通知"
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
                                text = "隐藏附加时段条、非本周灰显与冗余信息，专注本周课程",
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
            sectionTimes = state.sectionTimes,
            canDelete = state.allTimetables.size > 1,
            showMorningExercise = state.settings.showMorningExercise,
            extraBannerTitle = state.settings.extraBannerTitle,
            extraBannerStartTime = state.settings.extraBannerStartTime,
            extraBannerEndTime = state.settings.extraBannerEndTime,
            showLunchBreak = state.settings.showLunchBreak,
            lunchBreakTitle = state.settings.lunchBreakTitle,
            lunchAfterSection = state.settings.lunchAfterSection,
            lunchBreakStartTime = state.settings.lunchBreakStartTime,
            lunchBreakEndTime = state.settings.lunchBreakEndTime,
            showNonCurrentWeek = state.settings.showNonCurrentWeekCourses,
            showWeekend = state.settings.showWeekend,
            weekStartDay = state.settings.weekStartDay,
            rowHeightDp = state.settings.timetableRowHeightDp,
            fontScale = state.settings.timetableFontScale,
            courseReminderEnabled = state.settings.courseReminderEnabled,
            courseReminderLeadMinutes = state.settings.courseReminderLeadMinutes,
            onSetShowMorningExercise = onSetShowMorningExercise,
            onSetExtraBannerConfig = onSetExtraBannerConfig,
            onSetShowLunchBreak = onSetShowLunchBreak,
            onSetLunchBreakConfig = onSetLunchBreakConfig,
            onSetShowNonCurrentWeek = onSetShowNonCurrentWeek,
            onSetShowWeekend = onSetShowWeekend,
            onSetWeekStartDay = onSetWeekStartDay,
            onSetRowHeightDp = onSetTimetableRowHeightDp,
            onSetFontScale = onSetTimetableFontScale,
            onOpenSectionTimes = { showSectionTimesDialog = true },
            onOpenReminder = { showReminderDialog = true },
            onOpenImportExport = {
                showTimetableSettingsDialog = false
                onDismiss()
                onOpenImportExport()
            },
            onSave = { name, startMonday, totalWeeks ->
                onUpdateTimetableSettings(name, startMonday, totalWeeks)
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
            lockscreenDetails = state.settings.fluidCloudLockscreenDetails,
            onToggleEnabled = onSetCourseReminderEnabled,
            onSelectLeadMinutes = onSetCourseReminderLeadMinutes,
            onSelectDndLevel = onSetDndLevel,
            onSetFluidCloudDisplayOptions = onSetFluidCloudDisplayOptions,
            onSetLockscreenDetails = onSetFluidCloudLockscreenDetails,
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
    sectionTimes: List<SectionTimeEntity>,
    canDelete: Boolean,
    showMorningExercise: Boolean,
    extraBannerTitle: String,
    extraBannerStartTime: String,
    extraBannerEndTime: String,
    showLunchBreak: Boolean,
    lunchBreakTitle: String,
    lunchAfterSection: Int,
    lunchBreakStartTime: String,
    lunchBreakEndTime: String,
    showNonCurrentWeek: Boolean,
    showWeekend: Boolean,
    weekStartDay: WeekStartDay,
    rowHeightDp: Int,
    fontScale: Float,
    courseReminderEnabled: Boolean,
    courseReminderLeadMinutes: Int,
    onSetShowMorningExercise: (Boolean) -> Unit,
    onSetExtraBannerConfig: (title: String, startTime: String, endTime: String) -> Unit,
    onSetShowLunchBreak: (Boolean) -> Unit,
    onSetLunchBreakConfig: (title: String, afterSection: Int, startTime: String, endTime: String) -> Unit,
    onSetShowNonCurrentWeek: (Boolean) -> Unit,
    onSetShowWeekend: (Boolean) -> Unit,
    onSetWeekStartDay: (WeekStartDay) -> Unit,
    onSetRowHeightDp: (Int) -> Unit,
    onSetFontScale: (Float) -> Unit,
    onOpenSectionTimes: () -> Unit,
    onOpenReminder: () -> Unit,
    onOpenImportExport: () -> Unit,
    onSave: (name: String, semesterStartMondayMillis: Long, totalWeeks: Int) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme
    val maxAfterSection = (sectionTimes.size - 1).coerceAtLeast(1)

    var name by remember(timetable) { mutableStateOf(timetable.name) }
    var startMonday by remember(timetable) { mutableLongStateOf(timetable.semesterStartMondayMillis) }
    var totalWeeks by remember(timetable) { mutableIntStateOf(timetable.totalWeeks) }

    var bannerTitle by remember(extraBannerTitle) { mutableStateOf(extraBannerTitle) }
    var bannerStart by remember(extraBannerStartTime) { mutableStateOf(extraBannerStartTime) }
    var bannerEnd by remember(extraBannerEndTime) { mutableStateOf(extraBannerEndTime) }

    var lunchTitle by remember(lunchBreakTitle) { mutableStateOf(lunchBreakTitle) }
    var lunchAfter by remember(lunchAfterSection, maxAfterSection) {
        mutableIntStateOf(lunchAfterSection.coerceIn(1, maxAfterSection))
    }
    var lunchStart by remember(lunchBreakStartTime) { mutableStateOf(lunchBreakStartTime) }
    var lunchEnd by remember(lunchBreakEndTime) { mutableStateOf(lunchBreakEndTime) }

    var showDatePicker by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 24.dp)
                .widthIn(max = 560.dp)
                .heightIn(max = 680.dp),
            shape = MaterialTheme.shapes.extraLarge,
            color = colorScheme.surface,
            tonalElevation = 4.dp,
            border = BorderStroke(1.dp, colorScheme.outlineVariant.copy(alpha = 0.6f)),
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 20.dp, end = 12.dp, top = 16.dp, bottom = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(
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
                                imageVector = Icons.Rounded.Tune,
                                contentDescription = null,
                                tint = colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                        Column {
                            Text(
                                text = "课表设置",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = colorScheme.onSurface,
                            )
                            Text(
                                text = "学期、网格样式、可选时段与提醒数据",
                                style = MaterialTheme.typography.bodySmall,
                                color = colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "关闭",
                            tint = colorScheme.onSurfaceVariant,
                        )
                    }
                }

                HorizontalDivider(color = colorScheme.outlineVariant.copy(alpha = 0.7f))

                // 4 Grouped Settings Cards
                Column(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    // 1. 基础与学期
                    SettingsGroupCard(
                        icon = Icons.Rounded.CalendarMonth,
                        title = "基础与学期",
                    ) {
                        OutlinedTextField(
                            value = name,
                            onValueChange = { name = it },
                            label = { Text("课表名称") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )

                        SettingsNavigationRow(
                            icon = Icons.Rounded.CalendarMonth,
                            title = "开学第一周日期",
                            subtitle = DateTimeUtils.formatFullDate(startMonday),
                            onClick = { showDatePicker = true },
                            modifier = Modifier.testTag("settings_semester_start_picker"),
                        )

                        HorizontalDivider(color = colorScheme.outlineVariant.copy(alpha = 0.45f))

                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = "学期总周数",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = colorScheme.onSurface,
                                )
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
                                        color = colorScheme.primary,
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

                        HorizontalDivider(color = colorScheme.outlineVariant.copy(alpha = 0.45f))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "每周起始日",
                                style = MaterialTheme.typography.bodyMedium,
                                color = colorScheme.onSurface,
                            )
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

                        HorizontalDivider(color = colorScheme.outlineVariant.copy(alpha = 0.45f))

                        SettingsNavigationRow(
                            icon = Icons.Rounded.AccessTime,
                            title = "节次作息时间设置",
                            subtitle = "当前共 ${sectionTimes.size.coerceIn(6, 16)} 节 · 自由配置每节上下课时间",
                            onClick = onOpenSectionTimes,
                            modifier = Modifier.testTag("settings_section_times_entry"),
                        )

                        if (canDelete) {
                            HorizontalDivider(color = colorScheme.outlineVariant.copy(alpha = 0.45f))
                            TextButton(
                                onClick = onDelete,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Icon(Icons.Rounded.DeleteOutline, null, tint = FlowTheme.colors.deleteAccent)
                                Spacer(Modifier.width(6.dp))
                                Text("删除当前课表", color = FlowTheme.colors.deleteAccent)
                            }
                        }
                    }

                    // 2. 课表显示
                    SettingsGroupCard(
                        icon = Icons.Rounded.Visibility,
                        title = "课表显示",
                        trailing = {
                            TextButton(
                                onClick = {
                                    onSetRowHeightDp(72)
                                    onSetFontScale(1.0f)
                                },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                                modifier = Modifier.height(30.dp),
                            ) {
                                Text("恢复默认", style = MaterialTheme.typography.labelMedium)
                            }
                        },
                    ) {
                        SwitchSettingRow(
                            title = "显示周末列（周六、周日）",
                            subtitle = "关闭后仅展示周一至周五课程网格",
                            checked = showWeekend,
                            onCheckedChange = onSetShowWeekend,
                        )

                        HorizontalDivider(color = colorScheme.outlineVariant.copy(alpha = 0.45f))

                        SwitchSettingRow(
                            title = "显示非本周课程",
                            subtitle = "在网格中以半透明角标提示非本周排课",
                            checked = showNonCurrentWeek,
                            onCheckedChange = onSetShowNonCurrentWeek,
                        )

                        HorizontalDivider(color = colorScheme.outlineVariant.copy(alpha = 0.45f))

                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text("网格行高", style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    text = "${rowHeightDp} dp",
                                    fontFamily = MonoFontFamily,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = colorScheme.primary,
                                )
                            }
                            Slider(
                                value = rowHeightDp.toFloat().coerceIn(54f, 108f),
                                onValueChange = { onSetRowHeightDp(it.roundToInt()) },
                                valueRange = 54f..108f,
                                modifier = Modifier.testTag("settings_row_height_slider"),
                            )

                            Spacer(Modifier.height(4.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text("卡片字号缩放", style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    text = "${(fontScale * 100).roundToInt()}%",
                                    fontFamily = MonoFontFamily,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = colorScheme.primary,
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

                    // 3. 可选附加时段 (渐进式展开)
                    SettingsGroupCard(
                        icon = Icons.Rounded.Schedule,
                        title = "可选附加时段",
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            SwitchSettingRow(
                                title = "顶部打卡时段条",
                                subtitle = "默认关闭 · 开启后在课表上方展示自定义打卡条",
                                checked = showMorningExercise,
                                onCheckedChange = onSetShowMorningExercise,
                                modifier = Modifier.testTag("settings_show_extra_banner_switch"),
                            )
                            AnimatedVisibility(
                                visible = showMorningExercise,
                                enter = expandVertically() + fadeIn(),
                                exit = shrinkVertically() + fadeOut(),
                            ) {
                                Column(
                                    modifier = Modifier.padding(top = 4.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    OutlinedTextField(
                                        value = bannerTitle,
                                        onValueChange = { bannerTitle = it },
                                        label = { Text("时段名称") },
                                        placeholder = { Text("例如：早锻炼 / 早自习 / 晨读") },
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

                        HorizontalDivider(color = colorScheme.outlineVariant.copy(alpha = 0.55f))

                        val safeAfter = lunchAfter.coerceIn(1, maxAfterSection)
                        val prevSlot = sectionTimes.find { it.sectionNumber == safeAfter }
                        val nextSlot = sectionTimes.find { it.sectionNumber == safeAfter + 1 }
                        val autoStartPlaceholder = prevSlot?.endLabel() ?: "11:40"
                        val autoEndPlaceholder = nextSlot?.startLabel() ?: "14:00"

                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            SwitchSettingRow(
                                title = "课间午休分隔带",
                                subtitle = "默认关闭 · 开启后在指定节次之间插入分隔条",
                                checked = showLunchBreak,
                                onCheckedChange = onSetShowLunchBreak,
                                modifier = Modifier.testTag("settings_show_lunch_break_switch"),
                            )
                            AnimatedVisibility(
                                visible = showLunchBreak,
                                enter = expandVertically() + fadeIn(),
                                exit = shrinkVertically() + fadeOut(),
                            ) {
                                Column(
                                    modifier = Modifier.padding(top = 4.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp),
                                ) {
                                    OutlinedTextField(
                                        value = lunchTitle,
                                        onValueChange = { lunchTitle = it },
                                        label = { Text("分隔带名称") },
                                        placeholder = { Text("午休") },
                                        singleLine = true,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("settings_lunch_break_title"),
                                    )

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Text(
                                            text = "显示位置",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = colorScheme.onSurface,
                                        )
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        ) {
                                            FilledTonalButton(
                                                onClick = { if (lunchAfter > 1) lunchAfter-- },
                                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                                                modifier = Modifier
                                                    .height(30.dp)
                                                    .testTag("settings_lunch_after_minus"),
                                            ) { Text("-") }
                                            Text(
                                                text = "第 $safeAfter 节后",
                                                fontFamily = MonoFontFamily,
                                                fontWeight = FontWeight.Bold,
                                                color = colorScheme.primary,
                                            )
                                            FilledTonalButton(
                                                onClick = { if (lunchAfter < maxAfterSection) lunchAfter++ },
                                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                                                modifier = Modifier
                                                    .height(30.dp)
                                                    .testTag("settings_lunch_after_plus"),
                                            ) { Text("+") }
                                        }
                                    }

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        OutlinedTextField(
                                            value = lunchStart,
                                            onValueChange = { lunchStart = it },
                                            label = { Text("开始时间（可选）") },
                                            placeholder = { Text(autoStartPlaceholder) },
                                            singleLine = true,
                                            modifier = Modifier
                                                .weight(1f)
                                                .testTag("settings_lunch_break_start"),
                                        )
                                        OutlinedTextField(
                                            value = lunchEnd,
                                            onValueChange = { lunchEnd = it },
                                            label = { Text("结束时间（可选）") },
                                            placeholder = { Text(autoEndPlaceholder) },
                                            singleLine = true,
                                            modifier = Modifier
                                                .weight(1f)
                                                .testTag("settings_lunch_break_end"),
                                        )
                                    }

                                    Text(
                                        text = "留空则自动取第 $safeAfter 节下课至第 ${safeAfter + 1} 节上课的间隔时间（$autoStartPlaceholder - $autoEndPlaceholder）",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }

                    // 4. 提醒与数据
                    SettingsGroupCard(
                        icon = Icons.Rounded.NotificationsActive,
                        title = "提醒与数据",
                    ) {
                        SettingsNavigationRow(
                            icon = Icons.Rounded.NotificationsActive,
                            title = "课前提醒 / 实况通知快捷设置",
                            subtitle = if (courseReminderEnabled) {
                                "已开启 · 提前 ${courseReminderLeadMinutes} 分钟发送实况通知"
                            } else {
                                "已关闭 · 点击配置提醒、实况通知与免打扰"
                            },
                            onClick = onOpenReminder,
                            modifier = Modifier.testTag("settings_reminder_entry"),
                        )

                        HorizontalDivider(color = colorScheme.outlineVariant.copy(alpha = 0.45f))

                        SettingsNavigationRow(
                            icon = Icons.Rounded.ImportExport,
                            title = "导入导出与备份",
                            subtitle = "CSV / 教务网页导入、分享码与 JSON 全量备份恢复",
                            onClick = onOpenImportExport,
                            modifier = Modifier.testTag("settings_import_export_entry"),
                        )
                    }
                }

                HorizontalDivider(color = colorScheme.outlineVariant.copy(alpha = 0.7f))

                // Bottom actions
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("取消")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            onSetExtraBannerConfig(bannerTitle, bannerStart, bannerEnd)
                            onSetLunchBreakConfig(lunchTitle, lunchAfter, lunchStart, lunchEnd)
                            onSave(name, startMonday, totalWeeks)
                        },
                        modifier = Modifier.testTag("settings_save_button"),
                    ) {
                        Text("保存")
                    }
                }
            }
        }
    }

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
private fun SettingsGroupCard(
    icon: ImageVector,
    title: String,
    trailing: @Composable (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme
    val flowColors = FlowTheme.colors

    Surface(
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
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = colorScheme.onSurface,
                    )
                }
                trailing?.invoke()
            }
            HorizontalDivider(color = colorScheme.outlineVariant.copy(alpha = 0.65f))
            content()
        }
    }
}

@Composable
private fun SettingsNavigationRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colorScheme = MaterialTheme.colorScheme
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = colorScheme.primary,
                modifier = Modifier.size(18.dp),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = colorScheme.onSurface,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
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

@Composable
private fun SwitchSettingRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
) {
    val colorScheme = MaterialTheme.colorScheme
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = 12.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = colorScheme.onSurface,
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = colorScheme.onSurfaceVariant,
                )
            }
        }
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
    lockscreenDetails: Boolean,
    onToggleEnabled: (Boolean) -> Unit,
    onSelectLeadMinutes: (Int) -> Unit,
    onSelectDndLevel: (DndLevel) -> Unit,
    onSetFluidCloudDisplayOptions: (showLocation: Boolean, showTeacher: Boolean, showNotes: Boolean) -> Unit,
    onSetLockscreenDetails: (Boolean) -> Unit,
    onOpenPermissions: () -> Unit,
    onTriggerFluidCloudTest: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("通知、实况通知与免打扰") },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 440.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                SwitchSettingRow("上课自动触发实况通知", enabled, onToggleEnabled)

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

                // Live notification card metadata display toggles
                Text(
                    text = "实况通知卡片展示内容",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                SwitchSettingRow(
                    title = "展示地点 / 教室",
                    checked = showLocation,
                    onCheckedChange = { onSetFluidCloudDisplayOptions(it, showTeacher, showNotes) },
                )
                SwitchSettingRow(
                    title = "展示授课教师",
                    checked = showTeacher,
                    onCheckedChange = { onSetFluidCloudDisplayOptions(showLocation, it, showNotes) },
                )
                SwitchSettingRow(
                    title = "展示备注信息",
                    checked = showNotes,
                    onCheckedChange = { onSetFluidCloudDisplayOptions(showLocation, showTeacher, it) },
                )
                SwitchSettingRow(
                    title = "锁屏展示课程与日程详情（默认关闭）",
                    checked = lockscreenDetails,
                    onCheckedChange = onSetLockscreenDetails,
                )

                Button(
                    onClick = onTriggerFluidCloudTest,
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                ) {
                    Icon(Icons.Rounded.AutoAwesome, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("测试实况通知效果（1 分钟）")
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
