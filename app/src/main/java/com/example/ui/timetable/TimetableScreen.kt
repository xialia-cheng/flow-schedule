package com.example.ui.timetable

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import com.example.ui.theme.FlowMotion
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CalendarViewDay
import androidx.compose.material.icons.rounded.CalendarViewWeek
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.FormatListBulleted
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.CourseColorPalette
import com.example.data.CourseEntity
import com.example.data.DateTimeUtils
import com.example.data.ScheduleEntity
import com.example.data.BellScheduleGenerator
import com.example.data.SectionTimeEntity
import com.example.notification.LiveActivityItem
import com.example.notification.LiveUpdatePermissionStatus
import com.example.ui.components.CourseLongPressSheet
import com.example.ui.components.FluidCloudCapsuleBanner
import com.example.ui.components.SmartCaptureFab
import com.example.ui.components.rememberAppHaptics
import com.example.ui.components.rememberScrollAwareFabState
import com.example.ui.theme.FlowTheme
import com.example.ui.theme.MonoFontFamily
import kotlinx.coroutines.delay
import androidx.compose.ui.input.nestedscroll.nestedScroll
import java.util.Calendar

@Composable
fun TimetableScreen(
    viewModel: TimetableViewModel,
    schedules: List<ScheduleEntity>,
    activeLiveActivity: LiveActivityItem?,
    permissionStatus: LiveUpdatePermissionStatus,
    onToggleScheduleComplete: (ScheduleEntity) -> Unit,
    onEditSchedule: (ScheduleEntity) -> Unit,
    onLongClickSchedule: (ScheduleEntity) -> Unit = {},
    onOpenSmartCapture: () -> Unit,
    onShowMessage: (String) -> Unit,
    onOpenPermissions: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = rememberAppHaptics()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val toastMessage by viewModel.toastMessage.collectAsStateWithLifecycle()

    LaunchedEffect(toastMessage) {
        val msg = toastMessage ?: return@LaunchedEffect
        onShowMessage(msg)
        viewModel.consumeToast()
    }

    // Default read-only browse mode to prevent accidental blank-cell popups
    var isEditMode by rememberSaveable { mutableStateOf(false) }

    // Back press while in Day View returns cleanly to Week View
    BackHandler(enabled = state.viewMode == TimetableViewMode.DAY && !isEditMode) {
        viewModel.setViewMode(TimetableViewMode.WEEK)
    }
    // Back press while in Edit Mode exits Edit Mode first
    BackHandler(enabled = isEditMode) {
        isEditMode = false
    }

    var showCourseEditor by remember { mutableStateOf(false) }
    var editingCourse by remember { mutableStateOf<CourseEntity?>(null) }
    var editorInitialDay by remember { mutableIntStateOf(1) }
    var editorInitialSection by remember { mutableIntStateOf(1) }
    var inspectingCourse by remember { mutableStateOf<CourseEntity?>(null) }
    var longPressedCourse by remember { mutableStateOf<CourseEntity?>(null) }

    var showImportSheet by remember { mutableStateOf(false) }
    var showShareSheet by remember { mutableStateOf(false) }
    var showMoreSheet by remember { mutableStateOf(false) }
    var showCourseOverviewDialog by remember { mutableStateOf(false) }
    var showTodayProgressDialog by remember { mutableStateOf(false) }

    // Live ticker: 1s while a Fluid Cloud Live Activity is active, otherwise 15s for timetable progress
    var liveNowMillis by remember(state.nowMillis) { mutableLongStateOf(state.nowMillis) }
    val hasActiveLive = activeLiveActivity != null
    LaunchedEffect(hasActiveLive) {
        while (true) {
            delay(if (hasActiveLive) 1_000L else 15_000L)
            liveNowMillis = System.currentTimeMillis()
        }
    }

    val todayProgress = remember(state.courses, state.sectionTimes, state.currentActualWeek, liveNowMillis) {
        computeTodayCourseProgress(
            courses = state.courses,
            sectionTimes = state.sectionTimes,
            currentActualWeek = state.currentActualWeek,
            nowMillis = liveNowMillis,
        )
    }

    val colorScheme = MaterialTheme.colorScheme
    val flowColors = FlowTheme.colors
    val compactMode = state.settings.compactMode
    val showWeekend = state.settings.showWeekend
    val weekStartDay = state.settings.weekStartDay
    val visibleIsoDays = remember(weekStartDay, showWeekend) {
        DateTimeUtils.orderedIsoDays(weekStartDay, showWeekend)
    }
    val visibleWeekDates = remember(state.weekDatesMillis, visibleIsoDays) {
        val byIso = state.weekDatesMillis.associateBy { DateTimeUtils.isoDayOfWeek(it) }
        visibleIsoDays.mapNotNull { byIso[it] }.ifEmpty { state.weekDatesMillis.take(visibleIsoDays.size) }
    }
    val dayCount = visibleIsoDays.size
    val timeAxisWidth = 44.dp
    val baseRowHeightDp = state.settings.timetableRowHeightDp.coerceIn(54, 108)
    val sectionHeight = if (compactMode) (baseRowHeightDp - 12).coerceAtLeast(54).dp else baseRowHeightDp.dp
    val fontScale = state.settings.timetableFontScale.coerceIn(0.80f, 1.35f)

    Column(
        modifier = modifier
            .fillMaxSize()
            .testTag("timetable_screen"),
    ) {
        // 1. Top Bar: Week Header, Selected Date Subtitle, Edit Mode Toggle, Week/Day View Switcher & Actions
        TimetableTopBar(
            state = state,
            nowMillis = liveNowMillis,
            isEditMode = isEditMode,
            onTapWeekHeader = {
                if (!state.isViewingCurrentWeek) {
                    viewModel.jumpToCurrentWeek()
                } else {
                    showMoreSheet = true
                }
            },
            onJumpToday = { viewModel.jumpToCurrentWeek() },
            onToggleEditMode = { isEditMode = !isEditMode },
            onToggleViewMode = { viewModel.toggleViewMode() },
            onImportClick = { showImportSheet = true },
            onMoreClick = { showMoreSheet = true },
        )

        // 1.5 Explicit Edit Mode Banner when editing is enabled
        AnimatedVisibility(
            visible = isEditMode,
            enter = expandVertically(animationSpec = tween(180)) + fadeIn(animationSpec = tween(160)),
            exit = shrinkVertically(animationSpec = tween(160)) + fadeOut(animationSpec = tween(120)),
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("timetable_edit_mode_banner"),
                color = colorScheme.primaryContainer,
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Edit,
                            contentDescription = null,
                            tint = colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(15.dp),
                        )
                        Text(
                            text = "编辑模式 · 点空白加课，点课程改删",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = colorScheme.onPrimaryContainer,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Surface(
                        onClick = {
                            haptics.tick()
                            showCourseOverviewDialog = true
                        },
                        shape = CircleShape,
                        color = colorScheme.surface.copy(alpha = 0.85f),
                        modifier = Modifier.testTag("timetable_course_overview_button"),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.FormatListBulleted,
                                contentDescription = "课程总览与批量删除",
                                tint = colorScheme.onSurface,
                                modifier = Modifier.size(13.dp),
                            )
                            Text(
                                text = "总览",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = colorScheme.onSurface,
                                maxLines = 1,
                                softWrap = false,
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Surface(
                        onClick = {
                            haptics.tick()
                            editingCourse = null
                            editorInitialDay = state.selectedDayOfWeek.coerceIn(1, dayCount)
                            editorInitialSection = 1
                            showCourseEditor = true
                        },
                        shape = CircleShape,
                        color = colorScheme.primary,
                        modifier = Modifier.testTag("timetable_add_button"),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Add,
                                contentDescription = "添加课程",
                                tint = colorScheme.onPrimary,
                                modifier = Modifier.size(14.dp),
                            )
                            Text(
                                text = "加课",
                                style = MaterialTheme.typography.labelSmall,
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

        // 2. Interactive Weekday & Date Strip with "今日进度" mini-indicator crowning the time column
        WeekdayDateStrip(
            weekDatesMillis = visibleWeekDates,
            visibleIsoDays = visibleIsoDays,
            selectedDateMillis = state.selectedDateMillis,
            nowMillis = liveNowMillis,
            courses = state.courses,
            displayedWeek = state.displayedWeek,
            timeAxisWidth = timeAxisWidth,
            todayProgress = todayProgress,
            onSelectDate = { viewModel.selectDate(it) },
            onClickTodayProgress = { showTodayProgressDialog = true },
        )

        // 2.5 Active ColorOS 17 Fluid Cloud / Android 16 Live Activity real-time capsule strip
        val isFluidCloudVisible = activeLiveActivity != null && activeLiveActivity.isLiveEligible(liveNowMillis)
        var retainedFluidCloudItem by remember { mutableStateOf<LiveActivityItem?>(null) }
        if (isFluidCloudVisible && activeLiveActivity != null) {
            retainedFluidCloudItem = activeLiveActivity
        }
        AnimatedVisibility(
            visible = isFluidCloudVisible && retainedFluidCloudItem != null,
            enter = fadeIn(
                animationSpec = tween(durationMillis = 320, easing = FastOutSlowInEasing),
            ) + expandVertically(
                animationSpec = FlowMotion.standardSpring(),
                expandFrom = Alignment.Top,
            ) + scaleIn(
                initialScale = 0.96f,
                animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing),
            ),
            exit = fadeOut(
                animationSpec = tween(durationMillis = 240, easing = FastOutSlowInEasing),
            ) + shrinkVertically(
                animationSpec = tween(durationMillis = 260, easing = FastOutSlowInEasing),
                shrinkTowards = Alignment.Top,
            ) + scaleOut(
                targetScale = 0.96f,
                animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
            ),
            label = "timetable_fluid_cloud_visibility",
        ) {
            retainedFluidCloudItem?.let { liveItem ->
                FluidCloudCapsuleBanner(
                    item = liveItem,
                    permissionStatus = permissionStatus,
                    nowMillis = liveNowMillis,
                    onOpenPermissions = onOpenPermissions,
                )
            }
        }

        // 3. Optional Custom Extra Time Slot Bar (e.g. Morning Exercise, shown in Week View)
        if (state.settings.showMorningExercise && !compactMode && state.viewMode == TimetableViewMode.WEEK && !state.isMorningExerciseCheckedToday) {
            MorningExerciseStrip(
                title = state.settings.extraBannerTitle,
                startTime = state.settings.extraBannerStartTime,
                endTime = state.settings.extraBannerEndTime,
                checkedToday = state.isMorningExerciseCheckedToday,
                onToggleCheckIn = { haptics.confirm(); viewModel.toggleMorningExerciseCheckIn() },
            )
        }

        // 4. Main Content Area: Week Grid View OR Swipeable Day Timeline View
        val scrollState = rememberScrollState()
        val fabScrollState = rememberScrollAwareFabState()
        LaunchedEffect(state.viewMode, state.displayedWeek, state.selectedDateMillis) {
            fabScrollState.expand()
        }
        LaunchedEffect(scrollState.value, state.viewMode) {
            if (state.viewMode == TimetableViewMode.WEEK && scrollState.value <= 12) {
                fabScrollState.expand()
            }
        }
        val sectionCount = state.sectionTimes.size.coerceIn(
            BellScheduleGenerator.MIN_SECTION_COUNT,
            BellScheduleGenerator.MAX_SECTION_COUNT,
        )
        val resolvedLunchBreak: ResolvedLunchBreak? = remember(
            state.settings,
            state.sectionTimes,
            sectionCount,
        ) {
            resolveLunchBreak(
                settings = state.settings,
                sectionTimes = state.sectionTimes,
                sectionCount = sectionCount,
            )
        }
        val lunchAfterSection = resolvedLunchBreak?.afterSection
        val lunchBreakHeight = if (resolvedLunchBreak != null) {
            when {
                compactMode -> 24.dp
                resolvedLunchBreak.axisRangeText.isNotEmpty() -> 38.dp
                else -> 28.dp
            }
        } else {
            0.dp
        }
        val totalGridHeight = sectionHeight * sectionCount + lunchBreakHeight

        Spacer(modifier = Modifier.height(4.dp))
        HorizontalDivider(
            thickness = 0.5.dp,
            color = colorScheme.outlineVariant.copy(alpha = 0.45f),
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .nestedScroll(fabScrollState.nestedScrollConnection),
        ) {
            AnimatedContent(
                targetState = state.viewMode,
                transitionSpec = {
                    fadeIn(animationSpec = tween(200)) togetherWith fadeOut(animationSpec = tween(150))
                },
                label = "timetable_view_mode_transition",
            ) { mode ->
                when (mode) {
                    TimetableViewMode.WEEK -> {
                        val totalWeeks = (state.activeTimetable?.totalWeeks ?: 20).coerceAtLeast(1)
                        val weekPagerState = rememberPagerState(
                            initialPage = (state.displayedWeek - 1).coerceIn(0, totalWeeks - 1),
                            pageCount = { totalWeeks },
                        )

                        // Sync external week changes (top bar / jump today / Day View cross-week) into HorizontalPager
                        LaunchedEffect(state.displayedWeek, totalWeeks) {
                            val targetPage = (state.displayedWeek - 1).coerceIn(0, totalWeeks - 1)
                            if (weekPagerState.currentPage != targetPage) {
                                if (kotlin.math.abs(weekPagerState.currentPage - targetPage) <= 3) {
                                    weekPagerState.animateScrollToPage(targetPage)
                                } else {
                                    weekPagerState.scrollToPage(targetPage)
                                }
                            }
                        }

                        // Sync HorizontalPager swipe settlement back to TimetableViewModel
                        LaunchedEffect(weekPagerState.settledPage) {
                            val settledWeek = weekPagerState.settledPage + 1
                            if (settledWeek != state.displayedWeek) {
                                viewModel.selectWeek(settledWeek)
                            }
                        }

                        HorizontalPager(
                            state = weekPagerState,
                            modifier = Modifier
                                .fillMaxSize()
                                .testTag("timetable_week_pager"),
                        ) { pageIndex ->
                            val pageWeek = pageIndex + 1
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .verticalScroll(scrollState)
                                    .padding(bottom = 84.dp),
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(totalGridHeight),
                                ) {
                                    // Left dynamic 1..N section & bell time axis (high-contrast tinted rail)
                                    SectionTimeAxisColumn(
                                        sectionTimes = state.sectionTimes,
                                        sectionCount = sectionCount,
                                        sectionHeight = sectionHeight,
                                        resolvedLunchBreak = resolvedLunchBreak,
                                        lunchBreakHeight = lunchBreakHeight,
                                        compactMode = compactMode,
                                        nowMillis = liveNowMillis,
                                        highlightCurrentSection = pageWeek == state.currentActualWeek,
                                        modifier = Modifier
                                            .width(timeAxisWidth)
                                            .fillMaxHeight(),
                                    )

                                    // Right course cards grid for pageWeek
                                    BoxWithConstraints(
                                        modifier = Modifier
                                            .weight(1f)
                                            .fillMaxHeight()
                                            .padding(end = 6.dp),
                                    ) {
                                        val colWidth = maxWidth / dayCount
                                        val gridLineColor = colorScheme.outlineVariant.copy(
                                            alpha = if (flowColors.isDark) 0.34f else 0.32f,
                                        )
                                        val selectedColIndex = visibleIsoDays.indexOf(state.selectedDayOfWeek)
                                            .takeIf { it in 0 until dayCount }
                                        val selectedColHighlight = colorScheme.primary.copy(
                                            alpha = if (flowColors.isDark) 0.14f else 0.085f,
                                        )
                                        val selectedColEdge = colorScheme.primary.copy(
                                            alpha = if (flowColors.isDark) 0.42f else 0.28f,
                                        )

                                        // Selected day column soft backdrop + horizontal/vertical dashed gridlines
                                        Canvas(modifier = Modifier.fillMaxSize()) {
                                            if (selectedColIndex != null) {
                                                val colX = (colWidth * selectedColIndex).toPx()
                                                val colW = colWidth.toPx()
                                                drawRect(
                                                    color = selectedColHighlight,
                                                    topLeft = Offset(colX, 0f),
                                                    size = Size(colW, size.height),
                                                )
                                                drawRect(
                                                    color = selectedColEdge,
                                                    topLeft = Offset(colX, 0f),
                                                    size = Size(colW, 2.5.dp.toPx()),
                                                )
                                            }

                                            if (lunchAfterSection != null && lunchBreakHeight > 0.dp) {
                                                val lunchY = (sectionHeight * lunchAfterSection).toPx()
                                                val lunchH = lunchBreakHeight.toPx()
                                                drawRect(
                                                    color = colorScheme.surfaceContainer.copy(
                                                        alpha = if (flowColors.isDark) 0.58f else 0.78f,
                                                    ),
                                                    topLeft = Offset(0f, lunchY),
                                                    size = Size(size.width, lunchH),
                                                )
                                            }
                                            for (sec in 1..sectionCount) {
                                                val extraLunch = if (lunchAfterSection != null && sec > lunchAfterSection) lunchBreakHeight else 0.dp
                                                val y = (sectionHeight * sec + extraLunch).toPx()
                                                drawLine(
                                                    color = gridLineColor,
                                                    start = Offset(0f, y),
                                                    end = Offset(size.width, y),
                                                    strokeWidth = 0.5.dp.toPx(),
                                                )
                                                if (lunchAfterSection != null && sec == lunchAfterSection && lunchBreakHeight > 0.dp) {
                                                    val lunchTopY = (sectionHeight * sec).toPx()
                                                    drawLine(
                                                        color = gridLineColor,
                                                        start = Offset(0f, lunchTopY),
                                                        end = Offset(size.width, lunchTopY),
                                                        strokeWidth = 0.5.dp.toPx(),
                                                    )
                                                }
                                            }
                                            for (col in 1 until dayCount) {
                                                val x = (colWidth * col).toPx()
                                                drawLine(
                                                    color = gridLineColor.copy(alpha = if (flowColors.isDark) 0.22f else 0.20f),
                                                    start = Offset(x, 0f),
                                                    end = Offset(x, size.height),
                                                    strokeWidth = 0.5.dp.toPx(),
                                                )
                                            }
                                        }

                                        // Empty slots are ONLY clickable in Edit Mode to prevent accidental popups while browsing/swiping

                                        if (isEditMode) {
                                            Column(modifier = Modifier.fillMaxSize()) {
                                                for (section in 1..sectionCount) {
                                                    Row(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .height(sectionHeight),
                                                    ) {
                                                        visibleIsoDays.forEach { isoDay ->
                                                            Box(
                                                                modifier = Modifier
                                                                    .weight(1f)
                                                                    .fillMaxHeight()
                                                                    .clickable {
                                                                        haptics.tick()
                                                                        editingCourse = null
                                                                        editorInitialDay = isoDay
                                                                        editorInitialSection = section
                                                                        showCourseEditor = true
                                                                    },
                                                            )
                                                        }
                                                    }
                                                    if (lunchAfterSection == section && lunchBreakHeight > 0.dp) {
                                                        Spacer(modifier = Modifier.height(lunchBreakHeight))
                                                    }
                                                }
                                            }
                                        }

                                        // Deduplicate overlapping courses in the same (day, section) slot so cards never stack/overlap text
                                        val visibleIsoSet = visibleIsoDays.toSet()
                                        val candidateCourses = state.courses
                                            .filter { it.dayOfWeek in visibleIsoSet && it.startSection <= sectionCount }
                                            .filter { course ->
                                                val inWeek = course.isInWeek(pageWeek)
                                                inWeek || (state.settings.showNonCurrentWeekCourses && !compactMode)
                                            }
                                            .sortedWith(
                                                compareByDescending<CourseEntity> { it.isInWeek(pageWeek) }
                                                    .thenBy { if (it.endWeek >= pageWeek) 0 else 1 }
                                                    .thenBy { kotlin.math.abs(it.startWeek - pageWeek) }
                                            )
                                        val visibleCourses = mutableListOf<CourseEntity>()
                                        candidateCourses.forEach { candidate ->
                                            val overlapsExisting = visibleCourses.any { existing ->
                                                existing.dayOfWeek == candidate.dayOfWeek &&
                                                    existing.startSection <= candidate.endSection &&
                                                    candidate.startSection <= existing.endSection
                                            }
                                            if (!overlapsExisting) {
                                                visibleCourses.add(candidate)
                                            }
                                        }

                                        visibleCourses.forEach { course ->
                                            val colIdx = visibleIsoDays.indexOf(course.dayOfWeek)
                                            if (colIdx < 0) return@forEach
                                            val isCurrentWeekCourse = course.isInWeek(pageWeek)
                                            val clampedStart = course.startSection.coerceIn(1, sectionCount)
                                            val clampedEnd = course.endSection.coerceIn(clampedStart, sectionCount)
                                            val startLunchOffset = if (lunchAfterSection != null && clampedStart > lunchAfterSection) lunchBreakHeight else 0.dp
                                            val crossesLunch = lunchAfterSection != null && clampedStart <= lunchAfterSection && clampedEnd > lunchAfterSection
                                            val topOffset = sectionHeight * (clampedStart - 1) + startLunchOffset
                                            val spanCount = (clampedEnd - clampedStart + 1).coerceAtLeast(1)
                                            val cardHeight = sectionHeight * spanCount + (if (crossesLunch) lunchBreakHeight else 0.dp)
                                            val leftOffset = colWidth * colIdx

                                            CourseGridCard(
                                                course = course,
                                                isCurrentWeek = isCurrentWeekCourse,
                                                compactMode = compactMode,
                                                fontScale = fontScale,
                                                onClick = { haptics.tick(); inspectingCourse = course },
                                                onLongClick = { haptics.longPress(); longPressedCourse = course },
                                                modifier = Modifier
                                                    .offset(x = leftOffset, y = topOffset)
                                                    .width(colWidth)
                                                    .height(cardHeight)
                                                    .padding(horizontal = 1.dp, vertical = 1.dp),
                                            )
                                        }

                                        // Thin current-time indicator line on Today's column when viewing the actual current week
                                        val todayColIndex = if (pageWeek == state.currentActualWeek) {
                                            visibleIsoDays.indexOf(DateTimeUtils.isoDayOfWeek(liveNowMillis))
                                                .takeIf { it in 0 until dayCount }
                                        } else {
                                            null
                                        }
                                        val currentTimeOffsetDp = if (todayColIndex != null) {
                                            computeCurrentTimeGridOffsetDp(
                                                sectionTimes = state.sectionTimes,
                                                sectionCount = sectionCount,
                                                sectionHeightDp = sectionHeight.value,
                                                lunchAfterSection = lunchAfterSection,
                                                lunchBreakHeightDp = lunchBreakHeight.value,
                                                nowMillis = liveNowMillis,
                                            )
                                        } else {
                                            null
                                        }
                                        if (todayColIndex != null && currentTimeOffsetDp != null) {
                                            val indicatorColor = colorScheme.primary
                                            val indicatorHalo = colorScheme.surface.copy(alpha = 0.88f)
                                            Canvas(
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .zIndex(1.5f),
                                            ) {
                                                val colX = (colWidth * todayColIndex).toPx()
                                                val colW = colWidth.toPx()
                                                val y = currentTimeOffsetDp.dp.toPx().coerceIn(2.dp.toPx(), size.height - 2.dp.toPx())
                                                val dotRadius = 3.2.dp.toPx()
                                                val dotCenterX = colX + dotRadius + 1.dp.toPx()
                                                val lineEndX = (colX + colW - 1.5.dp.toPx()).coerceAtLeast(dotCenterX)

                                                drawLine(
                                                    color = indicatorHalo,
                                                    start = Offset(dotCenterX, y),
                                                    end = Offset(lineEndX, y),
                                                    strokeWidth = 2.6.dp.toPx(),
                                                )
                                                drawLine(
                                                    color = indicatorColor,
                                                    start = Offset(dotCenterX, y),
                                                    end = Offset(lineEndX, y),
                                                    strokeWidth = 1.5.dp.toPx(),
                                                )
                                                drawCircle(
                                                    color = indicatorHalo,
                                                    radius = dotRadius + 1.2.dp.toPx(),
                                                    center = Offset(dotCenterX, y),
                                                )
                                                drawCircle(
                                                    color = indicatorColor,
                                                    radius = dotRadius,
                                                    center = Offset(dotCenterX, y),
                                                )
                                            }
                                        }

                                        // Draw lunch break pill AFTER course cards, centered inside the widest unobstructed column run
                                        // (or elevated above cards with an opaque backdrop if every column spans across the break)
                                        if (resolvedLunchBreak != null && lunchBreakHeight > 0.dp) {
                                            val spannedColIndices = visibleCourses.mapNotNull { course ->
                                                val clampedStart = course.startSection.coerceIn(1, sectionCount)
                                                val clampedEnd = course.endSection.coerceIn(clampedStart, sectionCount)
                                                val crosses = clampedStart <= resolvedLunchBreak.afterSection &&
                                                    clampedEnd > resolvedLunchBreak.afterSection
                                                if (crosses) {
                                                    visibleIsoDays.indexOf(course.dayOfWeek).takeIf { it >= 0 }
                                                } else {
                                                    null
                                                }
                                            }.toSet()
                                            val placement = computeLunchBreakPlacement(
                                                dayCount = dayCount,
                                                spannedColIndices = spannedColIndices,
                                            )
                                            val labelText = when {
                                                placement.spanCols >= 3 -> resolvedLunchBreak.gridDisplayText
                                                placement.spanCols == 2 -> resolvedLunchBreak.compactGridDisplayText
                                                else -> resolvedLunchBreak.title
                                            }
                                            Box(
                                                modifier = Modifier
                                                    .offset(
                                                        x = colWidth * placement.startCol,
                                                        y = sectionHeight * resolvedLunchBreak.afterSection,
                                                    )
                                                    .width(colWidth * placement.spanCols)
                                                    .height(lunchBreakHeight)
                                                    .zIndex(2f),
                                                contentAlignment = Alignment.Center,
                                            ) {
                                                Surface(
                                                    shape = CircleShape,
                                                    color = colorScheme.surfaceContainerHigh.copy(
                                                        alpha = if (placement.isOverlappingFallback) 0.96f else 0.92f,
                                                    ),
                                                    border = BorderStroke(
                                                        width = 0.75.dp,
                                                        color = colorScheme.outlineVariant.copy(
                                                            alpha = if (flowColors.isDark) 0.8f else 0.65f,
                                                        ),
                                                    ),
                                                    shadowElevation = if (placement.isOverlappingFallback) 2.dp else 0.5.dp,
                                                    modifier = Modifier
                                                        .padding(horizontal = 4.dp)
                                                        .testTag("timetable_lunch_break_pill"),
                                                ) {
                                                    Text(
                                                        text = labelText,
                                                        style = MaterialTheme.typography.labelSmall,
                                                        fontSize = 10.sp,
                                                        fontWeight = FontWeight.Medium,
                                                        color = colorScheme.onSurfaceVariant,
                                                        maxLines = 1,
                                                        softWrap = false,
                                                        overflow = TextOverflow.Ellipsis,
                                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
                                                    )
                                                }
                                            }
                                        }

                                        // Gentle empty-timetable hint overlay positioned cleanly above the bottom-right FAB
                                        if (state.courses.isEmpty()) {
                                            Surface(
                                                modifier = Modifier
                                                    .align(Alignment.TopCenter)
                                                    .widthIn(max = 520.dp)
                                                    .padding(start = 20.dp, end = 24.dp, top = 84.dp, bottom = 88.dp),
                                                shape = MaterialTheme.shapes.large,
                                                color = flowColors.cardElevated.copy(alpha = 0.94f),
                                                border = BorderStroke(1.dp, colorScheme.outlineVariant),
                                            ) {
                                                Column(
                                                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp),
                                                    horizontalAlignment = Alignment.CenterHorizontally,
                                                    verticalArrangement = Arrangement.spacedBy(6.dp),
                                                ) {
                                                    Text(
                                                        text = if (isEditMode) {
                                                            "点击任意空白节次或顶部「加课」添加课程"
                                                        } else {
                                                            "开启顶部「编辑课表」后即可点空白加课"
                                                        },
                                                        style = MaterialTheme.typography.titleSmall,
                                                        color = colorScheme.onSurface,
                                                        textAlign = TextAlign.Center,
                                                    )
                                                    Text(
                                                        text = "也支持右上角「导入」CSV / HTML / 分享口令 / JSON 备份",
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = colorScheme.onSurfaceVariant,
                                                        textAlign = TextAlign.Center,
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    TimetableViewMode.DAY -> {
                        TimetableDayTimelineView(
                            selectedDateMillis = state.selectedDateMillis,
                            displayedWeek = state.displayedWeek,
                            courses = state.courses,
                            sectionTimes = state.sectionTimes,
                            schedules = schedules,
                            showNonCurrentWeekCourses = state.settings.showNonCurrentWeekCourses,
                            isEditMode = isEditMode,
                            nowMillis = liveNowMillis,
                            onShiftDay = { delta -> viewModel.shiftSelectedDate(delta) },
                            onClickCourse = { inspectingCourse = it },
                            onLongClickCourse = { longPressedCourse = it },
                            onAddCourseForDay = { dayOfWeek ->
                                editingCourse = null
                                editorInitialDay = dayOfWeek
                                editorInitialSection = 1
                                showCourseEditor = true
                            },
                            onToggleScheduleComplete = onToggleScheduleComplete,
                            onEditSchedule = onEditSchedule,
                            onLongClickSchedule = onLongClickSchedule,
                        )
                    }
                }
            }

            // Unified sparkle trigger button (bottom-right) with spring rebound & scroll collapse/expand
            SmartCaptureFab(
                visible = fabScrollState.isVisible,
                contentDescription = "一句话智能创建日程与课程",
                testTag = "timetable_smart_capture_fab",
                onClick = onOpenSmartCapture,
                modifier = Modifier.align(Alignment.BottomEnd),
            )
        }
    }

    if (showCourseEditor) {
        CourseEditorSheet(
            editingCourse = editingCourse,
            initialDayOfWeek = editorInitialDay,
            initialStartSection = editorInitialSection,
            totalWeeks = state.activeTimetable?.totalWeeks ?: 20,
            maxSectionCount = state.sectionTimes.size.coerceIn(
                BellScheduleGenerator.MIN_SECTION_COUNT,
                BellScheduleGenerator.MAX_SECTION_COUNT,
            ),
            sectionTimes = state.sectionTimes,
            onDismiss = {
                showCourseEditor = false
                editingCourse = null
            },
            onSave = { id, name, loc, teacher, day, startS, endS, startW, endW, weekType, colorIdx, notes, customHex, customWeeksCsv ->
                viewModel.saveCourse(
                    existingId = id,
                    name = name,
                    location = loc,
                    teacher = teacher,
                    dayOfWeek = day,
                    startSection = startS,
                    endSection = endS,
                    startWeek = startW,
                    endWeek = endW,
                    weekType = weekType,
                    colorIndex = colorIdx,
                    notes = notes,
                    customColorHex = customHex,
                    customWeeksCsv = customWeeksCsv,
                )
            },
            onDelete = if (editingCourse != null) {
                { viewModel.deleteCourse(it) }
            } else {
                null
            },
        )
    }

    inspectingCourse?.let { course ->
        val otherSlotCourses = state.courses.filter { other ->
            other.id != course.id &&
                other.dayOfWeek == course.dayOfWeek &&
                other.startSection <= course.endSection &&
                course.startSection <= other.endSection
        }
        CourseDetailDialog(
            course = course,
            sectionTimes = state.sectionTimes,
            displayedWeek = state.displayedWeek,
            otherSlotCourses = otherSlotCourses,
            canEdit = isEditMode,
            onSelectCourse = { inspectingCourse = it },
            onDismiss = { inspectingCourse = null },
            onEdit = { target ->
                editingCourse = target
                showCourseEditor = true
            },
            onDelete = { target ->
                viewModel.deleteCourse(target)
            },
        )
    }

    longPressedCourse?.let { rawCourse ->
        val currentCourse = state.courses.firstOrNull { it.id == rawCourse.id } ?: rawCourse
        val secLabel = if (currentCourse.startSection == currentCourse.endSection) {
            "第${currentCourse.startSection}节"
        } else {
            "第${currentCourse.startSection}-${currentCourse.endSection}节"
        }
        val activeReReminder = remember(currentCourse.id, state.reReminderVersion) {
            viewModel.getCourseReReminderMillis(currentCourse.id)
        }
        CourseLongPressSheet(
            course = currentCourse,
            sectionLabel = secLabel,
            courseReminderEnabled = state.settings.courseReminderEnabled,
            courseReminderLeadMinutes = state.settings.courseReminderLeadMinutes,
            activeReReminderMillis = activeReReminder,
            onDismiss = { longPressedCourse = null },
            onMoveToDay = { dayOfWeek -> viewModel.moveCourseToDay(currentCourse, dayOfWeek) },
            onMoveToDate = { dateMillis -> viewModel.moveCourseToDate(currentCourse, dateMillis) },
            onSelectColor = { colorIdx -> viewModel.updateCourseColor(currentCourse, colorIdx) },
            onScheduleReReminder = { minutes -> viewModel.scheduleCourseReReminder(currentCourse, minutes) },
            onCancelReReminder = { viewModel.cancelCourseReReminder(currentCourse) },
            onSetCourseReminderLead = { minutes ->
                if (!state.settings.courseReminderEnabled) {
                    viewModel.setCourseReminderEnabled(true)
                }
                viewModel.setCourseReminderLeadMinutes(minutes)
            },
            onDisableCourseReminder = { viewModel.setCourseReminderEnabled(false) },
            onInspect = { inspectingCourse = currentCourse },
            onEdit = {
                editingCourse = currentCourse
                showCourseEditor = true
            },
            onDelete = { viewModel.deleteCourse(currentCourse) },
        )
    }

    if (showImportSheet) {
        TimetableImportSheet(
            onImportCsv = { text, replace -> viewModel.importCsvText(text, replace) },
            onImportHtml = { html, replace -> viewModel.importHtmlText(html, replace) },
            onImportShareCode = { code, createNew -> viewModel.importShareCode(code, createNew) },
            onGenerateBackupJson = { viewModel.generateFullBackupJson() },
            onRestoreBackupJson = { json, mode -> viewModel.restoreFullBackupJson(json, mode) },
            onNotify = onShowMessage,
            onDismiss = { showImportSheet = false },
        )
    }

    if (showShareSheet) {
        TimetableShareSheet(
            timetableName = state.activeTimetable?.name ?: "我的课表",
            courseCount = state.courses.size,
            shareCode = viewModel.buildCurrentShareCode(),
            shareSummaryText = viewModel.buildCurrentShareText(),
            onCopied = onShowMessage,
            onDismiss = { showShareSheet = false },
        )
    }

    if (showMoreSheet) {
        TimetableMoreSheet(
            state = state,
            onSelectWeek = { viewModel.selectWeek(it) },
            onJumpCurrentWeek = { viewModel.jumpToCurrentWeek() },
            onSwitchTimetable = { viewModel.switchTimetable(it) },
            onCreateTimetable = { name, weeks -> viewModel.createTimetable(name, weeks) },
            onDeleteTimetable = { viewModel.deleteTimetable(it) },
            onSaveSectionTimes = { viewModel.updateSectionTimes(it) },
            onUpdateTimetableSettings = { name, monday, weeks ->
                viewModel.updateTimetableSettings(name, monday, weeks)
            },
            onEditCourse = { course ->
                editingCourse = course
                showCourseEditor = true
            },
            onDeleteCourse = { viewModel.deleteCourse(it) },
            onDeleteCourses = { viewModel.deleteCourses(it) },
            onSetShowMorningExercise = { viewModel.setShowMorningExercise(it) },
            onSetExtraBannerConfig = { title, start, end ->
                viewModel.setExtraBannerConfig(title, start, end)
            },
            onSetShowLunchBreak = { viewModel.setShowLunchBreak(it) },
            onSetLunchBreakConfig = { title, afterSec, start, end ->
                viewModel.setLunchBreakConfig(title, afterSec, start, end)
            },
            onSetShowNonCurrentWeek = { viewModel.setShowNonCurrentWeekCourses(it) },
            onSetShowWeekend = { viewModel.setShowWeekend(it) },
            onSetWeekStartDay = { viewModel.setWeekStartDay(it) },
            onSetTimetableRowHeightDp = { viewModel.setTimetableRowHeightDp(it) },
            onSetTimetableFontScale = { viewModel.setTimetableFontScale(it) },
            onSetCourseReminderEnabled = { viewModel.setCourseReminderEnabled(it) },
            onSetCourseReminderLeadMinutes = { viewModel.setCourseReminderLeadMinutes(it) },
            onSetDndLevel = { viewModel.setDndLevel(it) },
            onSetFluidCloudDisplayOptions = { loc, teacher, notes ->
                viewModel.setFluidCloudDisplayOptions(loc, teacher, notes)
            },
            onSetFluidCloudLockscreenDetails = { viewModel.setFluidCloudLockscreenDetails(it) },
            onSetThemeMode = { viewModel.setThemeMode(it) },
            onSetUseDynamicColor = { viewModel.setUseDynamicColor(it) },
            onSetCompactMode = { viewModel.setCompactMode(it) },
            onOpenPermissions = onOpenPermissions,
            onOpenImportExport = { showImportSheet = true },
            onOpenShare = { showShareSheet = true },
            onDismiss = { showMoreSheet = false },
        )
    }

    if (showCourseOverviewDialog) {
        CourseOverviewDialog(
            courses = state.courses,
            onEdit = { course ->
                showCourseOverviewDialog = false
                editingCourse = course
                showCourseEditor = true
            },
            onDeleteSingle = { viewModel.deleteCourse(it) },
            onDeleteBatch = { viewModel.deleteCourses(it) },
            onDismiss = { showCourseOverviewDialog = false },
        )
    }

    if (showTodayProgressDialog) {
        TodayProgressDetailDialog(
            progress = todayProgress,
            onDismiss = { showTodayProgressDialog = false },
        )
    }
}

@Composable
private fun TimetableTopBar(
    state: TimetableUiState,
    nowMillis: Long,
    isEditMode: Boolean,
    onTapWeekHeader: () -> Unit,
    onJumpToday: () -> Unit,
    onToggleEditMode: () -> Unit,
    onToggleViewMode: () -> Unit,
    onImportClick: () -> Unit,
    onMoreClick: () -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme
    val haptics = rememberAppHaptics()
    val isSelectedToday = DateTimeUtils.isSameDay(state.selectedDateMillis, nowMillis)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 6.dp, top = 6.dp, bottom = 4.dp),
    ) {
        // Row 1: Week title + Import, Share, More
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clip(MaterialTheme.shapes.small)
                    .clickable { haptics.tick(); onTapWeekHeader() }
                    .padding(vertical = 2.dp, horizontal = 2.dp)
                    .testTag("timetable_week_header"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = "第 ${state.displayedWeek} 周",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = colorScheme.onBackground,
                    maxLines = 1,
                    softWrap = false,
                )
                if (!state.isViewingCurrentWeek) {
                    Surface(
                        shape = CircleShape,
                        color = colorScheme.tertiary,
                        shadowElevation = 2.dp,
                        modifier = Modifier
                            .clip(CircleShape)
                            .clickable {
                                haptics.tick()
                                onJumpToday()
                            },
                    ) {
                        Text(
                            text = "回第${state.currentActualWeek}周",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = colorScheme.onTertiary,
                            maxLines = 1,
                            softWrap = false,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        )
                    }
                } else if (!state.settings.compactMode && state.allTimetables.size > 1 && state.activeTimetable != null) {
                    Surface(
                        shape = CircleShape,
                        color = colorScheme.primaryContainer,
                    ) {
                        Text(
                            text = state.activeTimetable.name,
                            style = MaterialTheme.typography.labelSmall,
                            color = colorScheme.onPrimaryContainer,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        )
                    }
                }
            }

            // Right: Import & More (Share moved into More sheet to reduce top bar noise)
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = { haptics.tick(); onImportClick() },
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("timetable_import_button"),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.FileDownload,
                        contentDescription = "导入课表",
                        tint = colorScheme.onBackground,
                    )
                }
                IconButton(
                    onClick = { haptics.tick(); onMoreClick() },
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("timetable_more_button"),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.MoreVert,
                        contentDescription = "更多设置",
                        tint = colorScheme.onBackground,
                    )
                }
            }
        }

        // Row 2: Selected date subtitle (+ quick "回今天") on left, Edit Mode & Week/Day toggle pills on right
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(end = 10.dp, bottom = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    text = DateTimeUtils.formatDateHeader(state.selectedDateMillis),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.testTag("timetable_selected_date_subtitle"),
                )
                if (!isSelectedToday) {
                    Surface(
                        shape = CircleShape,
                        color = colorScheme.primary,
                        shadowElevation = 2.dp,
                        modifier = Modifier
                            .clip(CircleShape)
                            .clickable {
                                haptics.tick()
                                onJumpToday()
                            }
                            .testTag("timetable_jump_today_pill"),
                    ) {
                        Text(
                            text = "回今天",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = colorScheme.onPrimary,
                            maxLines = 1,
                            softWrap = false,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        )
                    }
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                // Accidental-touch-safe Edit Timetable toggle
                Surface(
                    shape = CircleShape,
                    color = if (isEditMode) colorScheme.primary else colorScheme.surfaceContainerHigh,
                    border = BorderStroke(
                        width = 1.dp,
                        color = if (isEditMode) colorScheme.primary else colorScheme.outlineVariant,
                    ),
                    modifier = Modifier
                        .clip(CircleShape)
                        .clickable { haptics.tick(); onToggleEditMode() }
                        .testTag("timetable_edit_mode_toggle"),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Icon(
                            imageVector = if (isEditMode) Icons.Rounded.Check else Icons.Rounded.Edit,
                            contentDescription = if (isEditMode) "完成编辑课表" else "进入编辑课表模式",
                            tint = if (isEditMode) colorScheme.onPrimary else colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(14.dp),
                        )
                        Text(
                            text = if (isEditMode) "完成编辑" else "编辑课表",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (isEditMode) colorScheme.onPrimary else colorScheme.onSurface,
                            maxLines = 1,
                            softWrap = false,
                        )
                    }
                }

                // Crisp Week View / Day View switcher pill
                val isDayMode = state.viewMode == TimetableViewMode.DAY
                Surface(
                    shape = CircleShape,
                    color = if (isDayMode) colorScheme.primaryContainer else colorScheme.surfaceContainerHigh,
                    border = BorderStroke(
                        width = 1.dp,
                        color = if (isDayMode) colorScheme.primary.copy(alpha = 0.55f) else colorScheme.outlineVariant,
                    ),
                    modifier = Modifier
                        .clip(CircleShape)
                        .clickable { haptics.tick(); onToggleViewMode() }
                        .testTag("timetable_view_mode_toggle"),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Icon(
                            imageVector = if (isDayMode) Icons.Rounded.CalendarViewDay else Icons.Rounded.CalendarViewWeek,
                            contentDescription = "切换周视图或日视图",
                            tint = if (isDayMode) colorScheme.primary else colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(14.dp),
                        )
                        Text(
                            text = if (isDayMode) "切周" else "切日",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (isDayMode) colorScheme.onPrimaryContainer else colorScheme.onSurface,
                            maxLines = 1,
                            softWrap = false,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WeekdayDateStrip(
    weekDatesMillis: List<Long>,
    visibleIsoDays: List<Int>,
    selectedDateMillis: Long,
    nowMillis: Long,
    courses: List<CourseEntity>,
    displayedWeek: Int,
    timeAxisWidth: Dp,
    todayProgress: TodayCourseProgress,
    onSelectDate: (Long) -> Unit,
    onClickTodayProgress: () -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme
    val flowColors = FlowTheme.colors
    val haptics = rememberAppHaptics()
    val animatedFraction by animateFloatAsState(
        targetValue = todayProgress.fraction,
        animationSpec = tween(durationMillis = 500, easing = FastOutSlowInEasing),
        label = "today_course_progress_header",
    )

    val daysWithCourses = remember(courses, displayedWeek) {
        courses.asSequence()
            .filter { it.isInWeek(displayedWeek) }
            .map { it.dayOfWeek }
            .toSet()
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = flowColors.cardElevated,
        border = BorderStroke(0.5.dp, colorScheme.outlineVariant),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(end = 6.dp, top = 5.dp, bottom = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Time-column header: compact "今日进度" summary indicator (elapsed / total course time)
            Column(
                modifier = Modifier
                    .width(timeAxisWidth)
                    .defaultMinSize(minHeight = 48.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable {
                        haptics.tick()
                        onClickTodayProgress()
                    }
                    .padding(horizontal = 2.dp, vertical = 2.dp)
                    .testTag("timetable_today_progress_indicator"),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = "今日进度",
                    fontSize = 9.sp,
                    lineHeight = 10.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (todayProgress.hasCourses) colorScheme.primary else colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    softWrap = false,
                )
                Spacer(modifier = Modifier.height(3.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.5.dp)
                        .clip(CircleShape)
                        .background(colorScheme.surfaceContainerHighest),
                ) {
                    if (todayProgress.hasCourses && animatedFraction > 0f) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(animatedFraction.coerceIn(0.06f, 1f))
                                .clip(CircleShape)
                                .background(
                                    Brush.horizontalGradient(
                                        colors = if (todayProgress.percent >= 100) {
                                            listOf(flowColors.completedAccent, flowColors.completedAccent)
                                        } else {
                                            listOf(colorScheme.primary, flowColors.liveAccent)
                                        },
                                    ),
                                ),
                        )
                    }
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = todayProgress.compactRatioLabel(),
                    fontFamily = MonoFontFamily,
                    fontSize = 8.5.sp,
                    lineHeight = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (todayProgress.hasCourses) colorScheme.onSurface else colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    softWrap = false,
                )
            }

            visibleIsoDays.forEachIndexed { index, isoDay ->
                val dateMillis = weekDatesMillis.getOrNull(index) ?: nowMillis
                val isSelected = DateTimeUtils.isSameDay(dateMillis, selectedDateMillis)
                val isToday = DateTimeUtils.isSameDay(dateMillis, nowMillis)
                val hasCourse = isoDay in daysWithCourses
                val dayLabel = DateTimeUtils.dayOfWeekShortChinese(isoDay)
                val dayNum = DateTimeUtils.formatDayOfMonth(dateMillis)

                val pillBg = when {
                    isSelected -> colorScheme.primary
                    isToday -> colorScheme.primaryContainer.copy(alpha = if (flowColors.isDark) 0.42f else 0.55f)
                    else -> colorScheme.surface.copy(alpha = 0f)
                }
                val pillBorder = when {
                    !isSelected && isToday -> BorderStroke(1.5.dp, colorScheme.primary)
                    else -> null
                }
                val primaryTextColor = when {
                    isSelected -> colorScheme.onPrimary
                    isToday -> colorScheme.primary
                    else -> colorScheme.onSurfaceVariant.copy(alpha = 0.78f)
                }
                val secondaryTextColor = when {
                    isSelected -> colorScheme.onPrimary
                    isToday -> colorScheme.primary
                    else -> colorScheme.onSurface.copy(alpha = 0.76f)
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 2.dp),
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
                            .clickable {
                                haptics.tick()
                                onSelectDate(dateMillis)
                            }
                            .testTag("timetable_weekday_${index + 1}"),
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
                                color = primaryTextColor,
                            )
                            Spacer(modifier = Modifier.height(1.dp))
                            Text(
                                text = dayNum,
                                fontFamily = MonoFontFamily,
                                fontSize = 12.sp,
                                fontWeight = if (isSelected || isToday) FontWeight.ExtraBold else FontWeight.Medium,
                                color = secondaryTextColor,
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Box(
                                modifier = Modifier
                                    .size(if (isToday && !isSelected) 5.dp else 4.dp)
                                    .clip(CircleShape)
                                    .background(
                                        when {
                                            isSelected && (hasCourse || isToday) -> colorScheme.onPrimary
                                            isToday -> colorScheme.primary
                                            hasCourse -> colorScheme.outline.copy(alpha = 0.75f)
                                            else -> colorScheme.surface.copy(alpha = 0f)
                                        },
                                    ),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MorningExerciseStrip(
    title: String,
    startTime: String,
    endTime: String,
    checkedToday: Boolean,
    onToggleCheckIn: () -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme
    val flowColors = FlowTheme.colors

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onToggleCheckIn)
            .testTag("morning_exercise_strip"),
        shape = RoundedCornerShape(14.dp),
        color = if (checkedToday) {
            flowColors.completedAccent.copy(alpha = if (flowColors.isDark) 0.16f else 0.12f)
        } else {
            colorScheme.tertiaryContainer.copy(alpha = if (flowColors.isDark) 0.45f else 0.7f)
        },
        border = BorderStroke(
            1.dp,
            if (checkedToday) {
                flowColors.completedAccent.copy(alpha = 0.45f)
            } else {
                flowColors.upcomingAccent.copy(alpha = 0.35f)
            },
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector = if (checkedToday) Icons.Rounded.CheckCircle else Icons.Rounded.WbSunny,
                    contentDescription = null,
                    tint = if (checkedToday) flowColors.completedAccent else flowColors.upcomingAccent,
                    modifier = Modifier.size(16.dp),
                )
                Text(
                    text = title.ifBlank { "附加时段" },
                    style = MaterialTheme.typography.labelLarge,
                    color = colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "${startTime.ifBlank { "06:40" }} - ${endTime.ifBlank { "07:30" }}",
                    fontFamily = MonoFontFamily,
                    fontSize = 11.sp,
                    color = colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    softWrap = false,
                )
            }
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = if (checkedToday) "今日已打卡" else "点击打卡",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = if (checkedToday) flowColors.completedAccent else flowColors.upcomingAccent,
                maxLines = 1,
                softWrap = false,
            )
        }
    }
}

@Composable
private fun SectionTimeAxisColumn(
    sectionTimes: List<SectionTimeEntity>,
    sectionCount: Int,
    sectionHeight: Dp,
    resolvedLunchBreak: ResolvedLunchBreak?,
    lunchBreakHeight: Dp,
    compactMode: Boolean,
    nowMillis: Long,
    highlightCurrentSection: Boolean,
    modifier: Modifier = Modifier,
) {
    val colorScheme = MaterialTheme.colorScheme
    val flowColors = FlowTheme.colors

    val sectionElapsedMap: Map<Int, Float> = remember(sectionTimes, nowMillis, highlightCurrentSection) {
        if (!highlightCurrentSection) {
            emptyMap()
        } else {
            val cal = Calendar.getInstance().apply { timeInMillis = nowMillis }
            val nowSec = cal.get(Calendar.HOUR_OF_DAY) * 3600 +
                cal.get(Calendar.MINUTE) * 60 +
                cal.get(Calendar.SECOND)
            buildMap {
                sectionTimes.forEach { slot ->
                    val startSec = slot.startHour * 3600 + slot.startMinute * 60
                    val endSec = slot.endHour * 3600 + slot.endMinute * 60
                    val fraction = when {
                        endSec <= startSec -> 0f
                        nowSec >= endSec -> 1f
                        nowSec <= startSec -> 0f
                        else -> ((nowSec - startSec).toFloat() / (endSec - startSec).toFloat()).coerceIn(0f, 1f)
                    }
                    put(slot.sectionNumber, fraction)
                }
            }
        }
    }

    // Subtle breathing animation — only runs when a section is currently in progress
    val hasOngoingSection = sectionElapsedMap.values.any { it > 0f && it < 1f }
    val breathingPulse = if (hasOngoingSection) {
        val infiniteTransition = rememberInfiniteTransition(label = "active_section_breath")
        infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 2_200, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "active_section_breath_pulse",
        ).value
    } else {
        0f
    }

    val railBgColor = if (flowColors.isDark) {
        colorScheme.surfaceContainerLow
    } else {
        colorScheme.surfaceContainer
    }
    val dividerColor = colorScheme.outlineVariant

    Row(
        modifier = modifier.background(railBgColor),
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
        ) {
            for (section in 1..sectionCount) {
                val slot = sectionTimes.find { it.sectionNumber == section }
                val rawElapsed = sectionElapsedMap[section] ?: 0f
                val isCurrentSection = rawElapsed > 0f && rawElapsed < 1f
                val isFullyPassed = rawElapsed >= 1f
                val animatedProgress by animateFloatAsState(
                    targetValue = rawElapsed,
                    animationSpec = tween(durationMillis = 600, easing = FastOutSlowInEasing),
                    label = "section_progress_$section",
                )

                // Clean cohesive blue-slate palette: passed time shaded deeper, upcoming time stays bright & light
                val upcomingBg = if (flowColors.isDark) colorScheme.surfaceContainerLow else colorScheme.surface
                val passedBg = if (flowColors.isDark) {
                    colorScheme.surfaceContainerHighest.copy(alpha = 0.85f)
                } else {
                    colorScheme.primaryContainer.copy(alpha = 0.48f)
                }

                val numberColor = when {
                    isCurrentSection -> colorScheme.primary
                    isFullyPassed -> colorScheme.onSurfaceVariant.copy(alpha = 0.72f)
                    else -> colorScheme.onSurface
                }
                val startTimeColor = if (isFullyPassed) {
                    colorScheme.onSurfaceVariant.copy(alpha = 0.68f)
                } else {
                    colorScheme.onSurface
                }
                val endTimeColor = if (isFullyPassed) {
                    colorScheme.onSurfaceVariant.copy(alpha = 0.62f)
                } else {
                    colorScheme.onSurface.copy(alpha = 0.85f)
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(sectionHeight)
                        .background(upcomingBg)
                        .testTag("timetable_section_axis_$section"),
                ) {
                    if (animatedProgress > 0f) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val progressY = (size.height * animatedProgress.coerceIn(0f, 1f))
                            if (isCurrentSection) {
                                // Smooth vertical fade at the current-time boundary so there is no harsh cut
                                val fadeStart = (progressY - 14.dp.toPx()).coerceAtLeast(0f)
                                val fadeEnd = (progressY + 10.dp.toPx()).coerceAtMost(size.height)
                                drawRect(
                                    brush = Brush.verticalGradient(
                                        colorStops = arrayOf(
                                            0f to passedBg,
                                            (fadeStart / size.height).coerceIn(0f, 1f) to passedBg,
                                            (fadeEnd / size.height).coerceIn(0f, 1f) to Color.Transparent,
                                            1f to Color.Transparent,
                                        ),
                                        startY = 0f,
                                        endY = size.height,
                                    ),
                                    topLeft = Offset.Zero,
                                    size = size,
                                )
                            } else {
                                drawRect(
                                    color = passedBg,
                                    topLeft = Offset.Zero,
                                    size = Size(size.width, progressY),
                                )
                            }

                        }
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(
                                start = if (isCurrentSection) 3.5.dp else 1.5.dp,
                                end = 1.5.dp,
                                top = 1.dp,
                                bottom = 1.dp,
                            ),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                        ) {
                            Text(
                                text = "$section",
                                fontFamily = MonoFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = if (compactMode) 13.sp else 13.5.sp,
                                lineHeight = 14.5.sp,
                                color = numberColor,
                                maxLines = 1,
                                softWrap = false,
                            )
                            if (isCurrentSection) {
                                Spacer(modifier = Modifier.width(2.dp))
                                Box(
                                    modifier = Modifier
                                        .size(4.dp)
                                        .clip(CircleShape)
                                        .background(
                                            colorScheme.primary.copy(alpha = 0.45f + breathingPulse * 0.55f),
                                        ),
                                )
                            }
                        }
                        if (slot != null) {
                            Spacer(modifier = Modifier.height(1.dp))
                            Text(
                                text = slot.startLabel(),
                                fontFamily = MonoFontFamily,
                                fontWeight = if (isCurrentSection) FontWeight.Bold else FontWeight.Medium,
                                fontSize = 10.sp,
                                lineHeight = 11.sp,
                                letterSpacing = (-0.2).sp,
                                color = startTimeColor,
                                maxLines = 1,
                                softWrap = false,
                            )
                            Spacer(modifier = Modifier.height(1.dp))
                            Text(
                                text = slot.endLabel(),
                                fontFamily = MonoFontFamily,
                                fontWeight = if (isCurrentSection) FontWeight.Bold else FontWeight.Medium,
                                fontSize = 10.sp,
                                lineHeight = 11.sp,
                                letterSpacing = (-0.2).sp,
                                color = endTimeColor,
                                maxLines = 1,
                                softWrap = false,
                            )
                        }
                    }

                    if (section < sectionCount) {
                        HorizontalDivider(
                            modifier = Modifier.align(Alignment.BottomCenter),
                            thickness = 0.5.dp,
                            color = dividerColor.copy(alpha = 0.65f),
                        )
                    }
                }
                if (resolvedLunchBreak != null && resolvedLunchBreak.afterSection == section && lunchBreakHeight > 0.dp) {
                    val lunchRangeText = resolvedLunchBreak.axisRangeText
                    val startMins = parseClockMinutes(resolvedLunchBreak.startLabel)
                    val endMins = parseClockMinutes(resolvedLunchBreak.endLabel)
                    val lunchElapsed = if (highlightCurrentSection && startMins != null && endMins != null) {
                        val cal = Calendar.getInstance().apply { timeInMillis = nowMillis }
                        val nowSec = cal.get(Calendar.HOUR_OF_DAY) * 3600 + cal.get(Calendar.MINUTE) * 60 + cal.get(Calendar.SECOND)
                        val startSec = startMins * 60
                        val endSec = endMins * 60
                        when {
                            endSec <= startSec -> 0f
                            nowSec >= endSec -> 1f
                            nowSec <= startSec -> 0f
                            else -> ((nowSec - startSec).toFloat() / (endSec - startSec).toFloat()).coerceIn(0f, 1f)
                        }
                    } else 0f
                    val lunchPassedBg = if (flowColors.isDark) {
                        colorScheme.surfaceContainerHighest.copy(alpha = 0.85f)
                    } else {
                        colorScheme.primaryContainer.copy(alpha = 0.48f)
                    }
                    val lunchUpcomingBg = if (flowColors.isDark) colorScheme.surfaceContainerLow else colorScheme.surface

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(lunchBreakHeight)
                            .background(lunchUpcomingBg),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (lunchElapsed > 0f) {
                            Canvas(modifier = Modifier.fillMaxSize()) {
                                val py = size.height * lunchElapsed
                                drawRect(
                                    color = lunchPassedBg,
                                    topLeft = Offset.Zero,
                                    size = Size(size.width, py),
                                )
                            }
                        }
                        Column(
                            modifier = Modifier.padding(horizontal = 2.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            Text(
                                text = resolvedLunchBreak.title,
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.sp,
                                lineHeight = 11.sp,
                                color = if (lunchElapsed > 0f && lunchElapsed < 1f) colorScheme.primary else colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (!compactMode &&
                                resolvedLunchBreak.startLabel.isNotBlank() &&
                                resolvedLunchBreak.endLabel.isNotBlank()
                            ) {
                                Text(
                                    text = resolvedLunchBreak.startLabel,
                                    fontFamily = MonoFontFamily,
                                    fontSize = 8.5.sp,
                                    lineHeight = 9.5.sp,
                                    letterSpacing = (-0.2).sp,
                                    color = colorScheme.onSurfaceVariant.copy(alpha = 0.88f),
                                    maxLines = 1,
                                    softWrap = false,
                                )
                                Text(
                                    text = resolvedLunchBreak.endLabel,
                                    fontFamily = MonoFontFamily,
                                    fontSize = 8.5.sp,
                                    lineHeight = 9.5.sp,
                                    letterSpacing = (-0.2).sp,
                                    color = colorScheme.onSurfaceVariant.copy(alpha = 0.74f),
                                    maxLines = 1,
                                    softWrap = false,
                                )
                            }
                        }
                        HorizontalDivider(
                            modifier = Modifier.align(Alignment.BottomCenter),
                            thickness = 0.5.dp,
                            color = dividerColor.copy(alpha = 0.65f),
                        )
                    }
                }
            }
        }

        VerticalDivider(
            thickness = 1.dp,
            color = dividerColor,
        )
    }
}

