package com.example.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalSize
import androidx.glance.action.ActionParameters
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.example.MainActivity
import com.example.data.CourseColorPalette
import com.example.data.DateTimeUtils
import com.example.data.ScheduleDatabase
import com.example.data.UserPreferencesRepository
import com.example.notification.LiveActivitySourceType
import com.example.notification.LiveUpdateManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

data class WidgetSecondaryItem(
    val title: String,
    val startClock: String,
    val location: String,
)

data class WidgetNextItem(
    val sourceType: LiveActivitySourceType,
    val title: String,
    val location: String,
    val timeRange: String,
    val statusBadge: String,
    val remainingText: String,
    val isInProgress: Boolean,
    val totalRemainingToday: Int = 1,
    val upcomingItems: List<WidgetSecondaryItem> = emptyList(),
)

class FlowGlanceWidget : GlanceAppWidget() {

    override val sizeMode: SizeMode = SizeMode.Responsive(
        setOf(COMPACT_SIZE, EXPANDED_SIZE),
    )

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val nowMillis = System.currentTimeMillis()
        val nextItem = withContext(Dispatchers.IO) {
            loadTodayNextItem(context, nowMillis)
        }
        val dateHeader = DateTimeUtils.formatDateShort(nowMillis)

        provideContent {
            GlanceTheme {
                val size = LocalSize.current
                val showUpcomingList = size.height >= EXPANDED_SIZE.height
                FlowWidgetContent(
                    item = nextItem,
                    dateHeader = dateHeader,
                    showUpcomingList = showUpcomingList,
                    context = context,
                )
            }
        }
    }

    private suspend fun loadTodayNextItem(context: Context, nowMillis: Long): WidgetNextItem? {
        val db = ScheduleDatabase.getInstance(context)
        val settings = runCatching {
            UserPreferencesRepository(context).settings.first()
        }.getOrNull()
        val scheduleDao = db.scheduleDao()
        val timetableDao = db.timetableDao()

        val candidates = mutableListOf<CandidateEntry>()

        // 1. Today's incomplete schedules (in progress or upcoming today)
        val schedules = scheduleDao.getAllIncompleteSchedules()
        for (s in schedules) {
            if (s.isTestSchedule) continue
            val isTodayOrActive = DateTimeUtils.isToday(s.startTimeMillis, nowMillis) || s.isInProgress(nowMillis)
            if (!isTodayOrActive || s.endTimeMillis <= nowMillis) continue
            val loc = Regex("@([^\\s，。·,]+)").find(s.notes)?.groupValues?.get(0).orEmpty()
            candidates.add(
                CandidateEntry(
                    sourceType = LiveActivitySourceType.SCHEDULE,
                    title = s.title,
                    location = loc,
                    startTimeMillis = s.startTimeMillis,
                    endTimeMillis = s.endTimeMillis,
                ),
            )
        }

        // 2. Today's active timetable courses (in progress or upcoming today)
        val activeTimetable = timetableDao.getActiveTimetable()
        if (activeTimetable != null) {
            val dayStart = DateTimeUtils.startOfDayMillis(nowMillis)
            val dateKey = DateTimeUtils.formatDateKey(dayStart)
            val isoDay = DateTimeUtils.isoDayOfWeek(dayStart)
            val semesterWeek = DateTimeUtils.computeSemesterWeek(
                activeTimetable.semesterStartMondayMillis,
                dayStart,
                activeTimetable.totalWeeks,
                settings?.weekStartDay ?: com.example.data.WeekStartDay.MONDAY,
            )
            val courses = timetableDao.getCoursesForTimetable(activeTimetable.id)
            val sectionTimes = timetableDao.getSectionTimes(activeTimetable.id)
                .ifEmpty { CourseColorPalette.defaultSectionTimes(activeTimetable.id) }
                .associateBy { it.sectionNumber }
            val stateMap = timetableDao.getCourseStatesForDate(dateKey).associateBy { it.courseId }

            courses.filter { it.dayOfWeek == isoDay && it.isInWeek(semesterWeek) }.forEach { course ->
                val state = stateMap[course.id]
                if (state?.isCompleted == true) return@forEach
                val startSlot = sectionTimes[course.startSection] ?: return@forEach
                val endSlot = sectionTimes[course.endSection] ?: startSlot
                val snoozeMs = (state?.snoozeMinutes ?: 0) * 60_000L
                val rawStart = DateTimeUtils.combineDateAndTime(dayStart, startSlot.startHour, startSlot.startMinute)
                val rawEnd = DateTimeUtils.combineDateAndTime(dayStart, endSlot.endHour, endSlot.endMinute)
                val startMs = if (nowMillis >= rawStart) rawStart else rawStart + snoozeMs
                val endMs = (rawEnd + snoozeMs).coerceAtLeast(startMs + 60_000L)
                if (endMs <= nowMillis) return@forEach

                candidates.add(
                    CandidateEntry(
                        sourceType = LiveActivitySourceType.COURSE,
                        title = course.name,
                        location = course.formattedLocation(),
                        startTimeMillis = startMs,
                        endTimeMillis = endMs,
                    ),
                )
            }
        }

        val sorted = candidates.sortedWith(
            compareByDescending<CandidateEntry> { nowMillis in it.startTimeMillis until it.endTimeMillis }
                .thenBy { it.startTimeMillis },
        )
        val chosen = sorted.firstOrNull() ?: return null

        val inProgress = nowMillis in chosen.startTimeMillis until chosen.endTimeMillis
        val targetMs = if (inProgress) chosen.endTimeMillis else chosen.startTimeMillis
        val remainingMs = (targetMs - nowMillis).coerceAtLeast(0L)
        val remainingStr = DateTimeUtils.formatCountdownChinese(remainingMs)

        val badge = when {
            inProgress && chosen.sourceType == LiveActivitySourceType.COURSE -> "上课中"
            inProgress -> "进行中"
            chosen.sourceType == LiveActivitySourceType.COURSE -> "今日下节"
            else -> "今日待办"
        }

        val remainingDisplay = if (inProgress) {
            "剩余 $remainingStr"
        } else {
            val verb = if (chosen.sourceType == LiveActivitySourceType.COURSE) "距上课" else "距开始"
            "$verb $remainingStr"
        }

        val upcoming = sorted.drop(1).take(2).map { entry ->
            WidgetSecondaryItem(
                title = entry.title,
                startClock = DateTimeUtils.formatTime(entry.startTimeMillis),
                location = entry.location,
            )
        }

        return WidgetNextItem(
            sourceType = chosen.sourceType,
            title = chosen.title,
            location = chosen.location,
            timeRange = DateTimeUtils.formatTimeRange(chosen.startTimeMillis, chosen.endTimeMillis),
            statusBadge = badge,
            remainingText = remainingDisplay,
            isInProgress = inProgress,
            totalRemainingToday = sorted.size,
            upcomingItems = upcoming,
        )
    }

    private data class CandidateEntry(
        val sourceType: LiveActivitySourceType,
        val title: String,
        val location: String,
        val startTimeMillis: Long,
        val endTimeMillis: Long,
    )

    companion object {
        private val COMPACT_SIZE = DpSize(180.dp, 110.dp)
        private val EXPANDED_SIZE = DpSize(250.dp, 172.dp)
    }
}

