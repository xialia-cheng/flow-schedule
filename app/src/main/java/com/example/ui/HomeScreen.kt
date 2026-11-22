package com.example.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.EventAvailable
import androidx.compose.material.icons.rounded.Label
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.CustomScheduleTag
import com.example.data.DateTimeUtils
import com.example.data.ScheduleEntity
import com.example.data.WeekStartDay
import com.example.ui.components.CustomTagManagerDialog
import com.example.ui.components.HeroLiveCard
import com.example.ui.components.ScheduleEditorSheet
import com.example.ui.components.ScheduleItemCard
import com.example.ui.components.ScheduleListSectionHeader
import com.example.ui.theme.FlowMotion
import com.example.ui.theme.FlowTheme
import com.example.ui.theme.MonoFontFamily

@Composable
fun HomeScreen(
    viewModel: ScheduleViewModel,
    onOpenSmartCapture: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    val colorScheme = MaterialTheme.colorScheme
    val density = LocalDensity.current
    val swipeThresholdPx = remember(density) { with(density) { 64.dp.toPx() } }
    var horizontalDragTotal by remember { mutableFloatStateOf(0f) }
    var showCustomTagManager by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .pointerInput(state.selectedDateMillis) {
                    detectHorizontalDragGestures(
                        onDragStart = { horizontalDragTotal = 0f },
                        onHorizontalDrag = { _, dragAmount ->
                            horizontalDragTotal += dragAmount
                        },
                        onDragEnd = {
                            if (horizontalDragTotal <= -swipeThresholdPx) {
                                viewModel.shiftSelectedDate(1)
                            } else if (horizontalDragTotal >= swipeThresholdPx) {
                                viewModel.shiftSelectedDate(-1)
                            }
                            horizontalDragTotal = 0f
                        },
                        onDragCancel = { horizontalDragTotal = 0f },
                    )
                },
        ) {
            // Pinned top bar + weekday strip with statusBarsPadding so scrolling never clips under status bar
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding(),
                color = colorScheme.background.copy(alpha = 0.96f),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    TopHeaderRow(
                        selectedDateMillis = state.selectedDateMillis,
                        nowMillis = state.nowMillis,
                        permissionsReady = state.permissionStatus.allRecommendedGranted,
                        onJumpToday = { viewModel.jumpToToday() },
                        onOpenPermissions = { viewModel.openPermissionSheet() },
                        onOpenSmartCapture = onOpenSmartCapture,
                        onAddSchedule = { viewModel.openNewScheduleEditor() },
                    )

                    ScheduleWeekdayDateStrip(
                        weekDatesMillis = state.weekDatesMillis,
                        weekStartDay = state.weekStartDay,
                        selectedDateMillis = state.selectedDateMillis,
                        nowMillis = state.nowMillis,
                        scheduledDateKeys = state.scheduledDateKeys,
                        onSelectDate = { viewModel.selectDate(it) },
                        onShiftWeek = { delta -> viewModel.shiftSelectedDate(delta * 7) },
                    )
                }
            }

            HorizontalDivider(
                thickness = 0.75.dp,
                color = colorScheme.outlineVariant.copy(alpha = 0.6f),
            )

            // Scrollable schedule content below the pinned header — bottom-right is completely free of FABs
            BoxWithConstraints(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) {
                val isExpanded = maxWidth >= 700.dp

                if (isExpanded) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 24.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(24.dp),
                    ) {
                        LazyColumn(
                            modifier = Modifier
                                .weight(0.45f)
                                .fillMaxHeight(),
                            verticalArrangement = Arrangement.spacedBy(14.dp),
                            contentPadding = PaddingValues(bottom = 24.dp),
                        ) {
                            item {
                                TodaySummaryCard(
                                    pendingCount = state.todayPendingCount,
                                    completedCount = state.todayCompletedCount,
                                    totalCount = state.todayTotalCount,
                                )
                            }
                            item {
                                HeroLiveCard(
                                    schedule = state.heroSchedule,
                                    nowMillis = state.nowMillis,
                                    isLiveActive = state.isHeroLiveActive,
                                    customTags = state.customTags,
                                    onComplete = { viewModel.toggleComplete(it) },
                                    onSnooze10Min = { viewModel.snoozeSchedule10Minutes(it) },
                                    onEdit = { viewModel.openEditScheduleEditor(it) },
                                )
                            }
                        }

                        ScheduleTimelinePane(
                            state = state,
                            onSelectFilter = { viewModel.selectFilter(it) },
                            onManageTags = { showCustomTagManager = true },
                            onToggleComplete = { viewModel.toggleComplete(it) },
                            onDelete = { viewModel.deleteSchedule(it) },
                            onEdit = { viewModel.openEditScheduleEditor(it) },
                            modifier = Modifier
                                .weight(0.55f)
                                .fillMaxHeight(),
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("home_schedule_list"),
                        contentPadding = PaddingValues(
                            start = 16.dp,
                            end = 16.dp,
                            top = 12.dp,
                            bottom = 28.dp,
                        ),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        item(key = "today_summary") {
                            TodaySummaryCard(
                                pendingCount = state.todayPendingCount,
                                completedCount = state.todayCompletedCount,
                                totalCount = state.todayTotalCount,
                            )
                        }

                        item(key = "hero_section_title") {
                            SectionTitleRow(
                                title = if (state.isHeroLiveActive) "当前进行中 · 流体云已激活" else "当前进行中 / 即将开始",
                                trailingHint = if (state.isHeroLiveActive) "状态栏胶囊同步中" else null,
                            )
                        }

                        item(key = "hero_card") {
                            HeroLiveCard(
                                schedule = state.heroSchedule,
                                nowMillis = state.nowMillis,
                                isLiveActive = state.isHeroLiveActive,
                                customTags = state.customTags,
                                onComplete = { viewModel.toggleComplete(it) },
                                onSnooze10Min = { viewModel.snoozeSchedule10Minutes(it) },
                                onEdit = { viewModel.openEditScheduleEditor(it) },
                            )
                        }

                        item(key = "filter_row") {
                            FilterChipsRow(
                                selected = state.selectedFilter,
                                selectedDateMillis = state.selectedDateMillis,
                                customTagCount = state.customTags.size,
                                onSelect = { viewModel.selectFilter(it) },
                                onManageTags = { showCustomTagManager = true },
                            )
                        }

                        scheduleTimelineItems(
                            groups = state.groupedSchedules,
                            nowMillis = state.nowMillis,
                            selectedDateMillis = state.selectedDateMillis,
                            isDayScopedFilter = state.isDayScopedFilter,
                            showDateBadge = !state.isDayScopedFilter,
                            customTags = state.customTags,
                            onToggleComplete = { viewModel.toggleComplete(it) },
                            onDelete = { viewModel.deleteSchedule(it) },
                            onEdit = { viewModel.openEditScheduleEditor(it) },
                        )
                    }
                }
            }
        }
    }

    if (state.showEditorSheet) {
        ScheduleEditorSheet(
            editingSchedule = state.editingSchedule,
            customTags = state.customTags,
            onAddCustomTag = { name, hex -> viewModel.addCustomTag(name, hex) },
            onUpdateCustomTag = { id, name, hex -> viewModel.updateCustomTag(id, name, hex) },
            onDeleteCustomTag = { id -> viewModel.deleteCustomTag(id) },
            onDismiss = { viewModel.dismissEditorSheet() },
            onSave = { id, title, notes, start, end, repeat, lead, cat, customTagId, customTagName, customTagColorHex ->
                viewModel.saveSchedule(
                    existingId = id,
                    title = title,
                    notes = notes,
                    startTimeMillis = start,
                    endTimeMillis = end,
                    repeatRule = repeat,
                    reminderMinutesBefore = lead,
                    category = cat,
                    customTagId = customTagId,
                    customTagName = customTagName,
                    customTagColorHex = customTagColorHex,
                )
            },
            onDelete = if (state.editingSchedule != null) {
                { viewModel.deleteSchedule(it) }
            } else {
                null
            },
            onOpenSmartCapture = onOpenSmartCapture,
        )
    }

    if (showCustomTagManager) {
        CustomTagManagerDialog(
            customTags = state.customTags,
            onAddTag = { name, hex -> viewModel.addCustomTag(name, hex) },
            onUpdateTag = { id, name, hex -> viewModel.updateCustomTag(id, name, hex) },
            onDeleteTag = { id -> viewModel.deleteCustomTag(id) },
            onDismiss = { showCustomTagManager = false },
        )
    }
}