internal fun computeSectionElapsedFraction(
    slot: SectionTimeEntity,
    nowMillis: Long,
): Float? {
    val cal = Calendar.getInstance().apply { timeInMillis = nowMillis }
    val nowSecOfDay = cal.get(Calendar.HOUR_OF_DAY) * 3600 +
        cal.get(Calendar.MINUTE) * 60 +
        cal.get(Calendar.SECOND)
    val startSec = (slot.startHour * 60 + slot.startMinute) * 60
    val endSec = (slot.endHour * 60 + slot.endMinute) * 60
    if (endSec <= startSec || nowSecOfDay !in startSec..endSec) return null
    return ((nowSecOfDay - startSec).toFloat() / (endSec - startSec).toFloat()).coerceIn(0f, 1f)
}

internal data class TodayCourseProgress(
    val elapsedMinutes: Int,
    val totalMinutes: Int,
    val courseCount: Int,
    val completedCourseCount: Int,
    val ongoingCourseCount: Int,
) {
    val hasCourses: Boolean get() = totalMinutes > 0
    val remainingMinutes: Int get() = (totalMinutes - elapsedMinutes).coerceAtLeast(0)
    val fraction: Float
        get() = if (totalMinutes <= 0) 0f else (elapsedMinutes.toFloat() / totalMinutes.toFloat()).coerceIn(0f, 1f)
    val percent: Int get() = (fraction * 100f).toInt().coerceIn(0, 100)

    fun compactRatioLabel(): String {
        if (!hasCourses) return "今日无课"
        if (totalMinutes < 100) return "${elapsedMinutes}/${totalMinutes}m"
        return "${formatCompactHours(elapsedMinutes)}/${formatCompactHours(totalMinutes)}h"
    }

    private fun formatCompactHours(minutes: Int): String {
        val tenths = kotlin.math.round(minutes / 6.0).toInt()
        val whole = tenths / 10
        val rem = tenths % 10
        return if (rem == 0) "$whole" else "$whole.$rem"
    }
}

