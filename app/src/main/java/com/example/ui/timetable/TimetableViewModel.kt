package com.example.ui.timetable

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.AppSettings
import com.example.data.AppThemeMode
import com.example.data.BackupRestoreMode
import com.example.data.BellScheduleGenerator
import com.example.data.CourseColorPalette
import com.example.data.CourseEntity
import com.example.data.DateTimeUtils
import com.example.data.DndLevel
import com.example.data.SectionTimeEntity
import com.example.data.TimetableEntity
import com.example.data.TimetableImportExport
import com.example.data.TimetableRepository
import com.example.data.UserPreferencesRepository
import com.example.data.WeekStartDay
import com.example.data.WeekType
import com.example.notification.LiveUpdateCoordinator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class TimetableViewMode(val label: String) {
    WEEK("周视图"),
    DAY("日视图"),
}

data class TimetableUiState(
    val allTimetables: List<TimetableEntity> = emptyList(),
    val activeTimetable: TimetableEntity? = null,
    val courses: List<CourseEntity> = emptyList(),
    val sectionTimes: List<SectionTimeEntity> = emptyList(),
    val currentActualWeek: Int = 1,
    val displayedWeek: Int = 1,
    val weekDatesMillis: List<Long> = emptyList(),
    val selectedDateMillis: Long = DateTimeUtils.startOfDayMillis(System.currentTimeMillis()),
    val viewMode: TimetableViewMode = TimetableViewMode.WEEK,
    val settings: AppSettings = AppSettings(),
    val nowMillis: Long = System.currentTimeMillis(),
) {
    val isViewingCurrentWeek: Boolean
        get() = displayedWeek == currentActualWeek

    val selectedDayOfWeek: Int
        get() = DateTimeUtils.isoDayOfWeek(selectedDateMillis)

    val isMorningExerciseCheckedToday: Boolean
        get() = settings.morningExerciseCheckedDate == DateTimeUtils.formatDateKey(nowMillis)
}