private fun LazyListScope.scheduleTimelineItems(
    groups: List<ScheduleTimelineGroup>,
    nowMillis: Long,
    selectedDateMillis: Long,
    isDayScopedFilter: Boolean,
    showDateBadge: Boolean,
    customTags: List<CustomScheduleTag>,
    onToggleComplete: (ScheduleEntity) -> Unit,
    onDelete: (ScheduleEntity) -> Unit,
    onEdit: (ScheduleEntity) -> Unit,
) {
    if (groups.isEmpty()) {
        item(key = "empty_state") {
            EmptyScheduleCard(
                selectedDateMillis = selectedDateMillis,
                nowMillis = nowMillis,
                isDayScopedFilter = isDayScopedFilter,
            )
        }
        return
    }

    groups.forEach { group ->
        item(key = "header_${group.key}") {
            ScheduleListSectionHeader(
                title = group.title,
                count = group.items.size,
                isCompletedGroup = group.isCompletedGroup,
            )
        }
        itemsIndexed(
            items = group.items,
            key = { _, item -> item.id },
        ) { index, schedule ->
            ScheduleItemCard(
                schedule = schedule,
                nowMillis = nowMillis,
                showDateBadge = showDateBadge,
                isFirstInGroup = index == 0,
                isLastInGroup = index == group.items.lastIndex,
                customTags = customTags,
                onToggleComplete = onToggleComplete,
                onDelete = onDelete,
                onClick = onEdit,
                modifier = Modifier.animateItem(),
            )
        }
    }
}