internal fun computeTodayCourseProgress(
    courses: List<CourseEntity>,
    sectionTimes: List<SectionTimeEntity>,
    currentActualWeek: Int,
    nowMillis: Long,
): TodayCourseProgress {
    val todayIsoDay = DateTimeUtils.isoDayOfWeek(nowMillis)
    val dayStart = DateTimeUtils.startOfDayMillis(nowMillis)
    val timesBySection = sectionTimes
        .ifEmpty { CourseColorPalette.defaultSectionTimes(0L) }
        .associateBy { it.sectionNumber }

    val todayIntervals = mutableListOf<Pair<Long, Long>>()
    var completedCount = 0
    var ongoingCount = 0

    courses
        .filter { it.dayOfWeek == todayIsoDay && it.isInWeek(currentActualWeek) }
        .forEach { course ->
            val startSlot = timesBySection[course.startSection] ?: return@forEach
            val endSlot = timesBySection[course.endSection] ?: startSlot
            val startMs = DateTimeUtils.combineDateAndTime(dayStart, startSlot.startHour, startSlot.startMinute)
            val endMs = DateTimeUtils.combineDateAndTime(dayStart, endSlot.endHour, endSlot.endMinute)
            if (endMs <= startMs) return@forEach
            todayIntervals.add(startMs to endMs)
            when {
                nowMillis >= endMs -> completedCount++
                nowMillis >= startMs -> ongoingCount++
            }
        }

    if (todayIntervals.isEmpty()) {
        return TodayCourseProgress(
            elapsedMinutes = 0,
            totalMinutes = 0,
            courseCount = 0,
            completedCourseCount = 0,
            ongoingCourseCount = 0,
        )
    }

    // Merge overlapping intervals so total/elapsed course time is never double-counted
    val sorted = todayIntervals.sortedBy { it.first }
    val merged = mutableListOf<Pair<Long, Long>>()
    for (interval in sorted) {
        val last = merged.lastOrNull()
        if (last == null || interval.first > last.second) {
            merged.add(interval)
        } else {
            merged[merged.lastIndex] = last.first to maxOf(last.second, interval.second)
        }
    }

    var totalMs = 0L
    var elapsedMs = 0L
    for ((startMs, endMs) in merged) {
        totalMs += (endMs - startMs)
        elapsedMs += (nowMillis.coerceIn(startMs, endMs) - startMs)
    }

    return TodayCourseProgress(
        elapsedMinutes = (elapsedMs / 60_000L).toInt(),
        totalMinutes = (totalMs / 60_000L).toInt(),
        courseCount = todayIntervals.size,
        completedCourseCount = completedCount,
        ongoingCourseCount = ongoingCount,
    )
}