class TimetableViewModel(
    private val timetableRepository: TimetableRepository,
    private val preferencesRepository: UserPreferencesRepository,
    private val liveUpdateCoordinator: LiveUpdateCoordinator,
) : ViewModel() {

    private val _selectedWeekOverride = MutableStateFlow<Int?>(null)
    private val _selectedDateMillis = MutableStateFlow(DateTimeUtils.startOfDayMillis(System.currentTimeMillis()))
    private val _viewMode = MutableStateFlow(TimetableViewMode.WEEK)
    private val _toastMessage = MutableStateFlow<String?>(null)
    val toastMessage: StateFlow<String?> = _toastMessage

    private val viewControlFlow = combine(
        preferencesRepository.settings,
        _selectedWeekOverride,
        _selectedDateMillis,
        _viewMode,
    ) { settings, weekOverride, selectedDate, mode ->
        Quad(settings, weekOverride, selectedDate, mode)
    }

    val uiState: StateFlow<TimetableUiState> = combine(
        timetableRepository.allTimetables,
        timetableRepository.activeTimetable,
        timetableRepository.activeCourses,
        timetableRepository.activeSectionTimes,
        viewControlFlow,
    ) { allTimetables, activeTimetable, courses, sectionTimes, (settings, weekOverride, selectedDate, viewMode) ->
        val now = System.currentTimeMillis()
        val tt = activeTimetable ?: allTimetables.firstOrNull()
        val totalWeeks = (tt?.totalWeeks ?: 20).coerceAtLeast(1)
        val weekStartDay = settings.weekStartDay
        val semesterStart = tt?.semesterStartMondayMillis
            ?: DateTimeUtils.weekStartOfWeekMillis(now, weekStartDay)
        val actualWeek = DateTimeUtils.computeSemesterWeek(semesterStart, now, totalWeeks, weekStartDay)
        val displayedWeek = (weekOverride ?: actualWeek).coerceIn(1, totalWeeks)
        val weekDates = DateTimeUtils.weekDatesMillis(semesterStart, displayedWeek, weekStartDay)
        val times = if (sectionTimes.isNotEmpty()) {
            sectionTimes.sortedBy { it.sectionNumber }
        } else {
            CourseColorPalette.defaultSectionTimes(tt?.id ?: 0L)
        }

        // Keep selectedDateMillis aligned inside the displayed week regardless of Monday/Sunday start
        val alignedSelectedDate = if (weekDates.any { DateTimeUtils.isSameDay(it, selectedDate) }) {
            selectedDate
        } else {
            val iso = DateTimeUtils.isoDayOfWeek(selectedDate).coerceIn(1, 7)
            weekDates.firstOrNull { DateTimeUtils.isoDayOfWeek(it) == iso } ?: weekDates.firstOrNull() ?: selectedDate
        }

        TimetableUiState(
            allTimetables = allTimetables,
            activeTimetable = tt,
            courses = courses,
            sectionTimes = times,
            currentActualWeek = actualWeek,
            displayedWeek = displayedWeek,
            weekDatesMillis = weekDates,
            selectedDateMillis = alignedSelectedDate,
            viewMode = viewMode,
            settings = settings,
            nowMillis = now,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000L),
        initialValue = TimetableUiState(),
    )

    private data class Quad<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)

    init {
        viewModelScope.launch {
            timetableRepository.ensureDefaultTimetableExists()
            liveUpdateCoordinator.syncAll(rescheduleAlarms = true)
        }
    }

    fun selectWeek(week: Int) {
        val state = uiState.value
        val total = state.activeTimetable?.totalWeeks ?: 20
        val targetWeek = week.coerceIn(1, total)
        _selectedWeekOverride.value = targetWeek
        val weekStartDay = state.settings.weekStartDay
        val semesterStart = state.activeTimetable?.semesterStartMondayMillis
            ?: DateTimeUtils.weekStartOfWeekMillis(System.currentTimeMillis(), weekStartDay)
        val targetWeekDates = DateTimeUtils.weekDatesMillis(semesterStart, targetWeek, weekStartDay)
        val currentIsoDay = DateTimeUtils.isoDayOfWeek(_selectedDateMillis.value).coerceIn(1, 7)
        targetWeekDates.firstOrNull { DateTimeUtils.isoDayOfWeek(it) == currentIsoDay }
            ?.let { _selectedDateMillis.value = it }
    }

    fun jumpToCurrentWeek() {
        _selectedWeekOverride.value = null
        _selectedDateMillis.value = DateTimeUtils.startOfDayMillis(System.currentTimeMillis())
    }

    /**
     * Selects a specific day from the weekday strip.
     * Tapping the already-selected date while in Week View opens Day View directly.
     */
    fun selectDate(dateMillis: Long) {
        val normalized = DateTimeUtils.startOfDayMillis(dateMillis)
        val currentSelected = uiState.value.selectedDateMillis
        if (_viewMode.value == TimetableViewMode.WEEK && DateTimeUtils.isSameDay(normalized, currentSelected)) {
            _viewMode.value = TimetableViewMode.DAY
            return
        }
        _selectedDateMillis.value = normalized
        syncDisplayedWeekForDate(normalized)
    }

    /**
     * Shifts selected date by [deltaDays] (e.g., -1 for previous day, +1 for next day),
     * automatically updating displayedWeek when crossing week boundaries.
     */
    fun shiftSelectedDate(deltaDays: Int) {
        val state = uiState.value
        val nextDate = DateTimeUtils.addDays(state.selectedDateMillis, deltaDays)
        val tt = state.activeTimetable
        val weekStartDay = state.settings.weekStartDay
        if (tt != null) {
            val firstDay = DateTimeUtils.weekStartOfWeekMillis(tt.semesterStartMondayMillis, weekStartDay)
            val lastWeekDates = DateTimeUtils.weekDatesMillis(tt.semesterStartMondayMillis, tt.totalWeeks, weekStartDay)
            val lastDay = lastWeekDates.lastOrNull() ?: firstDay
            if (nextDate < firstDay) {
                emitMessage("已是本学期第 1 周首日")
                return
            }
            if (nextDate > lastDay) {
                emitMessage("已是本学期第 ${tt.totalWeeks} 周末日")
                return
            }
        }
        _selectedDateMillis.value = nextDate
        syncDisplayedWeekForDate(nextDate)
    }

    fun toggleViewMode() {
        _viewMode.value = when (_viewMode.value) {
            TimetableViewMode.WEEK -> TimetableViewMode.DAY
            TimetableViewMode.DAY -> TimetableViewMode.WEEK
        }
    }

    fun setViewMode(mode: TimetableViewMode) {
        _viewMode.value = mode
    }

    private fun syncDisplayedWeekForDate(dateMillis: Long) {
        val state = uiState.value
        val tt = state.activeTimetable ?: return
        val targetWeek = DateTimeUtils.computeSemesterWeek(
            semesterStartMondayMillis = tt.semesterStartMondayMillis,
            targetMillis = dateMillis,
            totalWeeks = tt.totalWeeks,
            weekStartDay = state.settings.weekStartDay,
        )
        _selectedWeekOverride.value = if (targetWeek == state.currentActualWeek) null else targetWeek
    }

    fun saveCourse(
        existingId: Long,
        name: String,
        location: String,
        teacher: String,
        dayOfWeek: Int,
        startSection: Int,
        endSection: Int,
        startWeek: Int,
        endWeek: Int,
        weekType: WeekType,
        colorIndex: Int,
        notes: String,
        customColorHex: String = "",
        customWeeksCsv: String = "",
    ) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            val active = timetableRepository.ensureDefaultTimetableExists()
            val maxSec = uiState.value.sectionTimes.size
                .coerceIn(BellScheduleGenerator.MIN_SECTION_COUNT, BellScheduleGenerator.MAX_SECTION_COUNT)
            val safeStartSec = startSection.coerceIn(1, maxSec)
            val safeEndSec = endSection.coerceIn(safeStartSec, maxSec)

            val parsedCustom = CourseEntity.parseCustomWeeksCsv(customWeeksCsv)
                .filter { it in 1..active.totalWeeks }
            val spec = if (parsedCustom.isNotEmpty()) {
                CourseEntity.summarizeWeeks(parsedCustom)
            } else {
                val safeStartWeek = startWeek.coerceIn(1, active.totalWeeks)
                val safeEndWeek = endWeek.coerceIn(safeStartWeek, active.totalWeeks)
                com.example.data.QuadWeekSpec(safeStartWeek, safeEndWeek, weekType, "")
            }

            val normalizedHex = CourseColorPalette.parseHexColorOrNull(customColorHex)
                ?.let { CourseColorPalette.formatHex(it) }
                ?: ""

            timetableRepository.saveCourse(
                CourseEntity(
                    id = existingId,
                    timetableId = active.id,
                    name = trimmed,
                    location = location.trim().removePrefix("@"),
                    teacher = teacher.trim(),
                    dayOfWeek = dayOfWeek.coerceIn(1, 7),
                    startSection = safeStartSec,
                    endSection = safeEndSec,
                    startWeek = spec.startWeek,
                    endWeek = spec.endWeek,
                    weekType = spec.weekType,
                    customWeeksCsv = spec.customWeeksCsv,
                    colorIndex = colorIndex,
                    notes = notes.trim(),
                    customColorHex = normalizedHex,
                ),
            )
            // Keep all courses with the same name using the exact same color
            val sameNameCourses = uiState.value.courses.filter {
                it.timetableId == active.id &&
                    it.id != existingId &&
                    it.name.trim() == trimmed &&
                    (it.colorIndex != colorIndex || it.customColorHex != normalizedHex)
            }
            sameNameCourses.forEach { sibling ->
                timetableRepository.saveCourse(
                    sibling.copy(
                        colorIndex = colorIndex,
                        customColorHex = normalizedHex,
                    ),
                )
            }
            emitMessage(if (existingId == 0L) "已添加课程「$trimmed」" else "已更新课程「$trimmed」")
        }
    }

    fun deleteCourse(course: CourseEntity) {
        viewModelScope.launch {
            timetableRepository.deleteCourse(course.id)
            emitMessage("已删除课程「${course.name}」")
        }
    }

    fun switchTimetable(timetableId: Long) {
        viewModelScope.launch {
            timetableRepository.switchTimetable(timetableId)
            _selectedWeekOverride.value = null
            emitMessage("已切换课表")
        }
    }

    fun createTimetable(name: String, totalWeeks: Int) {
        viewModelScope.launch {
            timetableRepository.createTimetable(name = name, totalWeeks = totalWeeks, activate = true)
            _selectedWeekOverride.value = null
            emitMessage("已新建并切换至「${name.trim().ifEmpty { "新建课表" }}」")
        }
    }

    fun deleteTimetable(timetable: TimetableEntity) {
        viewModelScope.launch {
            val deleted = timetableRepository.deleteTimetable(timetable.id)
            if (deleted) {
                _selectedWeekOverride.value = null
                emitMessage("已删除课表「${timetable.name}」")
            } else {
                emitMessage("至少需保留一份课表")
            }
        }
    }

    fun updateTimetableSettings(
        name: String,
        semesterStartMondayMillis: Long,
        totalWeeks: Int,
    ) {
        val current = uiState.value.activeTimetable ?: return
        val weekStartDay = uiState.value.settings.weekStartDay
        viewModelScope.launch {
            val alignedStart = DateTimeUtils.weekStartOfWeekMillis(semesterStartMondayMillis, weekStartDay)
            timetableRepository.updateTimetable(
                current.copy(
                    name = name.trim().ifEmpty { current.name },
                    semesterStartMondayMillis = alignedStart,
                    totalWeeks = totalWeeks.coerceIn(1, CourseEntity.MAX_TOTAL_WEEKS),
                ),
            )
            _selectedWeekOverride.value = null
            emitMessage("课表设置已保存")
        }
    }

    fun updateSectionTimes(times: List<SectionTimeEntity>) {
        val ttId = uiState.value.activeTimetable?.id ?: return
        viewModelScope.launch {
            val normalized = BellScheduleGenerator.normalize(times, ttId)
            timetableRepository.saveSectionTimes(ttId, normalized)
            emitMessage("已保存 ${normalized.size} 节作息，课表与流体云闹钟已同步")
        }
    }

    fun toggleMorningExerciseCheckIn() {
        viewModelScope.launch {
            val todayKey = DateTimeUtils.formatDateKey(System.currentTimeMillis())
            val checked = uiState.value.isMorningExerciseCheckedToday
            val bannerTitle = uiState.value.settings.extraBannerTitle
            preferencesRepository.setMorningExerciseCheckedDate(if (checked) "" else todayKey)
            emitMessage(if (checked) "已取消今日「$bannerTitle」打卡" else "今日「$bannerTitle」已完成打卡")
        }
    }

    fun setThemeMode(mode: AppThemeMode) {
        viewModelScope.launch {
            preferencesRepository.setThemeMode(mode)
            emitMessage("主题已切换为「${mode.label}」")
        }
    }

    fun setUseDynamicColor(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.setUseDynamicColor(enabled)
            emitMessage(if (enabled) "已开启系统壁纸动态取色" else "已切换为深邃蓝紫微光默认主题")
        }
    }

    fun setCompactMode(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.setCompactMode(enabled)
        }
    }

    fun setShowMorningExercise(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.setShowMorningExercise(enabled)
        }
    }

    fun setExtraBannerConfig(title: String, startTime: String, endTime: String) {
        viewModelScope.launch {
            preferencesRepository.setExtraBannerConfig(title, startTime, endTime)
            emitMessage("附加时段条已更新")
        }
    }

    fun setShowNonCurrentWeekCourses(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.setShowNonCurrentWeekCourses(enabled)
        }
    }

    fun setShowWeekend(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.setShowWeekend(enabled)
        }
    }

    fun setWeekStartDay(weekStartDay: WeekStartDay) {
        viewModelScope.launch {
            preferencesRepository.setWeekStartDay(weekStartDay)
            liveUpdateCoordinator.syncAll(rescheduleAlarms = true)
            emitMessage("每周起始日已设为${weekStartDay.label}")
        }
    }

    fun setTimetableRowHeightDp(heightDp: Int) {
        viewModelScope.launch {
            preferencesRepository.setTimetableRowHeightDp(heightDp)
        }
    }

    fun setTimetableFontScale(scale: Float) {
        viewModelScope.launch {
            preferencesRepository.setTimetableFontScale(scale)
        }
    }

    fun setCourseReminderEnabled(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.setCourseReminderEnabled(enabled)
            liveUpdateCoordinator.syncAll(rescheduleAlarms = true)
        }
    }

    fun setCourseReminderLeadMinutes(minutes: Int) {
        viewModelScope.launch {
            val safe = minutes.coerceIn(0, 120)
            preferencesRepository.setCourseReminderLeadMinutes(safe)
            liveUpdateCoordinator.syncAll(rescheduleAlarms = true)
            emitMessage("上课提前提醒已设为 $safe 分钟")
        }
    }

    fun setDndLevel(level: DndLevel) {
        viewModelScope.launch {
            preferencesRepository.setDndLevel(level)
            liveUpdateCoordinator.syncAll(rescheduleAlarms = false)
            emitMessage("免打扰级别已设为「${level.label}」")
        }
    }

    fun setFluidCloudDisplayOptions(
        showLocation: Boolean,
        showTeacher: Boolean,
        showNotes: Boolean,
    ) {
        viewModelScope.launch {
            preferencesRepository.setFluidCloudDisplayOptions(
                showLocation = showLocation,
                showTeacher = showTeacher,
                showNotes = showNotes,
            )
            liveUpdateCoordinator.syncAll(rescheduleAlarms = false)
        }
    }

    fun setFluidCloudLockscreenDetails(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.setFluidCloudLockscreenDetails(enabled)
            liveUpdateCoordinator.syncAll(rescheduleAlarms = false)
        }
    }

    // --- Import / Export / Share ---
    fun importCsvText(rawText: String, replaceExisting: Boolean) {
        viewModelScope.launch {
            val active = timetableRepository.ensureDefaultTimetableExists()
            val sectionTimes = timetableRepository.getActiveSectionTimes()
            val parsed = TimetableImportExport.parseCsvOrExcelText(rawText, active.id, sectionTimes)
            if (parsed.isEmpty()) {
                emitMessage("未识别到有效课程行，请检查 CSV/制表符格式")
                return@launch
            }
            val count = timetableRepository.importCoursesIntoActive(parsed, replaceExisting)
            val weekHint = syncImportedVisibility(parsed)
            emitMessage("成功导入 $count 门课程$weekHint")
        }
    }

    fun importHtmlText(htmlText: String, replaceExisting: Boolean) {
        viewModelScope.launch {
            val active = timetableRepository.ensureDefaultTimetableExists()
            val sectionTimes = timetableRepository.getActiveSectionTimes()
            val parsed = TimetableImportExport.parseHtmlTimetable(htmlText, active.id, sectionTimes)
            if (parsed.isEmpty()) {
                emitMessage("未从 HTML 表格中解析到课程，请粘贴完整课表表格源码")
                return@launch
            }
            val count = timetableRepository.importCoursesIntoActive(parsed, replaceExisting)
            val weekHint = syncImportedVisibility(parsed)
            emitMessage("成功从 HTML 导入 $count 门课程$weekHint")
        }
    }

    fun importShareCode(code: String, createNewTimetable: Boolean) {
        viewModelScope.launch {
            val active = timetableRepository.ensureDefaultTimetableExists()
            val parsed = TimetableImportExport.parseShareCode(code, active.id)
            if (parsed == null || parsed.courses.isEmpty()) {
                emitMessage("分享口令无效或课程为空，请检查是否复制完整")
                return@launch
            }
            if (createNewTimetable) {
                val newName = parsed.timetableName ?: "分享导入课表"
                val newId = timetableRepository.createTimetable(
                    name = newName,
                    totalWeeks = parsed.totalWeeks ?: 20,
                    activate = true,
                )
                timetableRepository.importCoursesIntoActive(
                    courses = parsed.courses.map { it.copy(timetableId = newId) },
                    replaceExisting = true,
                    sectionTimes = parsed.sectionTimes.map { it.copy(timetableId = newId) },
                )
            } else {
                timetableRepository.importCoursesIntoActive(
                    courses = parsed.courses,
                    replaceExisting = false,
                    sectionTimes = parsed.sectionTimes,
                )
            }
            val weekHint = syncImportedVisibility(parsed.courses)
            emitMessage("成功通过口令导入 ${parsed.courses.size} 门课程$weekHint")
        }
    }

    fun buildCurrentShareCode(): String {
        val state = uiState.value
        val tt = state.activeTimetable ?: TimetableEntity(
            name = "我的课表",
            semesterStartMondayMillis = DateTimeUtils.mondayOfWeekMillis(System.currentTimeMillis()),
        )
        return TimetableImportExport.generateShareCode(tt, state.courses, state.sectionTimes)
    }

    fun buildCurrentShareText(): String {
        val state = uiState.value
        val tt = state.activeTimetable ?: TimetableEntity(
            name = "我的课表",
            semesterStartMondayMillis = DateTimeUtils.mondayOfWeekMillis(System.currentTimeMillis()),
        )
        val summary = TimetableImportExport.buildShareSummaryText(tt, state.courses)
        val code = buildCurrentShareCode()
        return "$summary\n\n复制下方口令在「流云日程」课表页点击导入即可还原：\n$code"
    }

    suspend fun generateFullBackupJson(): String {
        return timetableRepository.buildFullBackupJson()
    }

    fun restoreFullBackupJson(
        jsonText: String,
        mode: BackupRestoreMode = BackupRestoreMode.SMART_MERGE,
    ) {
        viewModelScope.launch {
            val result = timetableRepository.restoreFullBackupWithMode(jsonText, mode)
            if (result == null) {
                emitMessage("JSON 备份格式无效，原有数据未作任何改动")
            } else {
                _selectedWeekOverride.value = null
                _selectedDateMillis.value = DateTimeUtils.startOfDayMillis(System.currentTimeMillis())
                val skippedPart = if (result.skippedDuplicates > 0) {
                    "（已自动跳过 ${result.skippedDuplicates} 条重复项）"
                } else {
                    ""
                }
                emitMessage(
                    "备份已恢复：导入 ${result.coursesImported} 门课程、${result.schedulesImported} 条日程$skippedPart",
                )
            }
        }
    }

    private suspend fun syncImportedVisibility(importedCourses: List<CourseEntity>): String {
        if (importedCourses.isEmpty()) return ""
        if (importedCourses.any { it.dayOfWeek >= 6 } && !uiState.value.settings.showWeekend) {
            preferencesRepository.setShowWeekend(true)
        }
        val currentDisplayedWeek = uiState.value.displayedWeek
        if (importedCourses.none { it.isInWeek(currentDisplayedWeek) }) {
            val targetWeek = importedCourses.minOf { it.startWeek }.coerceIn(1, CourseEntity.MAX_TOTAL_WEEKS)
            selectWeek(targetWeek)
            return "，已自动切换至第 $targetWeek 周"
        }
        return ""
    }

    fun consumeToast() {
        _toastMessage.value = null
    }

    private fun emitMessage(message: String) {
        _toastMessage.value = message
    }

    class Factory(
        private val timetableRepository: TimetableRepository,
        private val preferencesRepository: UserPreferencesRepository,
        private val liveUpdateCoordinator: LiveUpdateCoordinator,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return TimetableViewModel(
                timetableRepository = timetableRepository,
                preferencesRepository = preferencesRepository,
                liveUpdateCoordinator = liveUpdateCoordinator,
            ) as T
        }
    }
}