@Composable
private fun TopHeaderRow(
    selectedDateMillis: Long,
    nowMillis: Long,
    permissionsReady: Boolean,
    onJumpToday: () -> Unit,
    onOpenPermissions: () -> Unit,
    onOpenSmartCapture: () -> Unit,
    onAddSchedule: () -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme
    val flowColors = FlowTheme.colors
    val isSelectedToday = DateTimeUtils.isSameDay(selectedDateMillis, nowMillis)

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "日程",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = colorScheme.onBackground,
                maxLines = 1,
                softWrap = false,
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                AnimatedContent(
                    targetState = selectedDateMillis,
                    transitionSpec = {
                        val movingForward = targetState >= initialState
                        val enter = slideInHorizontally(
                            animationSpec = tween(durationMillis = 240, easing = FastOutSlowInEasing),
                            initialOffsetX = { fullWidth -> if (movingForward) fullWidth / 3 else -fullWidth / 3 },
                        ) + fadeIn(
                            animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
                        )
                        val exit = slideOutHorizontally(
                            animationSpec = tween(durationMillis = 190, easing = FastOutSlowInEasing),
                            targetOffsetX = { fullWidth -> if (movingForward) -fullWidth / 3 else fullWidth / 3 },
                        ) + fadeOut(
                            animationSpec = tween(durationMillis = 160, easing = FastOutSlowInEasing),
                        )
                        (enter togetherWith exit).using(SizeTransform(clip = false))
                    },
                    label = "schedule_header_date_transition",
                ) { targetDateMillis ->
                    Text(
                        text = DateTimeUtils.formatDateHeader(targetDateMillis),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        color = colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.testTag("schedule_selected_date_subtitle"),
                    )
                }
                AnimatedVisibility(
                    visible = !isSelectedToday,
                    enter = fadeIn(tween(180)) + scaleIn(initialScale = 0.88f, animationSpec = tween(180)),
                    exit = fadeOut(tween(140)) + scaleOut(targetScale = 0.88f, animationSpec = tween(140)),
                ) {
                    Surface(
                        shape = CircleShape,
                        color = colorScheme.secondaryContainer,
                        modifier = Modifier
                            .clip(CircleShape)
                            .clickable(onClick = onJumpToday)
                            .testTag("schedule_jump_today_pill"),
                    ) {
                        Text(
                            text = "回今天",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = colorScheme.onSecondaryContainer,
                            maxLines = 1,
                            softWrap = false,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                        )
                    }
                }
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (!permissionsReady) {
                IconButton(
                    onClick = onOpenPermissions,
                    modifier = Modifier
                        .size(40.dp)
                        .minimumInteractiveComponentSize()
                        .testTag("permissions_status_button"),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.NotificationsOff,
                        contentDescription = stringResource(R.string.cd_open_permissions),
                        tint = flowColors.upcomingAccent,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }

            // Compact sparkle AI one-sentence quick capture entry in top bar right
            Surface(
                onClick = onOpenSmartCapture,
                shape = CircleShape,
                color = colorScheme.secondaryContainer,
                modifier = Modifier
                    .size(40.dp)
                    .minimumInteractiveComponentSize()
                    .testTag("schedule_smart_capture_button"),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Rounded.AutoAwesome,
                        contentDescription = "一句话智能创建日程",
                        tint = colorScheme.onSecondaryContainer,
                        modifier = Modifier.size(19.dp),
                    )
                }
            }

            // Primary "+ 新建日程" pill button placed cleanly in top bar right
            Surface(
                onClick = onAddSchedule,
                shape = CircleShape,
                color = colorScheme.primary,
                modifier = Modifier
                    .defaultMinSize(minHeight = 40.dp)
                    .minimumInteractiveComponentSize()
                    .testTag("add_schedule_button"),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 13.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Add,
                        contentDescription = stringResource(R.string.cd_add_schedule),
                        tint = colorScheme.onPrimary,
                        modifier = Modifier.size(17.dp),
                    )
                    Text(
                        text = stringResource(R.string.action_add_schedule),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = colorScheme.onPrimary,
                        maxLines = 1,
                        softWrap = false,
                    )
                }
            }
        }
    }
}

