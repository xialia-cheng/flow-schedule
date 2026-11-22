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
                        startWeek = 2.coerceAtMost(safeTotalWeeks)
                        endWeek = if (safeTotalWeeks % 2 == 0) safeTotalWeeks else (safeTotalWeeks - 1).coerceAtLeast(1)
                        selectedWeeks = evenWeekSet
                        weekSelectionError = false
                    },
                    label = { Text("双周") },
                    shape = CircleShape,
                    modifier = Modifier.testTag("week_grid_select_even"),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = colorScheme.primaryContainer,
                        selectedLabelColor = colorScheme.onPrimaryContainer,
                    ),
                )
                FilterChip(
                    selected = selectedWeeks.isEmpty(),
                    onClick = {
                        selectedWeeks = emptySet()
                    },
                    label = { Text("清空") },
                    shape = CircleShape,
                    modifier = Modifier.testTag("week_grid_clear"),
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Visual 1..safeTotalWeeks square grid supporting arbitrary non-contiguous week toggling
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("course_week_multi_grid"),
                shape = MaterialTheme.shapes.medium,
                color = flowColors.cardElevated,
                border = BorderStroke(
                    1.dp,
                    if (weekSelectionError) flowColors.deleteAccent else colorScheme.outlineVariant,
                ),
            ) {
                Column(
                    modifier = Modifier.padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    val columns = 5
                    val rows = (1..safeTotalWeeks).chunked(columns)
                    rows.forEach { rowWeeks ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            rowWeeks.forEach { weekNum ->
                                val isPicked = weekNum in selectedWeeks
                                Surface(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(38.dp)
                                        .clip(MaterialTheme.shapes.small)
                                        .clickable {
                                            val updated = if (isPicked) {
                                                selectedWeeks - weekNum
                                            } else {
                                                selectedWeeks + weekNum
                                            }
                                            selectedWeeks = updated
                                            if (updated.isNotEmpty()) {
                                                val spec = CourseEntity.summarizeWeeks(updated)
                                                startWeek = spec.startWeek
                                                endWeek = spec.endWeek
                                                weekType = spec.weekType
                                                weekSelectionError = false
                                            }
                                        }
                                        .testTag("course_week_cell_$weekNum"),
                                    shape = MaterialTheme.shapes.small,
                                    color = if (isPicked) colorScheme.primary else colorScheme.surface,
                                    border = BorderStroke(
                                        width = 1.dp,
                                        color = if (isPicked) colorScheme.primary else colorScheme.outlineVariant,
                                    ),
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            text = "$weekNum",
                                            fontFamily = MonoFontFamily,
                                            style = MaterialTheme.typography.labelLarge,
                                            fontWeight = if (isPicked) FontWeight.Bold else FontWeight.Medium,
                                            color = if (isPicked) colorScheme.onPrimary else colorScheme.onSurface,
                                        )
                                    }
                                }
                            }
                            repeat(columns - rowWeeks.size) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                    if (weekSelectionError) {
                        Text(
                            text = "请至少选择 1 个上课周次",
                            style = MaterialTheme.typography.labelSmall,
                            color = flowColors.deleteAccent,
                        )
                    } else {
                        Text(
                            text = "点按任意方格可自由组合不连续周次（如 5-10, 12-18周）",
                            style = MaterialTheme.typography.labelSmall,
                            color = colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 16 high-distinction preset colors arranged in 2 clean rows + Custom Color entry
            val activeResolvedStyle = CourseColorPalette.resolveStyle(colorIndex, customColorHex)
            val activeCustomColor = CourseColorPalette.parseHexColorOrNull(customColorHex)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "卡片配色（${activeResolvedStyle.name}）",
                    style = MaterialTheme.typography.labelMedium,
                    color = colorScheme.onSurfaceVariant,
                )
                if (activeCustomColor != null) {
                    Text(
                        text = CourseColorPalette.formatHex(activeCustomColor),
                        fontFamily = MonoFontFamily,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = colorScheme.primary,
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))

            val row1 = CourseColorPalette.styles.take(8)
            val row2 = CourseColorPalette.styles.drop(8).take(8)
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                PresetColorRow(
                    styles = row1,
                    startIndex = 0,
                    selectedColorIndex = if (activeCustomColor == null) colorIndex else -1,
                    isDark = flowColors.isDark,
                    onSelect = { idx ->
                        colorIndex = idx
                        customColorHex = ""
                    },
                )
                PresetColorRow(
                    styles = row2,
                    startIndex = 8,
                    selectedColorIndex = if (activeCustomColor == null) colorIndex else -1,
                    isDark = flowColors.isDark,
                    onSelect = { idx ->
                        colorIndex = idx
                        customColorHex = ""
                    },
                )
                // Custom color entry bar at the end
                Surface(
                    onClick = { showCustomColorDialog = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("course_custom_color_button"),
                    shape = MaterialTheme.shapes.medium,
                    color = if (activeCustomColor != null) {
                        colorScheme.primaryContainer.copy(alpha = 0.5f)
                    } else {
                        flowColors.cardElevated
                    },
                    border = BorderStroke(
                        width = if (activeCustomColor != null) 1.5.dp else 1.dp,
                        color = if (activeCustomColor != null) colorScheme.primary else colorScheme.outlineVariant,
                    ),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(26.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (activeCustomColor != null) {
                                            Brush.linearGradient(listOf(activeCustomColor, activeCustomColor))
                                        } else {
                                            Brush.sweepGradient(
                                                listOf(
                                                    Color(0xFFEF4444),
                                                    Color(0xFFF59E0B),
                                                    Color(0xFF10B981),
                                                    Color(0xFF3B82F6),
                                                    Color(0xFF8B5CF6),
                                                    Color(0xFFEC4899),
                                                    Color(0xFFEF4444),
                                                ),
                                            )
                                        },
                                    )
                                    .border(
                                        width = 1.dp,
                                        color = colorScheme.outline.copy(alpha = 0.4f),
                                        shape = CircleShape,
                                    ),
                                contentAlignment = Alignment.Center,
                            ) {
                                if (activeCustomColor != null) {
                                    Icon(
                                        imageVector = Icons.Rounded.Check,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(14.dp),
                                    )
                                }
                            }
                            Column {
                                Text(
                                    text = "自定义颜色",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.SemiBold,
                                    color = colorScheme.onSurface,
                                )
                                Text(
                                    text = if (activeCustomColor != null) {
                                        "已选用 ${CourseColorPalette.formatHex(activeCustomColor)} · 点击重新调色"
                                    } else {
                                        "支持输入 Hex 色值或滑块自由调色"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        Icon(
                            imageVector = Icons.Rounded.Palette,
                            contentDescription = "打开自定义调色板",
                            tint = colorScheme.primary,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                label = { Text("课程备注（可选）") },
                placeholder = { Text("教材、考核方式或实验要求...") },
                minLines = 2,
                maxLines = 3,
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
            )

            Spacer(modifier = Modifier.height(20.dp))

            Button(
                onClick = {
                    if (name.isBlank()) {
                        nameError = true
                        return@Button
                    }
                    if (selectedWeeks.isEmpty()) {
                        weekSelectionError = true
                        return@Button
                    }
                    val spec = CourseEntity.summarizeWeeks(selectedWeeks)
                    onSave(
                        editingCourse?.id ?: 0L,
                        name,
                        location,
                        teacher,
                        dayOfWeek,
                        startSection,
                        endSection,
                        spec.startWeek,
                        spec.endWeek,
                        spec.weekType,
                        colorIndex,
                        notes,
                        customColorHex,
                        spec.customWeeksCsv,
                    )
                    onDismiss()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .navigationBarsPadding()
                    .testTag("course_save_button"),
                shape = MaterialTheme.shapes.medium,
                colors = ButtonDefaults.buttonColors(
                    containerColor = colorScheme.primary,
                    contentColor = colorScheme.onPrimary,
                ),
            ) {
                Text(
                    text = "保存课程",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    if (showCustomColorDialog) {
        val seedColor = CourseColorPalette.parseHexColorOrNull(customColorHex)
            ?: CourseColorPalette.styleFor(colorIndex).lightAccent
        CustomColorPickerDialog(
            initialColor = seedColor,
            coursePreviewName = name.trim().ifEmpty { "高等数学" },
            coursePreviewLocation = location.trim().ifEmpty { "流-515实" },
            onDismiss = { showCustomColorDialog = false },
            onConfirm = { pickedHex ->
                customColorHex = pickedHex
                showCustomColorDialog = false
            },
        )
    }
}

@Composable
private fun PresetColorRow(
    styles: List<com.example.data.CourseColorStyle>,
    startIndex: Int,
    selectedColorIndex: Int,
    isDark: Boolean,
    onSelect: (Int) -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme
    val totalCount = CourseColorPalette.styles.size
    val normalizedSelected = if (selectedColorIndex >= 0) {
        ((selectedColorIndex % totalCount) + totalCount) % totalCount
    } else {
        -1
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        styles.forEachIndexed { localIdx, style ->
            val idx = startIndex + localIdx
            val swatch = if (isDark) style.darkAccent else style.lightAccent
            val selected = normalizedSelected == idx
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(swatch)
                    .border(
                        width = if (selected) 2.5.dp else 1.dp,
                        color = if (selected) colorScheme.onSurface else Color.Black.copy(alpha = 0.08f),
                        shape = CircleShape,
                    )
                    .clickable { onSelect(idx) }
                    .testTag("course_preset_color_$idx"),
                contentAlignment = Alignment.Center,
            ) {
                if (selected) {
                    Icon(
                        imageVector = Icons.Rounded.Check,
                        contentDescription = style.name,
                        tint = Color.White,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun CustomColorPickerDialog(
    initialColor: Color,
    coursePreviewName: String,
    coursePreviewLocation: String,
    onDismiss: () -> Unit,
    onConfirm: (hex: String) -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme
    val flowColors = FlowTheme.colors

    val initHsv = remember(initialColor) {
        val out = FloatArray(3)
        android.graphics.Color.colorToHSV(initialColor.toArgb(), out)
        out
    }
    var hue by remember { mutableFloatStateOf(initHsv[0]) }
    var saturation by remember { mutableFloatStateOf(initHsv[1].coerceIn(0.15f, 1f)) }
    var brightness by remember { mutableFloatStateOf(initHsv[2].coerceIn(0.25f, 0.95f)) }

    val currentColor = remember(hue, saturation, brightness) {
        Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, saturation, brightness)))
    }
    var hexInput by remember { mutableStateOf(CourseColorPalette.formatHex(initialColor)) }
    var hexError by remember { mutableStateOf(false) }

    val previewStyle = remember(currentColor) {
        CourseColorPalette.styleFromCustomHex(CourseColorPalette.formatHex(currentColor))
            ?: CourseColorPalette.styleFor(0)
    }

    fun syncSlidersToHex(newHue: Float, newSat: Float, newVal: Float) {
        hue = newHue
        saturation = newSat
        brightness = newVal
        val c = Color(android.graphics.Color.HSVToColor(floatArrayOf(newHue, newSat, newVal)))
        hexInput = CourseColorPalette.formatHex(c)
        hexError = false
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colorScheme.surface,
        title = {
            Text(
                text = "自定义课程配色",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // Live course card preview
                val cardBg = if (flowColors.isDark) previewStyle.darkBg else previewStyle.lightBg
                val cardAccent = if (flowColors.isDark) previewStyle.darkAccent else previewStyle.lightAccent
                val cardText = if (flowColors.isDark) previewStyle.darkText else previewStyle.lightText
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .clip(CircleShape)
                            .background(currentColor)
                            .border(2.dp, colorScheme.outlineVariant, CircleShape),
                    )
                    Surface(
                        modifier = Modifier.weight(1f),
                        shape = MaterialTheme.shapes.small,
                        color = cardBg,
                        border = BorderStroke(1.dp, cardAccent.copy(alpha = 0.45f)),
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            Text(
                                text = coursePreviewName,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = cardText,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = "@${coursePreviewLocation.removePrefix("@")}",
                                style = MaterialTheme.typography.labelSmall,
                                color = cardAccent,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }

                // Hex input
                OutlinedTextField(
                    value = hexInput,
                    onValueChange = { raw ->
                        hexInput = raw.uppercase().take(7)
                        val parsed = CourseColorPalette.parseHexColorOrNull(hexInput)
                        if (parsed != null) {
                            val hsv = FloatArray(3)
                            android.graphics.Color.colorToHSV(parsed.toArgb(), hsv)
                            hue = hsv[0]
                            saturation = hsv[1]
                            brightness = hsv[2]
                            hexError = false
                        } else {
                            hexError = true
                        }
                    },
                    label = { Text("Hex 色值") },
                    placeholder = { Text("#3B82F6") },
                    singleLine = true,
                    isError = hexError,
                    supportingText = {
                        Text(if (hexError) "请输入 6 位十六进制色值，如 #3B82F6" else "输入 #RRGGBB 或拖动下方滑块调色")
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("custom_color_hex_input"),
                    shape = MaterialTheme.shapes.medium,
                )

                // Hue slider
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text("色相", style = MaterialTheme.typography.labelMedium, color = colorScheme.onSurfaceVariant)
                        Text(
                            "${hue.toInt()}°",
                            fontFamily = MonoFontFamily,
                            style = MaterialTheme.typography.labelSmall,
                            color = colorScheme.onSurface,
                        )
                    }
                    Slider(
                        value = hue,
                        onValueChange = { syncSlidersToHex(it, saturation, brightness) },
                        valueRange = 0f..360f,
                        colors = SliderDefaults.colors(
                            thumbColor = currentColor,
                            activeTrackColor = currentColor,
                        ),
                    )
                }

                // Saturation slider
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text("饱和度", style = MaterialTheme.typography.labelMedium, color = colorScheme.onSurfaceVariant)
                        Text(
                            "${(saturation * 100).toInt()}%",
                            fontFamily = MonoFontFamily,
                            style = MaterialTheme.typography.labelSmall,
                            color = colorScheme.onSurface,
                        )
                    }
                    Slider(
                        value = saturation,
                        onValueChange = { syncSlidersToHex(hue, it, brightness) },
                        valueRange = 0.1f..1f,
                        colors = SliderDefaults.colors(
                            thumbColor = currentColor,
                            activeTrackColor = currentColor,
                        ),
                    )
                }

                // Brightness slider
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text("明度", style = MaterialTheme.typography.labelMedium, color = colorScheme.onSurfaceVariant)
                        Text(
                            "${(brightness * 100).toInt()}%",
                            fontFamily = MonoFontFamily,
                            style = MaterialTheme.typography.labelSmall,
                            color = colorScheme.onSurface,
                        )
                    }
                    Slider(
                        value = brightness,
                        onValueChange = { syncSlidersToHex(hue, saturation, it) },
                        valueRange = 0.25f..0.95f,
                        colors = SliderDefaults.colors(
                            thumbColor = currentColor,
                            activeTrackColor = currentColor,
                        ),
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val parsed = CourseColorPalette.parseHexColorOrNull(hexInput) ?: currentColor
                    onConfirm(CourseColorPalette.formatHex(parsed))
                },
                modifier = Modifier.testTag("custom_color_confirm_button"),
            ) {
                Text("应用颜色")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        },
    )
}

@Composable
private fun NumericStepperCard(
    label: String,
    valueText: String,
    onDecrement: () -> Unit,
    onIncrement: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colorScheme = MaterialTheme.colorScheme
    val flowColors = FlowTheme.colors

    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        color = flowColors.cardElevated,
        border = BorderStroke(1.dp, colorScheme.outlineVariant),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Surface(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .clickable(onClick = onDecrement),
                    shape = CircleShape,
                    color = colorScheme.surfaceContainerHigh,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = "−",
                            style = MaterialTheme.typography.titleMedium,
                            color = colorScheme.onSurface,
                        )
                    }
                }
                Text(
                    text = valueText,
                    fontFamily = MonoFontFamily,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = colorScheme.onSurface,
                )
                Surface(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .clickable(onClick = onIncrement),
                    shape = CircleShape,
                    color = colorScheme.surfaceContainerHigh,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = "+",
                            style = MaterialTheme.typography.titleMedium,
                            color = colorScheme.onSurface,
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun CourseDetailDialog(
    course: CourseEntity,
    sectionTimes: List<SectionTimeEntity>,
    displayedWeek: Int,
    canEdit: Boolean = false,
    onDismiss: () -> Unit,
    onEdit: (CourseEntity) -> Unit,
    onDelete: (CourseEntity) -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme
    val flowColors = FlowTheme.colors
    val style = course.resolvedColorStyle()
    val accent = if (flowColors.isDark) style.darkAccent else style.lightAccent
    val isInCurrentWeek = course.isInWeek(displayedWeek)

    val startTime = sectionTimes.find { it.sectionNumber == course.startSection }?.startLabel() ?: "--:--"
    val endTime = sectionTimes.find { it.sectionNumber == course.endSection }?.endLabel() ?: "--:--"

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colorScheme.surface,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(11.dp)
                        .clip(CircleShape)
                        .background(accent),
                )
                Text(
                    text = course.name,
                    style = MaterialTheme.typography.titleLarge,
                    color = colorScheme.onSurface,
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (!isInCurrentWeek) {
                    Surface(
                        shape = CircleShape,
                        color = colorScheme.surfaceContainerHigh,
                    ) {
                        Text(
                            text = "非第 $displayedWeek 周课程",
                            style = MaterialTheme.typography.labelMedium,
                            color = colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        )
                    }
                }

                DetailRow(
                    icon = Icons.Rounded.Schedule,
                    text = "${DateTimeUtils.dayOfWeekFullChinese(course.dayOfWeek)} 第 ${course.startSection}-${course.endSection} 节 · $startTime-$endTime（${course.weekRangeLabel()}）",
                )
                if (course.location.isNotBlank()) {
                    DetailRow(
                        icon = Icons.Rounded.LocationOn,
                        text = course.formattedLocation(),
                    )
                }
                if (course.teacher.isNotBlank()) {
                    DetailRow(
                        icon = Icons.Rounded.PersonOutline,
                        text = "授课教师：${course.teacher}",
                    )
                }
                if (course.notes.isNotBlank()) {
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = colorScheme.surfaceContainerLow,
                    ) {
                        Text(
                            text = course.notes,
                            style = MaterialTheme.typography.bodySmall,
                            color = colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(10.dp),
                        )
                    }
                }
                if (!canEdit) {
                    Text(
                        text = "当前为浏览模式，如需修改或删除请开启顶部「编辑课表」",
                        style = MaterialTheme.typography.labelSmall,
                        color = colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            if (canEdit) {
                TextButton(
                    onClick = {
                        onDismiss()
                        onEdit(course)
                    },
                    modifier = Modifier.testTag("course_detail_edit_button"),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Edit,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("编辑", color = colorScheme.primary)
                }
            } else {
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.testTag("course_detail_close_button"),
                ) {
                    Text("知道了", color = colorScheme.primary)
                }
            }
        },
        dismissButton = if (canEdit) {
            {
                TextButton(
                    onClick = {
                        onDismiss()
                        onDelete(course)
                    },
                    modifier = Modifier.testTag("course_detail_delete_button"),
                ) {
                    Text("删除", color = flowColors.deleteAccent)
                }
            }
        } else {
            null
        },
    )
}

@Composable
private fun DetailRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}
