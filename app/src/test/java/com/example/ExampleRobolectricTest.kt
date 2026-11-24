package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.CourseColorPalette
import com.example.data.CourseEntity
import com.example.data.RepeatRule
import com.example.data.ScheduleEntity
import com.example.data.TimetableEntity
import com.example.data.TimetableImportExport
import com.example.data.WeekType
import com.example.notification.LiveActivityItem
import com.example.notification.LiveActivitySourceType
import com.example.smart.ChineseNaturalLanguageParser
import com.example.smart.ConflictDetector
import com.example.smart.DraftType
import com.example.smart.LocalKeystoreCipher
import com.example.smart.OpenAiCompatibleClient
import com.example.smart.SecureStorageException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Calendar

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun `read string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("微澜日程", appName)
    }

    @Test
    fun `schedule enters reminder window and live progress updates accurately`() {
        val start = 1_700_000_000_000L
        val end = start + 60 * 60_000L // 1 hour
        val entity = ScheduleEntity(
            id = 1L,
            title = "技术评审",
            startTimeMillis = start,
            endTimeMillis = end,
            reminderMinutesBefore = 15,
        )

        // 20 minutes before start -> not in reminder window yet
        assertFalse(entity.isLiveUpdateEligible(start - 20 * 60_000L))
        // 10 minutes before start -> inside 15m reminder window
        assertTrue(entity.isInReminderWindow(start - 10 * 60_000L))
        assertTrue(entity.isLiveUpdateEligible(start - 10 * 60_000L))
        assertEquals(100, entity.liveProgressPermille(start - 10 * 60_000L))
        // Halfway through active event -> in progress, permille = 300 + 350 = 650
        assertTrue(entity.isInProgress(start + 30 * 60_000L))
        assertEquals(650, entity.liveProgressPermille(start + 30 * 60_000L))
        // After end -> expired, not eligible for Live Update
        assertFalse(entity.isLiveUpdateEligible(end + 1_000L))
    }

    @Test
    fun `workdays repeat skips weekend`() {
        // Friday 2026-11-20 09:00
        val cal = Calendar.getInstance().apply {
            set(2026, Calendar.NOVEMBER, 20, 9, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val start = cal.timeInMillis
        val end = start + 30 * 60_000L

        val next = RepeatRule.WORKDAYS.computeNextOccurrence(start, end)
        assertNotNull(next)

        val nextCal = Calendar.getInstance().apply { timeInMillis = next!!.first }
        assertEquals(Calendar.MONDAY, nextCal.get(Calendar.DAY_OF_WEEK))
    }

    @Test
    fun `course week matching respects odd even and range`() {
        val oddCourse = CourseEntity(
            timetableId = 1L,
            name = "高等数学",
            location = "教A-301",
            dayOfWeek = 1,
            startSection = 1,
            endSection = 2,
            startWeek = 1,
            endWeek = 16,
            weekType = WeekType.ODD,
        )
        assertTrue(oddCourse.isInWeek(1))
        assertFalse(oddCourse.isInWeek(2))
        assertTrue(oddCourse.isInWeek(15))
        assertFalse(oddCourse.isInWeek(17))
        assertEquals("@教A-301", oddCourse.formattedLocation())
    }

    @Test
    fun `csv html and share code import export work accurately`() {
        val csv = """
            课程名称,地点,教师,星期,开始节次,结束节次,开始周,结束周,单双周
            数据结构,信工楼402,张老师,周一,1,2,1,16,全周
            大学物理,理教205,李老师,3,5,6,2,14,双周
        """.trimIndent()
        val fromCsv = TimetableImportExport.parseCsvOrExcelText(csv, timetableId = 1L)
        assertEquals(2, fromCsv.size)
        assertEquals("数据结构", fromCsv[0].name)
        assertEquals(1, fromCsv[0].dayOfWeek)
        assertEquals(WeekType.EVEN, fromCsv[1].weekType)

        val html = """
            <table>
              <tr><th>节次</th><th>周一</th><th>周二</th></tr>
              <tr><td>第1节</td><td rowspan="2">操作系统<br/>@教B-102<br/>1-16周</td><td></td></tr>
            </table>
        """.trimIndent()
        val fromHtml = TimetableImportExport.parseHtmlTimetable(html, timetableId = 1L)
        assertEquals(1, fromHtml.size)
        assertEquals("操作系统", fromHtml[0].name)
        assertEquals("教B-102", fromHtml[0].location)
        assertEquals(1, fromHtml[0].startSection)
        assertEquals(2, fromHtml[0].endSection)

        val timetable = TimetableEntity(id = 1L, name = "2026秋季课表", semesterStartMondayMillis = 1_700_000_000_000L, totalWeeks = 18)
        val times = CourseColorPalette.defaultSectionTimes(1L)
        val shareCode = TimetableImportExport.generateShareCode(timetable, fromCsv, times)
        assertTrue(shareCode.startsWith("FLOWTIMETABLE://"))

        val decoded = TimetableImportExport.parseShareCode(shareCode, timetableId = 2L)
        assertNotNull(decoded)
        assertEquals("2026秋季课表", decoded!!.timetableName)
        assertEquals(2, decoded.courses.size)
        assertEquals(12, decoded.sectionTimes.size)
    }

    @Test
    fun `live activity title includes at location and capsule text stays compact`() {
        val item = LiveActivityItem(
            sourceType = LiveActivitySourceType.COURSE,
            sourceId = 10L,
            dateKey = "2026-11-21",
            title = "编译原理",
            location = "逸夫楼 502",
            startTimeMillis = 1_700_000_000_000L,
            endTimeMillis = 1_700_003_600_000L,
            reminderMinutesBefore = 15,
        )
        assertEquals("编译原理 @逸夫楼 502", item.titleWithLocation())
        assertEquals("45s", com.example.data.DateTimeUtils.formatCapsuleShortText(45_000L, true))
        assertEquals("8:30", com.example.data.DateTimeUtils.formatCapsuleShortText(510_000L, false))
        assertEquals("25m", com.example.data.DateTimeUtils.formatCapsuleShortText(25 * 60_000L, true))
        assertEquals("1h15m", com.example.data.DateTimeUtils.formatCapsuleShortText(75 * 60_000L, true))
    }

    @Test
    fun `offline chinese nlp parser extracts relative dates times locations reminders and dnd`() {
        // Anchor: Wednesday 2026-11-18 10:00
        val anchorCal = Calendar.getInstance().apply {
            set(2026, Calendar.NOVEMBER, 18, 10, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val now = anchorCal.timeInMillis
        val sections = CourseColorPalette.defaultSectionTimes(1L)

        // 1. Single schedule/course with tomorrow + afternoon 2:30 + location + reminder + DND
        val drafts1 = ChineseNaturalLanguageParser.parse(
            rawInput = "明天下午2点半在教A-301上数据结构 提前15分钟 免打扰",
            nowMillis = now,
            sectionTimes = sections,
        )
        assertEquals(1, drafts1.size)
        val d1 = drafts1.first()
        assertEquals("教A-301", d1.location)
        assertEquals(15, d1.reminderMinutesBefore)
        assertTrue(d1.enableDnd)
        assertTrue(d1.title.contains("数据结构"))
        val d1Start = Calendar.getInstance().apply { timeInMillis = d1.startTimeMillis }
        assertEquals(19, d1Start.get(Calendar.DAY_OF_MONTH))
        assertEquals(14, d1Start.get(Calendar.HOUR_OF_DAY))
        assertEquals(30, d1Start.get(Calendar.MINUTE))

        // 2. Multi-weekday recurring course ("每周一三五早八@笃行楼302高等数学")
        val drafts2 = ChineseNaturalLanguageParser.parse(
            rawInput = "每周一三五早八@笃行楼302 高等数学",
            nowMillis = now,
            sectionTimes = sections,
        )
        assertEquals(1, drafts2.size)
        val d2 = drafts2.first()
        assertEquals(DraftType.COURSE, d2.draftType)
        assertEquals(listOf(1, 3, 5), d2.daysOfWeek)
        assertEquals(1, d2.startSection)
        assertEquals(2, d2.endSection)
        assertEquals("笃行楼302", d2.location)
        assertTrue(d2.title.contains("高等数学"))

        // 3. Multi-clause text with exact time range and next Wednesday
        val drafts3 = ChineseNaturalLanguageParser.parse(
            rawInput = "后天14:00-16:00在会议室B开项目评审；下周三晚上8点一刻健身 提前30分钟",
            nowMillis = now,
            sectionTimes = sections,
        )
        assertEquals(2, drafts3.size)
        assertEquals("会议室B", drafts3[0].location)
        val s0Start = Calendar.getInstance().apply { timeInMillis = drafts3[0].startTimeMillis }
        val s0End = Calendar.getInstance().apply { timeInMillis = drafts3[0].endTimeMillis }
        assertEquals(20, s0Start.get(Calendar.DAY_OF_MONTH))
        assertEquals(14, s0Start.get(Calendar.HOUR_OF_DAY))
        assertEquals(16, s0End.get(Calendar.HOUR_OF_DAY))

        val s1Start = Calendar.getInstance().apply { timeInMillis = drafts3[1].startTimeMillis }
        assertEquals(25, s1Start.get(Calendar.DAY_OF_MONTH))
        assertEquals(20, s1Start.get(Calendar.HOUR_OF_DAY))
        assertEquals(15, s1Start.get(Calendar.MINUTE))
        assertEquals(30, drafts3[1].reminderMinutesBefore)
    }

    @Test
    fun `conflict detector highlights schedule and course overlaps`() {
        // Monday 2026-11-16 08:00 (semester week 1 Monday)
        val monCal = Calendar.getInstance().apply {
            set(2026, Calendar.NOVEMBER, 16, 8, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val mondayStart = com.example.data.DateTimeUtils.startOfDayMillis(monCal.timeInMillis)
        val timetable = TimetableEntity(
            id = 1L,
            name = "默认课表",
            semesterStartMondayMillis = mondayStart,
            totalWeeks = 18,
        )
        val sections = CourseColorPalette.defaultSectionTimes(1L)
        val existingCourse = CourseEntity(
            id = 1L,
            timetableId = 1L,
            name = "高等数学",
            location = "教A-101",
            dayOfWeek = 1,
            startSection = 1,
            endSection = 2,
            startWeek = 1,
            endWeek = 16,
            weekType = WeekType.ALL,
        )
        val existingSchedule = ScheduleEntity(
            id = 2L,
            title = "晨会同步",
            startTimeMillis = com.example.data.DateTimeUtils.combineDateAndTime(mondayStart, 9, 0),
            endTimeMillis = com.example.data.DateTimeUtils.combineDateAndTime(mondayStart, 10, 0),
        )

        val candidate = ChineseNaturalLanguageParser.parse(
            rawInput = "今天08:30-09:30在图书馆讨论",
            nowMillis = com.example.data.DateTimeUtils.combineDateAndTime(mondayStart, 7, 0),
            sectionTimes = sections,
        ).first()

        val conflicts = ConflictDetector.detectConflicts(
            draft = candidate,
            existingSchedules = listOf(existingSchedule),
            activeTimetable = timetable,
            existingCourses = listOf(existingCourse),
            sectionTimes = sections,
        )
        assertEquals(2, conflicts.size)
        assertTrue(conflicts.any { it.isCourse && it.title == "高等数学" })
        assertTrue(conflicts.any { !it.isCourse && it.title == "晨会同步" })
    }

    @Test
    fun `openai compatible client normalizes urls and parses structured json`() {
        assertEquals(
            "https://api.deepseek.com/v1/chat/completions",
            OpenAiCompatibleClient.normalizeChatCompletionsUrl("https://api.deepseek.com/v1"),
        )
        assertEquals(
            "https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions",
            OpenAiCompatibleClient.normalizeChatCompletionsUrl("https://dashscope.aliyuncs.com/compatible-mode/v1/"),
        )
        assertEquals(
            "https://custom.example.com/v1/chat/completions",
            OpenAiCompatibleClient.normalizeChatCompletionsUrl("https://custom.example.com"),
        )

        val fencedJson = """
            ```json
            {
              "items": [
                {
                  "type": "COURSE",
                  "title": "算法设计与分析",
                  "location": "信工楼305",
                  "date": "2026-11-18",
                  "startTime": "08:00",
                  "endTime": "09:40",
                  "daysOfWeek": [1, 3],
                  "startSection": 1,
                  "endSection": 2,
                  "startWeek": 1,
                  "endWeek": 16,
                  "weekType": "ALL",
                  "reminderMinutes": 15,
                  "enableDnd": true
                },
                {
                  "type": "SCHEDULE",
                  "title": "组会汇报",
                  "location": "实验楼201",
                  "date": "2026-11-19",
                  "startTime": "15:00",
                  "endTime": "16:30",
                  "repeatRule": "WEEKLY",
                  "reminderMinutes": 30,
                  "enableDnd": false
                }
              ]
            }
            ```
        """.trimIndent()

        val drafts = OpenAiCompatibleClient.parseStructuredJsonResponse(
            content = fencedJson,
            nowMillis = 1_795_000_000_000L,
            sectionTimes = CourseColorPalette.defaultSectionTimes(1L),
            defaultTotalWeeks = 18,
        )
        assertEquals(2, drafts.size)
        assertEquals(DraftType.COURSE, drafts[0].draftType)
        assertEquals(listOf(1, 3), drafts[0].daysOfWeek)
        assertTrue(drafts[0].enableDnd)
        assertEquals(DraftType.SCHEDULE, drafts[1].draftType)
        assertEquals(RepeatRule.WEEKLY, drafts[1].repeatRule)
        assertEquals(30, drafts[1].reminderMinutesBefore)
    }

    @Test
    fun `local keystore cipher refuses silent plaintext fallback and flags legacy obf keys for reentry`() {
        val secret = "sk-test-deepseek-1234567890"
        // In Robolectric JVM without AndroidKeyStore provider, encrypt must fail closed with SecureStorageException
        val thrown = runCatching { LocalKeystoreCipher.encrypt(secret) }.exceptionOrNull()
        assertTrue(thrown is SecureStorageException)
        assertEquals("", LocalKeystoreCipher.encrypt(""))

        // Legacy OBF_V1 entries must NOT return obfuscated payload; instead flag requiresLegacyMigration
        val legacyResult = LocalKeystoreCipher.decryptWithStatus("OBF_V1:c2stdGVzdA==")
        assertEquals("", legacyResult.plainText)
        assertTrue(legacyResult.requiresLegacyMigration)

        val emptyResult = LocalKeystoreCipher.decryptWithStatus("")
        assertEquals("", emptyResult.plainText)
        assertFalse(emptyResult.requiresLegacyMigration)
    }

    @Test
    fun `today course progress and section elapsed fraction compute accurately`() {
        // Monday 2026-11-16 at 10:20 (middle of section 3: 10:00-10:45)
        val cal = Calendar.getInstance().apply {
            set(2026, Calendar.NOVEMBER, 16, 10, 20, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val now = cal.timeInMillis
        val sections = CourseColorPalette.defaultSectionTimes(1L)

        // Section 3 (10:00-10:45): 20/45 elapsed
        val sec3 = sections.first { it.sectionNumber == 3 }
        val secFraction = com.example.ui.timetable.computeSectionElapsedFraction(sec3, now)
        assertNotNull(secFraction)
        assertEquals(20f / 45f, secFraction!!, 0.01f)

        // Two courses on Monday week 1: sec 1-2 (08:00-09:40 = 100m, finished) & sec 3-4 (10:00-11:40 = 100m, 20m elapsed)
        val c1 = CourseEntity(
            id = 1L,
            timetableId = 1L,
            name = "高等数学",
            dayOfWeek = 1,
            startSection = 1,
            endSection = 2,
            startWeek = 1,
            endWeek = 16,
            weekType = WeekType.ALL,
        )
        val c2 = CourseEntity(
            id = 2L,
            timetableId = 1L,
            name = "数据结构",
            dayOfWeek = 1,
            startSection = 3,
            endSection = 4,
            startWeek = 1,
            endWeek = 16,
            weekType = WeekType.ALL,
        )
        val progress = com.example.ui.timetable.computeTodayCourseProgress(
            courses = listOf(c1, c2),
            sectionTimes = sections,
            currentActualWeek = 1,
            nowMillis = now,
        )
        assertEquals(200, progress.totalMinutes)
        assertEquals(120, progress.elapsedMinutes)
        assertEquals(60, progress.percent)
        assertEquals(1, progress.completedCourseCount)
        assertEquals(1, progress.ongoingCourseCount)
        assertEquals("2/3.3h", progress.compactRatioLabel())
    }

    @Test
    fun `csv import handles wakeup format clock times combined tokens bom and 2d grid`() {
        val sections = CourseColorPalette.defaultSectionTimes(1L)

        // 1. WakeUp CSV with UTF-8 BOM and header mapping
        val wakeupCsv = "\uFEFF课程名称,星期,开始节数,结束节数,老师,地点,周数\n" +
            "线性代数,2,3,4,王教授,教C-204,1-16周(单)\n" +
            "计算机网络,周四,5,6,赵老师,信工楼501,2-14周(双)\n"
        val wakeupCourses = TimetableImportExport.parseCsvOrExcelText(wakeupCsv, 1L, sections)
        assertEquals(2, wakeupCourses.size)
        assertEquals("线性代数", wakeupCourses[0].name)
        assertEquals(2, wakeupCourses[0].dayOfWeek)
        assertEquals(3, wakeupCourses[0].startSection)
        assertEquals(4, wakeupCourses[0].endSection)
        assertEquals("王教授", wakeupCourses[0].teacher)
        assertEquals("教C-204", wakeupCourses[0].location)
        assertEquals(WeekType.ODD, wakeupCourses[0].weekType)

        assertEquals("计算机网络", wakeupCourses[1].name)
        assertEquals(4, wakeupCourses[1].dayOfWeek)
        assertEquals(5, wakeupCourses[1].startSection)
        assertEquals(6, wakeupCourses[1].endSection)
        assertEquals(WeekType.EVEN, wakeupCourses[1].weekType)

        // 2. Clock-time CSV mapped to corresponding sections
        val clockCsv = """
            课程名称,星期,上课时间,地点,周数
            高等数学,周一,08:00-09:40,理教101,1-16周
            概率论,周三,14:00-15:40,理教302,1-12周
            选修摄影,周五,19:00-21:35,艺术楼201,3-10周
        """.trimIndent()
        val clockCourses = TimetableImportExport.parseCsvOrExcelText(clockCsv, 1L, sections)
        assertEquals(3, clockCourses.size)
        assertEquals(1, clockCourses[0].startSection)
        assertEquals(2, clockCourses[0].endSection)
        assertEquals(5, clockCourses[1].startSection)
        assertEquals(6, clockCourses[1].endSection)
        assertEquals(9, clockCourses[2].startSection)
        assertEquals(11, clockCourses[2].endSection)

        // 3. Headerless combined tokens (section range + week range)
        val headerlessCombined = "软件工程,实验楼303,刘老师,周三,3-4节,1-16周(单)"
        val combinedCourses = TimetableImportExport.parseCsvOrExcelText(headerlessCombined, 1L, sections)
        assertEquals(1, combinedCourses.size)
        assertEquals(3, combinedCourses[0].dayOfWeek)
        assertEquals(3, combinedCourses[0].startSection)
        assertEquals(4, combinedCourses[0].endSection)
        assertEquals(1, combinedCourses[0].startWeek)
        assertEquals(16, combinedCourses[0].endWeek)
        assertEquals(WeekType.ODD, combinedCourses[0].weekType)

        // 4. 2D tab-separated Excel timetable matrix
        val gridTsv = "节次\t周一\t周二\t周三\n" +
            "第1-2节\t大学英语 @外语楼102 1-16周\t\t离散数学 @理教202 1-16周\n" +
            "14:00-15:40\t\t体育 @操场 1-14周\t\n"
        val gridCourses = TimetableImportExport.parseCsvOrExcelText(gridTsv, 1L, sections)
        assertEquals(3, gridCourses.size)
        val eng = gridCourses.first { it.name.contains("大学英语") }
        assertEquals(1, eng.dayOfWeek)
        assertEquals(1, eng.startSection)
        assertEquals(2, eng.endSection)
        val pe = gridCourses.first { it.name.contains("体育") }
        assertEquals(2, pe.dayOfWeek)
        assertEquals(5, pe.startSection)
        assertEquals(6, pe.endSection)
    }

    @Test
    fun `html timetable import preserves weekday columns across rowspan and splits multi course cells`() {
        val sections = CourseColorPalette.defaultSectionTimes(1L)
        val html = """
            <table>
              <tr><th>时段</th><th>节次</th><th>周一</th><th>周二</th><th>周三</th></tr>
              <tr>
                <td rowspan="4">上午</td>
                <td>第1节</td>
                <td rowspan="2">高等数学<br/>@教A-101<br/>1-16周</td>
                <td></td>
                <td>大学英语<br/>08:00-09:40<br/>@外语楼201<br/>1-16周</td>
              </tr>
              <tr>
                <td>第2节</td>
                <td>形势与政策<br/>@报告厅<br/>1-8周<hr/>职业规划<br/>@报告厅<br/>9-16周</td>
                <td></td>
              </tr>
              <tr>
                <td>第3节</td>
                <td></td>
                <td rowspan="2">数据结构<br/>@信工楼402<br/>1-16周(单)</td>
                <td></td>
              </tr>
              <tr>
                <td>第4节</td>
                <td></td>
                <td></td>
              </tr>
            </table>
        """.trimIndent()

        val parsed = TimetableImportExport.parseHtmlTimetable(html, 1L, sections)
        assertEquals(5, parsed.size)

        val math = parsed.first { it.name == "高等数学" }
        assertEquals(1, math.dayOfWeek)
        assertEquals(1, math.startSection)
        assertEquals(2, math.endSection)

        val eng = parsed.first { it.name == "大学英语" }
        assertEquals(3, eng.dayOfWeek)
        assertEquals(1, eng.startSection)
        assertEquals(2, eng.endSection)

        val policy = parsed.first { it.name == "形势与政策" }
        assertEquals(2, policy.dayOfWeek)
        assertEquals(2, policy.startSection)
        assertEquals(2, policy.endSection)
        assertEquals(1, policy.startWeek)
        assertEquals(8, policy.endWeek)

        val career = parsed.first { it.name == "职业规划" }
        assertEquals(2, career.dayOfWeek)
        assertEquals(9, career.startWeek)
        assertEquals(16, career.endWeek)

        val ds = parsed.first { it.name == "数据结构" }
        assertEquals(2, ds.dayOfWeek)
        assertEquals(3, ds.startSection)
        assertEquals(4, ds.endSection)
        assertEquals(WeekType.ODD, ds.weekType)
    }

    @Test
    fun `date helpers shift days and compute semester weeks cleanly across week boundaries`() {
        // Semester start Monday: 2026-11-16
        val monCal = Calendar.getInstance().apply {
            set(2026, Calendar.NOVEMBER, 16, 0, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val monday = monCal.timeInMillis
        val sunday = com.example.data.DateTimeUtils.addDays(monday, 6)
        val nextMonday = com.example.data.DateTimeUtils.addDays(sunday, 1)

        assertEquals(1, com.example.data.DateTimeUtils.computeSemesterWeek(monday, monday, 18))
        assertEquals(1, com.example.data.DateTimeUtils.computeSemesterWeek(monday, sunday, 18))
        assertEquals(2, com.example.data.DateTimeUtils.computeSemesterWeek(monday, nextMonday, 18))

        val weekDates = com.example.data.DateTimeUtils.weekDatesContaining(sunday)
        assertEquals(7, weekDates.size)
        assertTrue(com.example.data.DateTimeUtils.isSameDay(monday, weekDates.first()))
        assertTrue(com.example.data.DateTimeUtils.isSameDay(sunday, weekDates.last()))
    }

    @Test
    fun `android 16 live update course notification formats capsule location and passes promotion check`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val manager = com.example.notification.LiveUpdateManager(context)
        manager.ensureNotificationChannel()

        val start = 1_795_000_000_000L
        val end = start + 100 * 60_000L
        val prepNow = start - 12 * 60_000L // 12 minutes before class

        val courseItem = LiveActivityItem(
            sourceType = LiveActivitySourceType.COURSE,
            sourceId = 42L,
            dateKey = "2026-11-22",
            title = "高等数学",
            location = "教A-301",
            sectionLabel = "第1-2节",
            startTimeMillis = start,
            endTimeMillis = end,
            reminderMinutesBefore = 15,
            isTest = false,
        )

        // Capsule short text shows course abbreviation + remaining time within Android 16's 7-char budget
        val chipText = courseItem.capsuleChipText(prepNow)
        assertEquals("高数 12m", chipText)
        assertTrue(chipText.length <= 7)

        // Expanded content text uses timeless clock target + classroom location (never freezes in background)
        val expandedBody = courseItem.expandedContentText(prepNow)
        assertTrue(expandedBody.contains("课前准备"))
        assertTrue(expandedBody.contains("上课"))
        assertTrue(expandedBody.contains("教A-301"))

        val prepNotification = manager.buildLiveActivityNotification(
            item = courseItem,
            nowMillis = prepNow,
            silentUpdate = false,
        )
        assertTrue(prepNotification.extras.getBoolean(android.app.Notification.EXTRA_REQUEST_PROMOTED_ONGOING))
        assertEquals("高等数学 @教A-301", prepNotification.extras.getCharSequence(android.app.Notification.EXTRA_TITLE)?.toString())
        assertTrue(
            prepNotification.extras.getCharSequence(android.app.Notification.EXTRA_TEXT)
                ?.toString().orEmpty().contains("教A-301"),
        )
        assertTrue(
            prepNotification.extras.getCharSequence(android.app.Notification.EXTRA_SUB_TEXT)
                ?.toString().orEmpty().contains("第1-2节"),
        )
        // Must NOT set shortCriticalText during countdown so SystemUI natively drives capsule countdown
        assertEquals(null, prepNotification.shortCriticalText)
        assertEquals(start, prepNotification.`when`)
        assertTrue(prepNotification.extras.getBoolean(android.app.Notification.EXTRA_SHOW_CHRONOMETER))
        assertTrue(prepNotification.extras.getBoolean(android.app.Notification.EXTRA_CHRONOMETER_COUNT_DOWN))
        assertEquals(
            "android.app.Notification\$ProgressStyle",
            prepNotification.extras.getString(android.app.Notification.EXTRA_TEMPLATE),
        )
        assertTrue((prepNotification.flags and android.app.Notification.FLAG_ONGOING_EVENT) != 0)
        assertEquals(0, prepNotification.flags and android.app.Notification.FLAG_GROUP_SUMMARY)
        assertFalse(prepNotification.extras.getBoolean(android.app.Notification.EXTRA_COLORIZED))

        // Verify transition into in-progress phase switches Chronometer target to endTimeMillis
        val inClassNow = start + 25 * 60_000L
        val activeNotification = manager.buildLiveActivityNotification(
            item = courseItem,
            nowMillis = inClassNow,
            silentUpdate = true,
        )
        assertEquals(null, activeNotification.shortCriticalText)
        assertEquals(end, activeNotification.`when`)
        assertTrue(activeNotification.extras.getBoolean(android.app.Notification.EXTRA_SHOW_CHRONOMETER))
        assertTrue(activeNotification.extras.getBoolean(android.app.Notification.EXTRA_CHRONOMETER_COUNT_DOWN))
        val inClassBody = courseItem.expandedContentText(inClassNow)
        assertTrue(inClassBody.contains("正在上课"))
        assertTrue(inClassBody.contains("下课"))
        assertTrue(inClassBody.contains("教A-301"))
        val inClassPermille = courseItem.progressPermille(inClassNow)
        assertTrue(inClassPermille in 470..480)
    }

    @Test
    fun `bell schedule generator supports custom 6 to 16 sections batch generation and independent overrides`() {
        // 1. Batch generate 13-section timetable with custom 40m class, 10m break, and custom period starts
        val config = com.example.data.BellBatchConfig(
            totalSections = 13,
            classDurationMinutes = 40,
            breakDurationMinutes = 10,
            morningCount = 4,
            afternoonCount = 5,
            morningStartHour = 8,
            morningStartMinute = 15,
            afternoonStartHour = 13,
            afternoonStartMinute = 30,
            eveningStartHour = 18,
            eveningStartMinute = 45,
        )
        val generated = com.example.data.BellScheduleGenerator.generate(timetableId = 7L, config = config)
        assertEquals(13, generated.size)
        assertEquals("08:15", generated[0].startLabel())
        assertEquals("08:55", generated[0].endLabel())
        assertEquals("09:05", generated[1].startLabel())
        assertEquals("09:45", generated[1].endLabel())
        // Afternoon first section (section #5, index 4)
        assertEquals(5, generated[4].sectionNumber)
        assertEquals("13:30", generated[4].startLabel())
        assertEquals("14:10", generated[4].endLabel())
        // Evening first section (section #10, index 9)
        assertEquals(10, generated[9].sectionNumber)
        assertEquals("18:45", generated[9].startLabel())
        assertEquals("19:25", generated[9].endLabel())
        assertEquals(13, generated.last().sectionNumber)

        // 2. Independently tweak section 1 to 55 min and section 2 break to 20 min, then grow to 15 sections
        val customized = generated.map { slot ->
            when (slot.sectionNumber) {
                1 -> slot.copy(startHour = 8, startMinute = 0, endHour = 8, endMinute = 55)
                2 -> slot.copy(startHour = 9, startMinute = 15, endHour = 9, endMinute = 50)
                else -> slot
            }
        }
        val grown = com.example.data.BellScheduleGenerator.resize(customized, newCount = 15, timetableId = 7L)
        assertEquals(15, grown.size)
        assertEquals(55, com.example.data.BellScheduleGenerator.durationMinutes(grown[0]))
        assertEquals(20, com.example.data.BellScheduleGenerator.breakMinutesBetween(grown[0], grown[1]))
        assertEquals(35, com.example.data.BellScheduleGenerator.durationMinutes(grown[1]))
        assertEquals(15, grown.last().sectionNumber)

        // 3. Shrink down to 6 sections (minimum) and verify custom edits remain intact
        val shrunk = com.example.data.BellScheduleGenerator.resize(grown, newCount = 4, timetableId = 7L)
        assertEquals(com.example.data.BellScheduleGenerator.MIN_SECTION_COUNT, shrunk.size)
        assertEquals("08:00", shrunk[0].startLabel())
        assertEquals("08:55", shrunk[0].endLabel())
        assertEquals("09:15", shrunk[1].startLabel())
        assertEquals("09:50", shrunk[1].endLabel())

        // 4. Verify CSV import maps courses up to section 13 cleanly using custom 13-section bell schedule
        val csv13 = """
            课程名称,星期,节次,地点,周数
            晚间实验,周四,12-13节,实验楼B502,1-16周
        """.trimIndent()
        val imported13 = TimetableImportExport.parseCsvOrExcelText(csv13, 7L, generated)
        assertEquals(1, imported13.size)
        assertEquals(12, imported13[0].startSection)
        assertEquals(13, imported13[0].endSection)
    }

    @Test
    fun `course palette offers 16 presets custom hex color compact campus stripping and fluid cloud accent binding`() {
        // 1. 16 distinct preset colors
        assertEquals(16, CourseColorPalette.styles.size)
        assertEquals(16, CourseColorPalette.styles.map { it.name }.toSet().size)

        // 2. Custom hex color parsing & style resolution
        val parsed = CourseColorPalette.parseHexColorOrNull("#3b82f6")
        assertNotNull(parsed)
        assertEquals("#3B82F6", CourseColorPalette.formatHex(parsed!!))
        assertEquals(null, CourseColorPalette.parseHexColorOrNull("#GGGGGG"))

        val customCourse = CourseEntity(
            timetableId = 1L,
            name = "数字图像处理",
            location = "越溪校区 流-515实",
            teacher = "陈老师",
            dayOfWeek = 3,
            startSection = 1,
            endSection = 2,
            startWeek = 1,
            endWeek = 16,
            weekType = WeekType.ALL,
            colorIndex = 2,
            customColorHex = "#3B82F6",
        )
        // Compact card location strips redundant campus prefix while full detail keeps it intact
        assertEquals("@流-515实", customCourse.compactDisplayLocation())
        assertEquals("@越溪校区 流-515实", customCourse.formattedLocation())

        val plainCourse = customCourse.copy(location = "教A-301")
        assertEquals("@教A-301", plainCourse.compactDisplayLocation())

        val resolved = customCourse.resolvedColorStyle()
        assertEquals("自定义 #3B82F6", resolved.name)

        // 3. Share code round-trip preserves customColorHex
        val tt = TimetableEntity(id = 1L, name = "测试课表", semesterStartMondayMillis = 1_700_000_000_000L, totalWeeks = 18)
        val shareCode = TimetableImportExport.generateShareCode(tt, listOf(customCourse), CourseColorPalette.defaultSectionTimes(1L))
        val decoded = TimetableImportExport.parseShareCode(shareCode, 2L)
        assertNotNull(decoded)
        assertEquals("#3B82F6", decoded!!.courses.first().customColorHex)

        // 4. Dynamic Fluid Cloud notification color binds course custom accentColorArgb
        val context = ApplicationProvider.getApplicationContext<Context>()
        val manager = com.example.notification.LiveUpdateManager(context)
        manager.ensureNotificationChannel()
        val expectedColor = 0xFF3B82F6.toInt()
        val start = 1_795_000_000_000L
        val end = start + 90 * 60_000L
        val liveItem = LiveActivityItem(
            sourceType = LiveActivitySourceType.COURSE,
            sourceId = 99L,
            dateKey = "2026-11-22",
            title = "数字图像处理",
            location = customCourse.compactDisplayLocation(),
            sectionLabel = "第1-2节",
            startTimeMillis = start,
            endTimeMillis = end,
            reminderMinutesBefore = 15,
            accentColorArgb = expectedColor,
        )
        val notification = manager.buildLiveActivityNotification(
            item = liveItem,
            nowMillis = start - 5 * 60_000L,
            silentUpdate = false,
        )
        assertEquals(expectedColor, notification.color)
        assertEquals(null, notification.shortCriticalText)
        assertEquals(start, notification.`when`)
        assertEquals(end - (start - 5 * 60_000L), notification.timeoutAfter)
        assertEquals(end, notification.extras.getLong(com.example.notification.LiveUpdateManager.EXTRA_LIVE_END_TIME_MILLIS))
    }

    @Test
    fun `live update sets system timeoutAfter to session end and purges expired notifications cleanly`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val manager = com.example.notification.LiveUpdateManager(context)
        manager.ensureNotificationChannel()

        val start = 1_795_100_000_000L
        val end = start + 45 * 60_000L
        val prepNow = start - 10 * 60_000L
        val activeNow = start + 15 * 60_000L

        val item = LiveActivityItem(
            sourceType = LiveActivitySourceType.COURSE,
            sourceId = 108L,
            dateKey = "2026-11-22",
            title = "操作系统",
            location = "@流-515实",
            sectionLabel = "第3-4节",
            startTimeMillis = start,
            endTimeMillis = end,
            reminderMinutesBefore = 15,
        )

        // 1. Prep phase sets Chronometer to startTimeMillis while system timeoutAfter matches remaining endTimeMillis
        val prepNotif = manager.buildLiveActivityNotification(item, prepNow, silentUpdate = false)
        assertEquals(start, prepNotif.`when`)
        assertEquals(end - prepNow, prepNotif.timeoutAfter)
        assertEquals(null, prepNotif.shortCriticalText)

        // 2. Active phase sets Chronometer to endTimeMillis and system timeoutAfter matches remaining endTimeMillis
        val activeNotif = manager.buildLiveActivityNotification(item, activeNow, silentUpdate = true)
        assertEquals(end, activeNotif.`when`)
        assertEquals(end - activeNow, activeNotif.timeoutAfter)
        assertEquals(null, activeNotif.shortCriticalText)

        // 3. Sync posts active live activity; before end time purge returns false, at/after end time purge cancels & clears it
        manager.syncLiveActivity(listOf(item), nowMillis = activeNow, forceAlert = false)
        assertNotNull(manager.activeLiveActivity.value)
        assertFalse(manager.purgeExpiredNotificationIfNeeded(end - 5_000L))
        assertNotNull(manager.activeLiveActivity.value)

        assertTrue(manager.purgeExpiredNotificationIfNeeded(end))
        assertEquals(null, manager.activeLiveActivity.value)
        assertFalse(manager.purgeExpiredNotificationIfNeeded(end + 10_000L))
    }

    @Test
    fun `course supports non contiguous custom weeks and preserves them in share code round trip`() {
        val selected = (5..10).toSet() + (12..18).toSet()
        val csv = CourseEntity.encodeCustomWeeksCsv(selected)
        assertEquals("5,6,7,8,9,10,12,13,14,15,16,17,18", csv)

        val spec = CourseEntity.summarizeWeeks(selected)
        assertEquals(5, spec.startWeek)
        assertEquals(18, spec.endWeek)
        assertEquals(WeekType.ALL, spec.weekType)
        assertEquals(csv, spec.customWeeksCsv)

        val course = CourseEntity(
            timetableId = 1L,
            name = "微机原理实验",
            location = "流-515实",
            teacher = "王老师",
            dayOfWeek = 2,
            startSection = 3,
            endSection = 4,
            startWeek = spec.startWeek,
            endWeek = spec.endWeek,
            weekType = spec.weekType,
            customWeeksCsv = spec.customWeeksCsv,
        )
        assertFalse(course.isInWeek(4))
        assertTrue(course.isInWeek(5))
        assertTrue(course.isInWeek(10))
        assertFalse(course.isInWeek(11)) // skipped week!
        assertTrue(course.isInWeek(12))
        assertTrue(course.isInWeek(18))
        assertFalse(course.isInWeek(19))
        assertEquals("5-10, 12-18周", course.weekRangeLabel())

        val tt = TimetableEntity(
            id = 1L,
            name = "全年长学期课表",
            semesterStartMondayMillis = 1_700_000_000_000L,
            totalWeeks = 36,
        )
        val shareCode = TimetableImportExport.generateShareCode(
            timetable = tt,
            courses = listOf(course),
            sectionTimes = CourseColorPalette.defaultSectionTimes(1L),
        )
        val decoded = TimetableImportExport.parseShareCode(shareCode, timetableId = 2L)
        assertNotNull(decoded)
        assertEquals(36, decoded!!.totalWeeks)
        assertEquals(csv, decoded.courses.first().customWeeksCsv)
        assertFalse(decoded.courses.first().isInWeek(11))
        assertTrue(decoded.courses.first().isInWeek(12))
    }

    @Test
    fun `week start day sunday and extended total weeks compute accurately`() {
        assertEquals(
            listOf(7, 1, 2, 3, 4, 5, 6),
            com.example.data.DateTimeUtils.orderedIsoDays(com.example.data.WeekStartDay.SUNDAY, showWeekend = true),
        )
        assertEquals(
            listOf(1, 2, 3, 4, 5),
            com.example.data.DateTimeUtils.orderedIsoDays(com.example.data.WeekStartDay.SUNDAY, showWeekend = false),
        )

        // Sunday 2026-11-15 -> week starts on Sunday 11/15 and ends on Saturday 11/21
        val wedCal = Calendar.getInstance().apply {
            set(2026, Calendar.NOVEMBER, 18, 12, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val sunWeek = com.example.data.DateTimeUtils.weekDatesContaining(
            wedCal.timeInMillis,
            com.example.data.WeekStartDay.SUNDAY,
        )
        assertEquals(7, sunWeek.size)
        assertEquals(7, com.example.data.DateTimeUtils.isoDayOfWeek(sunWeek.first()))
        assertEquals(6, com.example.data.DateTimeUtils.isoDayOfWeek(sunWeek.last()))

        // 36-week semester reaches week 30 cleanly without clamping at 20
        val semStart = sunWeek.first()
        val week30Day = com.example.data.DateTimeUtils.addDays(semStart, 29 * 7 + 2)
        assertEquals(
            30,
            com.example.data.DateTimeUtils.computeSemesterWeek(
                semesterStartMondayMillis = semStart,
                targetMillis = week30Day,
                totalWeeks = 36,
                weekStartDay = com.example.data.WeekStartDay.SUNDAY,
            ),
        )
    }

    @Test
    fun `custom schedule tags encode decode and resolve live metadata on schedule entities`() {
        val tags = listOf(
            com.example.data.CustomScheduleTag(id = "tag_1", name = "考研冲刺", colorHex = "#7C3AED"),
            com.example.data.CustomScheduleTag(id = "tag_2", name = "社团排练", colorHex = "#E11D6D"),
        )
        val encoded = com.example.data.CustomScheduleTagCodec.encode(tags)
        val decoded = com.example.data.CustomScheduleTagCodec.decode(encoded)
        assertEquals(tags, decoded)

        val schedule = ScheduleEntity(
            id = 5L,
            title = "数学一真题套卷",
            startTimeMillis = 1_795_000_000_000L,
            endTimeMillis = 1_795_007_200_000L,
            customTagId = "tag_1",
            customTagName = "旧名称",
            customTagColorHex = "#0B72FF",
        )
        // Resolves updated name/color from live tag definitions
        assertEquals("考研冲刺", schedule.resolvedTagLabel(decoded))
        assertEquals(tags.first().resolvedColor(), schedule.resolvedTagColor(decoded))
        // Falls back to stored snapshot if tag list is empty
        assertEquals("旧名称", schedule.resolvedTagLabel(emptyList()))
    }

    @Test
    fun `dnd level fluid cloud display toggles and focus live activity format accurately`() {
        assertEquals(
            android.app.NotificationManager.INTERRUPTION_FILTER_PRIORITY,
            com.example.data.DndLevel.PRIORITY.interruptionFilter,
        )
        assertEquals(
            android.app.NotificationManager.INTERRUPTION_FILTER_ALARMS,
            com.example.data.DndLevel.ALARMS.interruptionFilter,
        )
        assertEquals(
            android.app.NotificationManager.INTERRUPTION_FILTER_NONE,
            com.example.data.DndLevel.NONE.interruptionFilter,
        )

        val start = 1_795_200_000_000L
        val end = start + 50 * 60_000L
        val activeNow = start + 10 * 60_000L

        val richCourse = LiveActivityItem(
            sourceType = LiveActivitySourceType.COURSE,
            sourceId = 200L,
            dateKey = "2026-11-22",
            title = "机器学习",
            location = "流-515实",
            teacher = "李教授",
            notes = "带笔记本电脑",
            sectionLabel = "第5-6节",
            startTimeMillis = start,
            endTimeMillis = end,
            reminderMinutesBefore = 15,
            showLocation = true,
            showTeacher = true,
            showNotes = true,
        )
        assertEquals("机器学习 @流-515实", richCourse.titleWithLocation())
        val richBody = richCourse.expandedContentText(activeNow)
        assertTrue(richBody.contains("流-515实"))
        assertTrue(richBody.contains("李教授"))
        assertTrue(richBody.contains("带笔记本电脑"))

        val minimalCourse = richCourse.copy(
            showLocation = false,
            showTeacher = false,
            showNotes = false,
        )
        assertEquals("机器学习", minimalCourse.titleWithLocation())
        val minimalBody = minimalCourse.expandedContentText(activeNow)
        assertFalse(minimalBody.contains("流-515实"))
        assertFalse(minimalBody.contains("李教授"))
        assertFalse(minimalBody.contains("带笔记本电脑"))

        // Focus live activity builds native Chronometer + timeoutAfter without shortCriticalText
        val context = ApplicationProvider.getApplicationContext<Context>()
        val manager = com.example.notification.LiveUpdateManager(context)
        manager.ensureNotificationChannel()
        val focusItem = LiveActivityItem(
            sourceType = LiveActivitySourceType.FOCUS,
            sourceId = 1L,
            dateKey = "2026-11-22",
            title = "算法刷题",
            startTimeMillis = start,
            endTimeMillis = end,
            reminderMinutesBefore = 0,
            isBreakPhase = false,
        )
        val focusNotif = manager.buildLiveActivityNotification(focusItem, activeNow, silentUpdate = false)
        assertEquals(null, focusNotif.shortCriticalText)
        assertEquals(end, focusNotif.`when`)
        assertEquals(end - activeNow, focusNotif.timeoutAfter)
        assertTrue(focusItem.expandedContentText(activeNow).contains("沉浸专注"))
        assertTrue(focusItem.copy(isBreakPhase = true).expandedContentText(activeNow).contains("专注休息"))
    }

    @Test
    fun `openai client enforces strict https urls and extracts host safely`() {
        assertTrue(OpenAiCompatibleClient.isValidHttpsUrl("https://api.deepseek.com/v1"))
        assertTrue(OpenAiCompatibleClient.isValidHttpsUrl("  https://dashscope.aliyuncs.com/compatible-mode/v1  "))
        assertFalse(OpenAiCompatibleClient.isValidHttpsUrl("http://api.deepseek.com/v1"))
        assertFalse(OpenAiCompatibleClient.isValidHttpsUrl("ftp://example.com"))
        assertFalse(OpenAiCompatibleClient.isValidHttpsUrl(""))
        assertFalse(OpenAiCompatibleClient.isValidHttpsUrl("api.deepseek.com/v1"))

        assertEquals("api.deepseek.com", OpenAiCompatibleClient.extractHostOrNull("https://api.deepseek.com/v1"))
        assertNull(OpenAiCompatibleClient.extractHostOrNull("http://api.deepseek.com/v1"))
        assertNull(OpenAiCompatibleClient.extractHostOrNull("not-a-url"))
    }

    @Test
    fun `course gridDisplayLocation strips at sign and campus prefix while keeping compactDisplayLocation`() {
        val campusCourse = CourseEntity(
            timetableId = 1L,
            name = "毛泽东思想和中国特色社会主义理论体系概论",
            location = "越溪校区 流-515实",
            dayOfWeek = 1,
            startSection = 1,
            endSection = 2,
        )
        assertEquals("流-515实", campusCourse.gridDisplayLocation())
        assertEquals("@流-515实", campusCourse.compactDisplayLocation())

        val normalCourse = campusCourse.copy(location = "@信1-211多")
        assertEquals("信1-211多", normalCourse.gridDisplayLocation())

        val blankCourse = campusCourse.copy(location = "  ")
        assertEquals("", blankCourse.gridDisplayLocation())
    }

    @Test
    fun `live update hides sensitive lockscreen details by default and respects opt in toggle`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val manager = com.example.notification.LiveUpdateManager(context)
        manager.ensureNotificationChannel()

        val start = 1_795_300_000_000L
        val end = start + 90 * 60_000L
        val now = start + 15 * 60_000L

        val privateItem = LiveActivityItem(
            sourceType = LiveActivitySourceType.COURSE,
            sourceId = 301L,
            dateKey = "2026-11-22",
            title = "软件体系结构",
            location = "流-515实",
            teacher = "张教授",
            notes = "机密项目演示",
            sectionLabel = "第3-4节",
            startTimeMillis = start,
            endTimeMillis = end,
            reminderMinutesBefore = 15,
            showLockscreenDetails = false,
        )
        val privateNotif = manager.buildLiveActivityNotification(privateItem, now, silentUpdate = false)
        assertEquals(android.app.Notification.VISIBILITY_PRIVATE, privateNotif.visibility)
        assertNotNull(privateNotif.publicVersion)
        val publicText = privateNotif.publicVersion!!.extras
            .getCharSequence(android.app.Notification.EXTRA_TEXT)?.toString().orEmpty()
        assertFalse(publicText.contains("流-515实"))
        assertFalse(publicText.contains("张教授"))
        assertFalse(publicText.contains("机密项目演示"))

        val publicItem = privateItem.copy(showLockscreenDetails = true)
        val publicNotif = manager.buildLiveActivityNotification(publicItem, now, silentUpdate = false)
        assertEquals(android.app.Notification.VISIBILITY_PUBLIC, publicNotif.visibility)
        assertNull(publicNotif.publicVersion)
    }

    @Test
    fun `full backup rejects unrecognized json and preserves completed schedules cleanly`() {
        assertNull(TimetableImportExport.parseFullBackupJson("{}"))
        assertNull(TimetableImportExport.parseFullBackupJson("""{"hello":"world"}"""))
        assertNull(TimetableImportExport.parseFullBackupJson("not valid json"))

        val completedSchedule = ScheduleEntity(
            id = 88L,
            title = "已完成实验报告",
            notes = "提交 PDF",
            startTimeMillis = 1_795_000_000_000L,
            endTimeMillis = 1_795_003_600_000L,
            isCompleted = true,
        )
        val payload = com.example.data.FullBackupPayload(
            timetables = listOf(
                TimetableEntity(
                    id = 1L,
                    name = "测试学期",
                    semesterStartMondayMillis = 1_700_000_000_000L,
                    totalWeeks = 20,
                    isActive = true,
                ),
            ),
            courses = emptyList(),
            sectionTimes = CourseColorPalette.defaultSectionTimes(1L),
            schedules = listOf(completedSchedule),
        )
        val json = TimetableImportExport.exportFullBackupJson(payload)
        val restored = TimetableImportExport.parseFullBackupJson(json)
        assertNotNull(restored)
        assertEquals(1, restored!!.schedules.size)
        assertTrue(restored.schedules.first().isCompleted)
        assertEquals("已完成实验报告", restored.schedules.first().title)
    }

    @Test
    fun `app update semantic version comparator handles prefixes multi digit segments and prereleases`() {
        val checker = com.example.update.AppUpdateChecker

        assertTrue(checker.isRemoteVersionNewer("v2.2.0", "2.1.2"))
        assertTrue(checker.isRemoteVersionNewer("V2.1.10", "2.1.2"))
        assertTrue(checker.isRemoteVersionNewer("3.0", "2.9.9"))
        assertTrue(checker.isRemoteVersionNewer("2.2.0", "2.2.0-beta.1"))

        assertFalse(checker.isRemoteVersionNewer("v2.1.2", "2.1.2"))
        assertFalse(checker.isRemoteVersionNewer("2.1.2", "2.1.2"))
        assertFalse(checker.isRemoteVersionNewer("v2.1.1", "2.1.2"))
        assertFalse(checker.isRemoteVersionNewer("2.1.2-rc.1", "2.1.2"))
        assertFalse(checker.isRemoteVersionNewer("", "2.1.2"))
    }

    @Test
    fun `github releases parser extracts apk asset url and detects newer or up to date versions`() {
        val checker = com.example.update.AppUpdateChecker
        val newerReleaseJson = """
            {
              "tag_name": "v2.2.0",
              "html_url": "https://github.com/xialia-cheng/ripple-plan/releases/tag/v2.2.0",
              "body": "1. 优化实况通知显示\n2. 修复已知问题",
              "assets": [
                {
                  "name": "checksums.txt",
                  "browser_download_url": "https://github.com/xialia-cheng/ripple-plan/releases/download/v2.2.0/checksums.txt"
                },
                {
                  "name": "RipplePlan-v2.2.0-release.apk",
                  "browser_download_url": "https://github.com/xialia-cheng/ripple-plan/releases/download/v2.2.0/RipplePlan-v2.2.0-release.apk"
                }
              ]
            }
        """.trimIndent()

        val updateResult = checker.parseLatestReleaseJson(newerReleaseJson, currentVersion = "2.1.2")
        assertTrue(updateResult is com.example.update.UpdateCheckResult.UpdateAvailable)
        val available = updateResult as com.example.update.UpdateCheckResult.UpdateAvailable
        assertEquals("2.2.0", available.release.versionName)
        assertTrue(available.release.hasApkAsset)
        assertTrue(available.release.downloadUrl.endsWith(".apk"))
        assertTrue(available.release.releaseNotes.contains("优化实况通知显示"))

        val noApkJson = """
            {
              "tag_name": "v2.2.0",
              "html_url": "https://github.com/xialia-cheng/ripple-plan/releases/tag/v2.2.0",
              "body": ""
            }
        """.trimIndent()
        val fallbackResult = checker.parseLatestReleaseJson(noApkJson, currentVersion = "2.1.2")
        assertTrue(fallbackResult is com.example.update.UpdateCheckResult.UpdateAvailable)
        val fallbackAvailable = fallbackResult as com.example.update.UpdateCheckResult.UpdateAvailable
        assertFalse(fallbackAvailable.release.hasApkAsset)
        assertEquals(fallbackAvailable.release.releasePageUrl, fallbackAvailable.release.downloadUrl)

        val sameVersionResult = checker.parseLatestReleaseJson(newerReleaseJson, currentVersion = "2.2.0")
        assertTrue(sameVersionResult is com.example.update.UpdateCheckResult.UpToDate)
    }

    @Test
    fun `optional timetable bands default off and lunch break resolver supports full customization`() {
        val defaults = com.example.data.AppSettings()
        assertFalse(defaults.showMorningExercise)
        assertEquals("", defaults.extraBannerTitle)
        assertFalse(defaults.showLunchBreak)
        assertEquals("午休", defaults.lunchBreakTitle)
        assertEquals(4, defaults.lunchAfterSection)

        val defaultSections = CourseColorPalette.defaultSectionTimes(1L)
        assertNull(
            com.example.ui.timetable.resolveLunchBreak(
                settings = defaults,
                sectionTimes = defaultSections,
                sectionCount = defaultSections.size,
            ),
        )

        val enabledAuto = defaults.copy(showLunchBreak = true)
        val resolvedAuto = com.example.ui.timetable.resolveLunchBreak(
            settings = enabledAuto,
            sectionTimes = defaultSections,
            sectionCount = defaultSections.size,
        )
        assertNotNull(resolvedAuto)
        assertEquals("午休", resolvedAuto!!.title)
        assertEquals(4, resolvedAuto.afterSection)
        assertEquals("11:40", resolvedAuto.startLabel)
        assertEquals("14:00", resolvedAuto.endLabel)
        assertEquals("午休 · 11:40 - 14:00", resolvedAuto.gridDisplayText)
        assertEquals("11:40-14:00", resolvedAuto.axisRangeText)

        val customLunch = defaults.copy(
            showLunchBreak = true,
            lunchBreakTitle = "午间休息",
            lunchAfterSection = 5,
            lunchBreakStartTime = "12:15",
            lunchBreakEndTime = "13:45",
        )
        val resolvedCustom = com.example.ui.timetable.resolveLunchBreak(
            settings = customLunch,
            sectionTimes = defaultSections,
            sectionCount = defaultSections.size,
        )
        assertNotNull(resolvedCustom)
        assertEquals("午间休息", resolvedCustom!!.title)
        assertEquals(5, resolvedCustom.afterSection)
        assertEquals("12:15", resolvedCustom.startLabel)
        assertEquals("13:45", resolvedCustom.endLabel)
        assertEquals("午间休息 · 12:15 - 13:45", resolvedCustom.gridDisplayText)

        // Clamps out-of-range afterSection to sectionCount - 1
        val clamped = com.example.ui.timetable.resolveLunchBreak(
            settings = customLunch.copy(lunchAfterSection = 99),
            sectionTimes = defaultSections.take(8),
            sectionCount = 8,
        )
        assertNotNull(clamped)
        assertEquals(7, clamped!!.afterSection)
    }

    @Test
    fun `scroll aware fab collapses on downward scroll and expands on upward scroll`() {
        val fabState = com.example.ui.components.ScrollAwareFabState(
            initialVisible = true,
            thresholdPx = 20f,
        )
        val conn = fabState.nestedScrollConnection

        // 1. Non-scrollable zero consumed delta leaves FAB visible
        conn.onPostScroll(
            consumed = androidx.compose.ui.geometry.Offset.Zero,
            available = androidx.compose.ui.geometry.Offset(0f, -60f),
            source = androidx.compose.ui.input.nestedscroll.NestedScrollSource.UserInput,
        )
        assertTrue(fabState.isVisible)

        // 2. Small jitter below threshold does not collapse FAB
        conn.onPostScroll(
            consumed = androidx.compose.ui.geometry.Offset(0f, -12f),
            available = androidx.compose.ui.geometry.Offset.Zero,
            source = androidx.compose.ui.input.nestedscroll.NestedScrollSource.UserInput,
        )
        assertTrue(fabState.isVisible)

        // 3. Continuing downward scroll past threshold collapses FAB
        conn.onPostScroll(
            consumed = androidx.compose.ui.geometry.Offset(0f, -14f),
            available = androidx.compose.ui.geometry.Offset.Zero,
            source = androidx.compose.ui.input.nestedscroll.NestedScrollSource.UserInput,
        )
        assertFalse(fabState.isVisible)

        // 4. Fling/SideEffect alone does not flicker visibility
        conn.onPostScroll(
            consumed = androidx.compose.ui.geometry.Offset(0f, 40f),
            available = androidx.compose.ui.geometry.Offset.Zero,
            source = androidx.compose.ui.input.nestedscroll.NestedScrollSource.SideEffect,
        )
        assertFalse(fabState.isVisible)

        // 5. Scrolling back up past threshold expands FAB
        conn.onPostScroll(
            consumed = androidx.compose.ui.geometry.Offset(0f, 24f),
            available = androidx.compose.ui.geometry.Offset.Zero,
            source = androidx.compose.ui.input.nestedscroll.NestedScrollSource.UserInput,
        )
        assertTrue(fabState.isVisible)

        // 6. Collapse and verify explicit expand() restores visibility
        conn.onPostScroll(
            consumed = androidx.compose.ui.geometry.Offset(0f, -25f),
            available = androidx.compose.ui.geometry.Offset.Zero,
            source = androidx.compose.ui.input.nestedscroll.NestedScrollSource.UserInput,
        )
        assertFalse(fabState.isVisible)
        fabState.expand()
        assertTrue(fabState.isVisible)
    }
}



