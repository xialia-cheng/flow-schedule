package com.example.ui.timetable

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.EventNote
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.Swipe
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.CourseColorPalette
import com.example.data.CourseEntity
import com.example.data.DateTimeUtils
import com.example.data.ScheduleEntity
import com.example.data.SectionTimeEntity
import com.example.ui.components.rememberAppHaptics
import com.example.ui.theme.FlowTheme
import com.example.ui.theme.MonoFontFamily

@Composable
internal fun TimetableDayTimelineView(
    selectedDateMillis: Long,
    displayedWeek: Int,
    courses: List<CourseEntity>,
    sectionTimes: List<SectionTimeEntity>,
    schedules: List<ScheduleEntity>,
    showNonCurrentWeekCourses: Boolean,
    isEditMode: Boolean = false,
    nowMillis: Long,
    onShiftDay: (Int) -> Unit,
    onClickCourse: (CourseEntity) -> Unit,
    onLongClickCourse: ((CourseEntity) -> Unit)? = null,
    onAddCourseForDay: (dayOfWeek: Int) -> Unit,
    onToggleScheduleComplete: (ScheduleEntity) -> Unit,
    onEditSchedule: (ScheduleEntity) -> Unit,
    onLongClickSchedule: ((ScheduleEntity) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val colorScheme = MaterialTheme.colorScheme
    val flowColors = FlowTheme.colors
    val haptics = rememberAppHaptics()
    val density = LocalDensity.current
    val swipeThresholdPx = remember(density) { with(density) { 64.dp.toPx() } }

    var dragAccumulated by remember { mutableFloatStateOf(0f) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(selectedDateMillis) {
                detectHorizontalDragGestures(
                    onDragStart = { dragAccumulated = 0f },
                    onHorizontalDrag = { _, dragAmount ->
                        dragAccumulated += dragAmount
                    },
                    onDragEnd = {
                        if (dragAccumulated <= -swipeThresholdPx) {
                            onShiftDay(1)
                        } else if (dragAccumulated >= swipeThresholdPx) {
                            onShiftDay(-1)
                        }
                        dragAccumulated = 0f
                    },
                    onDragCancel = { dragAccumulated = 0f },
                )
            }
            .testTag("timetable_day_view"),
    ) {
        AnimatedContent(
            targetState = selectedDateMillis,
            transitionSpec = {
                val movingForward = targetState > initialState
                val enter = slideInHorizontally(
                    animationSpec = tween(220),
                    initialOffsetX = { width -> if (movingForward) width / 4 else -width / 4 },
                ) + fadeIn(animationSpec = tween(200))
                val exit = slideOutHorizontally(
                    animationSpec = tween(180),
                    targetOffsetX = { width -> if (movingForward) -width / 5 else width / 5 },
                ) + fadeOut(animationSpec = tween(150))
                enter togetherWith exit
            },
            label = "timetable_day_slide",
        ) { targetDate ->
            val selectedIsoDay = DateTimeUtils.isoDayOfWeek(targetDate)
            val isToday = DateTimeUtils.isSameDay(targetDate, nowMillis)
            val timesBySection = remember(sectionTimes) {
                sectionTimes.ifEmpty { CourseColorPalette.defaultSectionTimes(0L) }
                    .associateBy { it.sectionNumber }
            }

            val dayCourses = remember(courses, selectedIsoDay, displayedWeek, showNonCurrentWeekCourses) {
                courses
                    .filter { it.dayOfWeek == selectedIsoDay }
                    .filter { it.isInWeek(displayedWeek) || showNonCurrentWeekCourses }
                    .sortedWith(
                        compareByDescending<CourseEntity> { it.isInWeek(displayedWeek) }
                            .thenBy { it.startSection },
                    )
            }

            val daySchedules = remember(schedules, targetDate) {
                schedules
                    .filter { DateTimeUtils.isSameDay(it.startTimeMillis, targetDate) }
                    .sortedWith(compareBy<ScheduleEntity> { it.isCompleted }.thenBy { it.startTimeMillis })
            }

            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val extraSidePadding = ((maxWidth - 720.dp) / 2).coerceAtLeast(0.dp)
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = 14.dp + extraSidePadding,
                        end = 14.dp + extraSidePadding,
                        top = 10.dp,
                        bottom = 92.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                // Day header summary & prev/next switcher
                item(key = "day_header") {
                    Surface(
                        shape = MaterialTheme.shapes.large,
                        color = flowColors.cardElevated,
                        border = BorderStroke(1.dp, colorScheme.outlineVariant),
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 10.dp),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                IconButton(
                                    onClick = {
                                        haptics.tick()
                                        onShiftDay(-1)
                                    },
                                    modifier = Modifier
                                        .minimumInteractiveComponentSize()
                                        .testTag("day_view_prev_day"),
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowLeft,
                                        contentDescription = "前一天",
                                        tint = colorScheme.onSurface,
                                    )
                                }

                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier.weight(1f),
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    ) {
                                        Text(
                                            text = DateTimeUtils.formatDateHeader(targetDate),
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = colorScheme.onSurface,
                                        )
                                        if (isToday) {
                                            Surface(
                                                shape = CircleShape,
                                                color = colorScheme.primaryContainer,
                                            ) {
                                                Text(
                                                    text = "今天",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontWeight = FontWeight.Bold,
                                                    color = colorScheme.onPrimaryContainer,
                                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                                                )
                                            }
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(2.dp))
                                    val activeCourseCount = dayCourses.count { it.isInWeek(displayedWeek) }
                                    Text(
                                        text = "第 $displayedWeek 周 · $activeCourseCount 门本周课程 · ${daySchedules.size} 条关联日程",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = colorScheme.onSurfaceVariant,
                                    )
                                }

                                IconButton(
                                    onClick = {
                                        haptics.tick()
                                        onShiftDay(1)
                                    },
                                    modifier = Modifier
                                        .minimumInteractiveComponentSize()
                                        .testTag("day_view_next_day"),
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                                        contentDescription = "后一天",
                                        tint = colorScheme.onSurface,
                                    )
                                }
                            }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 2.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Swipe,
                                    contentDescription = null,
                                    tint = colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                    modifier = Modifier.size(13.dp),
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "左右滑动切换前一天 / 后一天，跨周自动联动",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = colorScheme.onSurfaceVariant.copy(alpha = 0.78f),
                                )
                            }
                        }
                    }
                }

                // Section 1: Day Courses Timeline
                if (dayCourses.isNotEmpty()) {
                    item(key = "courses_section_label") {
                        DayTimelineSectionLabel(
                            icon = Icons.Rounded.School,
                            title = "当日课程",
                            count = dayCourses.size,
                        )
                    }
                    items(
                        items = dayCourses,
                        key = { "day_course_${it.id}" },
                    ) { course ->
                        val isInWeek = course.isInWeek(displayedWeek)
                        val startSlot = timesBySection[course.startSection]
                        val endSlot = timesBySection[course.endSection] ?: startSlot
                        val isOngoing = isToday && isInWeek && startSlot != null && endSlot != null && run {
                            val startMs = DateTimeUtils.combineDateAndTime(targetDate, startSlot.startHour, startSlot.startMinute)
                            val endMs = DateTimeUtils.combineDateAndTime(targetDate, endSlot.endHour, endSlot.endMinute)
                            nowMillis in startMs..endMs
                        }

                        DayCourseTimelineCard(
                            course = course,
                            startTimeLabel = startSlot?.startLabel() ?: "--:--",
                            endTimeLabel = endSlot?.endLabel() ?: "--:--",
                            isCurrentWeek = isInWeek,
                            isOngoing = isOngoing,
                            onClick = { onClickCourse(course) },
                            onLongClick = onLongClickCourse?.let { cb -> { cb(course) } },
                        )
                    }
                }

                // Section 2: Associated Schedules for this date
                if (daySchedules.isNotEmpty()) {
                    item(key = "schedules_section_label") {
                        DayTimelineSectionLabel(
                            icon = Icons.Rounded.EventNote,
                            title = "当日关联日程",
                            count = daySchedules.size,
                        )
                    }
                    items(
                        items = daySchedules,
                        key = { "day_schedule_${it.id}" },
                    ) { schedule ->
                        DayAssociatedScheduleCard(
                            schedule = schedule,
                            nowMillis = nowMillis,
                            onToggleComplete = { onToggleScheduleComplete(schedule) },
                            onEdit = { onEditSchedule(schedule) },
                            onLongClick = onLongClickSchedule?.let { cb -> { cb(schedule) } },
                        )
                    }
                }

                // Empty State when neither courses nor schedules exist for this day
                if (dayCourses.isEmpty() && daySchedules.isEmpty()) {
                    item(key = "day_empty_state") {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("day_view_empty_card"),
                            shape = MaterialTheme.shapes.large,
                            color = flowColors.cardElevated,
                            border = BorderStroke(1.dp, colorScheme.outlineVariant),
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 24.dp, vertical = 28.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(52.dp)
                                        .clip(CircleShape)
                                        .background(colorScheme.primaryContainer.copy(alpha = 0.7f)),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.School,
                                        contentDescription = null,
                                        tint = colorScheme.primary,
                                        modifier = Modifier.size(26.dp),
                                    )
                                }
                                Text(
                                    text = "${DateTimeUtils.dayOfWeekFullChinese(selectedIsoDay)} 暂无课程与关联日程",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = colorScheme.onSurface,
                                )
                                Text(
                                    text = if (isEditMode) {
                                        "左右滑动可查看其他日期，或直接为这一天添加课程"
                                    } else {
                                        "左右滑动可查看其他日期，开启顶部「编辑课表」可添加课程"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colorScheme.onSurfaceVariant,
                                )
                                if (isEditMode) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    FilledTonalButton(
                                        onClick = {
                                            haptics.tick()
                                            onAddCourseForDay(selectedIsoDay)
                                        },
                                        shape = CircleShape,
                                        modifier = Modifier.testTag("day_view_add_course_button"),
                                    ) {
                                        Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("添加${DateTimeUtils.dayOfWeekFullChinese(selectedIsoDay)}课程")
                                    }
                                }
                            }
                        }
                    }
                }
            }
            }
        }
    }
}

