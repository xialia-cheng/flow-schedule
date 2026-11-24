package com.example.notification

import com.example.data.DateTimeUtils

enum class LiveActivitySourceType {
    SCHEDULE,
    COURSE,
    FOCUS,
}

/**
 * Unified real-time activity representation for Daily Schedules, Timetable Courses, and Study Focus sessions,
 * powering Android 16+ Live Updates and ColorOS 17 Fluid Cloud capsules across apps.
 */
data class LiveActivityItem(
    val sourceType: LiveActivitySourceType,
    val sourceId: Long,
    val dateKey: String,
    val title: String,
    val location: String = "",
    val teacher: String = "",
    val notes: String = "",
    val sectionLabel: String = "",
    val startTimeMillis: Long,
    val endTimeMillis: Long,
    val reminderMinutesBefore: Int,
    val isTest: Boolean = false,
    val isBreakPhase: Boolean = false,
    val accentColorArgb: Int? = null,
    val showLocation: Boolean = true,
    val showTeacher: Boolean = true,
    val showNotes: Boolean = false,
    val showLockscreenDetails: Boolean = false,
) {
    val reminderTriggerMillis: Long
        get() = startTimeMillis - reminderMinutesBefore * 60_000L

    val durationMillis: Long
        get() = (endTimeMillis - startTimeMillis).coerceAtLeast(1_000L)

    val uniqueKey: String
        get() = "${sourceType.name}_${sourceId}_$dateKey"

    fun rawLocationName(): String = location.trim().removePrefix("@").trim()

    fun formattedLocation(): String {
        val clean = rawLocationName()
        return if (clean.isEmpty()) "" else "@$clean"
    }

    fun titleWithLocation(): String {
        if (!showLocation) return title
        val loc = formattedLocation()
        return if (loc.isNotEmpty()) "$title $loc" else title
    }

    /**
     * Compact 2-character course abbreviation for 96dp status bar Live Update capsules
     * (Android 16 recommends <= 7 characters in setShortCriticalText to avoid truncation).
     */
    fun shortCapsuleName(): String {
        val clean = title.trim()
        if (clean.isEmpty()) return ""
        for ((keyword, abbr) in COMMON_COURSE_ABBREVIATIONS) {
            if (clean.contains(keyword)) return abbr
        }
        val stripped = clean.replace(Regex("[\\s（(【\\[].*"), "").ifEmpty { clean }
        return if (stripped.length <= 2) stripped else stripped.take(2)
    }

    /**
     * Builds the compact status bar capsule text (<= 7 chars) showing course abbreviation +
     * live remaining time so Android 16 / ColorOS 17 Fluid Cloud displays it without truncation.
     */
    fun capsuleChipText(nowMillis: Long): String {
        val isInProgress = isInProgress(nowMillis)
        val targetMillis = if (isInProgress) endTimeMillis else startTimeMillis
        val remainingMillis = (targetMillis - nowMillis).coerceAtLeast(0L)
        val compactTime = formatCompactChipDuration(remainingMillis)

        if (sourceType == LiveActivitySourceType.COURSE) {
            val shortName = shortCapsuleName()
            if (shortName.isNotEmpty()) {
                val spaced = "$shortName $compactTime"
                if (spaced.length <= MAX_CAPSULE_TEXT_CHARS) return spaced
                val tight = "$shortName$compactTime"
                if (tight.length <= MAX_CAPSULE_TEXT_CHARS) return tight
            }
        }
        return DateTimeUtils.formatCapsuleShortText(remainingMillis, isInProgress)
            .take(MAX_CAPSULE_TEXT_CHARS)
    }

    /**
     * Builds the expanded notification / Fluid Cloud body line highlighting phase,
     * exact target clock time, and user-selected metadata (location / teacher / notes).
     * Live ticking countdown is driven natively by SystemUI Chronometer.
     */
    fun expandedContentText(nowMillis: Long): String {
        val isInProgress = isInProgress(nowMillis)
        val parts = mutableListOf<String>()

        if (sourceType == LiveActivitySourceType.FOCUS) {
            val endClock = DateTimeUtils.formatTime(endTimeMillis)
            val prefix = if (isBreakPhase) "专注休息" else "沉浸专注"
            parts.add("$prefix · $endClock 结束")
        } else if (isInProgress) {
            val endClock = DateTimeUtils.formatTime(endTimeMillis)
            val activePrefix = if (sourceType == LiveActivitySourceType.COURSE) "正在上课" else "进行中"
            val endVerb = if (sourceType == LiveActivitySourceType.COURSE) "下课" else "结束"
            parts.add("$activePrefix · $endClock $endVerb")
        } else {
            val startClock = DateTimeUtils.formatTime(startTimeMillis)
            val startVerb = if (sourceType == LiveActivitySourceType.COURSE) "上课" else "开始"
            parts.add("课前准备 · $startClock $startVerb")
        }

        val cleanLoc = rawLocationName()
        if (showLocation && cleanLoc.isNotEmpty()) {
            parts.add("地点 $cleanLoc")
        }

        val cleanTeacher = teacher.trim()
        if (showTeacher && cleanTeacher.isNotEmpty()) {
            parts.add("教师 $cleanTeacher")
        }

        if (showNotes) {
            val cleanNotes = notes
                .replace(Regex("@[^\\s，。·,]+"), "")
                .replace(Regex("\\s+"), " ")
                .trim()
            if (cleanNotes.isNotEmpty()) {
                val snippet = if (cleanNotes.length > 24) "${cleanNotes.take(24)}…" else cleanNotes
                parts.add("备注 $snippet")
            }
        }

        return parts.joinToString(" · ")
    }

    fun isInProgress(nowMillis: Long): Boolean {
        return nowMillis in startTimeMillis until endTimeMillis
    }

    fun isInReminderWindow(nowMillis: Long): Boolean {
        if (reminderMinutesBefore <= 0) return false
        return nowMillis in reminderTriggerMillis until startTimeMillis
    }

    fun isLiveEligible(nowMillis: Long): Boolean {
        return isInProgress(nowMillis) || isInReminderWindow(nowMillis)
    }

    fun hasLeadPrepSegment(): Boolean {
        return reminderMinutesBefore > 0
    }

    fun progressPermille(nowMillis: Long): Int {
        return when {
            isInReminderWindow(nowMillis) -> {
                val leadTotal = (reminderMinutesBefore * 60_000L).coerceAtLeast(1_000L)
                val elapsed = (nowMillis - reminderTriggerMillis).coerceIn(0L, leadTotal)
                ((elapsed * 300L) / leadTotal).toInt().coerceIn(0, 300)
            }
            isInProgress(nowMillis) -> {
                val hasLead = hasLeadPrepSegment()
                val startMark = if (hasLead) 300 else 0
                val span = 1000 - startMark
                val elapsed = (nowMillis - startTimeMillis).coerceIn(0L, durationMillis)
                (startMark + ((elapsed * span) / durationMillis).toInt()).coerceIn(startMark, 1000)
            }
            nowMillis >= endTimeMillis -> 1000
            else -> 0
        }
    }

    fun alarmRequestKey(): Int {
        val base = (sourceId.toInt() and 0x3FFF) * 8
        val typeOffset = when {
            isTest -> 800_000
            sourceType == LiveActivitySourceType.COURSE -> 400_000
            else -> 0
        }
        val dayHash = (DateTimeUtils.isoDayOfWeek(startTimeMillis) and 0x7) * 50_000
        return typeOffset + dayHash + base
    }

    companion object {
        private const val MAX_CAPSULE_TEXT_CHARS = 7

        private val COMMON_COURSE_ABBREVIATIONS = listOf(
            "高等数学" to "高数",
            "线性代数" to "线代",
            "概率论" to "概率",
            "大学物理" to "大物",
            "大学英语" to "英语",
            "数据结构" to "数构",
            "操作系统" to "操统",
            "计算机网络" to "计网",
            "计算机组成" to "计组",
            "离散数学" to "离散",
            "编译原理" to "编译",
            "软件工程" to "软工",
            "数据库" to "数据",
            "人工智能" to "AI",
            "机器学习" to "机学",
            "形势与政策" to "形策",
            "马克思主义" to "马原",
            "思想道德" to "思修",
            "中国近代史" to "近史",
            "体育" to "体育",
        )

        private fun formatCompactChipDuration(remainingMillis: Long): String {
            val totalSeconds = kotlin.math.max(0L, (remainingMillis + 999L) / 1000L)
            val hours = totalSeconds / 3600L
            val minutes = (totalSeconds % 3600L) / 60L
            val seconds = totalSeconds % 60L
            return when {
                hours > 0 && minutes > 0 -> "${hours}h${minutes}m"
                hours > 0 -> "${hours}h"
                minutes > 0 -> "${minutes}m"
                else -> "${seconds}s"
            }
        }
    }
}
