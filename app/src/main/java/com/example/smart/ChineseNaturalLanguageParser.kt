package com.example.smart

import com.example.data.CourseColorPalette
import com.example.data.DateTimeUtils
import com.example.data.RepeatRule
import com.example.data.ScheduleCategory
import com.example.data.SectionTimeEntity
import com.example.data.WeekType
import java.security.MessageDigest
import java.util.Calendar
import kotlin.math.abs

data class ParserCacheStats(
    val hits: Long,
    val misses: Long,
    val size: Int,
)

/**
 * Pure offline Chinese natural language parser for creating schedules and timetable courses.
 * API 24-safe (uses java.util.Calendar rather than java.time).
 * Includes a thread-safe bounded SHA-256 LRU cache for repeated schedule descriptions.
 */
object ChineseNaturalLanguageParser {

    private const val ONE_DAY_MS = 86_400_000L
    private const val MAX_CACHE_ENTRIES = 128

    private val CLAUSE_SPLIT_REGEX =
        Regex("[;；\\n]+|(?<=\\S)\\s*(?:另外|还有|以及然后|然后)\\s*")
    private val DND_REGEX = Regex("(?:并?自动)?(?:开启|打开)?(?:免打扰|勿扰模式?|静音模式?)")
    private val NO_REMINDER_REGEX = Regex("(?:不用|无需|不)提醒")
    private val REMINDER_HALF_HOUR_REGEX = Regex("(?:提前|前)\\s*半\\s*(?:个)?小时(?:提醒)?")
    private val REMINDER_HOUR_REGEX = Regex("(?:提前|前)\\s*([0-9一二两三四五六七八九十]+)\\s*(?:个)?小时(?:提醒)?")
    private val REMINDER_MIN_REGEX = Regex("(?:提前|前)\\s*([0-9一二两三四五六七八九十]+)\\s*分钟?(?:提醒)?")
    private val AT_LOC_REGEX = Regex("@([^\\s，。,.；;!！?？]+)")
    private val IN_LOC_REGEX = Regex(
        "(?:在|地点[:：]?|教室[:：]?)([^\\s，。,.；;!！?？上开做参加讨论复习写举行听讲]{1,18})(?=[上开做参加讨论复习写举行听讲\\s，。,.；;]|$)",
    )
    private val PURE_TIME_FRAG_REGEX = Regex("^[0-9一二三四五六七八九十点分号周今天明后早中晚上下午:-]+$")

    private val cacheLock = Any()
    private val parseCache = object : LinkedHashMap<String, List<SmartParsedDraft>>(
        MAX_CACHE_ENTRIES + 1,
        0.75f,
        true,
    ) {
        override fun removeEldestEntry(
            eldest: MutableMap.MutableEntry<String, List<SmartParsedDraft>>?,
        ): Boolean = size > MAX_CACHE_ENTRIES
    }
    private var cacheHits = 0L
    private var cacheMisses = 0L

    internal fun cacheStats(): ParserCacheStats = synchronized(cacheLock) {
        ParserCacheStats(hits = cacheHits, misses = cacheMisses, size = parseCache.size)
    }

    internal fun clearCache() = synchronized(cacheLock) {
        parseCache.clear()
        cacheHits = 0L
        cacheMisses = 0L
    }

    fun parse(
        rawInput: String,
        nowMillis: Long = System.currentTimeMillis(),
        sectionTimes: List<SectionTimeEntity> = CourseColorPalette.defaultSectionTimes(0L),
        defaultTotalWeeks: Int = 16,
    ): List<SmartParsedDraft> {
        val cleaned = rawInput.trim()
        if (cleaned.isEmpty()) return emptyList()

        val effectiveSections = if (sectionTimes.isNotEmpty()) {
            sectionTimes.sortedBy { it.sectionNumber }
        } else {
            CourseColorPalette.defaultSectionTimes(0L)
        }
        val safeWeeks = defaultTotalWeeks.coerceIn(1, 30)
        val cacheKey = buildCacheKey(cleaned, nowMillis, effectiveSections, safeWeeks)

        synchronized(cacheLock) {
            val cached = parseCache[cacheKey]
            if (cached != null) {
                cacheHits++
                return cloneDrafts(cached)
            }
        }

        // Split multi-item instructions on semicolons, newlines, or explicit clause connectors
        val clauses = cleaned
            .split(CLAUSE_SPLIT_REGEX)
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        val parsed = clauses.mapNotNull { clause ->
            parseSingleClause(
                clause = clause,
                nowMillis = nowMillis,
                sectionTimes = effectiveSections,
                defaultTotalWeeks = safeWeeks,
            )
        }

        val snapshot = cloneDrafts(parsed)
        synchronized(cacheLock) {
            cacheMisses++
            parseCache[cacheKey] = snapshot
        }
        return cloneDrafts(snapshot)
    }