@Composable
private fun TodayProgressDetailDialog(
    progress: TodayCourseProgress,
    onDismiss: () -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme
    val flowColors = FlowTheme.colors
    val haptics = rememberAppHaptics()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "今日课程进度",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (!progress.hasCourses) {
                    Text(
                        text = "今天暂无本周排课，可以自由安排自习专注或个人日程。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colorScheme.onSurfaceVariant,
                    )
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.Bottom,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = "${progress.percent}%",
                            fontFamily = MonoFontFamily,
                            fontSize = 30.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (progress.percent >= 100) flowColors.completedAccent else colorScheme.primary,
                        )
                        Text(
                            text = "已过 ${formatMinutesReadable(progress.elapsedMinutes)} / 共 ${formatMinutesReadable(progress.totalMinutes)}",
                            style = MaterialTheme.typography.labelLarge,
                            color = colorScheme.onSurfaceVariant,
                        )
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(10.dp)
                            .clip(CircleShape)
                            .background(colorScheme.surfaceContainerHighest),
                    ) {
                        if (progress.fraction > 0f) {
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .fillMaxWidth(progress.fraction.coerceIn(0.04f, 1f))
                                    .clip(CircleShape)
                                    .background(
                                        Brush.horizontalGradient(
                                            colors = if (progress.percent >= 100) {
                                                listOf(flowColors.completedAccent, flowColors.completedAccent)
                                            } else {
                                                listOf(colorScheme.primary, flowColors.liveAccent)
                                            },
                                        ),
                                    ),
                            )
                        }
                    }

                    val upcomingCount = (progress.courseCount - progress.completedCourseCount - progress.ongoingCourseCount)
                        .coerceAtLeast(0)
                    Text(
                        text = "今日共 ${progress.courseCount} 门课 · 已完成 ${progress.completedCourseCount} 门 · 进行中 ${progress.ongoingCourseCount} 门 · 待上 $upcomingCount 门",
                        style = MaterialTheme.typography.bodySmall,
                        color = colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = if (progress.remainingMinutes == 0) {
                            "今日全部课程已结束，辛苦啦！"
                        } else {
                            "今日剩余课程时长：${formatMinutesReadable(progress.remainingMinutes)}"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = colorScheme.onSurface,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                haptics.tick()
                onDismiss()
            }) {
                Text("知道了")
            }
        },
    )
}

