package com.example.data

import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject

data class ParsedTimetableImport(
    val timetableName: String? = null,
    val totalWeeks: Int? = null,
    val courses: List<CourseEntity>,
    val sectionTimes: List<SectionTimeEntity> = emptyList(),
)

data class FullBackupPayload(
    val timetables: List<TimetableEntity>,
    val courses: List<CourseEntity>,
    val sectionTimes: List<SectionTimeEntity>,
    val schedules: List<ScheduleEntity>,
)

object TimetableImportExport {

    private const val SHARE_PREFIX = "FLOWTIMETABLE://"

    const val SAMPLE_CSV_TEMPLATE = """课程名称,地点,教师,星期(1-7),开始节次,结束节次,起始周,结束周,单双周(全周/单周/双周)
高等数学A,笃行楼 302,陈明远,1,1,2,1,16,全周
数据结构与算法,实验楼 B405,林清妍,2,3,4,1,16,全周
大学物理实验,理学楼 201,周致远,3,5,6,2,16,双周
人工智能导论,明德楼 108,苏景行,4,7,8,1,15,单周
现代界面设计,创新楼 502,许知微,5,3,4,1,14,全周"""

    /**
     * Parses CSV or Tab-separated Excel text into courses.
     * Supports:
     * - Dynamic column mapping from headers (default template, WakeUp CSV, arbitrary header order)
     * - Grid/matrix Excel pastes (header row containing Monday..Sunday columns)
     * - Clock-time ranges ("08:00-09:40" or separate "08:00","09:40") mapped accurately via [sectionTimes]
     * - Combined section ranges ("1-2", "第1-2节", "1,2") and combined week ranges ("1-16周(单)", "2-14双")
     * - UTF-8 BOM stripping and Chinese/English/numeric weekday tokens
     */
    fun parseCsvOrExcelText(
        rawText: String,
        timetableId: Long,
        sectionTimes: List<SectionTimeEntity> = CourseColorPalette.defaultSectionTimes(timetableId),
    ): List<CourseEntity> {
        val effectiveSections = if (sectionTimes.isNotEmpty()) {
            sectionTimes.sortedBy { it.sectionNumber }
        } else {
            CourseColorPalette.defaultSectionTimes(timetableId)
        }
        val lines = rawText
            .removePrefix("\uFEFF")
            .replace("\r\n", "\n")
            .replace("\r", "\n")
            .split("\n")
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        if (lines.isEmpty()) return emptyList()

        val firstDelimiter = if (lines.first().contains('\t')) '\t' else ','
        val firstRowCols = splitDelimitedLine(lines.first(), firstDelimiter).map { cleanCsvCell(it) }

        // Check if pasted text is a 2D weekday matrix (e.g. copied directly from Excel/web timetable grid)
        val matrixColToDay = detectWeekdayColumnMap(firstRowCols)
        if (matrixColToDay.size >= 2) {
            return parseDelimitedMatrix(
                lines = lines.drop(1),
                delimiter = firstDelimiter,
                colToDay = matrixColToDay,
                timetableId = timetableId,
                sectionTimes = effectiveSections,
            )
        }

        val headerMapping = detectCsvHeaderMapping(firstRowCols)
        val dataLines = if (headerMapping != null) lines.drop(1) else lines

        val parsed = mutableListOf<CourseEntity>()
        var colorCursor = 0

        for (line in dataLines) {
            val delimiter = if (line.contains('\t')) '\t' else ','
            val cols = splitDelimitedLine(line, delimiter).map { cleanCsvCell(it) }
            if (cols.size < 3) continue

            // Skip repeated header rows inside pasted content
            if (detectCsvHeaderMapping(cols) != null) continue

            val course = if (headerMapping != null) {
                parseMappedCsvRow(
                    cols = cols,
                    mapping = headerMapping,
                    timetableId = timetableId,
                    sectionTimes = effectiveSections,
                    colorIndex = colorCursor,
                )
            } else {
                parsePositionalCsvRow(
                    cols = cols,
                    timetableId = timetableId,
                    sectionTimes = effectiveSections,
                    colorIndex = colorCursor,
                )
            }

            if (course != null) {
                parsed.add(course)
                colorCursor++
            }
        }
        return assignConsistentColorsByName(parsed)
    }

