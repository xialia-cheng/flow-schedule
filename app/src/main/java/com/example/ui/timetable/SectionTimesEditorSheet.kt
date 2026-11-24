package com.example.ui.timetable

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Dialpad
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Keyboard
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.BellBatchConfig
import com.example.data.BellScheduleGenerator
import com.example.data.CourseColorPalette
import com.example.data.SectionTimeEntity
import com.example.ui.components.rememberAppHaptics
import com.example.ui.theme.FlowTheme
import com.example.ui.theme.MonoFontFamily
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

private sealed interface ActiveTimeEditTarget {
    data class SectionStart(val sectionNumber: Int) : ActiveTimeEditTarget
    data class SectionEnd(val sectionNumber: Int) : ActiveTimeEditTarget
    data object BatchMorningStart : ActiveTimeEditTarget
    data object BatchAfternoonStart : ActiveTimeEditTarget
    data object BatchEveningStart : ActiveTimeEditTarget
}

/**
 * Full-featured, freely customizable class bell schedule editor:
 * 1. Custom daily total sections (6..16)
 * 2. Independent click-to-edit start & end time for every single section via numeric wheel/input picker
 * 3. Collapsible "批量生成作息" (batch generate) toolbar with custom class length, break length,
 *    and morning / afternoon / evening first-section start times
 * 4. Immediate persistence + timetable/alarm/Fluid Cloud synchronization on Save
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SectionTimesEditorSheet(
    initialTimes: List<SectionTimeEntity>,
    maxCourseSection: Int,
    onDismiss: () -> Unit,
    onSave: (List<SectionTimeEntity>) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val colorScheme = MaterialTheme.colorScheme
    val flowColors = FlowTheme.colors
    val haptics = rememberAppHaptics()

    val timetableId = remember(initialTimes) { initialTimes.firstOrNull()?.timetableId ?: 0L }
    var editableTimes by remember(initialTimes) {
        val base = if (initialTimes.isNotEmpty()) {
            BellScheduleGenerator.normalize(initialTimes, timetableId)
        } else {
            CourseColorPalette.defaultSectionTimes(timetableId)
        }
        mutableStateOf(base)
    }

    // Collapsible batch generator state
    var batchExpanded by remember { mutableStateOf(false) }
    var batchDurationText by remember { mutableStateOf("45") }
    var batchBreakText by remember { mutableStateOf("10") }
    val initialSplit = remember(editableTimes.size) {
        BellScheduleGenerator.defaultPeriodSplit(editableTimes.size)
    }
    var batchMorningCount by remember { mutableIntStateOf(initialSplit.first) }
    var batchAfternoonCount by remember { mutableIntStateOf(initialSplit.second) }

    var batchMorningStart by remember {
        val first = editableTimes.firstOrNull()
        mutableStateOf((first?.startHour ?: 8) to (first?.startMinute ?: 0))
    }
    var batchAfternoonStart by remember {
        val afternoon = editableTimes.firstOrNull { it.startHour in 12..17 }
        mutableStateOf((afternoon?.startHour ?: 14) to (afternoon?.startMinute ?: 0))
    }
    var batchEveningStart by remember {
        val evening = editableTimes.firstOrNull { it.startHour >= 18 }
        mutableStateOf((evening?.startHour ?: 19) to (evening?.startMinute ?: 0))
    }
    var batchStatusHint by remember { mutableStateOf<String?>(null) }

    var activeEditTarget by remember { mutableStateOf<ActiveTimeEditTarget?>(null) }

    val invalidSectionNumbers by remember(editableTimes) {
        derivedStateOf {
            editableTimes
                .filter { BellScheduleGenerator.durationMinutes(it) <= 0 }
                .map { it.sectionNumber }
        }
    }
    val canSave = invalidSectionNumbers.isEmpty() && editableTimes.size in
        BellScheduleGenerator.MIN_SECTION_COUNT..BellScheduleGenerator.MAX_SECTION_COUNT

    fun updateTotalSections(newCount: Int) {
        val clamped = newCount.coerceIn(
            BellScheduleGenerator.MIN_SECTION_COUNT,
            BellScheduleGenerator.MAX_SECTION_COUNT,
        )
        if (clamped == editableTimes.size) return
        val duration = batchDurationText.toIntOrNull()?.coerceIn(10, 180) ?: 45
        val breakMin = batchBreakText.toIntOrNull()?.coerceIn(0, 60) ?: 10
        editableTimes = BellScheduleGenerator.resize(
            existing = editableTimes,
            newCount = clamped,
            timetableId = timetableId,
            defaultDurationMinutes = duration,
            defaultBreakMinutes = breakMin,
        )
        val split = BellScheduleGenerator.defaultPeriodSplit(clamped)
        batchMorningCount = split.first
        batchAfternoonCount = split.second
        batchStatusHint = null
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = null,
        containerColor = colorScheme.surface,
        contentColor = colorScheme.onSurface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.92f)
                .navigationBarsPadding()
                .imePadding(),
        ) {
            // 1. Top Header Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 12.dp, end = 16.dp, top = 14.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    IconButton(
                        onClick = {
                            haptics.tick()
                            onDismiss()
                        },
                        modifier = Modifier
                            .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                            .testTag("section_times_close_button"),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "关闭上课时间设置",
                            tint = colorScheme.onSurfaceVariant,
                        )
                    }
                    Column {
                        Text(
                            text = "上课时间设置",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = "每日 ${editableTimes.size} 节 · 点击任意起止时间单独微调",
                            style = MaterialTheme.typography.bodySmall,
                            color = colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                Button(
                    onClick = {
                        haptics.confirm()
                        onSave(editableTimes)
                    },
                    enabled = canSave,
                    shape = CircleShape,
                    contentPadding = PaddingValues(horizontal = 18.dp, vertical = 10.dp),
                    modifier = Modifier
                        .defaultMinSize(minHeight = 48.dp)
                        .testTag("section_times_save_button"),
                ) {
                    Text(
                        text = "保存",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        softWrap = false,
                    )
                }
            }

            HorizontalDivider(thickness = 0.5.dp, color = colorScheme.outlineVariant)

            // 2. Scrollable Editor Content (Stepper + Collapsible Batch Generator + Per-Section List)
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // 2a. Total Daily Sections Stepper Card (6..16)
                item(key = "section_count_card") {
                    SectionCountStepperCard(
                        sectionCount = editableTimes.size,
                        maxCourseSection = maxCourseSection,
                        onDecrease = { updateTotalSections(editableTimes.size - 1) },
                        onIncrease = { updateTotalSections(editableTimes.size + 1) },
                    )
                }

                // 2b. Collapsible Batch Schedule Generator Toolbar
                item(key = "batch_generator_card") {
                    BatchGeneratorCard(
                        expanded = batchExpanded,
                        onToggleExpanded = { batchExpanded = !batchExpanded },
                        totalSections = editableTimes.size,
                        durationText = batchDurationText,
                        onDurationChange = { input ->
                            val filtered = input.filter { it.isDigit() }.take(3)
                            batchDurationText = filtered
                        },
                        breakText = batchBreakText,
                        onBreakChange = { input ->
                            val filtered = input.filter { it.isDigit() }.take(2)
                            batchBreakText = filtered
                        },
                        morningCount = batchMorningCount,
                        afternoonCount = batchAfternoonCount,
                        onMorningCountChange = { nextM ->
                            val clampedM = nextM.coerceIn(1, (editableTimes.size - 1).coerceAtLeast(1))
                            batchMorningCount = clampedM
                            if (batchMorningCount + batchAfternoonCount > editableTimes.size) {
                                batchAfternoonCount = (editableTimes.size - batchMorningCount).coerceAtLeast(0)
                            }
                        },
                        onAfternoonCountChange = { nextA ->
                            val maxA = (editableTimes.size - batchMorningCount).coerceAtLeast(0)
                            batchAfternoonCount = nextA.coerceIn(0, maxA)
                        },
                        morningStart = batchMorningStart,
                        afternoonStart = batchAfternoonStart,
                        eveningStart = batchEveningStart,
                        onEditMorningStart = { activeEditTarget = ActiveTimeEditTarget.BatchMorningStart },
                        onEditAfternoonStart = { activeEditTarget = ActiveTimeEditTarget.BatchAfternoonStart },
                        onEditEveningStart = { activeEditTarget = ActiveTimeEditTarget.BatchEveningStart },
                        statusHint = batchStatusHint,
                        onApplyBatch = {
                            val duration = (batchDurationText.toIntOrNull() ?: 45).coerceIn(10, 180)
                            val breakMin = (batchBreakText.toIntOrNull() ?: 10).coerceIn(0, 60)
                            batchDurationText = duration.toString()
                            batchBreakText = breakMin.toString()

                            val config = BellBatchConfig(
                                totalSections = editableTimes.size,
                                classDurationMinutes = duration,
                                breakDurationMinutes = breakMin,
                                morningCount = batchMorningCount,
                                afternoonCount = batchAfternoonCount,
                                morningStartHour = batchMorningStart.first,
                                morningStartMinute = batchMorningStart.second,
                                afternoonStartHour = batchAfternoonStart.first,
                                afternoonStartMinute = batchAfternoonStart.second,
                                eveningStartHour = batchEveningStart.first,
                                eveningStartMinute = batchEveningStart.second,
                            )
                            editableTimes = BellScheduleGenerator.generate(timetableId, config)
                            batchStatusHint = "已按 ${duration}分钟/节 · 课间${breakMin}分钟 推算全部 ${editableTimes.size} 节，下方仍可逐节微调"
                        },
                    )
                }

                if (invalidSectionNumbers.isNotEmpty()) {
                    item(key = "invalid_warning_banner") {
                        Surface(
                            shape = MaterialTheme.shapes.medium,
                            color = colorScheme.errorContainer,
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Info,
                                    contentDescription = null,
                                    tint = colorScheme.onErrorContainer,
                                    modifier = Modifier.size(18.dp),
                                )
                                Text(
                                    text = "第 ${invalidSectionNumbers.joinToString("、")} 节下课时间必须晚于上课时间，请调整后再保存",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colorScheme.onErrorContainer,
                                )
                            }
                        }
                    }
                }

                // 2c. Per-Section Independently Editable Rows with Period Headers & Break Connectors
                itemsIndexed(
                    items = editableTimes,
                    key = { _, slot -> "section_slot_${slot.sectionNumber}" },
                ) { index, slot ->
                    val currentPeriod = BellScheduleGenerator.periodLabelFor(slot)
                    val prevSlot = editableTimes.getOrNull(index - 1)
                    val prevPeriod = prevSlot?.let { BellScheduleGenerator.periodLabelFor(it) }

                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        // Show break interval connector between consecutive sections in same/different periods
                        if (prevSlot != null) {
                            val gapMin = BellScheduleGenerator.breakMinutesBetween(prevSlot, slot)
                            BreakConnectorChip(
                                gapMinutes = gapMin,
                                isPeriodBreak = prevPeriod != currentPeriod,
                                nextPeriodLabel = currentPeriod,
                            )
                        }

                        if (prevPeriod != currentPeriod) {
                            PeriodGroupHeader(label = currentPeriod)
                        }

                        SectionTimeEditableRow(
                            slot = slot,
                            onEditStart = {
                                activeEditTarget = ActiveTimeEditTarget.SectionStart(slot.sectionNumber)
                            },
                            onEditEnd = {
                                activeEditTarget = ActiveTimeEditTarget.SectionEnd(slot.sectionNumber)
                            },
                        )
                    }
                }

                item(key = "bottom_sync_note") {
                    Text(
                        text = "保存后立即同步课表左侧时间轴、已有课程起止时间，并重新计算所有精确闹钟与实况通知倒计时。",
                        style = MaterialTheme.typography.bodySmall,
                        color = colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp, bottom = 16.dp),
                    )
                }
            }
        }
    }

    // 3. Clean Numeric Wheel & Direct Input Time Picker Dialog
    activeEditTarget?.let { target ->
        val (dialogTitle, initialHour, initialMinute, minTotalMinutes) = when (target) {
            is ActiveTimeEditTarget.SectionStart -> {
                val slot = editableTimes.find { it.sectionNumber == target.sectionNumber }
                Quad4(
                    "第 ${target.sectionNumber} 节 · 上课时间",
                    slot?.startHour ?: 8,
                    slot?.startMinute ?: 0,
                    null,
                )
            }
            is ActiveTimeEditTarget.SectionEnd -> {
                val slot = editableTimes.find { it.sectionNumber == target.sectionNumber }
                val startTotal = slot?.let { it.startHour * 60 + it.startMinute }
                Quad4(
                    "第 ${target.sectionNumber} 节 · 下课时间",
                    slot?.endHour ?: 8,
                    slot?.endMinute ?: 45,
                    startTotal,
                )
            }
            ActiveTimeEditTarget.BatchMorningStart -> Quad4(
                "上午首节开始时间",
                batchMorningStart.first,
                batchMorningStart.second,
                null,
            )
            ActiveTimeEditTarget.BatchAfternoonStart -> Quad4(
                "下午首节开始时间",
                batchAfternoonStart.first,
                batchAfternoonStart.second,
                null,
            )
            ActiveTimeEditTarget.BatchEveningStart -> Quad4(
                "晚上首节开始时间",
                batchEveningStart.first,
                batchEveningStart.second,
                null,
            )
        }

        CompactWheelTimePickerDialog(
            title = dialogTitle,
            initialHour = initialHour,
            initialMinute = initialMinute,
            mustBeAfterTotalMinutes = minTotalMinutes,
            onDismiss = { activeEditTarget = null },
            onConfirm = { pickedHour, pickedMinute ->
                when (target) {
                    is ActiveTimeEditTarget.SectionStart -> {
                        editableTimes = editableTimes.map { item ->
                            if (item.sectionNumber == target.sectionNumber) {
                                val prevDur = BellScheduleGenerator.durationMinutes(item).coerceIn(15, 180)
                                val newStart = pickedHour * 60 + pickedMinute
                                val oldEnd = item.endHour * 60 + item.endMinute
                                if (oldEnd <= newStart) {
                                    // Keep valid range if new start passes old end
                                    val shiftedEnd = (newStart + prevDur).coerceAtMost(23 * 60 + 59)
                                    item.copy(
                                        startHour = pickedHour,
                                        startMinute = pickedMinute,
                                        endHour = shiftedEnd / 60,
                                        endMinute = shiftedEnd % 60,
                                    )
                                } else {
                                    item.copy(
                                        startHour = pickedHour,
                                        startMinute = pickedMinute,
                                    )
                                }
                            } else {
                                item
                            }
                        }
                    }
                    is ActiveTimeEditTarget.SectionEnd -> {
                        editableTimes = editableTimes.map { item ->
                            if (item.sectionNumber == target.sectionNumber) {
                                item.copy(endHour = pickedHour, endMinute = pickedMinute)
                            } else {
                                item
                            }
                        }
                    }
                    ActiveTimeEditTarget.BatchMorningStart -> {
                        batchMorningStart = pickedHour to pickedMinute
                    }
                    ActiveTimeEditTarget.BatchAfternoonStart -> {
                        batchAfternoonStart = pickedHour to pickedMinute
                    }
                    ActiveTimeEditTarget.BatchEveningStart -> {
                        batchEveningStart = pickedHour to pickedMinute
                    }
                }
                activeEditTarget = null
            },
        )
    }
}

private data class Quad4<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)

@Composable
private fun SectionCountStepperCard(
    sectionCount: Int,
    maxCourseSection: Int,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme
    val flowColors = FlowTheme.colors
    val haptics = rememberAppHaptics()

    Surface(
        shape = MaterialTheme.shapes.large,
        color = flowColors.cardElevated,
        border = BorderStroke(1.dp, colorScheme.outlineVariant),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "每日总节数",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = colorScheme.onSurface,
                    )
                    Text(
                        text = "自由设置 ${BellScheduleGenerator.MIN_SECTION_COUNT}–${BellScheduleGenerator.MAX_SECTION_COUNT} 节，适配不同课表",
                        style = MaterialTheme.typography.bodySmall,
                        color = colorScheme.onSurfaceVariant,
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    val canDec = sectionCount > BellScheduleGenerator.MIN_SECTION_COUNT
                    val canInc = sectionCount < BellScheduleGenerator.MAX_SECTION_COUNT

                    Surface(
                        shape = CircleShape,
                        color = if (canDec) colorScheme.primaryContainer else colorScheme.surfaceContainerHigh,
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .clickable(enabled = canDec) {
                                haptics.tick()
                                onDecrease()
                            }
                            .testTag("section_count_decrease"),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Rounded.Remove,
                                contentDescription = "减少每日节数",
                                tint = if (canDec) {
                                    colorScheme.onPrimaryContainer
                                } else {
                                    colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                                },
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }

                    Text(
                        text = "$sectionCount 节",
                        fontFamily = MonoFontFamily,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = colorScheme.onSurface,
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier
                            .width(58.dp)
                            .testTag("section_count_value"),
                        textAlign = TextAlign.Center,
                    )

                    Surface(
                        shape = CircleShape,
                        color = if (canInc) colorScheme.primaryContainer else colorScheme.surfaceContainerHigh,
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .clickable(enabled = canInc) {
                                haptics.tick()
                                onIncrease()
                            }
                            .testTag("section_count_increase"),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Rounded.Add,
                                contentDescription = "增加每日节数",
                                tint = if (canInc) {
                                    colorScheme.onPrimaryContainer
                                } else {
                                    colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                                },
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                }
            }

            if (maxCourseSection > sectionCount) {
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = colorScheme.tertiaryContainer.copy(alpha = 0.7f),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Info,
                            contentDescription = null,
                            tint = colorScheme.onTertiaryContainer,
                            modifier = Modifier.size(15.dp),
                        )
                        Text(
                            text = "当前课表已有排到第 $maxCourseSection 节的课程，减少节数后超出部分将暂不显示",
                            style = MaterialTheme.typography.labelSmall,
                            color = colorScheme.onTertiaryContainer,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BatchGeneratorCard(
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    totalSections: Int,
    durationText: String,
    onDurationChange: (String) -> Unit,
    breakText: String,
    onBreakChange: (String) -> Unit,
    morningCount: Int,
    afternoonCount: Int,
    onMorningCountChange: (Int) -> Unit,
    onAfternoonCountChange: (Int) -> Unit,
    morningStart: Pair<Int, Int>,
    afternoonStart: Pair<Int, Int>,
    eveningStart: Pair<Int, Int>,
    onEditMorningStart: () -> Unit,
    onEditAfternoonStart: () -> Unit,
    onEditEveningStart: () -> Unit,
    statusHint: String?,
    onApplyBatch: () -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme
    val flowColors = FlowTheme.colors
    val haptics = rememberAppHaptics()
    val eveningCount = (totalSections - morningCount - afternoonCount).coerceAtLeast(0)

    Surface(
        shape = MaterialTheme.shapes.large,
        color = colorScheme.primaryContainer.copy(alpha = if (flowColors.isDark) 0.28f else 0.34f),
        border = BorderStroke(1.dp, colorScheme.primary.copy(alpha = 0.28f)),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Collapsible header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 52.dp)
                    .clickable {
                        haptics.tick()
                        onToggleExpanded()
                    }
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .testTag("batch_generator_toggle"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(colorScheme.primary.copy(alpha = 0.16f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.AutoFixHigh,
                            contentDescription = null,
                            tint = colorScheme.primary,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                    Column {
                        Text(
                            text = "批量生成作息",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = colorScheme.onSurface,
                        )
                        Text(
                            text = if (expanded) {
                                "自定义课时、课间及三时段首节时间后一键推算"
                            } else {
                                "点击展开 · 自定义课时/课间/三时段首节时间一键生成"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Icon(
                    imageVector = if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                    contentDescription = if (expanded) "收起批量生成工具栏" else "展开批量生成工具栏",
                    tint = colorScheme.primary,
                )
            }

            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut(),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, bottom = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    HorizontalDivider(thickness = 0.5.dp, color = colorScheme.primary.copy(alpha = 0.18f))

                    // Class duration + default break inputs
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        OutlinedTextField(
                            value = durationText,
                            onValueChange = onDurationChange,
                            label = {
                                Text(
                                    text = "单节时长(分)",
                                    maxLines = 1,
                                    softWrap = false,
                                )
                            },
                            placeholder = { Text("如 40 或 45") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            shape = MaterialTheme.shapes.medium,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("batch_class_duration_input"),
                        )
                        OutlinedTextField(
                            value = breakText,
                            onValueChange = onBreakChange,
                            label = {
                                Text(
                                    text = "默认课间(分)",
                                    maxLines = 1,
                                    softWrap = false,
                                )
                            },
                            placeholder = { Text("如 10") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            shape = MaterialTheme.shapes.medium,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("batch_break_duration_input"),
                        )
                    }

                    // Period section count distribution (Morning / Afternoon / Evening)
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "各时段节数分配（共 $totalSections 节）",
                            style = MaterialTheme.typography.labelMedium,
                            color = colorScheme.onSurfaceVariant,
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            PeriodCountChip(
                                label = "上午",
                                count = morningCount,
                                canDecrease = morningCount > 1,
                                canIncrease = morningCount < totalSections - 1,
                                onDecrease = { onMorningCountChange(morningCount - 1) },
                                onIncrease = { onMorningCountChange(morningCount + 1) },
                                modifier = Modifier.weight(1f),
                            )
                            PeriodCountChip(
                                label = "下午",
                                count = afternoonCount,
                                canDecrease = afternoonCount > 0,
                                canIncrease = morningCount + afternoonCount < totalSections,
                                onDecrease = { onAfternoonCountChange(afternoonCount - 1) },
                                onIncrease = { onAfternoonCountChange(afternoonCount + 1) },
                                modifier = Modifier.weight(1f),
                            )
                            Surface(
                                shape = MaterialTheme.shapes.medium,
                                color = colorScheme.surface.copy(alpha = 0.72f),
                                border = BorderStroke(1.dp, colorScheme.outlineVariant),
                                modifier = Modifier.weight(0.82f),
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 8.dp, horizontal = 6.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                ) {
                                    Text(
                                        text = "晚上",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        softWrap = false,
                                    )
                                    Text(
                                        text = "${eveningCount}节",
                                        fontFamily = MonoFontFamily,
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = if (eveningCount > 0) colorScheme.onSurface else colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        softWrap = false,
                                    )
                                }
                            }
                        }
                    }

                    // Morning / Afternoon / Evening first-section start time buttons
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "各时段首节开始时间（点击修改）",
                            style = MaterialTheme.typography.labelMedium,
                            color = colorScheme.onSurfaceVariant,
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            PeriodStartTimeButton(
                                label = "上午首节",
                                timeLabel = BellScheduleGenerator.formatTime(morningStart.first, morningStart.second),
                                enabled = morningCount > 0,
                                onClick = onEditMorningStart,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("batch_morning_start_button"),
                            )
                            PeriodStartTimeButton(
                                label = "下午首节",
                                timeLabel = BellScheduleGenerator.formatTime(afternoonStart.first, afternoonStart.second),
                                enabled = afternoonCount > 0,
                                onClick = onEditAfternoonStart,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("batch_afternoon_start_button"),
                            )
                            PeriodStartTimeButton(
                                label = "晚上首节",
                                timeLabel = BellScheduleGenerator.formatTime(eveningStart.first, eveningStart.second),
                                enabled = eveningCount > 0,
                                onClick = onEditEveningStart,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("batch_evening_start_button"),
                            )
                        }
                    }

                    FilledTonalButton(
                        onClick = {
                            haptics.tick()
                            onApplyBatch()
                        },
                        shape = MaterialTheme.shapes.medium,
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = colorScheme.primary,
                            contentColor = colorScheme.onPrimary,
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = 48.dp)
                            .testTag("batch_generate_apply_button"),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.AutoFixHigh,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "一键生成全部 $totalSections 节作息",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            softWrap = false,
                        )
                    }

                    if (!statusHint.isNullOrBlank()) {
                        Text(
                            text = statusHint,
                            style = MaterialTheme.typography.labelSmall,
                            color = colorScheme.primary,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PeriodCountChip(
    label: String,
    count: Int,
    canDecrease: Boolean,
    canIncrease: Boolean,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colorScheme = MaterialTheme.colorScheme
    val haptics = rememberAppHaptics()
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = colorScheme.surface.copy(alpha = 0.85f),
        border = BorderStroke(1.dp, colorScheme.outlineVariant),
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp, horizontal = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = colorScheme.onSurfaceVariant,
                maxLines = 1,
                softWrap = false,
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(colorScheme.surfaceContainerHigh)
                        .clickable(enabled = canDecrease) {
                            haptics.tick()
                            onDecrease()
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "−",
                        fontWeight = FontWeight.Bold,
                        color = if (canDecrease) colorScheme.onSurface else colorScheme.onSurfaceVariant.copy(alpha = 0.35f),
                    )
                }
                Text(
                    text = "${count}节",
                    fontFamily = MonoFontFamily,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = colorScheme.onSurface,
                    maxLines = 1,
                    softWrap = false,
                )
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(colorScheme.surfaceContainerHigh)
                        .clickable(enabled = canIncrease) {
                            haptics.tick()
                            onIncrease()
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "+",
                        fontWeight = FontWeight.Bold,
                        color = if (canIncrease) colorScheme.onSurface else colorScheme.onSurfaceVariant.copy(alpha = 0.35f),
                    )
                }
            }
        }
    }
}

@Composable
private fun PeriodStartTimeButton(
    label: String,
    timeLabel: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colorScheme = MaterialTheme.colorScheme
    val haptics = rememberAppHaptics()
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = if (enabled) colorScheme.surface else colorScheme.surfaceContainerLow,
        border = BorderStroke(
            width = 1.dp,
            color = if (enabled) colorScheme.primary.copy(alpha = 0.35f) else colorScheme.outlineVariant,
        ),
        modifier = modifier
            .defaultMinSize(minHeight = 52.dp)
            .clip(MaterialTheme.shapes.medium)
            .clickable(enabled = enabled) {
                haptics.tick()
                onClick()
            }
            .alpha(if (enabled) 1f else 0.5f),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = colorScheme.onSurfaceVariant,
                maxLines = 1,
                softWrap = false,
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = if (enabled) timeLabel else "无课",
                fontFamily = MonoFontFamily,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = if (enabled) colorScheme.primary else colorScheme.onSurfaceVariant,
                maxLines = 1,
                softWrap = false,
            )
        }
    }
}

@Composable
private fun PeriodGroupHeader(label: String) {
    val colorScheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = colorScheme.primary,
        )
        HorizontalDivider(
            modifier = Modifier.weight(1f),
            thickness = 0.5.dp,
            color = colorScheme.outlineVariant,
        )
    }
}

@Composable
private fun BreakConnectorChip(
    gapMinutes: Int,
    isPeriodBreak: Boolean,
    nextPeriodLabel: String,
) {
    val colorScheme = MaterialTheme.colorScheme
    val isOverlap = gapMinutes < 0
    val label = when {
        isOverlap -> "与上节重叠 ${-gapMinutes} 分钟"
        gapMinutes == 0 -> "连堂（无课间）"
        isPeriodBreak && nextPeriodLabel == "下午" -> "午休 ${BellScheduleGenerator.formatDurationZh(gapMinutes)}"
        isPeriodBreak && nextPeriodLabel == "晚上" -> "晚间休息 ${BellScheduleGenerator.formatDurationZh(gapMinutes)}"
        else -> "课间 ${BellScheduleGenerator.formatDurationZh(gapMinutes)}"
    }

    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            shape = CircleShape,
            color = when {
                isOverlap -> colorScheme.errorContainer
                isPeriodBreak -> colorScheme.secondaryContainer.copy(alpha = 0.65f)
                else -> colorScheme.surfaceContainerLow
            },
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = when {
                    isOverlap -> colorScheme.onErrorContainer
                    isPeriodBreak -> colorScheme.onSecondaryContainer
                    else -> colorScheme.onSurfaceVariant
                },
                maxLines = 1,
                softWrap = false,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 2.dp),
            )
        }
    }
}

@Composable
private fun SectionTimeEditableRow(
    slot: SectionTimeEntity,
    onEditStart: () -> Unit,
    onEditEnd: () -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme
    val flowColors = FlowTheme.colors
    val duration = BellScheduleGenerator.durationMinutes(slot)
    val isInvalid = duration <= 0

    Surface(
        shape = MaterialTheme.shapes.medium,
        color = if (isInvalid) {
            colorScheme.errorContainer.copy(alpha = 0.35f)
        } else {
            flowColors.cardElevated
        },
        border = BorderStroke(
            width = 1.dp,
            color = if (isInvalid) colorScheme.error else colorScheme.outlineVariant,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("section_row_${slot.sectionNumber}"),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            // Left: Section badge + duration summary
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f),
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(
                            if (isInvalid) colorScheme.errorContainer else colorScheme.primaryContainer,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "${slot.sectionNumber}",
                        fontFamily = MonoFontFamily,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = if (isInvalid) colorScheme.onErrorContainer else colorScheme.onPrimaryContainer,
                        maxLines = 1,
                        softWrap = false,
                    )
                }
                Column {
                    Text(
                        text = "第 ${slot.sectionNumber} 节",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = colorScheme.onSurface,
                        maxLines = 1,
                        softWrap = false,
                    )
                    Text(
                        text = if (isInvalid) "时间无效" else BellScheduleGenerator.formatDurationZh(duration),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isInvalid) colorScheme.error else colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        softWrap = false,
                    )
                }
            }

            // Right: Independently clickable Start & End time chips (48dp comfortable height)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                EditableTimeChip(
                    label = slot.startLabel(),
                    contentDescription = "编辑第 ${slot.sectionNumber} 节上课时间 ${slot.startLabel()}",
                    isError = isInvalid,
                    onClick = onEditStart,
                    modifier = Modifier.testTag("section_start_${slot.sectionNumber}"),
                )
                Text(
                    text = "—",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colorScheme.onSurfaceVariant,
                )
                EditableTimeChip(
                    label = slot.endLabel(),
                    contentDescription = "编辑第 ${slot.sectionNumber} 节下课时间 ${slot.endLabel()}",
                    isError = isInvalid,
                    onClick = onEditEnd,
                    modifier = Modifier.testTag("section_end_${slot.sectionNumber}"),
                )
            }
        }
    }
}

@Composable
private fun EditableTimeChip(
    label: String,
    contentDescription: String,
    isError: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colorScheme = MaterialTheme.colorScheme
    val haptics = rememberAppHaptics()
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (isError) colorScheme.errorContainer else colorScheme.surfaceContainerHigh,
        border = BorderStroke(
            width = 1.dp,
            color = if (isError) colorScheme.error else colorScheme.primary.copy(alpha = 0.28f),
        ),
        modifier = modifier
            .defaultMinSize(minWidth = 76.dp, minHeight = 44.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable {
                haptics.tick()
                onClick()
            },
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Text(
                text = label,
                fontFamily = MonoFontFamily,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = if (isError) colorScheme.onErrorContainer else colorScheme.primary,
                maxLines = 1,
                softWrap = false,
            )
        }
    }
}

/**
 * Clean, fast numeric wheel + direct keyboard input time picker dialog (24-hour HH:MM).
 * Users can flick-scroll the smooth Hour/Minute wheels, tap quick ±5m assists,
 * or toggle to direct numeric keyboard entry.
 */
