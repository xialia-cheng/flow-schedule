package com.example.data

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

enum class WeekType(val label: String) {
    ALL("全周"),
    ODD("单周"),
    EVEN("双周");

    fun matchesWeek(week: Int): Boolean = when (this) {
        ALL -> true
        ODD -> week % 2 == 1
        EVEN -> week % 2 == 0
    }
}

@Entity(tableName = "timetables")
data class TimetableEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val name: String,
    val semesterStartMondayMillis: Long,
    val totalWeeks: Int = 20,
    val isActive: Boolean = false,
    val createdAtMillis: Long = System.currentTimeMillis(),
)

@Entity(
    tableName = "courses",
    foreignKeys = [
        ForeignKey(
            entity = TimetableEntity::class,
            parentColumns = ["id"],
            childColumns = ["timetableId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["timetableId"])],
)
data class CourseEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val timetableId: Long,
    val name: String,
    val location: String = "",
    val teacher: String = "",
    /** 1 = Monday .. 7 = Sunday */
    val dayOfWeek: Int,
    /** 1..16 */
    val startSection: Int,
    /** 1..16, >= startSection */
    val endSection: Int,
    val startWeek: Int = 1,
    val endWeek: Int = 16,
    val weekType: WeekType = WeekType.ALL,
    @ColumnInfo(defaultValue = "")
    val customWeeksCsv: String = "",
    val colorIndex: Int = 0,
    @ColumnInfo(defaultValue = "")
    val customColorHex: String = "",
    val notes: String = "",
) {
    fun parsedCustomWeeks(): Set<Int> = parseCustomWeeksCsv(customWeeksCsv)

    fun resolvedWeekSet(maxWeek: Int = maxOf(endWeek, 52)): Set<Int> {
        val explicit = parsedCustomWeeks()
        if (explicit.isNotEmpty()) return explicit
        val lo = startWeek.coerceAtLeast(1)
        val hi = endWeek.coerceIn(lo, maxWeek.coerceAtLeast(lo))
        return (lo..hi).filter { weekType.matchesWeek(it) }.toSet()
    }

    fun isInWeek(week: Int): Boolean {
        val explicit = parsedCustomWeeks()
        if (explicit.isNotEmpty()) {
            return week in explicit
        }
        return week in startWeek..endWeek && weekType.matchesWeek(week)
    }

    fun formattedLocation(): String {
        val trimmed = location.trim()
        if (trimmed.isEmpty()) return ""
        return if (trimmed.startsWith("@")) trimmed else "@$trimmed"
    }

    /**
     * Compact classroom label for dense timetable grid cards: strips repetitive campus prefixes
     * such as "越溪校区 ", "石湖校区 ", "天平校区 ", or generic "XX校区 " while preserving the exact
     * room code (e.g. "越溪校区 流-515实" -> "@流-515实"). Full location remains shown in details.
     */
    fun compactDisplayLocation(): String {
        val raw = location.trim().removePrefix("@").trim()
        if (raw.isEmpty()) return ""
        val shortened = CAMPUS_PREFIX_REGEX.replaceFirst(raw, "").trim()
        val effective = shortened.ifEmpty { raw }
        return "@$effective"
    }

    /**
     * Clean classroom label for dense timetable grid cards: strips campus prefixes
     * and omits '@' so long room codes wrap cleanly without awkward line breaks.
     */
    fun gridDisplayLocation(): String {
        val raw = location.trim().removePrefix("@").trim()
        if (raw.isEmpty()) return ""
        val shortened = CAMPUS_PREFIX_REGEX.replaceFirst(raw, "").trim()
        return shortened.ifEmpty { raw }
    }

    fun resolvedColorStyle(): CourseColorStyle {
        return CourseColorPalette.resolveStyle(colorIndex, customColorHex)
    }

    fun weekRangeLabel(): String {
        val explicit = parsedCustomWeeks()
        if (explicit.isNotEmpty()) {
            return formatCompressedWeekRanges(explicit)
        }
        val suffix = when (weekType) {
            WeekType.ALL -> ""
            WeekType.ODD -> " 单周"
            WeekType.EVEN -> " 双周"
        }
        return if (startWeek == endWeek) {
            "第${startWeek}周$suffix"
        } else {
            "${startWeek}-${endWeek}周$suffix"
        }
    }

    companion object {
        const val MAX_TOTAL_WEEKS = 52

        private val CAMPUS_PREFIX_REGEX =
            Regex("^(?:越溪|石湖|天平|江枫|独墅湖|阳澄湖|天赐庄|[\\u4e00-\\u9fa5]{1,5})校区[\\s·•\\-—/:：]*")

        fun parseCustomWeeksCsv(csv: String): Set<Int> {
            if (csv.isBlank()) return emptySet()
            return csv.split(',')
                .mapNotNull { it.trim().toIntOrNull() }
                .filter { it in 1..MAX_TOTAL_WEEKS }
                .toSortedSet()
        }

        fun encodeCustomWeeksCsv(weeks: Collection<Int>): String {
            return weeks.filter { it in 1..MAX_TOTAL_WEEKS }
                .distinct()
                .sorted()
                .joinToString(",")
        }

        /**
         * Derives canonical (startWeek, endWeek, weekType, customWeeksCsv) from an arbitrary set of weeks.
         * Leaves customWeeksCsv empty when the set exactly matches a clean contiguous ALL/ODD/EVEN range.
         */
        fun summarizeWeeks(weeks: Collection<Int>): QuadWeekSpec {
            val sorted = weeks.filter { it in 1..MAX_TOTAL_WEEKS }.distinct().sorted()
            if (sorted.isEmpty()) {
                return QuadWeekSpec(1, 16, WeekType.ALL, "")
            }
            val minW = sorted.first()
            val maxW = sorted.last()
            val fullContiguous = (minW..maxW).toList()
            val oddContiguous = (minW..maxW).filter { it % 2 == 1 }
            val evenContiguous = (minW..maxW).filter { it % 2 == 0 }
            return when {
                sorted == fullContiguous -> QuadWeekSpec(minW, maxW, WeekType.ALL, "")
                sorted == oddContiguous && sorted.size >= 2 -> QuadWeekSpec(minW, maxW, WeekType.ODD, "")
                sorted == evenContiguous && sorted.size >= 2 -> QuadWeekSpec(minW, maxW, WeekType.EVEN, "")
                else -> QuadWeekSpec(minW, maxW, WeekType.ALL, sorted.joinToString(","))
            }
        }

        fun formatCompressedWeekRanges(weeks: Collection<Int>): String {
            val sorted = weeks.filter { it in 1..MAX_TOTAL_WEEKS }.distinct().sorted()
            if (sorted.isEmpty()) return "未选择周次"
            val minW = sorted.first()
            val maxW = sorted.last()
            if (sorted.size >= 2) {
                val oddContiguous = (minW..maxW).filter { it % 2 == 1 }
                if (sorted == oddContiguous) return "${minW}-${maxW}周 单周"
                val evenContiguous = (minW..maxW).filter { it % 2 == 0 }
                if (sorted == evenContiguous) return "${minW}-${maxW}周 双周"
            }
            val segments = mutableListOf<String>()
            var runStart = sorted.first()
            var prev = sorted.first()
            for (i in 1 until sorted.size) {
                val cur = sorted[i]
                if (cur == prev + 1) {
                    prev = cur
                } else {
                    segments.add(if (runStart == prev) "$runStart" else "$runStart-$prev")
                    runStart = cur
                    prev = cur
                }
            }
            segments.add(if (runStart == prev) "$runStart" else "$runStart-$prev")
            return "${segments.joinToString(", ")}周"
        }
    }
}

data class QuadWeekSpec(
    val startWeek: Int,
    val endWeek: Int,
    val weekType: WeekType,
    val customWeeksCsv: String,
)

@Entity(
    tableName = "section_times",
    primaryKeys = ["timetableId", "sectionNumber"],
    foreignKeys = [
        ForeignKey(
            entity = TimetableEntity::class,
            parentColumns = ["id"],
            childColumns = ["timetableId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["timetableId"])],
)
data class SectionTimeEntity(
    val timetableId: Long,
    val sectionNumber: Int,
    val startHour: Int,
    val startMinute: Int,
    val endHour: Int,
    val endMinute: Int,
) {
    fun startLabel(): String = DateTimeUtils.formatHourMinute(startHour, startMinute)
    fun endLabel(): String = DateTimeUtils.formatHourMinute(endHour, endMinute)
}

/**
 * Tracks per-day completion / snooze / dismissal state for recurring weekly course occurrences
 * so Fluid Cloud notification buttons ("已完成" / "延后10分钟") persist cleanly for that class session.
 */
@Entity(
    tableName = "course_occurrence_states",
    primaryKeys = ["courseId", "dateKey"],
)
data class CourseOccurrenceStateEntity(
    val courseId: Long,
    val dateKey: String,
    val isCompleted: Boolean = false,
    val snoozeMinutes: Int = 0,
    val dismissedLiveUpdate: Boolean = false,
)

@Entity(tableName = "study_tasks")
data class StudyTaskEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val title: String,
    val courseTag: String = "",
    val targetMinutes: Int = 25,
    val isCompleted: Boolean = false,
    val createdAtMillis: Long = System.currentTimeMillis(),
)

/**
 * Curated 16-color ColorOS 17 Aquamorphic high-differentiation palette + custom Hex color builder.
 */
data class CourseColorStyle(
    val name: String,
    val lightBg: Color,
    val lightText: Color,
    val lightAccent: Color,
    val darkBg: Color,
    val darkText: Color,
    val darkAccent: Color,
)

object CourseColorPalette {
    val styles: List<CourseColorStyle> = listOf(
        // Row 1 (8 distinct primary hues)
        CourseColorStyle(
            name = "晴空蓝",
            lightBg = Color(0xFFE3F1FF),
            lightText = Color(0xFF0A4988),
            lightAccent = Color(0xFF0B72FF),
            darkBg = Color(0xFF122C47),
            darkText = Color(0xFFBAE0FF),
            darkAccent = Color(0xFF38BDF8),
        ),
        CourseColorStyle(
            name = "流体青",
            lightBg = Color(0xFFDDF7F2),
            lightText = Color(0xFF06564C),
            lightAccent = Color(0xFF009B8A),
            darkBg = Color(0xFF0F3532),
            darkText = Color(0xFFA7F7E5),
            darkAccent = Color(0xFF4FE3C1),
        ),
        CourseColorStyle(
            name = "丁香紫",
            lightBg = Color(0xFFEEE8FF),
            lightText = Color(0xFF472793),
            lightAccent = Color(0xFF7C3AED),
            darkBg = Color(0xFF281C49),
            darkText = Color(0xFFDDD6FE),
            darkAccent = Color(0xFFA78BFA),
        ),
        CourseColorStyle(
            name = "暖杏橙",
            lightBg = Color(0xFFFFF0DB),
            lightText = Color(0xFF844100),
            lightAccent = Color(0xFFE67E22),
            darkBg = Color(0xFF3D2710),
            darkText = Color(0xFFFDE0B8),
            darkAccent = Color(0xFFFFB547),
        ),
        CourseColorStyle(
            name = "蔷薇粉",
            lightBg = Color(0xFFFFE6EE),
            lightText = Color(0xFF8C1D4B),
            lightAccent = Color(0xFFE11D6D),
            darkBg = Color(0xFF42192A),
            darkText = Color(0xFFFECDD3),
            darkAccent = Color(0xFFFB7185),
        ),
        CourseColorStyle(
            name = "青柠绿",
            lightBg = Color(0xFFE2F8E8),
            lightText = Color(0xFF145A32),
            lightAccent = Color(0xFF10B981),
            darkBg = Color(0xFF113626),
            darkText = Color(0xFFBBF7D0),
            darkAccent = Color(0xFF34D399),
        ),
        CourseColorStyle(
            name = "靛青蓝",
            lightBg = Color(0xFFE5ECFF),
            lightText = Color(0xFF1E3A8A),
            lightAccent = Color(0xFF4F46E5),
            darkBg = Color(0xFF1B2452),
            darkText = Color(0xFFC7D2FE),
            darkAccent = Color(0xFF818CF8),
        ),
        CourseColorStyle(
            name = "珊瑚橘",
            lightBg = Color(0xFFFFEAE4),
            lightText = Color(0xFF8A2C12),
            lightAccent = Color(0xFFEA580C),
            darkBg = Color(0xFF402018),
            darkText = Color(0xFFFED7AA),
            darkAccent = Color(0xFFFB923C),
        ),
        // Row 2 (8 additional high-distinction hues)
        CourseColorStyle(
            name = "绯红莓",
            lightBg = Color(0xFFFFE4E6),
            lightText = Color(0xFF881337),
            lightAccent = Color(0xFFDC2626),
            darkBg = Color(0xFF45161B),
            darkText = Color(0xFFFECACA),
            darkAccent = Color(0xFFF87171),
        ),
        CourseColorStyle(
            name = "琥珀金",
            lightBg = Color(0xFFFEF3C7),
            lightText = Color(0xFF78350F),
            lightAccent = Color(0xFFD97706),
            darkBg = Color(0xFF3E2D0F),
            darkText = Color(0xFFFDE68A),
            darkAccent = Color(0xFFFBBF24),
        ),
        CourseColorStyle(
            name = "森系绿",
            lightBg = Color(0xFFECFCCB),
            lightText = Color(0xFF365314),
            lightAccent = Color(0xFF65A30D),
            darkBg = Color(0xFF233513),
            darkText = Color(0xFFD9F99D),
            darkAccent = Color(0xFFA3E635),
        ),
        CourseColorStyle(
            name = "湖水青",
            lightBg = Color(0xFFCFFAFE),
            lightText = Color(0xFF155E75),
            lightAccent = Color(0xFF0891B2),
            darkBg = Color(0xFF0E3440),
            darkText = Color(0xFFA5F3FC),
            darkAccent = Color(0xFF22D3EE),
        ),
        CourseColorStyle(
            name = "魅影紫",
            lightBg = Color(0xFFFAE8FF),
            lightText = Color(0xFF701A75),
            lightAccent = Color(0xFFC026D3),
            darkBg = Color(0xFF3D1745),
            darkText = Color(0xFFF5D0FE),
            darkAccent = Color(0xFFE879F9),
        ),
        CourseColorStyle(
            name = "焦糖棕",
            lightBg = Color(0xFFF3EAE3),
            lightText = Color(0xFF5D3A24),
            lightAccent = Color(0xFFA16207),
            darkBg = Color(0xFF342419),
            darkText = Color(0xFFE7D3C2),
            darkAccent = Color(0xFFD4A373),
        ),
        CourseColorStyle(
            name = "云雾灰蓝",
            lightBg = Color(0xFFE2E8F0),
            lightText = Color(0xFF1E293B),
            lightAccent = Color(0xFF475569),
            darkBg = Color(0xFF232C3A),
            darkText = Color(0xFFCBD5E1),
            darkAccent = Color(0xFF94A3B8),
        ),
        CourseColorStyle(
            name = "松石绿",
            lightBg = Color(0xFFCCFBF1),
            lightText = Color(0xFF115E59),
            lightAccent = Color(0xFF0D9488),
            darkBg = Color(0xFF113533),
            darkText = Color(0xFF99F6E4),
            darkAccent = Color(0xFF2DD4BF),
        ),
    )

    fun styleFor(index: Int): CourseColorStyle {
        return styles[Math.floorMod(index, styles.size)]
    }

    fun resolveStyle(colorIndex: Int, customColorHex: String): CourseColorStyle {
        return styleFromCustomHex(customColorHex) ?: styleFor(colorIndex)
    }

    fun parseHexColorOrNull(rawHex: String): Color? {
        val cleaned = rawHex.trim().removePrefix("#")
        if (cleaned.length != 6 && cleaned.length != 8) return null
        val parsed = cleaned.toLongOrNull(16) ?: return null
        val argb = if (cleaned.length == 6) {
            (0xFF000000L or parsed).toInt()
        } else {
            parsed.toInt()
        }
        return Color(argb)
    }

    fun formatHex(color: Color): String {
        val rgb = color.toArgb() and 0x00FFFFFF
        return String.format("#%06X", rgb)
    }

    fun styleFromCustomHex(rawHex: String): CourseColorStyle? {
        val base = parseHexColorOrNull(rawHex) ?: return null
        val lightBg = lerp(base, Color.White, 0.84f)
        val lightText = lerp(base, Color(0xFF0E1420), 0.48f)
        val lightAccent = base
        val darkBg = lerp(base, Color(0xFF101520), 0.74f)
        val darkText = lerp(base, Color.White, 0.72f)
        val darkAccent = lerp(base, Color.White, 0.25f)
        return CourseColorStyle(
            name = "自定义 ${formatHex(base)}",
            lightBg = lightBg,
            lightText = lightText,
            lightAccent = lightAccent,
            darkBg = darkBg,
            darkText = darkText,
            darkAccent = darkAccent,
        )
    }

    fun defaultSectionTimes(timetableId: Long): List<SectionTimeEntity> {
        val slots = listOf(
            Triple(1, 8 to 0, 8 to 45),
            Triple(2, 8 to 55, 9 to 40),
            Triple(3, 10 to 0, 10 to 45),
            Triple(4, 10 to 55, 11 to 40),
            Triple(5, 14 to 0, 14 to 45),
            Triple(6, 14 to 55, 15 to 40),
            Triple(7, 16 to 0, 16 to 45),
            Triple(8, 16 to 55, 17 to 40),
            Triple(9, 19 to 0, 19 to 45),
            Triple(10, 19 to 55, 20 to 40),
            Triple(11, 20 to 50, 21 to 35),
            Triple(12, 21 to 40, 22 to 25),
        )
        return slots.map { (section, start, end) ->
            SectionTimeEntity(
                timetableId = timetableId,
                sectionNumber = section,
                startHour = start.first,
                startMinute = start.second,
                endHour = end.first,
                endMinute = end.second,
            )
        }
    }
}