    /**
     * Parses common university educational system HTML timetable tables (<tr>/<td> cells).
     * Properly tracks 2D rowspan/colspan occupancy so multi-section courses never shift neighboring days,
     * auto-detects weekday columns from the header row (skipping leading "上午/节次/时间" columns),
     * maps explicit clock times ("08:00-09:40") or section ranges to exact sections via [sectionTimes],
     * and splits multiple courses stacked inside a single <td> cell.
     */
    fun parseHtmlTimetable(
        html: String,
        timetableId: Long,
        sectionTimes: List<SectionTimeEntity> = CourseColorPalette.defaultSectionTimes(timetableId),
    ): List<CourseEntity> {
        val effectiveSections = if (sectionTimes.isNotEmpty()) {
            sectionTimes.sortedBy { it.sectionNumber }
        } else {
            CourseColorPalette.defaultSectionTimes(timetableId)
        }
        val maxSec = BellScheduleGenerator.MAX_SECTION_COUNT
        val cleanedHtml = html.removePrefix("\uFEFF")
        val rowRegex = Regex("<tr[^>]*>(.*?)</tr>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
        val cellRegex = Regex(
            "<t([dh])([^>]*)>(.*?)</t[dh]>",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
        )

        val rawRows = rowRegex.findAll(cleanedHtml).map { rowMatch ->
            cellRegex.findAll(rowMatch.groupValues[1]).map { m ->
                val attrs = m.groupValues[2]
                val innerHtml = m.groupValues[3]
                val rowspan = extractHtmlIntAttr(attrs, "rowspan").coerceIn(1, maxSec)
                val colspan = extractHtmlIntAttr(attrs, "colspan").coerceIn(1, maxSec)
                HtmlCell(
                    rawHtml = innerHtml,
                    plainText = htmlToPlainText(innerHtml),
                    rowspan = rowspan,
                    colspan = colspan,
                )
            }.toList()
        }.filter { it.isNotEmpty() }.toList()

        if (rawRows.isEmpty()) return emptyList()

        // Place every cell onto a 2D occupancy grid respecting rowspan and colspan
        val occupied = mutableSetOf<Pair<Int, Int>>()
        val placedRows = mutableListOf<List<PlacedHtmlCell>>()

        for ((rIdx, cells) in rawRows.withIndex()) {
            var colCursor = 0
            val placed = mutableListOf<PlacedHtmlCell>()
            for (cell in cells) {
                while ((rIdx to colCursor) in occupied) {
                    colCursor++
                }
                placed.add(PlacedHtmlCell(col = colCursor, cell = cell))
                for (dr in 0 until cell.rowspan) {
                    for (dc in 0 until cell.colspan) {
                        occupied.add((rIdx + dr) to (colCursor + dc))
                    }
                }
                colCursor += cell.colspan
            }
            placedRows.add(placed)
        }

        // Detect weekday header row & build exact column -> ISO day (1..7) map
        var headerRowIdx = -1
        var colToDay = emptyMap<Int, Int>()
        for ((rIdx, rowCells) in placedRows.withIndex()) {
            val map = mutableMapOf<Int, Int>()
            for (pc in rowCells) {
                val day = parseWeekdayHeaderToken(pc.cell.plainText)
                if (day != null && day !in map.values) {
                    map[pc.col] = day
                }
            }
            if (map.size >= 2) {
                headerRowIdx = rIdx
                colToDay = map
                break
            }
        }
        if (colToDay.isEmpty()) {
            // Fallback: assume column 1..7 are Monday..Sunday
            colToDay = (1..7).associateWith { it }
        }

        val dataRows = if (headerRowIdx >= 0) placedRows.drop(headerRowIdx + 1) else placedRows
        val minDayCol = colToDay.keys.minOrNull() ?: 1

        // Detect whether rows represent 1 section each (common with rowspan="2") or 2 sections each
        val hasMultiRowSpanCourse = dataRows.any { row ->
            row.any { it.col in colToDay && it.cell.rowspan >= 2 && it.cell.plainText.isNotBlank() }
        }
        val leadingSingleSectionNums = dataRows.mapNotNull { row ->
            val labelText = row.filter { it.col < minDayCol }.joinToString(" ") { it.cell.plainText }
            extractRowSectionRange(labelText, effectiveSections)?.takeIf { it.first == it.second }?.first
        }
        val isSingleSectionPerRow = hasMultiRowSpanCourse ||
            (leadingSingleSectionNums.size >= 2 && leadingSingleSectionNums[1] - leadingSingleSectionNums[0] == 1)

        val result = mutableListOf<CourseEntity>()
        var colorCursor = 0
        var dataRowIndex = 0

        for (row in dataRows) {
            // Skip repeated weekday header rows inside multi-table HTML
            val headerHits = row.count { parseWeekdayHeaderToken(it.cell.plainText) != null }
            if (headerHits >= 2) continue

            val labelText = row
                .filter { it.col < minDayCol }
                .joinToString(" ") { it.cell.plainText }
                .trim()
            val rowRange = extractRowSectionRange(labelText, effectiveSections)
            val inferredStart = rowRange?.first
                ?: (if (isSingleSectionPerRow) dataRowIndex + 1 else dataRowIndex * 2 + 1).coerceIn(1, maxSec)
            val inferredEnd = rowRange?.second
                ?: (if (isSingleSectionPerRow) inferredStart else (inferredStart + 1).coerceAtMost(maxSec))
            dataRowIndex++

            for (pc in row) {
                val dayOfWeek = colToDay[pc.col] ?: continue
                val blocks = splitHtmlCellIntoCourseBlocks(pc.cell.rawHtml)
                for (blockText in blocks) {
                    val trimmed = blockText.trim()
                    if (trimmed.isEmpty() || trimmed == "-" || trimmed == "无" || trimmed == "/") continue

                    val course = parseCourseFromGridBlock(
                        blockText = trimmed,
                        timetableId = timetableId,
                        dayOfWeek = dayOfWeek,
                        inferredStartSection = inferredStart,
                        inferredEndSection = if (pc.cell.rowspan > 1) {
                            (inferredStart + pc.cell.rowspan - 1).coerceIn(inferredStart, maxSec)
                        } else {
                            inferredEnd
                        },
                        sectionTimes = effectiveSections,
                        colorIndex = colorCursor,
                    )
                    if (course != null) {
                        result.add(course)
                        colorCursor++
                    }
                }
            }
        }
        return assignConsistentColorsByName(result)
    }

    /**
     * Maps a clock-time range (startHour:startMinute .. endHour:endMinute) to the matching 1..12 section range
     * using the active timetable's [sectionTimes].
     */
    internal fun mapClockTimeRangeToSections(
        startHour: Int,
        startMinute: Int,
        endHour: Int?,
        endMinute: Int?,
        sectionTimes: List<SectionTimeEntity>,
    ): Pair<Int, Int> {
        val slots = sectionTimes.ifEmpty { CourseColorPalette.defaultSectionTimes(0L) }
            .sortedBy { it.sectionNumber }
        val startTotal = startHour.coerceIn(0, 23) * 60 + startMinute.coerceIn(0, 59)
        val endTotal = if (endHour != null && endMinute != null) {
            val candidate = endHour.coerceIn(0, 23) * 60 + endMinute.coerceIn(0, 59)
            if (candidate > startTotal) candidate else startTotal + 45
        } else {
            startTotal + 45
        }

        // Overlap matching: any section overlapping by >= 15 min (or >= half of range if short)
        val minOverlap = minOf(15, ((endTotal - startTotal) / 2).coerceAtLeast(1))
        val overlapping = slots.filter { slot ->
            val slotStart = slot.startHour * 60 + slot.startMinute
            val slotEnd = slot.endHour * 60 + slot.endMinute
            val overlap = minOf(endTotal, slotEnd) - maxOf(startTotal, slotStart)
            overlap >= minOverlap
        }
        val maxSec = slots.maxOfOrNull { it.sectionNumber } ?: BellScheduleGenerator.MAX_SECTION_COUNT
        if (overlapping.isNotEmpty()) {
            return overlapping.first().sectionNumber.coerceIn(1, maxSec) to
                overlapping.last().sectionNumber.coerceIn(1, maxSec)
        }

        // Nearest fallback when bell times don't directly overlap
        val startSec = slots.minByOrNull { slot ->
            kotlin.math.abs((slot.startHour * 60 + slot.startMinute) - startTotal)
        }?.sectionNumber ?: 1
        val endSec = slots
            .filter { it.sectionNumber >= startSec }
            .minByOrNull { slot ->
                kotlin.math.abs((slot.endHour * 60 + slot.endMinute) - endTotal)
            }?.sectionNumber ?: startSec
        return startSec.coerceIn(1, maxSec) to endSec.coerceIn(startSec, maxSec)
    }

    private data class CsvHeaderMapping(
        val nameIdx: Int,
        val dayIdx: Int,
        val startSecIdx: Int,
        val endSecIdx: Int,
        val locationIdx: Int,
        val teacherIdx: Int,
        val startWeekIdx: Int,
        val endWeekIdx: Int,
        val weekTypeIdx: Int,
        val notesIdx: Int,
    )

    private data class HtmlCell(
        val rawHtml: String,
        val plainText: String,
        val rowspan: Int,
        val colspan: Int,
    )

    private data class PlacedHtmlCell(
        val col: Int,
        val cell: HtmlCell,
    )

    private fun cleanCsvCell(raw: String): String {
        return raw.removePrefix("\uFEFF").trim().trim('"').trim()
    }

    private fun detectWeekdayColumnMap(cols: List<String>): Map<Int, Int> {
        val map = mutableMapOf<Int, Int>()
        cols.forEachIndexed { idx, col ->
            val day = parseWeekdayHeaderToken(col)
            if (day != null && day !in map.values) {
                map[idx] = day
            }
        }
        return map
    }

    private fun detectCsvHeaderMapping(cols: List<String>): CsvHeaderMapping? {
        var nameIdx = -1
        var dayIdx = -1
        var startSecIdx = -1
        var endSecIdx = -1
        var locationIdx = -1
        var teacherIdx = -1
        var startWeekIdx = -1
        var endWeekIdx = -1
        var weekTypeIdx = -1
        var notesIdx = -1

        cols.forEachIndexed { i, raw ->
            val h = raw.lowercase()
            when {
                nameIdx == -1 && (h.contains("课程") || h.contains("名称") || h.contains("科目") || h == "course" || h == "name" || h == "title") -> nameIdx = i
                dayIdx == -1 && (h.contains("星期") || h.contains("周几") || h.contains("礼拜") || h == "day" || h.contains("weekday")) -> dayIdx = i
                startSecIdx == -1 && (h.contains("开始节") || h.contains("起始节") || h.contains("节次") || h.contains("时间") || h == "section" || h == "sections" || h == "time" || h == "startsection") -> startSecIdx = i
                endSecIdx == -1 && (h.contains("结束节") || h.contains("终止节") || h == "endsection") -> endSecIdx = i
                locationIdx == -1 && (h.contains("地点") || h.contains("教室") || h.contains("课室") || h.contains("位置") || h == "location" || h == "room" || h == "place") -> locationIdx = i
                teacherIdx == -1 && (h.contains("老师") || h.contains("教师") || h.contains("授课") || h == "teacher" || h == "instructor") -> teacherIdx = i
                weekTypeIdx == -1 && (h.contains("单双") || h.contains("weektype")) -> weekTypeIdx = i
                startWeekIdx == -1 && (h.contains("起始周") || h.contains("开始周") || h.contains("周数") || h.contains("周次") || h == "weeks" || h == "week" || h == "startweek") -> startWeekIdx = i
                endWeekIdx == -1 && (h.contains("结束周") || h.contains("终止周") || h == "endweek") -> endWeekIdx = i
                notesIdx == -1 && (h.contains("备注") || h.contains("说明") || h == "notes" || h == "note") -> notesIdx = i
            }
        }

        val recognizedCount = listOf(nameIdx, dayIdx, startSecIdx, locationIdx, teacherIdx, startWeekIdx).count { it >= 0 }
        return if (recognizedCount >= 2 && (nameIdx >= 0 || dayIdx >= 0)) {
            CsvHeaderMapping(
                nameIdx = if (nameIdx >= 0) nameIdx else 0,
                dayIdx = dayIdx,
                startSecIdx = startSecIdx,
                endSecIdx = endSecIdx,
                locationIdx = locationIdx,
                teacherIdx = teacherIdx,
                startWeekIdx = startWeekIdx,
                endWeekIdx = endWeekIdx,
                weekTypeIdx = weekTypeIdx,
                notesIdx = notesIdx,
            )
        } else {
            null
        }
    }

    private fun assignConsistentColorsByName(courses: List<CourseEntity>): List<CourseEntity> {
        val colorByName = LinkedHashMap<String, Int>()
        return courses.map { course ->
            val key = course.name.trim()
            val assigned = colorByName.getOrPut(key) { colorByName.size }
            course.copy(colorIndex = assigned)
        }
    }

    private fun parseMappedCsvRow(
        cols: List<String>,
        mapping: CsvHeaderMapping,
        timetableId: Long,
        sectionTimes: List<SectionTimeEntity>,
        colorIndex: Int,
    ): CourseEntity? {
        val name = cols.getOrNull(mapping.nameIdx).orEmpty().trim()
        if (name.isBlank()) return null

        val dayToken = if (mapping.dayIdx >= 0) cols.getOrNull(mapping.dayIdx).orEmpty() else ""
        val dayOfWeek = parseDayOfWeek(dayToken)
            ?: cols.firstNotNullOfOrNull { token -> parseExplicitChineseWeekday(token) }
            ?: return null

        val startSecToken = if (mapping.startSecIdx >= 0) cols.getOrNull(mapping.startSecIdx).orEmpty() else ""
        val endSecToken = if (mapping.endSecIdx >= 0) cols.getOrNull(mapping.endSecIdx).orEmpty() else ""
        val (startSection, endSection) = parseSectionRangeTokens(startSecToken, endSecToken, sectionTimes)

        val location = if (mapping.locationIdx >= 0) {
            cols.getOrNull(mapping.locationIdx).orEmpty().removePrefix("@")
        } else {
            ""
        }
        val teacher = if (mapping.teacherIdx >= 0) {
            cols.getOrNull(mapping.teacherIdx).orEmpty()
        } else {
            ""
        }

        val startWeekToken = if (mapping.startWeekIdx >= 0) cols.getOrNull(mapping.startWeekIdx).orEmpty() else ""
        val endWeekToken = if (mapping.endWeekIdx >= 0) cols.getOrNull(mapping.endWeekIdx).orEmpty() else ""
        val weekTypeToken = if (mapping.weekTypeIdx >= 0) cols.getOrNull(mapping.weekTypeIdx).orEmpty() else ""
        val (startWeek, endWeek, parsedWeekType) = parseWeekRangeTokens(startWeekToken, endWeekToken, weekTypeToken)

        val notes = if (mapping.notesIdx >= 0) cols.getOrNull(mapping.notesIdx).orEmpty() else ""

        return CourseEntity(
            timetableId = timetableId,
            name = name,
            location = location,
            teacher = teacher,
            dayOfWeek = dayOfWeek,
            startSection = startSection,
            endSection = endSection,
            startWeek = startWeek,
            endWeek = endWeek,
            weekType = parsedWeekType,
            colorIndex = colorIndex,
            notes = notes,
        )
    }

    private fun parsePositionalCsvRow(
        cols: List<String>,
        timetableId: Long,
        sectionTimes: List<SectionTimeEntity>,
        colorIndex: Int,
    ): CourseEntity? {
        val name = cols[0].trim()
        if (name.isBlank()) return null

        // Auto-detect WakeUp-style headerless row: 课程名称,星期,开始节,结束节,老师,地点,周数
        val col3Day = parseDayOfWeek(cols.getOrNull(3).orEmpty())
        val col1Day = parseDayOfWeek(cols.getOrNull(1).orEmpty())

        if (col3Day == null && col1Day != null) {
            val secToken = cols.getOrNull(2).orEmpty()
            val secIsCombined = isCombinedRangeToken(secToken)
            val endSecToken = if (secIsCombined) "" else cols.getOrNull(3).orEmpty()
            val (startSection, endSection) = parseSectionRangeTokens(secToken, endSecToken, sectionTimes)
            val nextIdx = if (secIsCombined) 3 else 4
            val teacher = cols.getOrNull(nextIdx).orEmpty()
            val location = cols.getOrNull(nextIdx + 1).orEmpty().removePrefix("@")
            val (startWeek, endWeek, weekType) = parseWeekRangeTokens(
                cols.getOrNull(nextIdx + 2).orEmpty(),
                cols.getOrNull(nextIdx + 3).orEmpty(),
                cols.getOrNull(nextIdx + 4).orEmpty(),
            )
            return CourseEntity(
                timetableId = timetableId,
                name = name,
                location = location,
                teacher = teacher,
                dayOfWeek = col1Day,
                startSection = startSection,
                endSection = endSection,
                startWeek = startWeek,
                endWeek = endWeek,
                weekType = weekType,
                colorIndex = colorIndex,
            )
        }

        val dayOfWeek = col3Day ?: return null
        val location = cols.getOrNull(1).orEmpty().removePrefix("@")
        val teacher = cols.getOrNull(2).orEmpty()

        val secToken = cols.getOrNull(4).orEmpty()
        val secIsCombined = isCombinedRangeToken(secToken)
        val endSecToken = if (secIsCombined) "" else cols.getOrNull(5).orEmpty()
        val (startSection, endSection) = parseSectionRangeTokens(secToken, endSecToken, sectionTimes)

        val weekBaseIdx = if (secIsCombined) 5 else 6
        val weekFirstToken = cols.getOrNull(weekBaseIdx).orEmpty()
        val weekIsCombined = isCombinedRangeToken(weekFirstToken) || weekFirstToken.contains("周")
        val (startWeek, endWeek, weekType) = if (weekIsCombined) {
            parseWeekRangeTokens(
                weekFirstToken,
                "",
                cols.getOrNull(weekBaseIdx + 1).orEmpty(),
            )
        } else {
            parseWeekRangeTokens(
                weekFirstToken,
                cols.getOrNull(weekBaseIdx + 1).orEmpty(),
                cols.getOrNull(weekBaseIdx + 2).orEmpty(),
            )
        }

        return CourseEntity(
            timetableId = timetableId,
            name = name,
            location = location,
            teacher = teacher,
            dayOfWeek = dayOfWeek,
            startSection = startSection,
            endSection = endSection,
            startWeek = startWeek,
            endWeek = endWeek,
            weekType = weekType,
            colorIndex = colorIndex,
        )
    }

    private fun parseDelimitedMatrix(
        lines: List<String>,
        delimiter: Char,
        colToDay: Map<Int, Int>,
        timetableId: Long,
        sectionTimes: List<SectionTimeEntity>,
    ): List<CourseEntity> {
        val result = mutableListOf<CourseEntity>()
        val minDayCol = colToDay.keys.minOrNull() ?: 1
        var colorCursor = 0

        lines.forEachIndexed { rowIdx, line ->
            val cols = splitDelimitedLine(line, delimiter).map { cleanCsvCell(it) }
            val labelText = cols.take(minDayCol).joinToString(" ").trim()
            val rowRange = extractRowSectionRange(labelText, sectionTimes)
            val maxSec = BellScheduleGenerator.MAX_SECTION_COUNT
            val inferredStart = rowRange?.first ?: (rowIdx * 2 + 1).coerceIn(1, maxSec)
            val inferredEnd = rowRange?.second ?: (inferredStart + 1).coerceAtMost(maxSec)

            for ((colIdx, dayOfWeek) in colToDay) {
                val cellText = cols.getOrNull(colIdx).orEmpty().trim()
                if (cellText.isEmpty() || cellText == "-" || cellText == "无") continue
                val course = parseCourseFromGridBlock(
                    blockText = cellText.replace(" / ", "\n"),
                    timetableId = timetableId,
                    dayOfWeek = dayOfWeek,
                    inferredStartSection = inferredStart,
                    inferredEndSection = inferredEnd,
                    sectionTimes = sectionTimes,
                    colorIndex = colorCursor,
                )
                if (course != null) {
                    result.add(course)
                    colorCursor++
                }
            }
        }
        return result
    }

    private fun parseCourseFromGridBlock(
        blockText: String,
        timetableId: Long,
        dayOfWeek: Int,
        inferredStartSection: Int,
        inferredEndSection: Int,
        sectionTimes: List<SectionTimeEntity>,
        colorIndex: Int,
    ): CourseEntity? {
        val lines = blockText
            .split("\n")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        val rawName = lines.firstOrNull() ?: return null
        val name = rawName
            .replace(Regex("[（(]\\d{1,2}\\s*[-~至,、]\\s*\\d{1,2}\\s*节[)）]"), "")
            .replace(Regex("\\d{1,2}[:：]\\d{2}\\s*[-~至]\\s*\\d{1,2}[:：]\\d{2}"), "")
            .trim()
            .ifEmpty { rawName }

        // Extract weeks e.g. "1-16周", "第2-14周(双)", "1-8,10-16周"
        val (startWeek, endWeek, weekType) = extractWeeksFromFreeText(blockText)

        // Extract section range: explicit clock-time > explicit section range > inferred row/rowspan range
        val explicitClock = extractClockTimeRange(blockText, sectionTimes)
        val explicitSection = extractExplicitSectionRange(blockText)
        val maxSec = BellScheduleGenerator.MAX_SECTION_COUNT
        val (startSec, endSec) = when {
            explicitClock != null -> explicitClock
            explicitSection != null -> explicitSection
            else -> inferredStartSection.coerceIn(1, maxSec) to inferredEndSection.coerceIn(inferredStartSection, maxSec)
        }

        val location = lines.firstOrNull { line ->
            line != rawName && (
                line.startsWith("@") ||
                    line.contains("楼") ||
                    line.contains("室") ||
                    line.contains("馆") ||
                    line.contains("教") ||
                    line.contains("操场") ||
                    Regex("^[A-Za-z0-9\\-]{2,10}$").matches(line)
                )
        }?.removePrefix("@")
            ?: Regex("@([^\\s,，;；]+)").find(blockText)?.groupValues?.get(1)
            ?: lines.drop(1).firstOrNull { line ->
                !line.contains("周") && !line.contains("节") && !line.contains(":") && !line.contains("：")
            }.orEmpty().removePrefix("@")

        val teacher = lines.firstOrNull { line ->
            line != rawName &&
                line.removePrefix("@") != location &&
                !line.contains("周") &&
                !line.contains("节") &&
                !line.contains(":") &&
                !line.contains("：") &&
                !line.startsWith("@") &&
                line.length in 2..8
        }.orEmpty()

        return CourseEntity(
            timetableId = timetableId,
            name = name,
            location = location.trim(),
            teacher = teacher.trim(),
            dayOfWeek = dayOfWeek.coerceIn(1, 7),
            startSection = startSec,
            endSection = endSec,
            startWeek = startWeek,
            endWeek = endWeek,
            weekType = weekType,
            colorIndex = colorIndex,
        )
    }

    private fun isCombinedRangeToken(token: String): Boolean {
        val t = token.trim()
        if (t.isEmpty()) return false
        return t.contains("-") || t.contains("~") || t.contains("至") || t.contains(",") || t.contains("、")
    }

    private fun parseSectionRangeTokens(
        startToken: String,
        endToken: String,
        sectionTimes: List<SectionTimeEntity>,
    ): Pair<Int, Int> {
        val combined = listOf(startToken, endToken).filter { it.isNotBlank() }.joinToString("-")

        // 1. Clock-time range in startToken (e.g. "08:00-09:40") or across startToken + endToken ("08:00", "09:40")
        extractClockTimeRange(startToken, sectionTimes)?.let { return it }
        if (startToken.contains(":") || startToken.contains("：") || endToken.contains(":") || endToken.contains("：")) {
            extractClockTimeRange(combined, sectionTimes)?.let { return it }
            extractSingleClockTime(startToken)?.let { (h, m) ->
                val endHm = extractSingleClockTime(endToken)
                return mapClockTimeRangeToSections(h, m, endHm?.first, endHm?.second, sectionTimes)
            }
        }

        val maxSec = BellScheduleGenerator.MAX_SECTION_COUNT
        // 2. Explicit section range inside startToken (e.g. "1-2", "第3-4节", "5,6节")
        val rangeNums = Regex("\\d+").findAll(startToken).mapNotNull { it.value.toIntOrNull() }.toList()
        if (rangeNums.size >= 2) {
            val s = rangeNums.first().coerceIn(1, maxSec)
            val e = rangeNums.last().coerceIn(s, maxSec)
            return s to e
        }

        // 3. Separate start & end section numbers
        val startSec = (rangeNums.firstOrNull() ?: 1).coerceIn(1, maxSec)
        val endRaw = Regex("\\d+").find(endToken)?.value?.toIntOrNull()
        val endSec = (endRaw ?: (startSec + 1).coerceAtMost(maxSec)).coerceIn(startSec, maxSec)
        return startSec to endSec
    }

    private fun parseWeekRangeTokens(
        startWeekToken: String,
        endWeekToken: String,
        weekTypeToken: String,
    ): Triple<Int, Int, WeekType> {
        val combinedTypeText = "$startWeekToken $endWeekToken $weekTypeToken"
        val weekType = parseWeekType(combinedTypeText)

        val startNums = Regex("\\d+").findAll(startWeekToken).mapNotNull { it.value.toIntOrNull() }.toList()
        val endNums = Regex("\\d+").findAll(endWeekToken).mapNotNull { it.value.toIntOrNull() }.toList()

        return when {
            startNums.size >= 2 -> {
                val sw = startNums.min().coerceIn(1, CourseEntity.MAX_TOTAL_WEEKS)
                val ew = startNums.max().coerceIn(sw, CourseEntity.MAX_TOTAL_WEEKS)
                Triple(sw, ew, weekType)
            }
            startNums.size == 1 && endNums.isNotEmpty() -> {
                val sw = startNums.first().coerceIn(1, CourseEntity.MAX_TOTAL_WEEKS)
                val ew = endNums.last().coerceIn(sw, CourseEntity.MAX_TOTAL_WEEKS)
                Triple(sw, ew, weekType)
            }
            startNums.size == 1 -> {
                val sw = startNums.first().coerceIn(1, CourseEntity.MAX_TOTAL_WEEKS)
                // Single token like "16" without end token -> if contains "周" treat as single week, else default 1..16
                val ew = if (startWeekToken.contains("周")) sw else 16.coerceAtLeast(sw)
                Triple(sw, ew, weekType)
            }
            else -> Triple(1, 16, weekType)
        }
    }

    private fun extractClockTimeRange(
        text: String,
        sectionTimes: List<SectionTimeEntity>,
    ): Pair<Int, Int>? {
        val regex = Regex("(\\d{1,2})[:：](\\d{2})\\s*[-~至到]+\\s*(\\d{1,2})[:：](\\d{2})")
        val m = regex.find(text) ?: return null
        val sh = m.groupValues[1].toIntOrNull() ?: return null
        val sm = m.groupValues[2].toIntOrNull() ?: return null
        val eh = m.groupValues[3].toIntOrNull() ?: return null
        val em = m.groupValues[4].toIntOrNull() ?: return null
        if (sh !in 0..23 || eh !in 0..23 || sm !in 0..59 || em !in 0..59) return null
        return mapClockTimeRangeToSections(sh, sm, eh, em, sectionTimes)
    }

    private fun extractSingleClockTime(text: String): Pair<Int, Int>? {
        val m = Regex("(\\d{1,2})[:：](\\d{2})").find(text) ?: return null
        val h = m.groupValues[1].toIntOrNull() ?: return null
        val min = m.groupValues[2].toIntOrNull() ?: return null
        if (h !in 0..23 || min !in 0..59) return null
        return h to min
    }

    private fun extractExplicitSectionRange(text: String): Pair<Int, Int>? {
        val maxSec = BellScheduleGenerator.MAX_SECTION_COUNT
        val rangeRegex = Regex("(?:第)?(\\d{1,2})\\s*[-~至,、]\\s*(\\d{1,2})\\s*节")
        val rm = rangeRegex.find(text)
        if (rm != null) {
            val s = rm.groupValues[1].toIntOrNull()
            val e = rm.groupValues[2].toIntOrNull()
            if (s != null && e != null && s in 1..maxSec && e in s..maxSec) {
                return s to e
            }
        }
        val singleRegex = Regex("第\\s*(\\d{1,2})\\s*节")
        val sm = singleRegex.find(text)
        if (sm != null) {
            val s = sm.groupValues[1].toIntOrNull()
            if (s != null && s in 1..maxSec) {
                return s to s
            }
        }
        return null
    }

    private fun extractRowSectionRange(
        labelText: String,
        sectionTimes: List<SectionTimeEntity>,
    ): Pair<Int, Int>? {
        if (labelText.isBlank()) return null
        val maxSec = BellScheduleGenerator.MAX_SECTION_COUNT
        extractClockTimeRange(labelText, sectionTimes)?.let { return it }
        extractExplicitSectionRange(labelText)?.let { return it }

        // Check numeric range like "1-2" or "3~4"
        val dashMatch = Regex("(\\d{1,2})\\s*[-~至,、]\\s*(\\d{1,2})").find(labelText)
        if (dashMatch != null) {
            val s = dashMatch.groupValues[1].toIntOrNull()
            val e = dashMatch.groupValues[2].toIntOrNull()
            if (s != null && e != null && s in 1..maxSec && e in s..maxSec) {
                return s to e
            }
        }
        // Check single section number like "1" or "第3节"
        val singleNum = Regex("(\\d{1,2})").find(labelText)?.groupValues?.get(1)?.toIntOrNull()
        if (singleNum != null && singleNum in 1..maxSec) {
            return singleNum to singleNum
        }
        return null
    }

    private fun extractWeeksFromFreeText(text: String): Triple<Int, Int, WeekType> {
        val weekType = parseWeekType(text)
        val rangeMatches = Regex("(\\d{1,2})\\s*[-~至]\\s*(\\d{1,2})\\s*周").findAll(text).toList()
        if (rangeMatches.isNotEmpty()) {
            val minStart = rangeMatches.mapNotNull { it.groupValues[1].toIntOrNull() }
                .minOrNull()?.coerceIn(1, CourseEntity.MAX_TOTAL_WEEKS) ?: 1
            val maxEnd = rangeMatches.mapNotNull { it.groupValues[2].toIntOrNull() }
                .maxOrNull()?.coerceIn(minStart, CourseEntity.MAX_TOTAL_WEEKS) ?: 16
            return Triple(minStart, maxEnd, weekType)
        }
        // Also match "1-16(单)" or "第1-16周" or "1,3,5周"
        val listMatch = Regex("(\\d{1,2}(?:\\s*[,，、\\-~]\\s*\\d{1,2})+)\\s*周").find(text)
        if (listMatch != null) {
            val nums = Regex("\\d+").findAll(listMatch.groupValues[1]).mapNotNull { it.value.toIntOrNull() }.toList()
            if (nums.isNotEmpty()) {
                val minStart = nums.min().coerceIn(1, CourseEntity.MAX_TOTAL_WEEKS)
                val maxEnd = nums.max().coerceIn(minStart, CourseEntity.MAX_TOTAL_WEEKS)
                return Triple(minStart, maxEnd, weekType)
            }
        }
        val singleWeek = Regex("第?(\\d{1,2})\\s*周").find(text)?.groupValues?.get(1)?.toIntOrNull()
        if (singleWeek != null && singleWeek in 1..CourseEntity.MAX_TOTAL_WEEKS) {
            return Triple(singleWeek, singleWeek, weekType)
        }
        return Triple(1, 16, weekType)
    }

    private fun extractHtmlIntAttr(attrs: String, attrName: String): Int {
        val regex = Regex("$attrName\\s*=\\s*[\"']?(\\d+)[\"']?", RegexOption.IGNORE_CASE)
        return regex.find(attrs)?.groupValues?.get(1)?.toIntOrNull() ?: 1
    }

    private fun htmlToPlainText(rawHtml: String): String {
        return rawHtml
            .replace(Regex("<br\\s*/?>", RegexOption.IGNORE_CASE), "\n")
            .replace(Regex("</(p|div|li|tr)>", RegexOption.IGNORE_CASE), "\n")
            .replace(Regex("<[^>]+>"), " ")
            .replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace(Regex("[ \\t]+"), " ")
            .replace(Regex("\\n\\s*\\n+"), "\n\n")
            .trim()
    }

    private fun splitHtmlCellIntoCourseBlocks(rawHtml: String): List<String> {
        val hrSplit = rawHtml.split(Regex("<hr[^>]*>|-{4,}|={4,}", RegexOption.IGNORE_CASE))
        val results = mutableListOf<String>()
        for (segment in hrSplit) {
            val plain = htmlToPlainText(segment)
            if (plain.isBlank()) continue
            val doubleBreakParts = plain.split(Regex("\\n\\s*\\n")).map { it.trim() }.filter { it.isNotEmpty() }
            if (doubleBreakParts.size >= 2 && doubleBreakParts.count { it.contains("周") || it.contains("节") || it.contains("@") || it.contains("\n") } >= 2) {
                results.addAll(doubleBreakParts)
            } else {
                results.add(plain)
            }
        }
        return results
    }

    private fun parseWeekdayHeaderToken(token: String): Int? {
        val t = token.trim()
        if (t.isEmpty() || t.length > 12) return null
        return parseExplicitChineseWeekday(t) ?: when {
            t.equals("Mon", true) || t.equals("Monday", true) -> 1
            t.equals("Tue", true) || t.equals("Tuesday", true) -> 2
            t.equals("Wed", true) || t.equals("Wednesday", true) -> 3
            t.equals("Thu", true) || t.equals("Thursday", true) -> 4
            t.equals("Fri", true) || t.equals("Friday", true) -> 5
            t.equals("Sat", true) || t.equals("Saturday", true) -> 6
            t.equals("Sun", true) || t.equals("Sunday", true) -> 7
            else -> null
        }
    }

    private fun parseExplicitChineseWeekday(token: String): Int? {
        val t = token.trim()
        if (!t.contains("周") && !t.contains("星期") && !t.contains("礼拜")) return null
        return when {
            t.contains("一") || t.endsWith("1") -> 1
            t.contains("二") || t.endsWith("2") -> 2
            t.contains("三") || t.endsWith("3") -> 3
            t.contains("四") || t.endsWith("4") -> 4
            t.contains("五") || t.endsWith("5") -> 5
            t.contains("六") || t.endsWith("6") -> 6
            t.contains("日") || t.contains("天") || t.endsWith("7") -> 7
            else -> null
        }
    }

    /**
     * Generates a compact copyable share code (`FLOWTIMETABLE://<Base64JSON>`) for the current timetable.
     */
    fun generateShareCode(
        timetable: TimetableEntity,
        courses: List<CourseEntity>,
        sectionTimes: List<SectionTimeEntity>,
    ): String {
        val root = JSONObject().apply {
            put("v", 1)
            put("name", timetable.name)
            put("weeks", timetable.totalWeeks)
            put("courses", encodeCoursesJson(courses))
            put("times", encodeSectionTimesJson(sectionTimes))
        }
        val bytes = root.toString().toByteArray(Charsets.UTF_8)
        val encoded = Base64.encodeToString(bytes, Base64.NO_WRAP)
        return "$SHARE_PREFIX$encoded"
    }

    /**
     * Parses a share code (`FLOWTIMETABLE://...`) back into courses & section times.
     */
    fun parseShareCode(rawCode: String, timetableId: Long): ParsedTimetableImport? {
        val cleaned = rawCode.trim()
        val payload = when {
            cleaned.contains(SHARE_PREFIX) -> cleaned.substringAfter(SHARE_PREFIX).trim()
            else -> cleaned
        }
        return runCatching {
            val jsonStr = String(Base64.decode(payload, Base64.DEFAULT), Charsets.UTF_8)
            val root = JSONObject(jsonStr)
            val courses = decodeCoursesJson(root.optJSONArray("courses"), timetableId)
            val times = decodeSectionTimesJson(root.optJSONArray("times"), timetableId)
            ParsedTimetableImport(
                timetableName = root.optString("name").takeIf { it.isNotBlank() },
                totalWeeks = root.optInt("weeks", 20).coerceIn(1, CourseEntity.MAX_TOTAL_WEEKS),
                courses = courses,
                sectionTimes = times,
            )
        }.getOrNull()
    }

    /**
     * Generates human-readable text summary for system share sheets.
     */
    fun buildShareSummaryText(timetable: TimetableEntity, courses: List<CourseEntity>): String {
        val sb = StringBuilder()
        sb.appendLine("【${timetable.name}】共 ${courses.size} 门课程")
        for (day in 1..7) {
            val dayCourses = courses.filter { it.dayOfWeek == day }.sortedBy { it.startSection }
            if (dayCourses.isEmpty()) continue
            sb.appendLine("${DateTimeUtils.dayOfWeekFullChinese(day)}：")
            dayCourses.forEach { c ->
                val loc = if (c.location.isNotBlank()) " ${c.formattedLocation()}" else ""
                sb.appendLine("  · 第${c.startSection}-${c.endSection}节 ${c.name}$loc (${c.weekRangeLabel()})")
            }
        }
        return sb.toString().trim()
    }

    /**
     * Full local JSON backup export containing all timetables, courses, section times, and schedules.
     */
    fun exportFullBackupJson(payload: FullBackupPayload): String {
        val root = JSONObject()
        root.put("schemaVersion", 3)
        root.put("exportedAt", System.currentTimeMillis())

        val ttArray = JSONArray()
        payload.timetables.forEach { tt ->
            ttArray.put(
                JSONObject().apply {
                    put("id", tt.id)
                    put("name", tt.name)
                    put("semesterStartMondayMillis", tt.semesterStartMondayMillis)
                    put("totalWeeks", tt.totalWeeks)
                    put("isActive", tt.isActive)
                },
            )
        }
        root.put("timetables", ttArray)

        val courseArray = JSONArray()
        payload.courses.forEach { c ->
            courseArray.put(
                JSONObject().apply {
                    put("timetableId", c.timetableId)
                    put("name", c.name)
                    put("location", c.location)
                    put("teacher", c.teacher)
                    put("dayOfWeek", c.dayOfWeek)
                    put("startSection", c.startSection)
                    put("endSection", c.endSection)
                    put("startWeek", c.startWeek)
                    put("endWeek", c.endWeek)
                    put("weekType", c.weekType.name)
                    if (c.customWeeksCsv.isNotBlank()) put("customWeeksCsv", c.customWeeksCsv)
                    put("colorIndex", c.colorIndex)
                    put("customColorHex", c.customColorHex)
                    put("notes", c.notes)
                },
            )
        }
        root.put("courses", courseArray)

        val timesArray = JSONArray()
        payload.sectionTimes.forEach { st ->
            timesArray.put(
                JSONObject().apply {
                    put("timetableId", st.timetableId)
                    put("sectionNumber", st.sectionNumber)
                    put("startHour", st.startHour)
                    put("startMinute", st.startMinute)
                    put("endHour", st.endHour)
                    put("endMinute", st.endMinute)
                },
            )
        }
        root.put("sectionTimes", timesArray)

        val scheduleArray = JSONArray()
        payload.schedules.forEach { s ->
            scheduleArray.put(
                JSONObject().apply {
                    put("title", s.title)
                    put("notes", s.notes)
                    put("startTimeMillis", s.startTimeMillis)
                    put("endTimeMillis", s.endTimeMillis)
                    put("repeatRule", s.repeatRule.name)
                    put("reminderMinutesBefore", s.reminderMinutesBefore)
                    put("category", s.category.name)
                    if (s.customTagId.isNotBlank()) put("customTagId", s.customTagId)
                    if (s.customTagName.isNotBlank()) put("customTagName", s.customTagName)
                    if (s.customTagColorHex.isNotBlank()) put("customTagColorHex", s.customTagColorHex)
                    put("isCompleted", s.isCompleted)
                },
            )
        }
        root.put("schedules", scheduleArray)

        return root.toString(2)
    }

    /**
     * Parses full JSON backup (or standalone timetable JSON) safely.
     */
    fun parseFullBackupJson(jsonText: String): FullBackupPayload? {
        return runCatching {
            val root = JSONObject(jsonText.trim())
            val timetables = mutableListOf<TimetableEntity>()
            val ttArr = root.optJSONArray("timetables")
            if (ttArr != null) {
                for (i in 0 until ttArr.length()) {
                    val obj = ttArr.getJSONObject(i)
                    timetables.add(
                        TimetableEntity(
                            id = obj.optLong("id", (i + 1).toLong()),
                            name = obj.optString("name", "导入课表"),
                            semesterStartMondayMillis = obj.optLong(
                                "semesterStartMondayMillis",
                                DateTimeUtils.mondayOfWeekMillis(System.currentTimeMillis()),
                            ),
                            totalWeeks = obj.optInt("totalWeeks", 20).coerceIn(1, CourseEntity.MAX_TOTAL_WEEKS),
                            isActive = obj.optBoolean("isActive", i == 0),
                        ),
                    )
                }
            }

            val courses = mutableListOf<CourseEntity>()
            val cArr = root.optJSONArray("courses")
            if (cArr != null) {
                for (i in 0 until cArr.length()) {
                    val obj = cArr.getJSONObject(i)
                    val maxSec = BellScheduleGenerator.MAX_SECTION_COUNT
                    val sSec = obj.optInt("startSection", 1).coerceIn(1, maxSec)
                    val eSec = obj.optInt("endSection", sSec).coerceIn(sSec, maxSec)
                    val sWeek = obj.optInt("startWeek", 1).coerceIn(1, CourseEntity.MAX_TOTAL_WEEKS)
                    val eWeek = obj.optInt("endWeek", 16).coerceIn(sWeek, CourseEntity.MAX_TOTAL_WEEKS)
                    courses.add(
                        CourseEntity(
                            timetableId = obj.optLong("timetableId", timetables.firstOrNull()?.id ?: 1L),
                            name = obj.optString("name", "未命名课程"),
                            location = obj.optString("location", ""),
                            teacher = obj.optString("teacher", ""),
                            dayOfWeek = obj.optInt("dayOfWeek", 1).coerceIn(1, 7),
                            startSection = sSec,
                            endSection = eSec,
                            startWeek = sWeek,
                            endWeek = eWeek,
                            weekType = runCatching {
                                WeekType.valueOf(obj.optString("weekType", "ALL"))
                            }.getOrDefault(WeekType.ALL),
                            customWeeksCsv = obj.optString("customWeeksCsv", ""),
                            colorIndex = obj.optInt("colorIndex", i),
                            customColorHex = obj.optString("customColorHex", ""),
                            notes = obj.optString("notes", ""),
                        ),
                    )
                }
            }

            val sectionTimes = mutableListOf<SectionTimeEntity>()
            val stArr = root.optJSONArray("sectionTimes")
            if (stArr != null) {
                for (i in 0 until stArr.length()) {
                    val obj = stArr.getJSONObject(i)
                    sectionTimes.add(
                        SectionTimeEntity(
                            timetableId = obj.optLong("timetableId", timetables.firstOrNull()?.id ?: 1L),
                            sectionNumber = obj.optInt("sectionNumber", i + 1).coerceIn(1, BellScheduleGenerator.MAX_SECTION_COUNT),
                            startHour = obj.optInt("startHour", 8).coerceIn(0, 23),
                            startMinute = obj.optInt("startMinute", 0).coerceIn(0, 59),
                            endHour = obj.optInt("endHour", 8).coerceIn(0, 23),
                            endMinute = obj.optInt("endMinute", 45).coerceIn(0, 59),
                        ),
                    )
                }
            }

            val schedules = mutableListOf<ScheduleEntity>()
            val sArr = root.optJSONArray("schedules")
            if (sArr != null) {
                for (i in 0 until sArr.length()) {
                    val obj = sArr.getJSONObject(i)
                    val start = obj.optLong("startTimeMillis", System.currentTimeMillis())
                    val end = obj.optLong("endTimeMillis", start + 30 * 60_000L)
                    schedules.add(
                        ScheduleEntity(
                            title = obj.optString("title", "日程"),
                            notes = obj.optString("notes", ""),
                            startTimeMillis = start,
                            endTimeMillis = end,
                            repeatRule = runCatching {
                                RepeatRule.valueOf(obj.optString("repeatRule", "NONE"))
                            }.getOrDefault(RepeatRule.NONE),
                            reminderMinutesBefore = obj.optInt("reminderMinutesBefore", 15),
                            category = runCatching {
                                ScheduleCategory.valueOf(obj.optString("category", "WORK"))
                            }.getOrDefault(ScheduleCategory.WORK),
                            customTagId = obj.optString("customTagId", ""),
                            customTagName = obj.optString("customTagName", ""),
                            customTagColorHex = obj.optString("customTagColorHex", ""),
                            isCompleted = obj.optBoolean("isCompleted", false),
                        ),
                    )
                }
            }

            FullBackupPayload(
                timetables = timetables,
                courses = courses,
                sectionTimes = sectionTimes,
                schedules = schedules,
            )
        }.getOrNull()
    }

    private fun encodeCoursesJson(courses: List<CourseEntity>): JSONArray {
        return JSONArray().apply {
            courses.forEach { c ->
                put(
                    JSONObject().apply {
                        put("n", c.name)
                        put("l", c.location)
                        put("t", c.teacher)
                        put("d", c.dayOfWeek)
                        put("s", c.startSection)
                        put("e", c.endSection)
                        put("sw", c.startWeek)
                        put("ew", c.endWeek)
                        put("wt", c.weekType.name)
                        if (c.customWeeksCsv.isNotBlank()) put("cw", c.customWeeksCsv)
                        put("ci", c.colorIndex)
                        if (c.customColorHex.isNotBlank()) put("ch", c.customColorHex)
                        put("nt", c.notes)
                    },
                )
            }
        }
    }

    private fun decodeCoursesJson(arr: JSONArray?, timetableId: Long): List<CourseEntity> {
        if (arr == null) return emptyList()
        val list = mutableListOf<CourseEntity>()
        val maxSec = BellScheduleGenerator.MAX_SECTION_COUNT
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val s = o.optInt("s", 1).coerceIn(1, maxSec)
            val e = o.optInt("e", s).coerceIn(s, maxSec)
            val sw = o.optInt("sw", 1).coerceIn(1, CourseEntity.MAX_TOTAL_WEEKS)
            val ew = o.optInt("ew", 16).coerceIn(sw, CourseEntity.MAX_TOTAL_WEEKS)
            list.add(
                CourseEntity(
                    timetableId = timetableId,
                    name = o.optString("n", "课程"),
                    location = o.optString("l", ""),
                    teacher = o.optString("t", ""),
                    dayOfWeek = o.optInt("d", 1).coerceIn(1, 7),
                    startSection = s,
                    endSection = e,
                    startWeek = sw,
                    endWeek = ew,
                    weekType = runCatching { WeekType.valueOf(o.optString("wt", "ALL")) }
                        .getOrDefault(WeekType.ALL),
                    customWeeksCsv = o.optString("cw", ""),
                    colorIndex = o.optInt("ci", i),
                    customColorHex = o.optString("ch", ""),
                    notes = o.optString("nt", ""),
                ),
            )
        }
        return list
    }