@Composable
fun CompactWheelTimePickerDialog(
    title: String,
    initialHour: Int,
    initialMinute: Int,
    mustBeAfterTotalMinutes: Int? = null,
    onDismiss: () -> Unit,
    onConfirm: (hour: Int, minute: Int) -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme
    val flowColors = FlowTheme.colors
    val haptics = rememberAppHaptics()

    var selectedHour by remember(initialHour) { mutableIntStateOf(initialHour.coerceIn(0, 23)) }
    var selectedMinute by remember(initialMinute) { mutableIntStateOf(initialMinute.coerceIn(0, 59)) }
    var useKeyboardMode by remember { mutableStateOf(false) }

    var hourText by remember(initialHour) { mutableStateOf(String.format("%02d", initialHour.coerceIn(0, 23))) }
    var minuteText by remember(initialMinute) { mutableStateOf(String.format("%02d", initialMinute.coerceIn(0, 59))) }

    fun applyTotalMinutes(total: Int) {
        val clamped = total.coerceIn(0, 23 * 60 + 59)
        selectedHour = clamped / 60
        selectedMinute = clamped % 60
        hourText = String.format("%02d", selectedHour)
        minuteText = String.format("%02d", selectedMinute)
    }

    val currentTotal = selectedHour * 60 + selectedMinute
    val isValid = mustBeAfterTotalMinutes == null || currentTotal > mustBeAfterTotalMinutes

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = true),
    ) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = colorScheme.surface,
            tonalElevation = 6.dp,
            border = BorderStroke(1.dp, colorScheme.outlineVariant),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("compact_time_picker_dialog"),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                // Header row with mode switcher (Wheel <-> Keyboard)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Schedule,
                            contentDescription = null,
                            tint = colorScheme.primary,
                            modifier = Modifier.size(20.dp),
                        )
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    IconButton(
                        onClick = {
                            haptics.tick()
                            if (!useKeyboardMode) {
                                hourText = String.format("%02d", selectedHour)
                                minuteText = String.format("%02d", selectedMinute)
                            }
                            useKeyboardMode = !useKeyboardMode
                        },
                        modifier = Modifier
                            .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                            .testTag("time_picker_toggle_input_mode"),
                    ) {
                        Icon(
                            imageVector = if (useKeyboardMode) Icons.Rounded.Dialpad else Icons.Rounded.Keyboard,
                            contentDescription = if (useKeyboardMode) "切换至数字滚轮" else "切换至键盘输入",
                            tint = colorScheme.primary,
                        )
                    }
                }

                // Live big formatted time display
                Surface(
                    shape = MaterialTheme.shapes.large,
                    color = flowColors.cardElevated,
                    border = BorderStroke(
                        1.dp,
                        if (isValid) colorScheme.primary.copy(alpha = 0.25f) else colorScheme.error,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = BellScheduleGenerator.formatTime(selectedHour, selectedMinute),
                        fontFamily = MonoFontFamily,
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isValid) colorScheme.primary else colorScheme.error,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 10.dp),
                    )
                }

                if (!useKeyboardMode) {
                    // Two-column smooth numeric wheel (Hours 00..23 | Minutes 00..59)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(
                                text = "时",
                                style = MaterialTheme.typography.labelMedium,
                                color = colorScheme.onSurfaceVariant,
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            NumericWheelPicker(
                                range = 0..23,
                                selectedValue = selectedHour,
                                onValueSelected = { h ->
                                    selectedHour = h
                                    hourText = String.format("%02d", h)
                                },
                                modifier = Modifier.testTag("wheel_picker_hour"),
                            )
                        }

                        Text(
                            text = ":",
                            fontFamily = MonoFontFamily,
                            fontSize = 26.sp,
                            fontWeight = FontWeight.Bold,
                            color = colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 20.dp, start = 6.dp, end = 6.dp),
                        )

                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(
                                text = "分",
                                style = MaterialTheme.typography.labelMedium,
                                color = colorScheme.onSurfaceVariant,
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            NumericWheelPicker(
                                range = 0..59,
                                selectedValue = selectedMinute,
                                onValueSelected = { m ->
                                    selectedMinute = m
                                    minuteText = String.format("%02d", m)
                                },
                                modifier = Modifier.testTag("wheel_picker_minute"),
                            )
                        }
                    }
                } else {
                    // Direct keyboard numeric entry
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        OutlinedTextField(
                            value = hourText,
                            onValueChange = { raw ->
                                val digits = raw.filter { it.isDigit() }.take(2)
                                hourText = digits
                                digits.toIntOrNull()?.let { parsed ->
                                    if (parsed in 0..23) selectedHour = parsed
                                }
                            },
                            label = { Text("小时 (0-23)") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            shape = MaterialTheme.shapes.medium,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("input_picker_hour"),
                        )
                        Text(
                            text = ":",
                            fontFamily = MonoFontFamily,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            color = colorScheme.onSurfaceVariant,
                        )
                        OutlinedTextField(
                            value = minuteText,
                            onValueChange = { raw ->
                                val digits = raw.filter { it.isDigit() }.take(2)
                                minuteText = digits
                                digits.toIntOrNull()?.let { parsed ->
                                    if (parsed in 0..59) selectedMinute = parsed
                                }
                            },
                            label = { Text("分钟 (0-59)") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            shape = MaterialTheme.shapes.medium,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("input_picker_minute"),
                        )
                    }
                }

                // Quick ±5 / ±10 minute assist pills for rapid fine-tuning
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    listOf(-10 to "-10分", -5 to "-5分", 5 to "+5分", 10 to "+10分").forEach { (delta, label) ->
                        OutlinedButton(
                            onClick = {
                                haptics.tick()
                                applyTotalMinutes(currentTotal + delta)
                            },
                            shape = CircleShape,
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 6.dp),
                            modifier = Modifier
                                .weight(1f)
                                .defaultMinSize(minHeight = 40.dp),
                        ) {
                            Text(
                                text = label,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                softWrap = false,
                            )
                        }
                    }
                }

                if (!isValid && mustBeAfterTotalMinutes != null) {
                    Text(
                        text = "下课时间需晚于上课时间（${BellScheduleGenerator.formatTime(mustBeAfterTotalMinutes / 60, mustBeAfterTotalMinutes % 60)}）",
                        style = MaterialTheme.typography.labelSmall,
                        color = colorScheme.error,
                    )
                }

                // Confirm / Cancel actions
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(
                        onClick = {
                            haptics.tick()
                            onDismiss()
                        },
                        modifier = Modifier.defaultMinSize(minHeight = 48.dp),
                    ) {
                        Text("取消")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            haptics.confirm()
                            onConfirm(selectedHour, selectedMinute)
                        },
                        enabled = isValid,
                        shape = CircleShape,
                        contentPadding = PaddingValues(horizontal = 22.dp, vertical = 10.dp),
                        modifier = Modifier
                            .defaultMinSize(minHeight = 48.dp)
                            .testTag("time_picker_confirm_button"),
                    ) {
                        Text("确定", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun NumericWheelPicker(
    range: IntRange,
    selectedValue: Int,
    onValueSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colorScheme = MaterialTheme.colorScheme
    val haptics = rememberAppHaptics()
    val values = remember(range) { range.toList() }
    val itemHeight = 40.dp
    val visibleCount = 3
    val initialIndex = (selectedValue - range.first).coerceIn(0, values.lastIndex)
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = initialIndex)
    val flingBehavior = rememberSnapFlingBehavior(lazyListState = listState)
    val coroutineScope = rememberCoroutineScope()

    val latestSelectedValue by rememberUpdatedState(selectedValue)
    var isProgrammaticScroll by remember { mutableStateOf(false) }

    // Sync external ±5m / ±10m updates to wheel scroll position without fighting snapshotFlow
    LaunchedEffect(selectedValue) {
        val targetIndex = (selectedValue - range.first).coerceIn(0, values.lastIndex)
        if (listState.firstVisibleItemIndex != targetIndex || listState.firstVisibleItemScrollOffset != 0) {
            isProgrammaticScroll = true
            try {
                listState.animateScrollToItem(targetIndex)
            } finally {
                isProgrammaticScroll = false
            }
        }
    }

    // Report user-scrolled center item back to caller
    LaunchedEffect(listState) {
        snapshotFlow {
            if (isProgrammaticScroll) return@snapshotFlow null
            val offsetThreshold = 20
            val idx = if (listState.firstVisibleItemScrollOffset > offsetThreshold) {
                listState.firstVisibleItemIndex + 1
            } else {
                listState.firstVisibleItemIndex
            }
            idx.coerceIn(0, values.lastIndex)
        }
            .distinctUntilChanged()
            .collect { centeredIdx ->
                if (centeredIdx == null) return@collect
                val value = values[centeredIdx]
                if (value != latestSelectedValue) {
                    haptics.tick()
                    onValueSelected(value)
                }
            }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(itemHeight * visibleCount),
        contentAlignment = Alignment.Center,
    ) {
        // Highlighted center selection slot
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = colorScheme.primaryContainer.copy(alpha = 0.55f),
            border = BorderStroke(1.dp, colorScheme.primary.copy(alpha = 0.35f)),
            modifier = Modifier
                .fillMaxWidth()
                .height(itemHeight),
        ) {}

        LazyColumn(
            state = listState,
            flingBehavior = flingBehavior,
            contentPadding = PaddingValues(vertical = itemHeight),
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth(),
        ) {
            itemsIndexed(values, key = { _, v -> v }) { index, value ->
                val isSelected = value == selectedValue
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(itemHeight)
                        .clip(RoundedCornerShape(10.dp))
                        .clickable {
                            haptics.tick()
                            onValueSelected(value)
                            coroutineScope.launch {
                                listState.animateScrollToItem(index)
                            }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = String.format("%02d", value),
                        fontFamily = MonoFontFamily,
                        fontSize = if (isSelected) 22.sp else 16.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        color = if (isSelected) {
                            colorScheme.primary
                        } else {
                            colorScheme.onSurfaceVariant.copy(alpha = 0.52f)
                        },
                        maxLines = 1,
                        softWrap = false,
                    )
                }
            }
        }
    }
}