@Composable
private fun ScheduleWeekdayDateStrip(
    weekDatesMillis: List<Long>,
    weekStartDay: WeekStartDay,
    selectedDateMillis: Long,
    nowMillis: Long,
    scheduledDateKeys: Set<String>,
    onSelectDate: (Long) -> Unit,
    onShiftWeek: (Int) -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme
    val flowColors = FlowTheme.colors

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("schedule_weekday_strip"),
        shape = MaterialTheme.shapes.large,
        color = flowColors.cardElevated,
        border = BorderStroke(1.dp, colorScheme.outlineVariant),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(
                onClick = { onShiftWeek(-1) },
                modifier = Modifier
                    .size(32.dp)
                    .testTag("schedule_prev_week_btn"),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowLeft,
                    contentDescription = "上一周",
                    tint = colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
            }

            val currentWeekStartMillis = weekDatesMillis.firstOrNull()
                ?: DateTimeUtils.weekStartOfWeekMillis(selectedDateMillis, weekStartDay)

            AnimatedContent(
                targetState = currentWeekStartMillis,
                modifier = Modifier
                    .weight(1f)
                    .clipToBounds(),
                transitionSpec = {
                    val movingForward = targetState >= initialState
                    val enter = slideInHorizontally(
                        animationSpec = tween(durationMillis = 260, easing = FastOutSlowInEasing),
                        initialOffsetX = { fullWidth -> if (movingForward) fullWidth / 2 else -fullWidth / 2 },
                    ) + fadeIn(
                        animationSpec = tween(durationMillis = 240, easing = FastOutSlowInEasing),
                    )
                    val exit = slideOutHorizontally(
                        animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing),
                        targetOffsetX = { fullWidth -> if (movingForward) -fullWidth / 2 else fullWidth / 2 },
                    ) + fadeOut(
                        animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing),
                    )
                    (enter togetherWith exit).using(SizeTransform(clip = false))
                },
                label = "schedule_weekday_strip_transition",
            ) { targetWeekStart ->
                val animatedWeekDates = remember(targetWeekStart, weekStartDay) {
                    DateTimeUtils.weekDatesContaining(targetWeekStart, weekStartDay)
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    for (index in 0 until 7) {
                        val dateMillis = animatedWeekDates.getOrNull(index) ?: nowMillis
                        val isSelected = DateTimeUtils.isSameDay(dateMillis, selectedDateMillis)
                        val isToday = DateTimeUtils.isSameDay(dateMillis, nowMillis)
                        val hasSchedule = DateTimeUtils.formatDateKey(dateMillis) in scheduledDateKeys
                        val dayLabel = DateTimeUtils.dayOfWeekShortChinese(DateTimeUtils.isoDayOfWeek(dateMillis))
                        val dayNum = DateTimeUtils.formatDayOfMonth(dateMillis)

                        val pillBg by animateColorAsState(
                            targetValue = when {
                                isSelected -> colorScheme.primary
                                isToday -> colorScheme.primaryContainer.copy(alpha = if (flowColors.isDark) 0.42f else 0.55f)
                                else -> Color.Transparent
                            },
                            animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing),
                            label = "schedule_day_pill_bg_$index",
                        )
                        val pillBorder = when {
                            !isSelected && isToday -> BorderStroke(1.5.dp, colorScheme.primary)
                            else -> null
                        }
                        val topColor by animateColorAsState(
                            targetValue = when {
                                isSelected -> colorScheme.onPrimary
                                isToday -> colorScheme.primary
                                else -> colorScheme.onSurfaceVariant.copy(alpha = 0.78f)
                            },
                            animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing),
                            label = "schedule_day_top_color_$index",
                        )
                        val numColor by animateColorAsState(
                            targetValue = when {
                                isSelected -> colorScheme.onPrimary
                                isToday -> colorScheme.primary
                                else -> colorScheme.onSurface.copy(alpha = 0.76f)
                            },
                            animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing),
                            label = "schedule_day_num_color_$index",
                        )
                        val dotColor by animateColorAsState(
                            targetValue = when {
                                isSelected && (hasSchedule || isToday) -> colorScheme.onPrimary
                                isToday -> colorScheme.primary
                                hasSchedule -> colorScheme.outline.copy(alpha = 0.75f)
                                else -> Color.Transparent
                            },
                            animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing),
                            label = "schedule_day_dot_color_$index",
                        )

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 1.5.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = pillBg,
                                border = pillBorder,
                                shadowElevation = if (isSelected) 2.dp else 0.dp,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .defaultMinSize(minHeight = 48.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable { onSelectDate(dateMillis) }
                                    .testTag("schedule_weekday_${index + 1}"),
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center,
                                ) {
                                    Text(
                                        text = dayLabel,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = if (isSelected || isToday) FontWeight.Bold else FontWeight.Medium,
                                        color = topColor,
                                    )
                                    Spacer(modifier = Modifier.height(1.dp))
                                    Text(
                                        text = dayNum,
                                        fontFamily = MonoFontFamily,
                                        fontSize = 12.sp,
                                        fontWeight = if (isSelected || isToday) FontWeight.ExtraBold else FontWeight.Medium,
                                        color = numColor,
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Box(
                                        modifier = Modifier
                                            .size(if (isToday && !isSelected) 5.dp else 4.dp)
                                            .clip(CircleShape)
                                            .background(dotColor),
                                    )
                                }
                            }
                        }
                    }
                }
            }

            IconButton(
                onClick = { onShiftWeek(1) },
                modifier = Modifier
                    .size(32.dp)
                    .testTag("schedule_next_week_btn"),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                    contentDescription = "下一周",
                    tint = colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

@Composable
private fun TodaySummaryCard(
    pendingCount: Int,
    completedCount: Int,
    totalCount: Int,
) {
    val colorScheme = MaterialTheme.colorScheme
    val flowColors = FlowTheme.colors
    val cardShape = MaterialTheme.shapes.large

    val ratio = if (totalCount == 0) 0f else completedCount.toFloat() / totalCount.toFloat()
    val animatedRatio by animateFloatAsState(
        targetValue = ratio,
        animationSpec = FlowMotion.standardSpring(),
        label = "today_ring",
    )

    val ringTrackColor = colorScheme.surfaceContainerHigh
    val ringStartColor = flowColors.secondaryAccent
    val ringEndColor = flowColors.liveAccent

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
        modifier = cardModifier.testTag("today_todo_summary_card"),
        shape = cardShape,
        color = flowColors.cardElevated,
        border = BorderStroke(1.dp, colorScheme.outlineVariant),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.today_todo_title),
                    style = MaterialTheme.typography.labelLarge,
                    color = colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = "$pendingCount",
                        fontFamily = MonoFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 30.sp,
                        color = colorScheme.onSurface,
                    )
                    Text(
                        text = "项待完成",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 5.dp),
                    )
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = if (totalCount == 0) {
                        "本地离线存储 · 无需登录"
                    } else {
                        "今日共 $totalCount 项 · 已完成 $completedCount 项"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = colorScheme.onSurfaceVariant,
                )
            }

            Box(
                modifier = Modifier.size(60.dp),
                contentAlignment = Alignment.Center,
            ) {
                Canvas(modifier = Modifier.size(56.dp)) {
                    val strokeWidth = 6.dp.toPx()
                    drawArc(
                        color = ringTrackColor,
                        startAngle = 0f,
                        sweepAngle = 360f,
                        useCenter = false,
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
                    )
                    drawArc(
                        brush = Brush.sweepGradient(
                            colors = listOf(ringStartColor, ringEndColor, ringStartColor),
                        ),
                        startAngle = -90f,
                        sweepAngle = 360f * animatedRatio,
                        useCenter = false,
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
                    )
                }
                Text(
                    text = "${(animatedRatio * 100).toInt()}%",
                    fontFamily = MonoFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    color = if (totalCount > 0 && pendingCount == 0) {
                        flowColors.completedAccent
                    } else {
                        colorScheme.primary
                    },
                )
            }
        }
    }
}