    private fun encodeSectionTimesJson(times: List<SectionTimeEntity>): JSONArray {
        return JSONArray().apply {
            times.forEach { st ->
                put(
                    JSONObject().apply {
                        put("sec", st.sectionNumber)
                        put("sh", st.startHour)
                        put("sm", st.startMinute)
                        put("eh", st.endHour)
                        put("em", st.endMinute)
                    },
                )
            }
        }
    }

    private fun decodeSectionTimesJson(arr: JSONArray?, timetableId: Long): List<SectionTimeEntity> {
        if (arr == null) return emptyList()
        val list = mutableListOf<SectionTimeEntity>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            list.add(
                SectionTimeEntity(
                    timetableId = timetableId,
                    sectionNumber = o.optInt("sec", i + 1).coerceIn(1, BellScheduleGenerator.MAX_SECTION_COUNT),
                    startHour = o.optInt("sh", 8).coerceIn(0, 23),
                    startMinute = o.optInt("sm", 0).coerceIn(0, 59),
                    endHour = o.optInt("eh", 8).coerceIn(0, 23),
                    endMinute = o.optInt("em", 45).coerceIn(0, 59),
                ),
            )
        }
        return list
    }

    private fun parseDayOfWeek(token: String): Int? {
        val t = token.trim()
        if (t.isEmpty()) return null
        return parseExplicitChineseWeekday(t) ?: when {
            t == "1" || t == "一" || t.equals("Mon", true) || t.equals("Monday", true) -> 1
            t == "2" || t == "二" || t.equals("Tue", true) || t.equals("Tuesday", true) -> 2
            t == "3" || t == "三" || t.equals("Wed", true) || t.equals("Wednesday", true) -> 3
            t == "4" || t == "四" || t.equals("Thu", true) || t.equals("Thursday", true) -> 4
            t == "5" || t == "五" || t.equals("Fri", true) || t.equals("Friday", true) -> 5
            t == "6" || t == "六" || t.equals("Sat", true) || t.equals("Saturday", true) -> 6
            t == "7" || t == "日" || t == "天" || t.equals("Sun", true) || t.equals("Sunday", true) -> 7
            else -> t.filter(Char::isDigit).toIntOrNull()?.takeIf { it in 1..7 }
        }
    }

    private fun parseWeekType(token: String): WeekType = when {
        token.contains("单") || token.equals("ODD", true) -> WeekType.ODD
        token.contains("双") || token.equals("EVEN", true) -> WeekType.EVEN
        else -> WeekType.ALL
    }

    private fun splitDelimitedLine(line: String, delimiter: Char): List<String> {
        val result = mutableListOf<String>()
        val current = StringBuilder()
        var inQuotes = false
        for (ch in line) {
            when {
                ch == '"' -> inQuotes = !inQuotes
                ch == delimiter && !inQuotes -> {
                    result.add(current.toString())
                    current.clear()
                }
                else -> current.append(ch)
            }
        }
        result.add(current.toString())
        return result
    }
}