    private fun cloneDrafts(drafts: List<SmartParsedDraft>): List<SmartParsedDraft> {
        return drafts.map { draft ->
            draft.copy(
                daysOfWeek = draft.daysOfWeek.toList(),
                conflicts = draft.conflicts.toList(),
            )
        }
    }

    private fun buildCacheKey(
        cleanedInput: String,
        nowMillis: Long,
        sectionTimes: List<SectionTimeEntity>,
        totalWeeks: Int,
    ): String {
        val dateKey = DateTimeUtils.formatDateKey(nowMillis)
        val (hourOfDay, _) = DateTimeUtils.extractHourMinute(nowMillis)
        val sectionsSig = buildString(sectionTimes.size * 12) {
            sectionTimes.forEach { slot ->
                append(slot.sectionNumber)
                append(':')
                append(slot.startHour)
                append(':')
                append(slot.startMinute)
                append('-')
                append(slot.endHour)
                append(':')
                append(slot.endMinute)
                append(';')
            }
        }
        val canonical = "$cleanedInput|$dateKey|$hourOfDay|$totalWeeks|$sectionsSig"
        val digest = MessageDigest.getInstance("SHA-256").digest(canonical.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { byte -> "%02x".format(byte) }
    }

    private fun parseSingleClause(
        clause: String,
        nowMillis: Long,
        sectionTimes: List<SectionTimeEntity>,
        defaultTotalWeeks: Int,
    ): SmartParsedDraft? {
        var working = clause

        // 1. Do Not Disturb keywords
        val enableDnd = DND_REGEX.containsMatchIn(working)
        working = working.replace(DND_REGEX, " ")

        // 2. Advance reminder lead minutes
        var reminderMinutes = 15
        if (NO_REMINDER_REGEX.containsMatchIn(working)) {
            reminderMinutes = 0
            working = working.replace(NO_REMINDER_REGEX, " ")
        } else {
            when {
                REMINDER_HALF_HOUR_REGEX.containsMatchIn(working) -> {
                    reminderMinutes = 30
                    working = working.replace(REMINDER_HALF_HOUR_REGEX, " ")
                }
                REMINDER_HOUR_REGEX.containsMatchIn(working) -> {
                    val m = REMINDER_HOUR_REGEX.find(working)!!
                    val hrs = parseChineseOrArabicNumber(m.groupValues[1]) ?: 1
                    reminderMinutes = (hrs * 60).coerceIn(5, 120)
                    working = working.replace(m.value, " ")
                }
                REMINDER_MIN_REGEX.containsMatchIn(working) -> {
                    val m = REMINDER_MIN_REGEX.find(working)!!
                    reminderMinutes = (parseChineseOrArabicNumber(m.groupValues[1]) ?: 15).coerceIn(0, 120)
                    working = working.replace(m.value, " ")
                }
            }
        }

        // 3. Location extraction ("@xxx" or "在xxx")
        var location = ""
        val atMatch = AT_LOC_REGEX.find(working)
        if (atMatch != null) {
            location = atMatch.groupValues[1].trim()
            working = working.replace(atMatch.value, " ")
        } else {
            val inMatch = IN_LOC_REGEX.find(working)
            if (inMatch != null) {
                val candidate = inMatch.groupValues[1].trim()
                // Ignore pure time fragments mistakenly matched after "在"
                if (!PURE_TIME_FRAG_REGEX.matches(candidate)) {
                    location = candidate
                    working = working.replace(inMatch.value, " ")
                }
            }
        }

        // 4. Relative date / weekday / recurring pattern
        var targetDateMillis = DateTimeUtils.startOfDayMillis(nowMillis)
        var daysOfWeek = mutableListOf<Int>()
        var repeatRule = RepeatRule.NONE
        var explicitDateOrWeekFound = false

        // Multi-day recurring weekday pattern: 每周一三五 / 每周二、四 / 每逢周一和周三
        val multiWeekdayRegex = Regex(
            "(?:每|逢|每逢)(?:周|星期|礼拜)([一二三四五六日天末1-7](?:[、,，和及与\\s]*(?:周|星期|礼拜)?[一二三四五六日天末1-7])*)",
        )
        val multiMatch = multiWeekdayRegex.find(working)
        if (multiMatch != null) {
            daysOfWeek.addAll(extractWeekdaysFromToken(multiMatch.groupValues[1]))
            repeatRule = RepeatRule.WEEKLY
            explicitDateOrWeekFound = true
            working = working.replace(multiMatch.value, " ")
        } else {
            val dailyRegex = Regex("每天|每日")
            val workdaysRegex = Regex("每个?工作日|周一[到至-]周五")
            val nextWeekDayRegex = Regex("下(?:周|星期|礼拜)([一二三四五六日天1-7])")
            val thisWeekDayRegex = Regex("(?:这|本)?(?:周|星期|礼拜)([一二三四五六日天1-7])")
            val explicitMonthDayRegex = Regex("([0-9]{1,2})\\s*[月/\\-.]\\s*([0-9]{1,2})\\s*[日号]?")

            when {
                dailyRegex.containsMatchIn(working) -> {
                    repeatRule = RepeatRule.DAILY
                    explicitDateOrWeekFound = true
                    working = working.replace(dailyRegex, " ")
                }
                workdaysRegex.containsMatchIn(working) -> {
                    repeatRule = RepeatRule.WORKDAYS
                    daysOfWeek.addAll(listOf(1, 2, 3, 4, 5))
                    explicitDateOrWeekFound = true
                    working = working.replace(workdaysRegex, " ")
                }
                nextWeekDayRegex.containsMatchIn(working) -> {
                    val m = nextWeekDayRegex.find(working)!!
                    val iso = parseSingleWeekday(m.groupValues[1]) ?: DateTimeUtils.isoDayOfWeek(nowMillis)
                    targetDateMillis = computeWeekdayDateMillis(nowMillis, iso, forceNextWeek = true)
                    daysOfWeek.add(iso)
                    explicitDateOrWeekFound = true
                    working = working.replace(m.value, " ")
                }
                working.contains("大后天") -> {
                    targetDateMillis = DateTimeUtils.startOfDayMillis(nowMillis + 3 * ONE_DAY_MS)
                    explicitDateOrWeekFound = true
                    working = working.replace("大后天", " ")
                }
                working.contains("后天") -> {
                    targetDateMillis = DateTimeUtils.startOfDayMillis(nowMillis + 2 * ONE_DAY_MS)
                    explicitDateOrWeekFound = true
                    working = working.replace("后天", " ")
                }
                Regex("明天|明日|明早|明晚").containsMatchIn(working) -> {
                    targetDateMillis = DateTimeUtils.startOfDayMillis(nowMillis + ONE_DAY_MS)
                    explicitDateOrWeekFound = true
                    // Preserve 早/晚 period cue by replacing only 明天/明日/明
                    working = working
                        .replace("明天", " ")
                        .replace("明日", " ")
                        .replace("明早", "早上 ")
                        .replace("明晚", "晚上 ")
                }
                Regex("今天|今日|今早|今晚").containsMatchIn(working) -> {
                    targetDateMillis = DateTimeUtils.startOfDayMillis(nowMillis)
                    explicitDateOrWeekFound = true
                    working = working
                        .replace("今天", " ")
                        .replace("今日", " ")
                        .replace("今早", "早上 ")
                        .replace("今晚", "晚上 ")
                }
                thisWeekDayRegex.containsMatchIn(working) -> {
                    val m = thisWeekDayRegex.find(working)!!
                    val iso = parseSingleWeekday(m.groupValues[1]) ?: DateTimeUtils.isoDayOfWeek(nowMillis)
                    targetDateMillis = computeWeekdayDateMillis(nowMillis, iso, forceNextWeek = false)
                    daysOfWeek.add(iso)
                    explicitDateOrWeekFound = true
                    working = working.replace(m.value, " ")
                }
                explicitMonthDayRegex.containsMatchIn(working) -> {
                    val m = explicitMonthDayRegex.find(working)!!
                    val month = m.groupValues[1].toIntOrNull()?.coerceIn(1, 12) ?: 1
                    val day = m.groupValues[2].toIntOrNull()?.coerceIn(1, 31) ?: 1
                    val cal = Calendar.getInstance().apply {
                        timeInMillis = nowMillis
                        set(Calendar.MONTH, month - 1)
                        set(Calendar.DAY_OF_MONTH, day)
                        set(Calendar.HOUR_OF_DAY, 0)
                        set(Calendar.MINUTE, 0)
                        set(Calendar.SECOND, 0)
                        set(Calendar.MILLISECOND, 0)
                    }
                    if (cal.timeInMillis < DateTimeUtils.startOfDayMillis(nowMillis)) {
                        cal.add(Calendar.YEAR, 1)
                    }
                    targetDateMillis = cal.timeInMillis
                    explicitDateOrWeekFound = true
                    working = working.replace(m.value, " ")
                }
            }
        }

        if (daysOfWeek.isNotEmpty() && repeatRule == RepeatRule.WEEKLY) {
            targetDateMillis = computeWeekdayDateMillis(nowMillis, daysOfWeek.first(), forceNextWeek = false)
        } else if (daysOfWeek.isEmpty()) {
            daysOfWeek.add(DateTimeUtils.isoDayOfWeek(targetDateMillis))
        }

        // 5. Week range & odd/even week hints for courses (e.g. 1-16周, 单周, 双周)
        var startWeek = 1
        var endWeek = defaultTotalWeeks
        var weekType = WeekType.ALL
        val weekRangeRegex = Regex("第?(\\d{1,2})\\s*[-~至到]\\s*(\\d{1,2})\\s*周")
        val weekRangeMatch = weekRangeRegex.find(working)
        if (weekRangeMatch != null) {
            startWeek = (weekRangeMatch.groupValues[1].toIntOrNull() ?: 1).coerceIn(1, 30)
            endWeek = (weekRangeMatch.groupValues[2].toIntOrNull() ?: defaultTotalWeeks).coerceIn(startWeek, 30)
            working = working.replace(weekRangeMatch.value, " ")
        }
        when {
            working.contains("单周") -> {
                weekType = WeekType.ODD
                working = working.replace("单周", " ")
            }
            working.contains("双周") -> {
                weekType = WeekType.EVEN
                working = working.replace("双周", " ")
            }
        }

        // 6. Time extraction (sections, campus slang, explicit range, single time + period)
        var startHour = -1
        var startMin = 0
        var endHour = -1
        var endMin = 0
        var explicitSectionRange: Pair<Int, Int>? = null
        var isCourseHintByTime = false

        // 6a. Section range: 第1-2节 / 第3、4节 / 1-2节 / 第13节
        val maxSec = (sectionTimes.maxOfOrNull { it.sectionNumber } ?: 12).coerceIn(6, 16)
        val sectionRegex = Regex("第?\\s*([1-9]|1[0-6])\\s*[-~至到、,]\\s*([1-9]|1[0-6])\\s*节")
        val singleSectionRegex = Regex("第\\s*([1-9]|1[0-6])\\s*节")
        val secRangeMatch = sectionRegex.find(working)
        val singleSecMatch = if (secRangeMatch == null) singleSectionRegex.find(working) else null

        if (secRangeMatch != null) {
            val s1 = secRangeMatch.groupValues[1].toInt().coerceIn(1, maxSec)
            val s2 = secRangeMatch.groupValues[2].toInt().coerceIn(s1, maxSec)
            explicitSectionRange = s1 to s2
            val slot1 = sectionTimes.find { it.sectionNumber == s1 }
            val slot2 = sectionTimes.find { it.sectionNumber == s2 } ?: slot1
            if (slot1 != null && slot2 != null) {
                startHour = slot1.startHour
                startMin = slot1.startMinute
                endHour = slot2.endHour
                endMin = slot2.endMinute
            }
            isCourseHintByTime = true
            working = working.replace(secRangeMatch.value, " ")
        } else if (singleSecMatch != null) {
            val s1 = singleSecMatch.groupValues[1].toInt().coerceIn(1, maxSec)
            explicitSectionRange = s1 to s1
            val slot = sectionTimes.find { it.sectionNumber == s1 }
            if (slot != null) {
                startHour = slot.startHour
                startMin = slot.startMinute
                endHour = slot.endHour
                endMin = slot.endMinute
            }
            isCourseHintByTime = true
            working = working.replace(singleSecMatch.value, " ")
        }

        // 6b. Campus slang: 早八 / 早十 / 午休 / 晚自习
        if (startHour < 0) {
            when {
                Regex("早八|早8").containsMatchIn(working) -> {
                    val s1 = sectionTimes.find { it.sectionNumber == 1 }
                    val s2 = sectionTimes.find { it.sectionNumber == 2 }
                    startHour = s1?.startHour ?: 8
                    startMin = s1?.startMinute ?: 0
                    endHour = s2?.endHour ?: 9
                    endMin = s2?.endMinute ?: 40
                    explicitSectionRange = 1 to 2
                    isCourseHintByTime = true
                    working = working.replace(Regex("早八|早8"), " ")
                }
                Regex("早十|早10").containsMatchIn(working) -> {
                    val s3 = sectionTimes.find { it.sectionNumber == 3 }
                    val s4 = sectionTimes.find { it.sectionNumber == 4 }
                    startHour = s3?.startHour ?: 10
                    startMin = s3?.startMinute ?: 0
                    endHour = s4?.endHour ?: 11
                    endMin = s4?.endMinute ?: 40
                    explicitSectionRange = 3 to 4
                    isCourseHintByTime = true
                    working = working.replace(Regex("早十|早10"), " ")
                }
            }
        }

        // 6c. Explicit 24h range: 14:00-16:00 / 9:30到11:00
        if (startHour < 0) {
            val colonRangeRegex = Regex(
                "(上午|早上|清晨|中午|下午|傍晚|晚上|晚间)?\\s*(\\d{1,2})[:：](\\d{2})\\s*[-~至到]\\s*(上午|早上|中午|下午|傍晚|晚上|晚间)?\\s*(\\d{1,2})[:：](\\d{2})",
            )
            val m = colonRangeRegex.find(working)
            if (m != null) {
                val p1 = m.groupValues[1]
                val h1 = adjustHourForPeriod(m.groupValues[2].toInt(), p1)
                val min1 = m.groupValues[3].toInt().coerceIn(0, 59)
                val p2 = m.groupValues[4].ifBlank { p1 }
                var h2 = adjustHourForPeriod(m.groupValues[5].toInt(), p2)
                val min2 = m.groupValues[6].toInt().coerceIn(0, 59)
                if (h2 < h1 && h2 in 1..11) h2 += 12
                startHour = h1
                startMin = min1
                endHour = h2.coerceIn(0, 23)
                endMin = min2
                working = working.replace(m.value, " ")
            }
        }

        // 6d. Chinese spoken range: 下午2点半到4点 / 上午9点至11点半
        if (startHour < 0) {
            val spokenRangeRegex = Regex(
                "(上午|早上|清晨|中午|下午|傍晚|晚上|晚间)?\\s*([0-9一二两三四五六七八九十]+)\\s*[点时](半|一刻|三刻|[0-9一二三四五六七八九十]+分?)?\\s*[-~至到]\\s*(上午|早上|中午|下午|傍晚|晚上|晚间)?\\s*([0-9一二两三四五六七八九十]+)\\s*[点时](半|一刻|三刻|[0-9一二三四五六七八九十]+分?)?",
            )
            val m = spokenRangeRegex.find(working)
            if (m != null) {
                val p1 = m.groupValues[1]
                val rawH1 = parseChineseOrArabicNumber(m.groupValues[2]) ?: 9
                val h1 = adjustHourForPeriod(rawH1, p1)
                val min1 = parseChineseMinuteToken(m.groupValues[3])
                val p2 = m.groupValues[4].ifBlank { p1 }
                val rawH2 = parseChineseOrArabicNumber(m.groupValues[5]) ?: (rawH1 + 1)
                var h2 = adjustHourForPeriod(rawH2, p2)
                val min2 = parseChineseMinuteToken(m.groupValues[6])
                if (h2 < h1 && h2 in 1..11) h2 += 12
                startHour = h1
                startMin = min1
                endHour = h2.coerceIn(0, 23)
                endMin = min2
                working = working.replace(m.value, " ")
            }
        }

        // 6e. Single exact colon time: 下午2:30 / 14:30
        if (startHour < 0) {
            val singleColonRegex = Regex("(上午|早上|清晨|中午|下午|傍晚|晚上|晚间)?\\s*(\\d{1,2})[:：](\\d{2})")
            val m = singleColonRegex.find(working)
            if (m != null) {
                startHour = adjustHourForPeriod(m.groupValues[2].toInt(), m.groupValues[1])
                startMin = m.groupValues[3].toInt().coerceIn(0, 59)
                working = working.replace(m.value, " ")
            }
        }

        // 6f. Single Chinese spoken time: 下午2点半 / 晚上8点一刻 / 9点
        if (startHour < 0) {
            val singleSpokenRegex = Regex(
                "(上午|早上|清晨|中午|下午|傍晚|晚上|晚间)?\\s*([0-9一二两三四五六七八九十]+)\\s*[点时](半|一刻|三刻|[0-9一二三四五六七八九十]+分?)?",
            )
            val m = singleSpokenRegex.find(working)
            if (m != null) {
                val rawH = parseChineseOrArabicNumber(m.groupValues[2]) ?: 9
                startHour = adjustHourForPeriod(rawH, m.groupValues[1])
                startMin = parseChineseMinuteToken(m.groupValues[3])
                working = working.replace(m.value, " ")
            }
        }

        // 6g. Period keyword fallback (上午/中午/下午/傍晚/晚上/午休/晚自习)
        if (startHour < 0) {
            val periodFallback = listOf(
                "午休" to (12 to 30),
                "晚自习" to (19 to 0),
                "清晨" to (7 to 30),
                "早上" to (8 to 30),
                "上午" to (9 to 30),
                "中午" to (12 to 0),
                "下午" to (14 to 30),
                "傍晚" to (17 to 30),
                "晚上" to (19 to 30),
                "晚间" to (19 to 30),
            )
            for ((kw, hm) in periodFallback) {
                if (working.contains(kw)) {
                    startHour = hm.first
                    startMin = hm.second
                    if (kw != "午休" && kw != "晚自习") {
                        working = working.replace(kw, " ")
                    }
                    break
                }
            }
        }

        // 6h. Default to next rounded hour if no time was mentioned
        if (startHour < 0) {
            val cal = Calendar.getInstance().apply {
                timeInMillis = nowMillis
                add(Calendar.HOUR_OF_DAY, 1)
                set(Calendar.MINUTE, 0)
            }
            startHour = cal.get(Calendar.HOUR_OF_DAY)
            startMin = 0
        }

        // Duration extraction if end time wasn't explicitly specified
        var durationMinutes = 60
        val halfHourDurRegex = Regex("(?:持续|共|时长)?([0-9一二两三四]+)个半小时")
        val hourDurRegex = Regex("(?:持续|共|时长)?([0-9一二两三四五六]+)(?:个)?小时")
        val minDurRegex = Regex("(?:持续|共|时长)([0-9]{1,3})分钟?")
        when {
            halfHourDurRegex.containsMatchIn(working) -> {
                val m = halfHourDurRegex.find(working)!!
                val h = parseChineseOrArabicNumber(m.groupValues[1]) ?: 1
                durationMinutes = h * 60 + 30
                working = working.replace(m.value, " ")
            }
            hourDurRegex.containsMatchIn(working) -> {
                val m = hourDurRegex.find(working)!!
                val h = parseChineseOrArabicNumber(m.groupValues[1]) ?: 1
                durationMinutes = h * 60
                working = working.replace(m.value, " ")
            }
            minDurRegex.containsMatchIn(working) -> {
                val m = minDurRegex.find(working)!!
                durationMinutes = (m.groupValues[1].toIntOrNull() ?: 60).coerceIn(10, 360)
                working = working.replace(m.value, " ")
            }
        }

        val startTimeMillis = DateTimeUtils.combineDateAndTime(targetDateMillis, startHour, startMin)
        val endTimeMillis = if (endHour >= 0) {
            var candidateEnd = DateTimeUtils.combineDateAndTime(targetDateMillis, endHour, endMin)
            if (candidateEnd <= startTimeMillis) {
                candidateEnd = startTimeMillis + durationMinutes * 60_000L
            }
            candidateEnd
        } else {
            val defaultDur = if (isCourseHintByTime) 100 else durationMinutes
            startTimeMillis + defaultDur * 60_000L
        }

        // 7. Determine Course vs Schedule intent
        val hasCourseKeyword = isCourseHintByTime ||
            daysOfWeek.size > 1 ||
            weekRangeMatch != null ||
            Regex("上课|课程|专业课|选修课|实验课|高数|高等数学|线代|线性代数|概率论|大物|大学物理|数据结构|算法|操作系统|计算机网络|英语课|体育课|思政|马原|毛概")
                .containsMatchIn(clause)

        val draftType = if (hasCourseKeyword) DraftType.COURSE else DraftType.SCHEDULE

        // Map start/end times to closest 1..12 section range
        val mappedSections = explicitSectionRange ?: mapTimeToSectionRange(
            startTimeMillis = startTimeMillis,
            endTimeMillis = endTimeMillis,
            sectionTimes = sectionTimes,
        )

        // 8. Extract clean title
        val title = cleanTitle(working, fallback = clause)
        val notesParts = buildList {
            if (location.isNotBlank()) add("@$location")
            if (enableDnd) add("[免打扰]")
        }

        val category = when {
            Regex("开会|会议|评审|面试|答辩|汇报").containsMatchIn(clause) -> ScheduleCategory.MEETING
            Regex("跑步|健身|锻炼|打球|游泳|体检").containsMatchIn(clause) -> ScheduleCategory.HEALTH
            Regex("自习|复习|作业|考试|刷题|背单词|论文").containsMatchIn(clause) -> ScheduleCategory.FOCUS
            Regex("吃饭|聚餐|看电影|取快递|买菜|生日").containsMatchIn(clause) -> ScheduleCategory.LIFE
            else -> ScheduleCategory.WORK
        }

        return SmartParsedDraft(
            draftType = draftType,
            title = title,
            location = location,
            startTimeMillis = startTimeMillis,
            endTimeMillis = endTimeMillis,
            daysOfWeek = daysOfWeek.distinct().sorted(),
            startSection = mappedSections.first,
            endSection = mappedSections.second,
            startWeek = startWeek,
            endWeek = endWeek,
            weekType = weekType,
            repeatRule = repeatRule,
            reminderMinutesBefore = reminderMinutes,
            enableDnd = enableDnd,
            category = category,
            notes = notesParts.joinToString(" · "),
        )
    }

    fun mapTimeToSectionRange(
        startTimeMillis: Long,
        endTimeMillis: Long,
        sectionTimes: List<SectionTimeEntity>,
    ): Pair<Int, Int> {
        if (sectionTimes.isEmpty()) return 1 to 2
        val (sh, sm) = DateTimeUtils.extractHourMinute(startTimeMillis)
        val (eh, em) = DateTimeUtils.extractHourMinute(endTimeMillis)
        val startMins = sh * 60 + sm
        val endMins = eh * 60 + em

        val maxSec = sectionTimes.maxOfOrNull { it.sectionNumber } ?: 12
        val startSec = sectionTimes.minByOrNull { abs(it.startHour * 60 + it.startMinute - startMins) }
            ?.sectionNumber ?: 1
        val endSec = sectionTimes.minByOrNull { abs(it.endHour * 60 + it.endMinute - endMins) }
            ?.sectionNumber ?: (startSec + 1).coerceAtMost(maxSec)

        val safeStart = startSec.coerceIn(1, maxSec)
        val safeEnd = endSec.coerceIn(safeStart, maxSec)
        return safeStart to safeEnd
    }

    private fun computeWeekdayDateMillis(
        nowMillis: Long,
        targetIsoDay: Int,
        forceNextWeek: Boolean,
    ): Long {
        val todayStart = DateTimeUtils.startOfDayMillis(nowMillis)
        val todayIso = DateTimeUtils.isoDayOfWeek(nowMillis)
        val diff = if (forceNextWeek) {
            (7 - todayIso) + targetIsoDay
        } else {
            val raw = targetIsoDay - todayIso
            if (raw >= 0) raw else raw + 7
        }
        return todayStart + diff * ONE_DAY_MS
    }

    private fun extractWeekdaysFromToken(token: String): List<Int> {
        val result = mutableListOf<Int>()
        for (ch in token) {
            val iso = parseSingleWeekday(ch.toString())
            if (iso != null && iso !in result) {
                result.add(iso)
            }
        }
        return result
    }

    private fun parseSingleWeekday(token: String): Int? = when (token.trim()) {
        "一", "1" -> 1
        "二", "2" -> 2
        "三", "3" -> 3
        "四", "4" -> 4
        "五", "5" -> 5
        "六", "6" -> 6
        "日", "天", "7" -> 7
        else -> null
    }

    private fun adjustHourForPeriod(rawHour: Int, period: String): Int {
        val h = rawHour.coerceIn(0, 23)
        return when (period) {
            "下午", "傍晚", "晚上", "晚间" -> if (h in 1..11) h + 12 else h
            "中午" -> if (h in 1..5) h + 12 else h
            "上午", "早上", "清晨" -> if (h == 12) 0 else h
            else -> h
        }
    }

    private fun parseChineseMinuteToken(token: String): Int {
        val cleaned = token.trim().removeSuffix("分")
        if (cleaned.isEmpty()) return 0
        return when (cleaned) {
            "半" -> 30
            "一刻" -> 15
            "三刻" -> 45
            else -> (parseChineseOrArabicNumber(cleaned) ?: 0).coerceIn(0, 59)
        }
    }

    private fun parseChineseOrArabicNumber(raw: String): Int? {
        val trimmed = raw.trim()
        trimmed.toIntOrNull()?.let { return it }
        val digitMap = mapOf(
            '零' to 0, '〇' to 0, '一' to 1, '二' to 2, '两' to 2, '三' to 3,
            '四' to 4, '五' to 5, '六' to 6, '七' to 7, '八' to 8, '九' to 9,
        )
        if (trimmed == "十") return 10
        if (trimmed.contains('十')) {
            val parts = trimmed.split('十')
            val tens = if (parts[0].isEmpty()) 1 else (digitMap[parts[0].first()] ?: 1)
            val ones = if (parts.getOrNull(1).isNullOrEmpty()) 0 else (digitMap[parts[1].first()] ?: 0)
            return tens * 10 + ones
        }
        if (trimmed.length == 1) return digitMap[trimmed[0]]
        return null
    }

    private fun cleanTitle(remaining: String, fallback: String): String {
        val stripped = remaining
            .replace(Regex("^(?:帮我|请帮我|请|记得|提醒我|安排|创建|新建|添加|去|要|上|有一个|有个)+"), "")
            .replace(Regex("(?:提醒我?|课表|日程|安排|一下)$"), "")
            .replace(Regex("[，。,.；;：:]+"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
            .removePrefix("上")
            .trim()

        return when {
            stripped.length >= 2 -> stripped.take(36)
            stripped.isNotEmpty() -> stripped
            else -> fallback.take(28)
        }
    }
}