@Composable
private fun SectionTitleRow(
    title: String,
    trailingHint: String?,
) {
    val colorScheme = MaterialTheme.colorScheme
    val flowColors = FlowTheme.colors

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = colorScheme.onSurfaceVariant,
        )
        if (trailingHint != null) {
            Text(
                text = trailingHint,
                style = MaterialTheme.typography.labelSmall,
                color = flowColors.liveAccent,
            )
        }
    }
}

@Composable
private fun FilterChipsRow(
    selected: ScheduleFilter,
    selectedDateMillis: Long,
    customTagCount: Int,
    onSelect: (ScheduleFilter) -> Unit,
    onManageTags: () -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme
    val flowColors = FlowTheme.colors

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val visibleFilters = remember {
            listOf(
                ScheduleFilter.TODAY,
                ScheduleFilter.TOMORROW,
                ScheduleFilter.UPCOMING,
                ScheduleFilter.ALL,
            )
        }
        visibleFilters.forEach { filter ->
            val isSelected = filter == selected
            FilterChip(
                selected = isSelected,
                onClick = { onSelect(filter) },
                label = { Text(filter.label, maxLines = 1, softWrap = false) },
                modifier = Modifier.testTag("filter_chip_${filter.name.lowercase()}"),
                shape = CircleShape,
                colors = FilterChipDefaults.filterChipColors(
                    containerColor = flowColors.cardElevated,
                    labelColor = colorScheme.onSurfaceVariant,
                    selectedContainerColor = colorScheme.primaryContainer,
                    selectedLabelColor = colorScheme.onPrimaryContainer,
                ),
                border = FilterChipDefaults.filterChipBorder(
                    enabled = true,
                    selected = isSelected,
                    borderColor = colorScheme.outlineVariant,
                    selectedBorderColor = colorScheme.primary.copy(alpha = 0.45f),
                ),
            )
        }
        if (selected == ScheduleFilter.SELECTED_DAY) {
            FilterChip(
                selected = true,
                onClick = { },
                label = {
                    Text(
                        text = DateTimeUtils.formatMonthDay(selectedDateMillis),
                        maxLines = 1,
                        softWrap = false,
                    )
                },
                modifier = Modifier.testTag("filter_chip_selected_day"),
                shape = CircleShape,
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = colorScheme.primaryContainer,
                    selectedLabelColor = colorScheme.onPrimaryContainer,
                ),
                border = FilterChipDefaults.filterChipBorder(
                    enabled = true,
                    selected = true,
                    borderColor = colorScheme.primary.copy(alpha = 0.45f),
                    selectedBorderColor = colorScheme.primary.copy(alpha = 0.45f),
                ),
            )
        }
        FilterChip(
            selected = false,
            onClick = onManageTags,
            leadingIcon = {
                Icon(
                    imageVector = Icons.Rounded.Label,
                    contentDescription = null,
                    modifier = Modifier.size(15.dp),
                )
            },
            label = {
                Text(
                    text = if (customTagCount > 0) "标签($customTagCount)" else "+ 自定义标签",
                    maxLines = 1,
                    softWrap = false,
                )
            },
            modifier = Modifier.testTag("schedule_manage_tags_chip"),
            shape = CircleShape,
            colors = FilterChipDefaults.filterChipColors(
                containerColor = colorScheme.secondaryContainer.copy(alpha = 0.6f),
                labelColor = colorScheme.onSecondaryContainer,
                iconColor = colorScheme.onSecondaryContainer,
            ),
            border = FilterChipDefaults.filterChipBorder(
                enabled = true,
                selected = false,
                borderColor = colorScheme.outlineVariant,
            ),
        )
    }
}