@Composable
private fun DayTimelineSectionLabel(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    count: Int,
) {
    val colorScheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = colorScheme.primary,
                modifier = Modifier.size(16.dp),
            )
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = colorScheme.onSurface,
            )
        }
        Surface(
            shape = CircleShape,
            color = colorScheme.surfaceContainerHigh,
        ) {
            Text(
                text = "$count 项",
                style = MaterialTheme.typography.labelSmall,
                color = colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DayCourseTimelineCard(
    course: CourseEntity,
    startTimeLabel: String,
    endTimeLabel: String,
    isCurrentWeek: Boolean,
    isOngoing: Boolean,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
) {
    val colorScheme = MaterialTheme.colorScheme
    val flowColors = FlowTheme.colors
    val haptics = rememberAppHaptics()
    val style = course.resolvedColorStyle()

    val accentColor = if (isCurrentWeek) {
        if (flowColors.isDark) style.darkAccent else style.lightAccent
    } else {
        colorScheme.outline
    }
    val cardBg = if (isCurrentWeek) {
        if (flowColors.isDark) style.darkBg.copy(alpha = 0.72f) else style.lightBg.copy(alpha = 0.78f)
    } else {
        colorScheme.surfaceContainerLow
    }
    val titleColor = if (isCurrentWeek) {
        if (flowColors.isDark) style.darkText else style.lightText
    } else {
        colorScheme.onSurfaceVariant
    }

    val shape = RoundedCornerShape(20.dp)
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (isCurrentWeek) 1f else 0.68f)
            .clip(shape)
            .combinedClickable(
                onClick = {
                    haptics.tick()
                    onClick()
                },
                onLongClick = onLongClick?.let { cb ->
                    {
                        haptics.longPress()
                        cb()
                    }
                },
            )
            .testTag("day_course_card_${course.id}"),
        shape = shape,
        color = cardBg,
        border = BorderStroke(
            width = if (isOngoing) 1.5.dp else 1.dp,
            color = accentColor.copy(alpha = if (isOngoing) 0.85f else 0.35f),
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Left time & section column
            Column(
                modifier = Modifier.width(68.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = startTimeLabel,
                    fontFamily = MonoFontFamily,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = titleColor,
                )
                Text(
                    text = endTimeLabel,
                    fontFamily = MonoFontFamily,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = titleColor.copy(alpha = 0.78f),
                )
                Spacer(modifier = Modifier.height(6.dp))
                Surface(
                    shape = CircleShape,
                    color = accentColor.copy(alpha = if (flowColors.isDark) 0.24f else 0.16f),
                ) {
                    Text(
                        text = if (course.startSection == course.endSection) {
                            "第${course.startSection}节"
                        } else {
                            "第${course.startSection}-${course.endSection}节"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = accentColor,
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                    )
                }
            }

            Spacer(modifier = Modifier.width(10.dp))

            // Vertical colored timeline bar
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(58.dp)
                    .clip(CircleShape)
                    .background(accentColor),
            )

            Spacer(modifier = Modifier.width(12.dp))

            // Right course details
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = course.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = titleColor,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (isOngoing) {
                        Surface(
                            shape = CircleShape,
                            color = flowColors.liveAccent,
                        ) {
                            Text(
                                text = "进行中",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = colorScheme.onPrimary,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                            )
                        }
                    } else if (!isCurrentWeek) {
                        Surface(
                            shape = CircleShape,
                            color = colorScheme.surfaceContainerHighest,
                        ) {
                            Text(
                                text = "非本周",
                                style = MaterialTheme.typography.labelSmall,
                                color = colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                            )
                        }
                    }
                }

                val loc = course.formattedLocation()
                if (loc.isNotEmpty()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.LocationOn,
                            contentDescription = null,
                            tint = accentColor,
                            modifier = Modifier.size(14.dp),
                        )
                        Text(
                            text = loc,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = titleColor.copy(alpha = 0.9f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    if (course.teacher.isNotBlank()) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Person,
                                contentDescription = null,
                                tint = titleColor.copy(alpha = 0.72f),
                                modifier = Modifier.size(13.dp),
                            )
                            Text(
                                text = course.teacher,
                                style = MaterialTheme.typography.bodySmall,
                                color = titleColor.copy(alpha = 0.78f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    Text(
                        text = course.weekRangeLabel(),
                        style = MaterialTheme.typography.bodySmall,
                        color = titleColor.copy(alpha = 0.72f),
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DayAssociatedScheduleCard(
    schedule: ScheduleEntity,
    nowMillis: Long,
    onToggleComplete: () -> Unit,
    onEdit: () -> Unit,
    onLongClick: (() -> Unit)? = null,
) {
    val colorScheme = MaterialTheme.colorScheme
    val flowColors = FlowTheme.colors
    val haptics = rememberAppHaptics()
    val isInProgress = !schedule.isCompleted && schedule.isInProgress(nowMillis)
    val shape = RoundedCornerShape(18.dp)

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .combinedClickable(
                onClick = {
                    haptics.tick()
                    onEdit()
                },
                onLongClick = onLongClick?.let { cb ->
                    {
                        haptics.longPress()
                        cb()
                    }
                },
            )
            .testTag("day_schedule_card_${schedule.id}"),
        shape = shape,
        color = flowColors.cardElevated,
        border = BorderStroke(
            width = if (isInProgress) 1.3.dp else 1.dp,
            color = if (isInProgress) flowColors.liveAccent else colorScheme.outlineVariant,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            IconButton(
                onClick = {
                    haptics.confirm()
                    onToggleComplete()
                },
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("day_schedule_toggle_${schedule.id}"),
            ) {
                Icon(
                    imageVector = if (schedule.isCompleted) {
                        Icons.Rounded.CheckCircle
                    } else {
                        Icons.Rounded.RadioButtonUnchecked
                    },
                    contentDescription = if (schedule.isCompleted) "标为未完成" else "标为已完成",
                    tint = if (schedule.isCompleted) flowColors.completedAccent else colorScheme.primary,
                )
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = schedule.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (schedule.isCompleted) colorScheme.onSurfaceVariant else colorScheme.onSurface,
                        textDecoration = if (schedule.isCompleted) TextDecoration.LineThrough else TextDecoration.None,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    Surface(
                        shape = CircleShape,
                        color = colorScheme.secondaryContainer,
                    ) {
                        Text(
                            text = schedule.customTagName.ifBlank { schedule.category.label },
                            style = MaterialTheme.typography.labelSmall,
                            color = colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 1.dp),
                        )
                    }
                }

                Text(
                    text = DateTimeUtils.formatTimeRange(schedule.startTimeMillis, schedule.endTimeMillis),
                    fontFamily = MonoFontFamily,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (isInProgress) flowColors.liveAccent else colorScheme.onSurfaceVariant,
                )

                if (schedule.notes.isNotBlank()) {
                    Text(
                        text = schedule.notes,
                        style = MaterialTheme.typography.bodySmall,
                        color = colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}
