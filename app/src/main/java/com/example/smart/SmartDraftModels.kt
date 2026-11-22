package com.example.smart

import com.example.data.RepeatRule
import com.example.data.ScheduleCategory
import com.example.data.WeekType
import java.util.UUID

enum class DraftType(val label: String) {
    SCHEDULE("日程"),
    COURSE("课程"),
}

enum class ParseSource(val label: String) {
    OFFLINE_LOCAL("本地离线解析"),
    CUSTOM_OPENAI_API("自定义 AI 接口"),
    OFFLINE_FALLBACK("已自动回退离线解析"),
}

data class TimeConflict(
    val title: String,
    val timeRange: String,
    val isCourse: Boolean,
    val detail: String = "",
)

data class SmartParsedDraft(
    val id: String = UUID.randomUUID().toString(),
    val draftType: DraftType = DraftType.SCHEDULE,
    val title: String,
    val location: String = "",
    val startTimeMillis: Long,
    val endTimeMillis: Long,
    val daysOfWeek: List<Int> = emptyList(), // ISO 1..7 (Monday..Sunday)
    val startSection: Int = 1,
    val endSection: Int = 2,
    val startWeek: Int = 1,
    val endWeek: Int = 16,
    val weekType: WeekType = WeekType.ALL,
    val repeatRule: RepeatRule = RepeatRule.NONE,
    val reminderMinutesBefore: Int = 15,
    val enableDnd: Boolean = false,
    val category: ScheduleCategory = ScheduleCategory.WORK,
    val notes: String = "",
    val conflicts: List<TimeConflict> = emptyList(),
) {
    val hasConflicts: Boolean
        get() = conflicts.isNotEmpty()
}

data class SmartParseResult(
    val drafts: List<SmartParsedDraft>,
    val source: ParseSource,
    val notice: String? = null,
)