@Composable
private fun ScheduleTimelinePane(
    state: ScheduleUiState,
    onSelectFilter: (ScheduleFilter) -> Unit,
    onManageTags: () -> Unit,
    onToggleComplete: (ScheduleEntity) -> Unit,
    onDelete: (ScheduleEntity) -> Unit,
    onEdit: (ScheduleEntity) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.testTag("home_schedule_list"),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(bottom = 24.dp),
    ) {
        item(key = "filter_row") {
            FilterChipsRow(
                selected = state.selectedFilter,
                selectedDateMillis = state.selectedDateMillis,
                customTagCount = state.customTags.size,
                onSelect = onSelectFilter,
                onManageTags = onManageTags,
            )
        }
        scheduleTimelineItems(
            groups = state.groupedSchedules,
            nowMillis = state.nowMillis,
            selectedDateMillis = state.selectedDateMillis,
            isDayScopedFilter = state.isDayScopedFilter,
            showDateBadge = !state.isDayScopedFilter,
            customTags = state.customTags,
            onToggleComplete = onToggleComplete,
            onDelete = onDelete,
            onEdit = onEdit,
        )
    }
}

@Composable
private fun EmptyScheduleCard(
    selectedDateMillis: Long,
    nowMillis: Long,
    isDayScopedFilter: Boolean,
) {
    val colorScheme = MaterialTheme.colorScheme
    val flowColors = FlowTheme.colors
    val cardShape = MaterialTheme.shapes.large
    val isCustomNonToday = isDayScopedFilter && !DateTimeUtils.isSameDay(selectedDateMillis, nowMillis)

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("empty_schedule_card"),
        shape = cardShape,
        color = flowColors.cardElevated,
        border = BorderStroke(1.dp, colorScheme.outlineVariant),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (flowColors.isDark) {
                Image(
                    painter = painterResource(id = R.drawable.img_empty_schedule_1795285804505),
                    contentDescription = stringResource(R.string.cd_empty_illustration),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(148.dp)
                        .clip(MaterialTheme.shapes.medium),
                )
            } else {
                // Airy ColorOS 17 Aquamorphic light-mode serenity header
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(132.dp)
                        .clip(MaterialTheme.shapes.medium)
                        .background(
                            Brush.linearGradient(
                                colors = listOf(
                                    colorScheme.primaryContainer.copy(alpha = 0.7f),
                                    colorScheme.secondaryContainer.copy(alpha = 0.75f),
                                ),
                            ),
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        modifier = Modifier
                            .size(58.dp)
                            .clip(CircleShape)
                            .background(colorScheme.surfaceContainerLowest),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.EventAvailable,
                            contentDescription = stringResource(R.string.cd_empty_illustration),
                            tint = colorScheme.primary,
                            modifier = Modifier.size(30.dp),
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = if (isCustomNonToday) {
                    "${DateTimeUtils.formatDateShort(selectedDateMillis)} 暂无安排日程"
                } else {
                    stringResource(R.string.empty_schedule_title)
                },
                style = MaterialTheme.typography.titleMedium,
                color = colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = if (isCustomNonToday) {
                    "左右滑动可切换前一天 / 后一天，或点击右上角「+ 新建」/ ✨ 添加日程"
                } else {
                    "点击右上角「+ 新建」或 ✨ 一句话快速创建你的下一条日程"
                },
                style = MaterialTheme.typography.bodySmall,
                color = colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}
