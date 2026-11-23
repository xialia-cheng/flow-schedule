package com.example.ui.timetable

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.PersonOutline
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.CourseColorPalette
import com.example.data.CourseEntity
import com.example.data.DateTimeUtils
import com.example.data.SectionTimeEntity
import com.example.data.WeekType
import com.example.ui.theme.FlowTheme
import com.example.ui.theme.MonoFontFamily

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CourseEditorSheet(
    editingCourse: CourseEntity?,
    initialDayOfWeek: Int = 1,
    initialStartSection: Int = 1,
    totalWeeks: Int = 20,
    maxSectionCount: Int = 12,
    sectionTimes: List<SectionTimeEntity> = emptyList(),
    onDismiss: () -> Unit,
    onSave: (
        existingId: Long,
        name: String,
        location: String,
        teacher: String,
        dayOfWeek: Int,
        startSection: Int,
        endSection: Int,
        startWeek: Int,
        endWeek: Int,
        weekType: WeekType,
        colorIndex: Int,
        notes: String,
        customColorHex: String,
        customWeeksCsv: String,
    ) -> Unit,
    onDelete: ((CourseEntity) -> Unit)? = null,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val colorScheme = MaterialTheme.colorScheme
    val flowColors = FlowTheme.colors
    val safeMaxSection = maxSectionCount.coerceIn(6, 16)
    val safeTotalWeeks = totalWeeks.coerceIn(1, CourseEntity.MAX_TOTAL_WEEKS)

    var name by remember(editingCourse) { mutableStateOf(editingCourse?.name ?: "") }
    var location by remember(editingCourse) { mutableStateOf(editingCourse?.location ?: "") }
    var teacher by remember(editingCourse) { mutableStateOf(editingCourse?.teacher ?: "") }
    var dayOfWeek by remember(editingCourse, initialDayOfWeek) {
        mutableIntStateOf(editingCourse?.dayOfWeek ?: initialDayOfWeek.coerceIn(1, 7))
    }
    var startSection by remember(editingCourse, initialStartSection, safeMaxSection) {
        mutableIntStateOf((editingCourse?.startSection ?: initialStartSection).coerceIn(1, safeMaxSection))
    }
    var endSection by remember(editingCourse, initialStartSection, safeMaxSection) {
        mutableIntStateOf(
            (editingCourse?.endSection ?: (initialStartSection + 1)).coerceIn(startSection, safeMaxSection),
        )
    }
    var startWeek by remember(editingCourse, safeTotalWeeks) {
        mutableIntStateOf((editingCourse?.startWeek ?: 1).coerceIn(1, safeTotalWeeks))
    }
    var endWeek by remember(editingCourse, safeTotalWeeks) {
        mutableIntStateOf((editingCourse?.endWeek ?: 16.coerceAtMost(safeTotalWeeks)).coerceIn(startWeek, safeTotalWeeks))
    }
    var weekType by remember(editingCourse) { mutableStateOf(editingCourse?.weekType ?: WeekType.ALL) }
    var selectedWeeks by remember(editingCourse, safeTotalWeeks) {
        val initial = editingCourse?.resolvedWeekSet(safeTotalWeeks)
            ?.filter { it in 1..safeTotalWeeks }
            ?.toSet()
            ?.takeIf { it.isNotEmpty() }
            ?: (1..16.coerceAtMost(safeTotalWeeks)).toSet()
        mutableStateOf(initial)
    }
    var weekSelectionError by remember { mutableStateOf(false) }
    var colorIndex by remember(editingCourse) { mutableIntStateOf(editingCourse?.colorIndex ?: 0) }
    var customColorHex by remember(editingCourse) { mutableStateOf(editingCourse?.customColorHex ?: "") }
    var showCustomColorDialog by remember { mutableStateOf(false) }
    var notes by remember(editingCourse) { mutableStateOf(editingCourse?.notes ?: "") }
    var nameError by remember { mutableStateOf(false) }

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
                    text = if (editingCourse == null) "添加课程" else "编辑课程",
                    style = MaterialTheme.typography.headlineSmall,
                    color = colorScheme.onSurface,
                )
                if (editingCourse != null && onDelete != null) {
                    IconButton(
                        onClick = {
                            onDelete(editingCourse)
                            onDismiss()
                        },
                        modifier = Modifier.testTag("course_editor_delete_button"),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.DeleteOutline,
                            contentDescription = "删除课程",
                            tint = flowColors.deleteAccent,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = name,
                onValueChange = {
                    name = it
                    if (nameError && it.isNotBlank()) nameError = false
                },
                label = { Text("课程名称") },
                placeholder = { Text("例如：高等数学 / 操作系统") },
                singleLine = true,
                isError = nameError,
                supportingText = if (nameError) {
                    { Text("请输入课程名称") }
                } else {
                    null
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("course_name_input"),
                shape = MaterialTheme.shapes.medium,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = colorScheme.primary,
                    unfocusedBorderColor = colorScheme.outline,
                ),
            )

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedTextField(
                    value = location,
                    onValueChange = { location = it },
                    label = { Text("上课地点") },
                    placeholder = { Text("笃行楼 302") },
                    prefix = { Text("@", color = colorScheme.primary) },
                    singleLine = true,
                    modifier = Modifier
                        .weight(1.2f)
                        .testTag("course_location_input"),
                    shape = MaterialTheme.shapes.medium,
                )
                OutlinedTextField(
                    value = teacher,
                    onValueChange = { teacher = it },
                    label = { Text("教师") },
                    placeholder = { Text("选填") },
                    singleLine = true,
                    modifier = Modifier
                        .weight(0.8f)
                        .testTag("course_teacher_input"),
                    shape = MaterialTheme.shapes.medium,
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Weekday selector (1..7)
            Text(
                text = "上课星期",
                style = MaterialTheme.typography.labelMedium,
                color = colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                for (day in 1..7) {
                    val selected = dayOfWeek == day
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .height(40.dp)
                            .clip(CircleShape)
                            .clickable { dayOfWeek = day },
                        shape = CircleShape,
                        color = if (selected) colorScheme.primary else flowColors.cardElevated,
                        border = BorderStroke(
                            1.dp,
                            if (selected) colorScheme.primary else colorScheme.outlineVariant,
                        ),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = DateTimeUtils.dayOfWeekShortChinese(day),
                                style = MaterialTheme.typography.labelLarge,
                                color = if (selected) colorScheme.onPrimary else colorScheme.onSurface,
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Section range (1..safeMaxSection) with live custom bell-time preview
            val startSlotLabel = sectionTimes.find { it.sectionNumber == startSection }?.startLabel()
            val endSlotLabel = sectionTimes.find { it.sectionNumber == endSection }?.endLabel()
            val bellPreview = if (startSlotLabel != null && endSlotLabel != null) {
                " · $startSlotLabel - $endSlotLabel"
            } else {
                ""
            }
            Text(
                text = "节次范围（第 1 - $safeMaxSection 节$bellPreview）",
                style = MaterialTheme.typography.labelMedium,
                color = colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                NumericStepperCard(
                    label = "开始节次",
                    valueText = "第 $startSection 节",
                    onDecrement = {
                        if (startSection > 1) {
                            startSection--
                            if (endSection < startSection) endSection = startSection
                        }
                    },
                    onIncrement = {
                        if (startSection < safeMaxSection) {
                            startSection++
                            if (endSection < startSection) endSection = startSection
                        }
                    },
                    modifier = Modifier.weight(1f),
                )
                NumericStepperCard(
                    label = "结束节次",
                    valueText = "第 $endSection 节",
                    onDecrement = {
                        if (endSection > startSection) endSection--
                    },
                    onIncrement = {
                        if (endSection < safeMaxSection) endSection++
                    },
                    modifier = Modifier.weight(1f),
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Week range steppers + visual multi-select week grid (supports non-contiguous weeks like 5-10, 12-18)
            val weekSummaryText = remember(selectedWeeks) {
                CourseEntity.formatCompressedWeekRanges(selectedWeeks)
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "上课周次（共 ${selectedWeeks.size} 周）",
                    style = MaterialTheme.typography.labelMedium,
                    color = colorScheme.onSurfaceVariant,
                )
                Text(
                    text = weekSummaryText,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = if (weekSelectionError) flowColors.deleteAccent else colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                NumericStepperCard(
                    label = "连续起始周",
                    valueText = "第 $startWeek 周",
                    onDecrement = {
                        if (startWeek > 1) {
                            startWeek--
                            selectedWeeks = (startWeek..endWeek).filter { weekType.matchesWeek(it) }.toSet()
                            weekSelectionError = false
                        }
                    },
                    onIncrement = {
                        if (startWeek < safeTotalWeeks) {
                            startWeek++
                            if (endWeek < startWeek) endWeek = startWeek
                            selectedWeeks = (startWeek..endWeek).filter { weekType.matchesWeek(it) }.toSet()
                            weekSelectionError = false
                        }
                    },
                    modifier = Modifier.weight(1f),
                )
                NumericStepperCard(
                    label = "连续结束周",
                    valueText = "第 $endWeek 周",
                    onDecrement = {
                        if (endWeek > startWeek) {
                            endWeek--
                            selectedWeeks = (startWeek..endWeek).filter { weekType.matchesWeek(it) }.toSet()
                            weekSelectionError = false
                        }
                    },
                    onIncrement = {
                        if (endWeek < safeTotalWeeks) {
                            endWeek++
                            selectedWeeks = (startWeek..endWeek).filter { weekType.matchesWeek(it) }.toSet()
                            weekSelectionError = false
                        }
                    },
                    modifier = Modifier.weight(1f),
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Quick batch week-selection pills (全选 / 单周 / 双周 / 清空)
            val allWeekSet = remember(safeTotalWeeks) { (1..safeTotalWeeks).toSet() }
            val oddWeekSet = remember(safeTotalWeeks) { (1..safeTotalWeeks).filter { it % 2 == 1 }.toSet() }
            val evenWeekSet = remember(safeTotalWeeks) { (1..safeTotalWeeks).filter { it % 2 == 0 }.toSet() }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                FilterChip(
                    selected = selectedWeeks == allWeekSet,
                    onClick = {
                        weekType = WeekType.ALL
                        startWeek = 1
                        endWeek = safeTotalWeeks
                        selectedWeeks = allWeekSet
                        weekSelectionError = false
                    },
                    label = { Text("全选") },
                    shape = CircleShape,
                    modifier = Modifier.testTag("week_grid_select_all"),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = colorScheme.primaryContainer,
                        selectedLabelColor = colorScheme.onPrimaryContainer,
                    ),
                )
                FilterChip(
                    selected = selectedWeeks == oddWeekSet,
                    onClick = {
                        weekType = WeekType.ODD
                        startWeek = 1
                        endWeek = if (safeTotalWeeks % 2 == 1) safeTotalWeeks else (safeTotalWeeks - 1).coerceAtLeast(1)
                        selectedWeeks = oddWeekSet
                        weekSelectionError = false
                    },
                    label = { Text("单周") },
                    shape = CircleShape,
                    modifier = Modifier.testTag("week_grid_select_odd"),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = colorScheme.primaryContainer,
                        selectedLabelColor = colorScheme.onPrimaryContainer,
                    ),
                )
                FilterChip(
                    selected = selectedWeeks == evenWeekSet,
                    onClick = {
                        weekType = WeekType.EVEN
                        sta