@Composable
private fun FlowWidgetContent(
    item: WidgetNextItem?,
    dateHeader: String,
    showUpcomingList: Boolean,
    context: Context,
) {
    val openIntent = Intent(context, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        if (item != null) {
            putExtra(LiveUpdateManager.EXTRA_SOURCE_TYPE, item.sourceType.name)
        }
    }

    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .cornerRadius(24.dp)
            .background(GlanceTheme.colors.surface)
            .clickable(actionStartActivity(openIntent))
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Top Header Row: Status Badge + Date + Manual Refresh
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = GlanceModifier
                    .cornerRadius(12.dp)
                    .background(
                        if (item?.isInProgress == true) {
                            GlanceTheme.colors.primaryContainer
                        } else {
                            GlanceTheme.colors.secondaryContainer
                        },
                    )
                    .padding(horizontal = 10.dp, vertical = 4.dp),
            ) {
                Text(
                    text = item?.statusBadge ?: "今日清朗",
                    style = TextStyle(
                        color = if (item?.isInProgress == true) {
                            GlanceTheme.colors.onPrimaryContainer
                        } else {
                            GlanceTheme.colors.onSecondaryContainer
                        },
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                )
            }

            Spacer(modifier = GlanceModifier.width(8.dp))

            Text(
                text = dateHeader,
                style = TextStyle(
                    color = GlanceTheme.colors.onSurfaceVariant,
                    fontSize = 11.sp,
                ),
                modifier = GlanceModifier.defaultWeight(),
            )

            Box(
                modifier = GlanceModifier
                    .cornerRadius(10.dp)
                    .background(GlanceTheme.colors.surfaceVariant)
                    .clickable(actionRunCallback<RefreshFlowWidgetAction>())
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            ) {
                Text(
                    text = "刷新",
                    style = TextStyle(
                        color = GlanceTheme.colors.primary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                    ),
                )
            }
        }

        Spacer(modifier = GlanceModifier.height(10.dp))

        if (item == null) {
            Text(
                text = "今日课程与日程已全部完成",
                style = TextStyle(
                    color = GlanceTheme.colors.onSurface,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                ),
                maxLines = 1,
            )
            Spacer(modifier = GlanceModifier.height(4.dp))
            Text(
                text = "点击打开微澜日程，随时一句话创建新安排",
                style = TextStyle(
                    color = GlanceTheme.colors.onSurfaceVariant,
                    fontSize = 12.sp,
                ),
                maxLines = 1,
            )
        } else {
            Text(
                text = item.title,
                style = TextStyle(
                    color = GlanceTheme.colors.onSurface,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                ),
                maxLines = 1,
            )

            Spacer(modifier = GlanceModifier.height(4.dp))

            val subtitleLine = buildString {
                if (item.location.isNotBlank()) {
                    append(item.location)
                    append("  ·  ")
                }
                append(item.timeRange)
            }
            Text(
                text = subtitleLine,
                style = TextStyle(
                    color = GlanceTheme.colors.onSurfaceVariant,
                    fontSize = 12.sp,
                ),
                maxLines = 1,
            )

            Spacer(modifier = GlanceModifier.height(8.dp))

            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = GlanceModifier
                        .cornerRadius(12.dp)
                        .background(GlanceTheme.colors.primary)
                        .padding(horizontal = 12.dp, vertical = 5.dp),
                ) {
                    Text(
                        text = item.remainingText,
                        style = TextStyle(
                            color = GlanceTheme.colors.onPrimary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                        ),
                    )
                }

                if (item.totalRemainingToday > 1) {
                    Spacer(modifier = GlanceModifier.width(8.dp))
                    Text(
                        text = "今日余 ${item.totalRemainingToday} 项",
                        style = TextStyle(
                            color = GlanceTheme.colors.onSurfaceVariant,
                            fontSize = 11.sp,
                        ),
                        maxLines = 1,
                    )
                }
            }

            if (showUpcomingList && item.upcomingItems.isNotEmpty()) {
                Spacer(modifier = GlanceModifier.height(8.dp))
                Column(modifier = GlanceModifier.fillMaxWidth()) {
                    item.upcomingItems.forEach { upcoming ->
                        val nextLine = buildString {
                            append(upcoming.startClock)
                            append(" · ")
                            append(upcoming.title)
                            if (upcoming.location.isNotBlank()) {
                                append(" ")
                                append(upcoming.location)
                            }
                        }
                        Text(
                            text = nextLine,
                            style = TextStyle(
                                color = GlanceTheme.colors.onSurfaceVariant,
                                fontSize = 11.sp,
                            ),
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}

class RefreshFlowWidgetAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        FlowGlanceWidget().update(context, glanceId)
    }
}

class FlowGlanceWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = FlowGlanceWidget()
}

object FlowWidgetUpdater {
    @Volatile
    private var lastUpdateAt: Long = 0L

    suspend fun requestUpdate(context: Context, force: Boolean = false) {
        val now = System.currentTimeMillis()
        if (!force && now - lastUpdateAt < 30_000L) return
        lastUpdateAt = now
        runCatching {
            FlowGlanceWidget().updateAll(context)
        }
    }
}