private fun formatMinutesReadable(minutes: Int): String {
    if (minutes <= 0) return "0分钟"
    val h = minutes / 60
    val m = minutes % 60
    return when {
        h > 0 && m > 0 -> "${h}小时${m}分钟"
        h > 0 -> "${h}小时"
        else -> "${m}分钟"
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CourseGridCard(
    course: CourseEntity,
    isCurrentWeek: Boolean,
    compactMode: Boolean,
    fontScale: Float = 1.0f,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val colorScheme = MaterialTheme.colorScheme
    val flowColors = FlowTheme.colors
    val style = course.resolvedColorStyle()
    val scale = fontScale.coerceIn(0.80f, 1.35f)

    val bgColor = if (isCurrentWeek) {
        if (flowColors.isDark) style.darkBg else style.lightBg
    } else {
        colorScheme.surfaceContainer.copy(alpha = if (flowColors.isDark) 0.88f else 0.72f)
    }
    val textColor = if (isCurrentWeek) {
        if (flowColors.isDark) style.darkText else style.lightText
    } else {
        colorScheme.onSurfaceVariant
    }
    val accentColor = if (isCurrentWeek) {
        if (flowColors.isDark) style.darkAccent else style.lightAccent
    } else {
        colorScheme.outline
    }

    val shape = RoundedCornerShape(13.dp)
    val spanSections = (course.endSection - course.startSection + 1).coerceAtLeast(1)

    Surface(
        modifier = modifier
            .alpha(if (isCurrentWeek) 1f else if (flowColors.isDark) 0.72f else 0.62f)
            .clip(shape)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
            )
            .testTag("course_card_${course.id}"),
        shape = shape,
        color = bgColor,
        border = BorderStroke(
            width = if (isCurrentWeek) 0.9.dp else 0.75.dp,
            color = accentColor.copy(alpha = if (isCurrentWeek) 0.38f else 0.3f),
        ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    start = 2.dp,
                    end = 2.dp,
                    top = if (spanSections == 1) 5.dp else 7.dp,
                    bottom = 4.dp,
                ),
            contentAlignment = Alignment.TopCenter,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .padding(bottom = if (!isCurrentWeek) 14.dp else 0.dp),
                verticalArrangement = Arrangement.Top,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = course.name,
                    style = MaterialTheme.typography.labelMedium.copy(
                        platformStyle = PlatformTextStyle(includeFontPadding = false),
                    ),
                    fontWeight = FontWeight.Bold,
                    fontSize = (9.8f * scale).sp,
                    lineHeight = (11.5f * scale).sp,
                    letterSpacing = (-0.35).sp,
                    textAlign = TextAlign.Center,
                    color = textColor,
                    maxLines = Int.MAX_VALUE,
                    overflow = TextOverflow.Clip,
                )
                val compactLoc = course.gridDisplayLocation()
                if (compactLoc.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = compactLoc,
                        style = MaterialTheme.typography.bodySmall.copy(
                            platformStyle = PlatformTextStyle(includeFontPadding = false),
                        ),
                        fontSize = (8.8f * scale).sp,
                        lineHeight = (10.5f * scale).sp,
                        letterSpacing = (-0.25).sp,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center,
                        color = textColor.copy(alpha = 0.80f),
                        maxLines = Int.MAX_VALUE,
                        overflow = TextOverflow.Clip,
                    )
                }
                if (!compactMode && course.teacher.isNotBlank() && spanSections >= 3) {
                    Spacer(modifier = Modifier.height(2.5.dp))
                    Text(
                        text = course.teacher,
                        style = MaterialTheme.typography.bodySmall.copy(
                            platformStyle = PlatformTextStyle(includeFontPadding = false),
                        ),
                        fontSize = (8.5f * scale).sp,
                        lineHeight = (10f * scale).sp,
                        letterSpacing = (-0.2).sp,
                        textAlign = TextAlign.Center,
                        color = textColor.copy(alpha = 0.65f),
                        maxLines = Int.MAX_VALUE,
                        overflow = TextOverflow.Clip,
                    )
                }
            }

            // Bottom "非本周" corner badge pinned cleanly at bottom without pushing top text apart
            if (!isCurrentWeek) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = colorScheme.onSurfaceVariant.copy(alpha = 0.18f),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth(),
                ) {
                    Text(
                        text = "非本周",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 1.dp),
                    )
                }
            }
        }
    }
}

private fun parseClockMinutes(clock: String): Int? {
    val parts = clock.trim().split(":")
    if (parts.size != 2) return null
    val hour = parts[0].toIntOrNull() ?: return null
    val minute = parts[1].toIntOrNull() ?: return null
    if (hour !in 0..23 || minute !in 0..59) return null
    return hour * 60 + minute
}

