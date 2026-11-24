package com.example.ui.study

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CloudQueue
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.DoNotDisturbOn
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.StudyTaskEntity
import com.example.ui.components.rememberAppHaptics
import com.example.ui.theme.FlowMotion
import com.example.ui.theme.FlowTheme
import com.example.ui.theme.MonoFontFamily
import java.util.Locale

@Composable
fun StudyScreen(
    viewModel: StudyViewModel,
    onShowMessage: (String) -> Unit,
    onOpenDndPolicySettings: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val toastMessage by viewModel.toastMessage.collectAsStateWithLifecycle()
    val colorScheme = MaterialTheme.colorScheme
    val flowColors = FlowTheme.colors
    val haptics = rememberAppHaptics()

    var showAddTaskDialog by remember { mutableStateOf(false) }
    var showCustomFocusDialog by remember { mutableStateOf(false) }

    LaunchedEffect(toastMessage) {
        val msg = toastMessage ?: return@LaunchedEffect
        onShowMessage(msg)
        viewModel.consumeToast()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .testTag("study_screen"),
        contentAlignment = Alignment.TopCenter,
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 640.dp),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item(key = "study_header") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Text(
                            text = "学习专注",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = colorScheme.onBackground,
                            maxLines = 1,
                            softWrap = false,
                        )
                        Text(
                            text = "自定义专注/休息时长 · 支持实况通知与免打扰联动",
                            style = MaterialTheme.typography.bodySmall,
                            color = colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Surface(
                        shape = CircleShape,
                        color = colorScheme.primaryContainer,
                        modifier = Modifier.testTag("study_total_focus_pill"),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Timer,
                                contentDescription = null,
                                tint = colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(15.dp),
                            )
                            Text(
                                text = "累计 ${state.totalFocusMinutes} 分钟",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = colorScheme.onPrimaryContainer,
                                maxLines = 1,
                                softWrap = false,
                            )
                        }
                    }
                }
            }

            item(key = "pomodoro_card") {
                PomodoroFocusCard(
                    state = state,
                    onSelectSubject = { viewModel.selectFocusSubject(it) },
                    onSelectPreset = { viewModel.selectPresetMinutes(it) },
                    onSwitchPhase = { viewModel.switchPhase(it) },
                    onToggleLinkFluidCloud = { viewModel.setLinkFluidCloud(it) },
                    onToggleLinkDnd = { viewModel.setLinkDnd(it) },
                    onOpenCustomConfig = { showCustomFocusDialog = true },
                    onOpenDndPolicySettings = onOpenDndPolicySettings,
                    onToggleTimer = { viewModel.toggleTimer() },
                    onResetTimer = { viewModel.resetTimer() },
                )
            }

            item(key = "tasks_header") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    val completedCount = state.tasks.count { it.isCompleted }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "学习任务清单",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = colorScheme.onBackground,
                            maxLines = 1,
                            softWrap = false,
                        )
                        Text(
                            text = if (state.tasks.isEmpty()) {
                                "按课程拆分复习与作业计划"
                            } else {
                                "已完成 $completedCount / ${state.tasks.size} 项"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }

                    FilledTonalButton(
                        onClick = {
                            haptics.tick()
                            showAddTaskDialog = true
                        },
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("study_add_task_button"),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Add,
                            contentDescription = "添加学习任务",
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "添加任务",
                            maxLines = 1,
                            softWrap = false,
                        )
                    }
                }
            }

            if (state.tasks.isEmpty()) {
                item(key = "empty_study_tasks") {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.large,
                        color = flowColors.cardElevated,
                        border = BorderStroke(1.dp, colorScheme.outlineVariant),
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = 24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text(
                                text = "还没有学习任务",
                                style = MaterialTheme.typography.titleSmall,
                                color = colorScheme.onSurface,
                            )
                            Text(
                                text = "可关联当前课表中的课程，记录预习、作业或考前复习计划",
                                style = MaterialTheme.typography.bodySmall,
                                color = colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            } else {
                items(
                    items = state.tasks,
                    key = { it.id },
                ) { task ->
                    StudyTaskRowCard(
                        task = task,
                        onToggle = { viewModel.toggleTask(task) },
                        onDelete = {
                            viewModel.deleteTask(task)
                            onShowMessage("已删除任务「${task.title}」")
                        },
                        modifier = Modifier.animateItem(),
                    )
                }
            }

            item(key = "bottom_spacer") {
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }

    if (showAddTaskDialog) {
        AddStudyTaskDialog(
            courseNames = state.courseNames,
            onDismiss = { showAddTaskDialog = false },
            onSave = { title, courseTag, minutes ->
                viewModel.addTask(title, courseTag, minutes)
                showAddTaskDialog = false
                onShowMessage("已添加学习任务「$title」")
            },
        )
    }

    if (showCustomFocusDialog) {
        CustomFocusConfigDialog(
            initialFocusMinutes = state.selectedPresetMinutes,
            initialBreakMinutes = state.breakDurationMinutes,
            initialEventName = state.activeFocusLabel,
            initialLinkFluidCloud = state.linkFluidCloud,
            initialLinkDnd = state.linkDnd,
            dndLevelLabel = state.dndLevel.label,
            isRunning = state.isTimerRunning,
            onDismiss = { showCustomFocusDialog = false },
            onConfirm = { focusMin, breakMin, eventName, linkCloud, linkDnd ->
                viewModel.applyCustomFocusConfig(
                    focusMinutes = focusMin,
                    breakMinutes = breakMin,
                    eventName = eventName,
                    linkFluidCloud = linkCloud,
                    linkDnd = linkDnd,
                )
                showCustomFocusDialog = false
            },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PomodoroFocusCard(
    state: StudyUiState,
    onSelectSubject: (String) -> Unit,
    onSelectPreset: (Int) -> Unit,
    onSwitchPhase: (FocusPhase) -> Unit,
    onToggleLinkFluidCloud: (Boolean) -> Unit,
    onToggleLinkDnd: (Boolean) -> Unit,
    onOpenCustomConfig: () -> Unit,
    onOpenDndPolicySettings: () -> Unit,
    onToggleTimer: () -> Unit,
    onResetTimer: () -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme
    val flowColors = FlowTheme.colors
    val haptics = rememberAppHaptics()
    val cardShape = MaterialTheme.shapes.large

    val animatedProgress by animateFloatAsState(
        targetValue = state.timerProgress,
        animationSpec = FlowMotion.standardSpring(),
        label = "study_timer_progress",
    )

    val mins = state.remainingSeconds / 60
    val secs = state.remainingSeconds % 60
    val formattedClock = String.format(Locale.getDefault(), "%02d:%02d", mins, secs)

    val subjects = remember(state.courseNames, state.activeFocusLabel) {
        buildList {
            if (state.activeFocusLabel.isNotBlank()) add(state.activeFocusLabel)
            add("专注学习")
            addAll(state.courseNames)
        }.distinct()
    }
    val presets = listOf(15, 25, 45, 60)
    val isCustomDuration = state.selectedPresetMinutes !in presets
    val isBreakPhase = state.currentPhase == FocusPhase.BREAK

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
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Top bar: Focus/Break phase switcher + Custom settings button
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    FilterChip(
                        selected = state.currentPhase == FocusPhase.FOCUS,
                        enabled = !state.isTimerRunning,
                        onClick = {
                            haptics.tick()
                            onSwitchPhase(FocusPhase.FOCUS)
                        },
                        label = {
                            Text(
                                text = "专注 ${state.selectedPresetMinutes}分",
                                maxLines = 1,
                                softWrap = false,
                            )
                        },
                        shape = CircleShape,
                        modifier = Modifier.testTag("study_phase_focus_chip"),
                    )
                    FilterChip(
                        selected = state.currentPhase == FocusPhase.BREAK,
                        enabled = !state.isTimerRunning,
                        onClick = {
                            haptics.tick()
                            onSwitchPhase(FocusPhase.BREAK)
                        },
                        label = {
                            Text(
                                text = if (state.breakDurationMinutes > 0) "休息 ${state.breakDurationMinutes}分" else "不休息",
                                maxLines = 1,
                                softWrap = false,
                            )
                        },
                        shape = CircleShape,
                        modifier = Modifier.testTag("study_phase_break_chip"),
                    )
                }

                Surface(
                    onClick = {
                        haptics.tick()
                        onOpenCustomConfig()
                    },
                    shape = CircleShape,
                    color = colorScheme.secondaryContainer,
                    modifier = Modifier.testTag("study_custom_config_button"),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Tune,
                            contentDescription = "自定义专注参数",
                            tint = colorScheme.onSecondaryContainer,
                            modifier = Modifier.size(15.dp),
                        )
                        Text(
                            text = "自定义",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = colorScheme.onSecondaryContainer,
                            maxLines = 1,
                            softWrap = false,
                        )
                    }
                }
            }

            // Event / Course subject selector strip + edit event name trigger
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                items(subjects) { subject ->
                    val selected = state.activeFocusLabel == subject
                    FilterChip(
                        selected = selected,
                        onClick = {
                            haptics.tick()
                            onSelectSubject(subject)
                        },
                        label = {
                            Text(
                                text = subject,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                        shape = CircleShape,
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = colorScheme.primaryContainer,
                            selectedLabelColor = colorScheme.onPrimaryContainer,
                        ),
                    )
                }
                item(key = "edit_focus_label") {
                    FilterChip(
                        selected = false,
                        onClick = {
                            haptics.tick()
                            onOpenCustomConfig()
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Rounded.Edit,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                            )
                        },
                        label = { Text("自定义事件名") },
                        shape = CircleShape,
                        modifier = Modifier.testTag("study_custom_event_name_chip"),
                    )
                }
            }

            // Circular focus dial
            val trackColor = colorScheme.surfaceContainerHigh
            val accentStart = if (isBreakPhase) flowColors.completedAccent else flowColors.secondaryAccent
            val accentEnd = if (isBreakPhase) flowColors.completedAccent else flowColors.liveAccent

            Box(
                modifier = Modifier.size(188.dp),
                contentAlignment = Alignment.Center,
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val stroke = 11.dp.toPx()
                    drawArc(
                        color = trackColor,
                        startAngle = 0f,
                        sweepAngle = 360f,
                        useCenter = false,
                        style = Stroke(width = stroke, cap = StrokeCap.Round),
                    )
                    drawArc(
                        brush = Brush.sweepGradient(
                            colors = listOf(accentStart, accentEnd, accentStart),
                        ),
                        startAngle = -90f,
                        sweepAngle = (360f * animatedProgress).coerceAtLeast(if (state.isTimerRunning) 4f else 0f),
                        useCenter = false,
                        style = Stroke(width = stroke, cap = StrokeCap.Round),
                    )
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = if (isBreakPhase) "休息时段" else "专注时段",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isBreakPhase) flowColors.completedAccent else colorScheme.primary,
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = formattedClock,
                        fontFamily = MonoFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 36.sp,
                        color = colorScheme.onSurface,
                        modifier = Modifier.testTag("study_timer_clock"),
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = state.activeFocusLabel,
                        style = MaterialTheme.typography.labelMedium,
                        color = colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 24.dp),
                    )
                }
            }

            // Duration quick presets + custom minute pill
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                presets.forEach { preset ->
                    FilterChip(
                        selected = !isBreakPhase && state.selectedPresetMinutes == preset,
                        enabled = !state.isTimerRunning,
                        onClick = {
                            haptics.tick()
                            onSelectPreset(preset)
                        },
                        label = { Text("${preset}分") },
                        shape = CircleShape,
                        modifier = Modifier.testTag("study_preset_$preset"),
                    )
                }
                FilterChip(
                    selected = !isBreakPhase && isCustomDuration,
                    enabled = !state.isTimerRunning,
                    onClick = {
                        haptics.tick()
                        onOpenCustomConfig()
                    },
                    label = {
                        Text(if (isCustomDuration) "${state.selectedPresetMinutes}分(自定)" else "自定义时长")
                    },
                    shape = CircleShape,
                    modifier = Modifier.testTag("study_custom_duration_chip"),
                )
            }

            // Action controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Button(
                    onClick = {
                        haptics.tick()
                        onToggleTimer()
                    },
                    modifier = Modifier
                        .height(48.dp)
                        .widthIn(min = 160.dp)
                        .testTag("study_timer_toggle_button"),
                    shape = CircleShape,
                ) {
                    Icon(
                        imageVector = if (state.isTimerRunning) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = null,
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = when {
                            state.isTimerRunning && isBreakPhase -> "暂停休息"
                            state.isTimerRunning -> "暂停专注"
                            isBreakPhase -> "开始休息"
                            else -> "开始专注"
                        },
                        fontWeight = FontWeight.Bold,
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                FilledTonalIconButton(
                    onClick = {
                        haptics.tick()
                        onResetTimer()
                    },
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("study_timer_reset_button"),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Refresh,
                        contentDescription = "重置计时器",
                    )
                }
            }

            HorizontalDivider(color = colorScheme.outlineVariant.copy(alpha = 0.7f))

            // Linkage toggles: Fluid Cloud & System DND
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(
                        modifier = Modifier.weight(1f).padding(end = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.CloudQueue,
                            contentDescription = null,
                            tint = colorScheme.primary,
                            modifier = Modifier.size(19.dp),
                        )
                        Column {
                            Text(
                                text = "联动实况通知",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = colorScheme.onSurface,
                            )
                            Text(
                                text = "开始计时后在状态栏实况胶囊或实时进度通知中同步显示倒计时与事件名",
                                style = MaterialTheme.typography.bodySmall,
                                color = colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Switch(
                        checked = state.linkFluidCloud,
                        onCheckedChange = {
                            haptics.tick()
                            onToggleLinkFluidCloud(it)
                        },
                        modifier = Modifier.testTag("study_switch_fluid_cloud"),
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(
                        modifier = Modifier.weight(1f).padding(end = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.DoNotDisturbOn,
                            contentDescription = null,
                            tint = if (state.linkDnd) colorScheme.primary else colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(19.dp),
                        )
                        Column {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Text(
                                    text = "联动开启系统免打扰",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = colorScheme.onSurface,
                                )
                                if (state.linkDnd && !state.hasDndPolicyAccess) {
                                    Surface(
                                        onClick = {
                                            haptics.tick()
                                            onOpenDndPolicySettings()
                                        },
                                        shape = CircleShape,
                                        color = flowColors.upcomingAccent.copy(alpha = 0.16f),
                                        modifier = Modifier.testTag("study_dnd_grant_pill"),
                                    ) {
                                        Text(
                                            text = "去授权",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.SemiBold,
                                            color = flowColors.upcomingAccent,
                                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                                        )
                                    }
                                }
                            }
                            Text(
                                text = "专注时自动切入「${state.dndLevel.label}」，暂停或结束后自动恢复",
                                style = MaterialTheme.typography.bodySmall,
                                color = colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Switch(
                        checked = state.linkDnd,
                        onCheckedChange = {
                            haptics.tick()
                            onToggleLinkDnd(it)
                        },
                        modifier = Modifier.testTag("study_switch_dnd"),
                    )
                }
            }
        }
    }
}

@Composable
private fun CustomFocusConfigDialog(
    initialFocusMinutes: Int,
    initialBreakMinutes: Int,
    initialEventName: String,
    initialLinkFluidCloud: Boolean,
    initialLinkDnd: Boolean,
    dndLevelLabel: String,
    isRunning: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (
        focusMinutes: Int,
        breakMinutes: Int,
        eventName: String,
        linkFluidCloud: Boolean,
        linkDnd: Boolean,
    ) -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme
    val haptics = rememberAppHaptics()
    var eventName by remember { mutableStateOf(initialEventName) }
    var focusText by remember { mutableStateOf(initialFocusMinutes.toString()) }
    var breakText by remember { mutableStateOf(initialBreakMinutes.toString()) }
    var linkFluidCloud by remember { mutableStateOf(initialLinkFluidCloud) }
    var linkDnd by remember { mutableStateOf(initialLinkDnd) }

    val parsedFocus = focusText.toIntOrNull()?.coerceIn(1, 180)
    val parsedBreak = breakText.toIntOrNull()?.coerceIn(0, 60)

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colorScheme.surfaceContainerHigh,
        title = {
            Text(
                text = "自定义专注与联动设置",
                style = MaterialTheme.typography.titleMedium,
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
                OutlinedTextField(
                    value = eventName,
                    onValueChange = { if (it.length <= 24) eventName = it },
                    label = { Text("专注事件名称") },
                    placeholder = { Text("例如：考研英语阅读 / 算法刷题") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("focus_custom_name_input"),
                )

                // Focus duration (1..180 min)
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "专注时长（1–180 分钟）",
                        style = MaterialTheme.typography.labelMedium,
                        color = colorScheme.onSurfaceVariant,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        IconButton(
                            onClick = {
                                haptics.tick()
                                val next = ((parsedFocus ?: initialFocusMinutes) - 5).coerceAtLeast(1)
                                focusText = next.toString()
                            },
                            enabled = !isRunning,
                        ) {
                            Icon(Icons.Rounded.Remove, contentDescription = "减少5分钟")
                        }
                        OutlinedTextField(
                            value = focusText,
                            onValueChange = { raw ->
                                val digits = raw.filter(Char::isDigit).take(3)
                                focusText = digits
                            },
                            enabled = !isRunning,
                            singleLine = true,
                            suffix = { Text("分钟") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("focus_custom_duration_input"),
                        )
                        IconButton(
                            onClick = {
                                haptics.tick()
                                val next = ((parsedFocus ?: initialFocusMinutes) + 5).coerceAtMost(180)
                                focusText = next.toString()
                            },
                            enabled = !isRunning,
                        ) {
                            Icon(Icons.Rounded.Add, contentDescription = "增加5分钟")
                        }
                    }
                }

                // Break duration (0..60 min)
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "休息时长（0–60 分钟，0 表示专注后不进入休息）",
                        style = MaterialTheme.typography.labelMedium,
                        color = colorScheme.onSurfaceVariant,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        IconButton(
                            onClick = {
                                haptics.tick()
                                val next = ((parsedBreak ?: initialBreakMinutes) - 1).coerceAtLeast(0)
                                breakText = next.toString()
                            },
                            enabled = !isRunning,
                        ) {
                            Icon(Icons.Rounded.Remove, contentDescription = "减少1分钟休息")
                        }
                        OutlinedTextField(
                            value = breakText,
                            onValueChange = { raw ->
                                val digits = raw.filter(Char::isDigit).take(2)
                                breakText = digits
                            },
                            enabled = !isRunning,
                            singleLine = true,
                            suffix = { Text("分钟") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("focus_custom_break_input"),
                        )
                        IconButton(
                            onClick = {
                                haptics.tick()
                                val next = ((parsedBreak ?: initialBreakMinutes) + 1).coerceAtMost(60)
                                breakText = next.toString()
                            },
                            enabled = !isRunning,
                        ) {
                            Icon(Icons.Rounded.Add, contentDescription = "增加1分钟休息")
                        }
                    }
                }

                if (isRunning) {
                    Text(
                        text = "计时运行中仅可修改事件名与联动开关，暂停或重置后可调整时长",
                        style = MaterialTheme.typography.bodySmall,
                        color = colorScheme.primary,
                    )
                }

                HorizontalDivider(color = colorScheme.outlineVariant)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                        Text("联动实况通知", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "跨应用在状态栏实况胶囊或实时进度通知中倒计时",
                            style = MaterialTheme.typography.bodySmall,
                            color = colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = linkFluidCloud,
                        onCheckedChange = {
                            haptics.tick()
                            linkFluidCloud = it
                        },
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                        Text("联动系统免打扰", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "使用「$dndLevelLabel」级别，结束自动恢复",
                            style = MaterialTheme.typography.bodySmall,
                            color = colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = linkDnd,
                        onCheckedChange = {
                            haptics.tick()
                            linkDnd = it
                        },
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    haptics.confirm()
                    onConfirm(
                        parsedFocus ?: initialFocusMinutes,
                        parsedBreak ?: initialBreakMinutes,
                        eventName.trim().ifBlank { "专注学习" },
                        linkFluidCloud,
                        linkDnd,
                    )
                },
                enabled = parsedFocus != null && parsedBreak != null,
                modifier = Modifier.testTag("focus_custom_config_save_button"),
            ) {
                Text("保存配置")
            }
        },
        dismissButton = {
            TextButton(onClick = {
                haptics.tick()
                onDismiss()
            }) {
                Text("取消")
            }
        },
    )
}

