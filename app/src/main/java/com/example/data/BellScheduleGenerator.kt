package com.example.data

/**
 * Pure Kotlin configuration and generation helpers for customizable class bell schedules
 * supporting 6..16 daily sections, arbitrary per-section durations, and batch calculation.
 */
data class BellBatchConfig(
    val totalSections: Int = BellScheduleGenerator.DEFAULT_SECTION_COUNT,
    val classDurationMinutes: Int = 45,
    val breakDurationMinutes: Int = 10,
    val morningCount: Int = 4,
    val afternoonCount: Int = 4,
    val morningStartHour: Int = 8,
    val morningStartMinute: Int = 0,
    val afternoonStartHour: Int = 14,
    val afternoonStartMinute: Int = 0,
    val eveningStartHour: Int = 19,
    val eveningStartMinute: Int = 0,
) {
    val safeTotalSections: Int
        get() = totalSections.coerceIn(
            BellScheduleGenerator.MIN_SECTION_COUNT,
            BellScheduleGenerator.MAX_SECTION_COUNT,
        )

    val safeMorningCount: Int
        get() = morningCount.coerceIn(1, (safeTotalSections - 1).coerceAtLeast(1))

    val safeAfternoonCount: Int
        get() = afternoonCount.coerceIn(0, (safeTotalSections - safeMorningCount).coerceAtLeast(0))

    val eveningCount: Int
        get() = (safeTotalSections - safeMorningCount - safeAfternoonCount).coerceAtLeast(0)
}

object BellScheduleGenerator {
    const val MIN_SECTION_COUNT = 6
    const val MAX_SECTION_COUNT = 16
    const val DEFAULT_SECTION_COUNT = 12
    private const val MAX_MINUTE_OF_DAY = 23 * 60 + 59

    /**
     * Computes a balanced (morning, afternoon, evening) split for any total section count in 6..16.
     */
    fun defaultPeriodSplit(totalSections: Int): Triple<Int, Int, Int> {
        val total = totalSections.coerceIn(MIN_SECTION_COUNT, MAX_SECTION_COUNT)
        return when (total) {
            6 -> Triple(3, 3, 0)
            7 -> Triple(4, 3, 0)
            8 -> Triple(4, 4, 0)
            9 -> Triple(4, 4, 1)
            10 -> Triple(4, 4, 2)
            11 -> Triple(4, 4, 3)
            12 -> Triple(4, 4, 4)
            13 -> Triple(4, 5, 4)
            14 -> Triple(5, 5, 4)
            15 -> Triple(5, 5, 5)
            else -> Triple(5, 6, 5)
        }
    }

    /**
     * Generates a complete 1..N bell schedule from batch parameters.
     * Each period (morning / afternoon / evening) starts at its configured start time
     * and advances sequentially using [BellBatchConfig.classDurationMinutes] and
     * [BellBatchConfig.breakDurationMinutes].
     */
    fun generate(
        timetableId: Long,
        config: BellBatchConfig,
    ): List<SectionTimeEntity> {
        val total = config.safeTotalSections
        val duration = config.classDurationMinutes.coerceIn(10, 180)
        val breakMin = config.breakDurationMinutes.coerceIn(0, 60)

        val morningCount = config.safeMorningCount
        val afternoonCount = config.safeAfternoonCount
        val eveningCount = config.eveningCount

        val result = ArrayList<SectionTimeEntity>(total)
        var sectionNumber = 1

        fun appendPeriod(count: Int, startHour: Int, startMinute: Int) {
            if (count <= 0) return
            var cursor = (startHour.coerceIn(0, 23) * 60 + startMinute.coerceIn(0, 59))
                .coerceAtMost(MAX_MINUTE_OF_DAY - 1)
            repeat(count) { idx ->
                val startTotal = cursor.coerceIn(0, MAX_MINUTE_OF_DAY - 1)
                val endTotal = (startTotal + duration).coerceIn(startTotal + 1, MAX_MINUTE_OF_DAY)
                result.add(
                    SectionTimeEntity(
                        timetableId = timetableId,
                        sectionNumber = sectionNumber++,
                        startHour = startTotal / 60,
                        startMinute = startTotal % 60,
                        endHour = endTotal / 60,
                        endMinute = endTotal % 60,
                    ),
                )
                if (idx < count - 1) {
                    cursor = (endTotal + breakMin).coerceAtMost(MAX_MINUTE_OF_DAY - 1)
                }
            }
        }

        appendPeriod(morningCount, config.morningStartHour, config.morningStartMinute)
        appendPeriod(afternoonCount, config.afternoonStartHour, config.afternoonStartMinute)
        appendPeriod(eveningCount, config.eveningStartHour, config.eveningStartMinute)

        return result
    }

