package com.example.data

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.max
import kotlin.math.round

enum class WeekStartDay(val label: String) {
    MONDAY("周一"),
    SUNDAY("周日"),
}

object DateTimeUtils {

    private const val DAY_MILLIS = 86_400_000L

    fun formatTime(millis: Long): String {
        val formatter = SimpleDateFormat("HH:mm", Locale.CHINA)
        return formatter.format(Date(millis))
    }

    fun formatHourMinute(hour: Int, minute: Int): String {
        return String.format(Locale.US, "%02d:%02d", hour.coerceIn(0, 23), minute.coerceIn(0, 59))
    }

    fun formatTimeRange(startMillis: Long, endMillis: Long): String {
        return "${formatTime(startMillis)} - ${formatTime(endMillis)}"
    }

    fun formatDateShort(millis: Long): String {
        val formatter = SimpleDateFormat("M月d日 E", Locale.CHINA)
        return formatter.format(Date(millis))
    }

    fun formatMonthDay(millis: Long): String {
        val formatter = SimpleDateFormat("M/d", Locale.CHINA)
        return formatter.format(Date(millis))
    }

    fun formatDayOfMonth(millis: Long): String {
        val cal = Calendar.getInstance().apply { timeInMillis = millis }
        return cal.get(Calendar.DAY_OF_MONTH).toString()
    }

    fun formatMonthLabel(millis: Long): String {
        val cal = Calendar.getInstance().apply { timeInMillis = millis }
        return "${cal.get(Calendar.MONTH) + 1}月"
    }

    fun formatDateHeader(millis: Long): String {
        val formatter = SimpleDateFormat("M月d日 EEEE", Locale.CHINA)
        return formatter.format(Date(millis))
    }

    fun formatFullDate(millis: Long): String {
        val formatter = SimpleDateFormat("yyyy年M月d日", Locale.CHINA)
        return formatter.format(Date(millis))
    }

    fun formatDateKey(millis: Long): String {
        val formatter = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        return formatter.format(Date(millis))
    }

