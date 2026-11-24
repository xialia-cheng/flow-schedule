package com.example.data

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "schedules")
data class ScheduleEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val title: String,
    val notes: String = "",
    val startTimeMillis: Long,
    val endTimeMillis: Long,
    val repeatRule: RepeatRule = RepeatRule.NONE,
    val reminderMinutesBefore: Int = 15,
    val category: ScheduleCategory = ScheduleCategory.WORK,
    @ColumnInfo(defaultValue = "")
    val customTagId: String = "",
    @ColumnInfo(defaultValue = "")
    val customTagName: String = "",
    @ColumnInfo(defaultValue = "")
    val customTagColorHex: String = "",
    val isCompleted: Boolean = false,
    val completedAtMillis: Long? = null,
    val isTestSchedule: Boolean = false,
    val dismissedLiveUpdate: Boolean = false,
    val createdAtMillis: Long = System.currentTimeMillis(),
) {
    fun resolvedTagLabel(customTags: List<CustomScheduleTag> = emptyList()): String {
        if (customTagId.isNotBlank()) {
            val matched = customTags.firstOrNull { it.id == customTagId }
            if (matched != null) return matched.name
        }
        if (customTagName.isNotBlank()) return customTagName
        return category.label
    }

    fun resolvedTagColor(customTags: List<CustomScheduleTag> = emptyList()): Color {
        if (customTagId.isNotBlank()) {
            val matched = customTags.firstOrNull { it.id == customTagId }
            if (matched != null) return matched.resolvedColor()
        }
        if (customTagColorHex.isNotBlank()) {
            CourseColorPalette.parseHexColorOrNull(customTagColorHex)?.let { return it }
        }
        return category.accentColor
    }

    fun resolvedTagIcon(customTags: List<CustomScheduleTag> = emptyList()): ImageVector {
        if (customTagId.isNotBlank()) {
            val matched = customTags.firstOrNull { it.id == customTagId }
            if (matched != null) return matched.resolvedIcon()
        }
        return TagIconCatalog.iconForCategory(category)
    }

    val reminderTriggerMillis: Long
        get() = startTimeMillis - reminderMinutesBefore * 60_000L

    val durationMillis: Long
        get() = (endTimeMillis - startTimeMillis).coerceAtLeast(1_000L)

    fun isInProgress(nowMillis: Long): Boolean {
        return !isCompleted && nowMillis in startTimeMillis until endTimeMillis
    }

    fun isInReminderWindow(nowMillis: Long): Boolean {
        if (isCompleted || reminderMinutesBefore <= 0) return false
        return nowMillis in reminderTriggerMillis until startTimeMillis
    }

    fun isLiveUpdateEligible(nowMillis: Long): Boolean {
        return !isCompleted && !dismissedLiveUpdate && (isInProgress(nowMillis) || isInReminderWindow(nowMillis))
    }

    fun isExpired(nowMillis: Long): Boolean {
        return !isCompleted && nowMillis >= endTimeMillis
    }

    /**
     * Progress in 0..1000 range for smooth ProgressStyle rendering.
     * During reminder window (before start): maps 0..300 to build anticipation.
     * During active schedule (start..end): maps 300..1000 (or 0..1000 if no reminder lead).
     */
    fun liveProgressPermille(nowMillis: Long): Int {
        return when {
            isInReminderWindow(nowMillis) -> {
                val leadTotal = (reminderMinutesBefore * 60_000L).coerceAtLeast(1_000L)
                val elapsed = (nowMillis - reminderTriggerMillis).coerceIn(0L, leadTotal)
                ((elapsed * 300L) / leadTotal).toInt().coerceIn(0, 300)
            }
            isInProgress(nowMillis) -> {
                val hasLead = reminderMinutesBefore > 0 && !isTestSchedule
                val startMark = if (hasLead) 300 else 0
                val span = 1000 - startMark
                val elapsed = (nowMillis - startTimeMillis).coerceIn(0L, durationMillis)
                (startMark + ((elapsed * span) / durationMillis).toInt()).coerceIn(startMark, 1000)
            }
            nowMillis >= endTimeMillis -> 1000
            else -> 0
        }
    }

    /**
     * Pure linear progress 0f..1f for the active card UI.
     */
    fun uiProgressFraction(nowMillis: Long): Float {
        return when {
            isInProgress(nowMillis) -> {
                val elapsed = (nowMillis - startTimeMillis).coerceIn(0L, durationMillis)
                (elapsed.toFloat() / durationMillis.toFloat()).coerceIn(0f, 1f)
            }
            isInReminderWindow(nowMillis) -> {
                val leadTotal = (reminderMinutesBefore * 60_000L).coerceAtLeast(1_000L)
                val elapsed = (nowMillis - reminderTriggerMillis).coerceIn(0L, leadTotal)
                (elapsed.toFloat() / leadTotal.toFloat()).coerceIn(0f, 1f)
            }
            isCompleted || nowMillis >= endTimeMillis -> 1f
            else -> 0f
        }
    }
}