    /**
     * Resizes an existing bell schedule to [newCount] (6..16) while preserving every
     * existing custom start/end time. Newly appended trailing slots continue naturally
     * after the last existing slot using its inferred duration and [defaultBreakMinutes].
     */
    fun resize(
        existing: List<SectionTimeEntity>,
        newCount: Int,
        timetableId: Long,
        defaultDurationMinutes: Int = 45,
        defaultBreakMinutes: Int = 10,
    ): List<SectionTimeEntity> {
        val targetCount = newCount.coerceIn(MIN_SECTION_COUNT, MAX_SECTION_COUNT)
        val normalized = existing
            .sortedBy { it.sectionNumber }
            .distinctBy { it.sectionNumber }
            .mapIndexed { idx, slot ->
                slot.copy(timetableId = timetableId, sectionNumber = idx + 1)
            }

        val base = if (normalized.isEmpty()) {
            CourseColorPalette.defaultSectionTimes(timetableId)
        } else {
            normalized
        }

        if (base.size >= targetCount) {
            return base.take(targetCount)
        }

        val result = base.toMutableList()
        val inferredDuration = base.lastOrNull()
            ?.let { durationMinutes(it) }
            ?.takeIf { it in 15..120 }
            ?: defaultDurationMinutes.coerceIn(10, 180)
        val safeBreak = defaultBreakMinutes.coerceIn(0, 60)

        while (result.size < targetCount) {
            val prev = result.last()
            val prevEnd = prev.endHour * 60 + prev.endMinute
            val nextStart = (prevEnd + safeBreak).coerceIn(0, MAX_MINUTE_OF_DAY - 1)
            val nextEnd = (nextStart + inferredDuration).coerceIn(nextStart + 1, MAX_MINUTE_OF_DAY)
            result.add(
                SectionTimeEntity(
                    timetableId = timetableId,
                    sectionNumber = result.size + 1,
                    startHour = nextStart / 60,
                    startMinute = nextStart % 60,
                    endHour = nextEnd / 60,
                    endMinute = nextEnd % 60,
                ),
            )
        }
        return result
    }

    /**
     * Normalizes a list of section times to contiguous 1..N numbering (within 6..16)
     * and ensures valid 24-hour clock bounds.
     */
    fun normalize(
        times: List<SectionTimeEntity>,
        timetableId: Long,
    ): List<SectionTimeEntity> {
        if (times.isEmpty()) return CourseColorPalette.defaultSectionTimes(timetableId)
        val clampedCount = times.size.coerceIn(MIN_SECTION_COUNT, MAX_SECTION_COUNT)
        val sorted = times
            .sortedBy { it.sectionNumber }
            .mapIndexed { index, slot ->
                val sh = slot.startHour.coerceIn(0, 23)
                val sm = slot.startMinute.coerceIn(0, 59)
                val eh = slot.endHour.coerceIn(0, 23)
                val em = slot.endMinute.coerceIn(0, 59)
                val startTotal = sh * 60 + sm
                val rawEndTotal = eh * 60 + em
                val safeEndTotal = if (rawEndTotal > startTotal) {
                    rawEndTotal
                } else {
                    (startTotal + 1).coerceAtMost(MAX_MINUTE_OF_DAY)
                }
                SectionTimeEntity(
                    timetableId = timetableId,
                    sectionNumber = index + 1,
                    startHour = startTotal / 60,
                    startMinute = startTotal % 60,
                    endHour = safeEndTotal / 60,
                    endMinute = safeEndTotal % 60,
                )
            }
        return if (sorted.size < MIN_SECTION_COUNT) {
            resize(sorted, MIN_SECTION_COUNT, timetableId)
        } else {
            sorted.take(clampedCount)
        }
    }

    fun durationMinutes(slot: SectionTimeEntity): Int {
        val start = slot.startHour * 60 + slot.startMinute
        val end = slot.endHour * 60 + slot.endMinute
        return end - start
    }

    fun breakMinutesBetween(previous: SectionTimeEntity, next: SectionTimeEntity): Int {
        val prevEnd = previous.endHour * 60 + previous.endMinute
        val nextStart = next.startHour * 60 + next.startMinute
        return nextStart - prevEnd
    }

    fun periodLabelFor(slot: SectionTimeEntity): String = when {
        slot.startHour < 12 -> "上午"
        slot.startHour < 18 -> "下午"
        else -> "晚上"
    }

    fun formatDurationZh(minutes: Int): String {
        if (minutes <= 0) return "${minutes}分"
        val h = minutes / 60
        val m = minutes % 60
        return when {
            h > 0 && m > 0 -> "${h}小时${m}分"
            h > 0 -> "${h}小时"
            else -> "${m}分钟"
        }
    }

    fun formatTime(hour: Int, minute: Int): String {
        return String.format("%02d:%02d", hour.coerceIn(0, 23), minute.coerceIn(0, 59))
    }
}