    fun parseDateKeyMillis(dateKey: String): Long? {
        if (dateKey.isBlank()) return null
        return runCatching {
            val formatter = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
                isLenient = false
            }
            formatter.parse(dateKey.trim())?.time?.let { startOfDayMillis(it) }
        }.getOrNull()
    }

    fun startOfDayMillis(millis: Long): Long {
        val cal = Calendar.getInstance().apply {
            timeInMillis = millis
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return cal.timeInMillis
    }

    /**
     * DST-safe day addition returning start-of-day timestamp.
     */
    fun addDays(millis: Long, deltaDays: Int): Long {
        val cal = Calendar.getInstance().apply {
            timeInMillis = startOfDayMillis(millis)
            add(Calendar.DAY_OF_YEAR, deltaDays)
        }
        return cal.timeInMillis
    }

    /**
     * Ordered ISO day numbers (1=Mon..7=Sun) matching the user's [weekStartDay] and [showWeekend] preference.
     */
    fun orderedIsoDays(
        weekStartDay: WeekStartDay = WeekStartDay.MONDAY,
        showWeekend: Boolean = true,
    ): List<Int> {
        if (!showWeekend) return listOf(1, 2, 3, 4, 5)
        return when (weekStartDay) {
            WeekStartDay.MONDAY -> listOf(1, 2, 3, 4, 5, 6, 7)
            WeekStartDay.SUNDAY -> listOf(7, 1, 2, 3, 4, 5, 6)
        }
    }

    /**
     * Returns Monday 00:00:00.000 of the week containing [millis].
     */
    fun mondayOfWeekMillis(millis: Long): Long {
        return weekStartOfWeekMillis(millis, WeekStartDay.MONDAY)
    }

    /**
     * Returns the first day (Monday or Sunday 00:00:00.000) of the week containing [millis].
     */
    fun weekStartOfWeekMillis(
        millis: Long,
        weekStartDay: WeekStartDay = WeekStartDay.MONDAY,
    ): Long {
        val cal = Calendar.getInstance().apply {
            timeInMillis = startOfDayMillis(millis)
            val dow = get(Calendar.DAY_OF_WEEK)
            val daysFromStart = when (weekStartDay) {
                WeekStartDay.MONDAY -> if (dow == Calendar.SUNDAY) 6 else dow - Calendar.MONDAY
                WeekStartDay.SUNDAY -> dow - Calendar.SUNDAY
            }
            add(Calendar.DAY_OF_YEAR, -daysFromStart)
        }
        return cal.timeInMillis
    }

    /**
     * Returns 7 day-start timestamps for the week containing [dateMillis] starting at [weekStartDay].
     */
    fun weekDatesContaining(
        dateMillis: Long,
        weekStartDay: WeekStartDay = WeekStartDay.MONDAY,
    ): List<Long> {
        val start = weekStartOfWeekMillis(dateMillis, weekStartDay)
        val cal = Calendar.getInstance().apply { timeInMillis = start }
        return List(7) { offset ->
            val c = (cal.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, offset) }
            c.timeInMillis
        }
    }

    /**
     * ISO day of week: 1 = Monday ... 7 = Sunday.
     */
    fun isoDayOfWeek(millis: Long): Int {
        val cal = Calendar.getInstance().apply { timeInMillis = millis }
        return when (cal.get(Calendar.DAY_OF_WEEK)) {
            Calendar.MONDAY -> 1
            Calendar.TUESDAY -> 2
            Calendar.WEDNESDAY -> 3
            Calendar.THURSDAY -> 4
            Calendar.FRIDAY -> 5
            Calendar.SATURDAY -> 6
            else -> 7
        }
    }

    fun dayOfWeekShortChinese(isoDay: Int): String = when (isoDay) {
        1 -> "一"
        2 -> "二"
        3 -> "三"
        4 -> "四"
        5 -> "五"
        6 -> "六"
        else -> "日"
    }

    fun dayOfWeekFullChinese(isoDay: Int): String = "周${dayOfWeekShortChinese(isoDay)}"

    /**
     * Computes 1-based semester week index given week 1 start timestamp (DST-safe).
     */
    fun computeSemesterWeek(
        semesterStartMondayMillis: Long,
        targetMillis: Long,
        totalWeeks: Int,
        weekStartDay: WeekStartDay = WeekStartDay.MONDAY,
    ): Int {
        val startBase = weekStartOfWeekMillis(semesterStartMondayMillis, weekStartDay)
        val targetBase = weekStartOfWeekMillis(targetMillis, weekStartDay)
        val diffDays = round((targetBase - startBase).toDouble() / DAY_MILLIS.toDouble()).toInt()
        val week = Math.floorDiv(diffDays, 7) + 1
        return week.coerceIn(1, totalWeeks.coerceAtLeast(1))
    }

    /**
     * Returns 7 day-start timestamps for the given 1-based [weekNumber] starting on [weekStartDay].
     */
    fun weekDatesMillis(
        semesterStartMondayMillis: Long,
        weekNumber: Int,
        weekStartDay: WeekStartDay = WeekStartDay.MONDAY,
    ): List<Long> {
        val base = weekStartOfWeekMillis(semesterStartMondayMillis, weekStartDay)
        val cal = Calendar.getInstance().apply {
            timeInMillis = base
            add(Calendar.WEEK_OF_YEAR, (weekNumber - 1).coerceAtLeast(0))
        }
        return List(7) { offset ->
            val c = (cal.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, offset) }
            c.timeInMillis
        }
    }

    fun isSameDay(millisA: Long, millisB: Long): Boolean {
        val calA = Calendar.getInstance().apply { timeInMillis = millisA }
        val calB = Calendar.getInstance().apply { timeInMillis = millisB }
        return calA.get(Calendar.YEAR) == calB.get(Calendar.YEAR) &&
            calA.get(Calendar.DAY_OF_YEAR) == calB.get(Calendar.DAY_OF_YEAR)
    }

    fun isToday(millis: Long, nowMillis: Long = System.currentTimeMillis()): Boolean {
        return isSameDay(millis, nowMillis)
    }

    fun isTomorrow(millis: Long, nowMillis: Long = System.currentTimeMillis()): Boolean {
        val tomorrow = Calendar.getInstance().apply {
            timeInMillis = nowMillis
            add(Calendar.DAY_OF_YEAR, 1)
        }
        return isSameDay(millis, tomorrow.timeInMillis)
    }

    fun combineDateAndTime(dateMillis: Long, hour: Int, minute: Int): Long {
        val cal = Calendar.getInstance().apply {
            timeInMillis = dateMillis
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return cal.timeInMillis
    }

    fun extractHourMinute(millis: Long): Pair<Int, Int> {
        val cal = Calendar.getInstance().apply { timeInMillis = millis }
        return cal.get(Calendar.HOUR_OF_DAY) to cal.get(Calendar.MINUTE)
    }

    fun formatCountdownDigital(remainingMillis: Long): String {
        val totalSeconds = max(0L, (remainingMillis + 999L) / 1000L)
        val hours = totalSeconds / 3600L
        val minutes = (totalSeconds % 3600L) / 60L
        val seconds = totalSeconds % 60L
        return if (hours > 0) {
            String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(Locale.US, "%02d:%02d", minutes, seconds)
        }
    }

    fun formatCountdownChinese(remainingMillis: Long): String {
        val totalSeconds = max(0L, (remainingMillis + 999L) / 1000L)
        val hours = totalSeconds / 3600L
        val minutes = (totalSeconds % 3600L) / 60L
        val seconds = totalSeconds % 60L
        return when {
            hours > 0 && minutes > 0 -> "${hours}小时${minutes}分"
            hours > 0 -> "${hours}小时"
            minutes > 0 -> "${minutes}分钟"
            else -> "${seconds}秒"
        }
    }

    fun formatCapsuleShortText(remainingMillis: Long, isInProgress: Boolean): String {
        val totalSeconds = max(0L, (remainingMillis + 999L) / 1000L)
        val hours = totalSeconds / 3600L
        val minutes = (totalSeconds % 3600L) / 60L
        val seconds = totalSeconds % 60L
        return when {
            hours > 0 -> "${hours}h${minutes}m"
            minutes >= 10 -> "${minutes}m"
            minutes > 0 -> String.format(Locale.US, "%d:%02d", minutes, seconds)
            else -> "${seconds}s"
        }
    }
}