@Composable
private fun StudyTaskRowCard(
    task: StudyTaskEntity,
    onToggle: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colorScheme = MaterialTheme.colorScheme
    val flowColors = FlowTheme.colors
    val haptics = rememberAppHaptics()

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .clickable {
                haptics.confirm()
                onToggle()
            }
            .testTag("study_task_card_${task.id}"),
        shape = MaterialTheme.shapes.medium,
        color = flowColors.cardElevated,
        border = BorderStroke(1.dp, colorScheme.outlineVariant),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(
                onClick = {
                    haptics.confirm()
                    onToggle()
                },
                modifier = Modifier.minimumInteractiveComponentSize(),
            ) {
                Icon(
                    imageVector = if (task.isCompleted) {
                        Icons.Rounded.CheckCircle
                    } else {
                        Icons.Rounded.RadioButtonUnchecked
                    },
                    contentDescription = "切换完成状态",
                    tint = if (task.isCompleted) flowColors.completedAccent else colorScheme.primary,
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = task.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = if (task.isCompleted) colorScheme.onSurfaceVariant else colorScheme.onSurface,
                    textDecoration = if (task.isCompleted) TextDecoration.LineThrough else TextDecoration.None,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (task.courseTag.isNotBlank()) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = colorScheme.secondaryContainer,
                        ) {
                            Text(
                                text = task.courseTag,
                                style = MaterialTheme.typography.labelSmall,
                                color = colorScheme.onSecondaryContainer,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            )
                        }
                    }
                    Text(
                        text = "预计 ${task.targetMinutes} 分钟",
                        fontFamily = MonoFontFamily,
                        fontSize = 11.sp,
                        color = colorScheme.onSurfaceVariant,
                    )
                }
            }

            IconButton(
                onClick = {
                    haptics.tick()
                    onDelete()
                },
                modifier = Modifier.minimumInteractiveComponentSize(),
            ) {
                Icon(
                    imageVector = Icons.Rounded.DeleteOutline,
                    contentDescription = "删除学习任务",
                    tint = colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AddStudyTaskDialog(
    courseNames: List<String>,
    onDismiss: () -> Unit,
    onSave: (title: String, courseTag: String, targetMinutes: Int) -> Unit,
) {
    val haptics = rememberAppHaptics()
    var title by remember { mutableStateOf("") }
    var selectedCourse by remember { mutableStateOf("") }
    var targetMinutes by remember { mutableIntStateOf(30) }
    var targetMinutesText by remember { mutableStateOf("30") }
    val durationOptions = listOf(15, 30, 45, 60)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("新建学习任务") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("任务内容") },
                    placeholder = { Text("例如：完成高数第三章习题") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("study_task_title_input"),
                )

                if (courseNames.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "关联课程（可选）",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            courseNames.take(8).forEach { name ->
                                FilterChip(
                                    selected = selectedCourse == name,
                                    onClick = {
                                        haptics.tick()
                                        selectedCourse = if (selectedCourse == name) "" else name
                                    },
                                    label = { Text(name) },
                                )
                            }
                        }
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "预计时长（1–360 分钟，可自由填写）",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        durationOptions.forEach { mins ->
                            FilterChip(
                                selected = targetMinutes == mins,
                                onClick = {
                                    haptics.tick()
                                    targetMinutes = mins
                                    targetMinutesText = mins.toString()
                                },
                                label = { Text("${mins}分") },
                            )
                        }
                    }
                    OutlinedTextField(
                        value = targetMinutesText,
                        onValueChange = { raw ->
                            val digits = raw.filter(Char::isDigit).take(3)
                            targetMinutesText = digits
                            digits.toIntOrNull()?.let { targetMinutes = it.coerceIn(1, 360) }
                        },
                        label = { Text("自定义预计分钟数") },
                        singleLine = true,
                        suffix = { Text("分钟") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("study_task_custom_minutes_input"),
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    haptics.confirm()
                    val safeMins = (targetMinutesText.toIntOrNull() ?: targetMinutes).coerceIn(1, 360)
                    onSave(title.trim(), selectedCourse, safeMins)
                },
                enabled = title.isNotBlank(),
                modifier = Modifier.testTag("study_task_save_button"),
            ) {
                Text("保存")
            }
        },
        dismissButton = {
            TextButton(onClick = {
                haptics.tick()
                onDismiss()
            }) {
                Text("取消")
            }
        },
    )
}
