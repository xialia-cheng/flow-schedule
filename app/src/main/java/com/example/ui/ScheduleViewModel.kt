package com.example.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.AppThemeMode
import com.example.data.CustomScheduleTag
import com.example.data.DateTimeUtils
import com.example.data.RepeatRule
import com.example.data.ScheduleCategory
import com.example.data.ScheduleEntity
import com.example.data.ScheduleRepository
import com.example.data.UserPreferencesRepository
import com.example.data.WeekStartDay
import com.example.notification.LiveActivityItem
import com.example.notification.LiveUpdateManager
import com.example.notification.LiveUpdatePermissionStatus
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Calendar

enum class ScheduleFilter(val label: String) {
    TODAY("今天"),
    TOMORROW("明天"),
    UPCOMING("未来"),
    ALL("全部"),
    SELECTED_DAY("按日"),
}

sealed class ScheduleTagFilter {
    data object All : ScheduleTagFilter()
    data class BuiltIn(val category: ScheduleCategory) : ScheduleTagFilter()
    data class Custom(val tagId: String) : ScheduleTagFilter()
}

data class ScheduleTimelineGroup(
    val key: String,
    val title: String,
    val isCompletedGroup: Boolean = false,
    val items: List<ScheduleEntity>,
)

data class ScheduleUiState(
    val nowMillis: Long = System.currentTimeMillis(),
    val selectedDateMillis: Long = DateTimeUtils.startOfDayMillis(System.currentTimeMillis()),
    val weekDatesMillis: List<Long> = DateTimeUtils.weekDatesContaining(System.currentTimeMillis()),
    val scheduledDateKeys: Set<String> = emptySet(),
    val allSchedules: List<ScheduleEntity> = emptyList(),
    val selectedFilter: ScheduleFilter = ScheduleFilter.TODAY,
    val selectedTagFilter: ScheduleTagFilter = ScheduleTagFilter.All,
    val filteredSchedules: List<ScheduleEntity> = emptyList(),
    val groupedSchedules: List<ScheduleTimelineGroup> = emptyList(),
    val todayTotalCount: Int = 0,
    val todayCompletedCount: Int = 0,
    val todayPendingCount: Int = 0,
    val heroSchedule: ScheduleEntity? = null,
    val isHeroLiveActive: Boolean = false,
    val hasActiveTestSchedule: Boolean = false,
    val themeMode: AppThemeMode = AppThemeMode.SYSTEM,
    val weekStartDay: WeekStartDay = WeekStartDay.MONDAY,
    val customTags: List<CustomScheduleTag> = emptyList(),
    val permissionStatus: LiveUpdatePermissionStatus = LiveUpdatePermissionStatus(
        hasPostNotifications = false,
        canPostPromotedNotifications = false,
        canScheduleExactAlarms = false,
        isAndroid16OrAbove = false,
    ),
    val activeLiveActivity: LiveActivityItem? = null,
    val showPermissionSheet: Boolean = false,
    val showEditorSheet: Boolean = false,
    val editingSchedule: ScheduleEntity? = null,
    val quickActionSchedule: ScheduleEntity? = null,
    val reReminderVersion: Int = 0,
) {
    val isDayScopedFilter: Boolean
        get() = selectedFilter == ScheduleFilter.TODAY ||
            selectedFilter == ScheduleFilter.TOMORROW ||
            selectedFilter == ScheduleFilter.SELECTED_DAY
}

data class SnackbarEvent(
    val id: Long = System.nanoTime(),
    val message: String,
    val canUndoDelete: Boolean = false,
)

class ScheduleViewModel(
    private val repository: ScheduleRepository,
    private val preferencesRepository: UserPreferencesRepository,
    private val liveUpdateManager: LiveUpdateManager,
) : ViewModel() {

    private val _selectedFilter = MutableStateFlow(ScheduleFilter.TODAY)
    private val _selectedTagFilter = MutableStateFlow<ScheduleTagFilter>(ScheduleTagFilter.All)
    private val _selectedDateMillis = MutableStateFlow(DateTimeUtils.startOfDayMillis(System.currentTimeMillis()))
    private val _nowMillis = MutableStateFlow(System.currentTimeMillis())
    private val _showPermissionSheet = MutableStateFlow(false)
    private val _showEditorSheet = MutableStateFlow(false)
    private val _editingSchedule = MutableStateFlow<ScheduleEntity?>(null)
    private val _quickActionSchedule = MutableStateFlow<ScheduleEntity?>(null)
    private val _reReminderVersion = MutableStateFlow(0)

    private val _snackbarEvent = MutableStateFlow<SnackbarEvent?>(null)
    val snackbarEvent: StateFlow<SnackbarEvent?> = _snackbarEvent

    val themeMode: StateFlow<AppThemeMode> = preferencesRepository.themeMode.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000L),
        initialValue = AppThemeMode.SYSTEM,
    )

    val useDynamicColor: StateFlow<Boolean> = preferencesRepository.useDynamicColor.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000L),
        initialValue = true,
    )

    private var lastDeletedSchedule: ScheduleEntity? = null
    private var lastNotificationSyncAt: Long = 0L
    private var lastLiveScheduleId: Long? = null

    private data class FilterCriteria(
        val filter: ScheduleFilter,
        val selectedDateMillis: Long,
        val tagFilter: ScheduleTagFilter,
    )

    private val filterCriteriaFlow = combine(
        _selectedFilter,
        _selectedDateMillis,
        _selectedTagFilter,
    ) { f, d, tf -> FilterCriteria(f, d, tf) }

    private val combinedDataFlow = combine(
        repository.allSchedules,
        _nowMillis,
        filterCriteriaFlow,
        liveUpdateManager.permissionStatus,
        preferencesRepository.settings,
    ) { allSchedules, nowMillis, criteria, permStatus, settings ->
        val filter = criteria.filter
        val selectedDateMillis = criteria.selectedDateMillis
        val effectiveTagFilter = when (val tf = criteria.tagFilter) {
            is ScheduleTagFilter.Custom -> {
                if (settings.customScheduleTags.any { it.id == tf.tagId }) tf else ScheduleTagFilter.All
            }
            else -> tf
        }

        val todaySchedules = allSchedules.filter {
            DateTimeUtils.isToday(it.startTimeMillis, nowMillis) || it.isInProgress(nowMillis)
        }
        val todayTotal = todaySchedules.size
        val todayCompleted = todaySchedules.count { it.isCompleted }
        val todayPending = (todayTotal - todayCompleted).coerceAtLeast(0)

        val incomplete = allSchedules.filter { !it.isCompleted }
        val inProgress = incomplete
            .filter { it.isInProgress(nowMillis) }
            .sortedWith(compareByDescending<ScheduleEntity> { it.isTestSchedule }.thenBy { it.endTimeMillis })
            .firstOrNull()
        val imminent = incomplete
            .filter { it.isInReminderWindow(nowMillis) }
            .minByOrNull { it.startTimeMillis }
        val nextToday = incomplete
            .filter { it.startTimeMillis > nowMillis && DateTimeUtils.isToday(it.startTimeMillis, nowMillis) }
            .minByOrNull { it.startTimeMillis }

        val hero = inProgress ?: imminent ?: nextToday
        val isHeroLive = hero != null && (hero.isInProgress(nowMillis) || hero.isInReminderWindow(nowMillis))
        val hasActiveTest = incomplete.any { it.isTestSchedule && !it.isExpired(nowMillis) }

        val startOfTomorrow = DateTimeUtils.addDays(nowMillis, 1)
        val dateScoped = when (filter) {
            ScheduleFilter.TODAY -> todaySchedules
            ScheduleFilter.TOMORROW -> allSchedules.filter {
                DateTimeUtils.isTomorrow(it.startTimeMillis, nowMillis)
            }
            ScheduleFilter.SELECTED_DAY -> allSchedules.filter {
                DateTimeUtils.isSameDay(it.startTimeMillis, selectedDateMillis) ||
                    (DateTimeUtils.isToday(selectedDateMillis, nowMillis) && it.isInProgress(nowMillis))
            }
            ScheduleFilter.UPCOMING -> allSchedules.filter {
                it.startTimeMillis >= startOfTomorrow || (!it.isCompleted && it.endTimeMillis > nowMillis)
            }
            ScheduleFilter.ALL -> allSchedules
        }

        val filtered = dateScoped
            .filter { item ->
                when (effectiveTagFilter) {
                    ScheduleTagFilter.All -> true
                    is ScheduleTagFilter.BuiltIn ->
                        item.customTagId.isBlank() && item.category == effectiveTagFilter.category
                    is ScheduleTagFilter.Custom ->
                        item.customTagId == effectiveTagFilter.tagId
                }
            }
            .sortedWith(
                compareBy<ScheduleEntity> { it.isCompleted }
                    .thenBy { it.startTimeMillis },
            )

        val grouped = buildTimelineGroups(filtered, filter, nowMillis)
        val scheduledDateKeys = allSchedules
            .asSequence()
            .filter { !it.isCompleted }
            .map { DateTimeUtils.formatDateKey(it.startTimeMillis) }
            .toSet()

        ScheduleUiState(
            nowMillis = nowMillis,
            selectedDateMillis = selectedDateMillis,
            weekDatesMillis = DateTimeUtils.weekDatesContaining(selectedDateMillis, settings.weekStartDay),
            scheduledDateKeys = scheduledDateKeys,
            allSchedules = allSchedules,
            selectedFilter = filter,
            selectedTagFilter = effectiveTagFilter,
            filteredSchedules = filtered,
            groupedSchedules = grouped,
            todayTotalCount = todayTotal,
            todayCompletedCount = todayCompleted,
            todayPendingCount = todayPending,
            heroSchedule = hero,
            isHeroLiveActive = isHeroLive,
            hasActiveTestSchedule = hasActiveTest,
            themeMode = settings.themeMode,
            weekStartDay = settings.weekStartDay,
            customTags = settings.customScheduleTags,
            permissionStatus = permStatus,
        )
    }

    private val modalOverlayFlow = combine(
        _showPermissionSheet,
        _showEditorSheet,
        _editingSchedule,
        _quickActionSchedule,
        _reReminderVersion,
    ) { showPerm, showEditor, editing, quickAction, reReminderVer ->
        ModalOverlayState(showPerm, showEditor, editing, quickAction, reReminderVer)
    }

    private data class ModalOverlayState(
        val showPerm: Boolean,
        val showEditor: Boolean,
        val editing: ScheduleEntity?,
        val quickAction: ScheduleEntity?,
        val reReminderVersion: Int,
    )

    val uiState: StateFlow<ScheduleUiState> = combine(
        combinedDataFlow,
        liveUpdateManager.activeLiveActivity,
        modalOverlayFlow,
    ) { base, activeLive, modal ->
        val syncedQuickAction = modal.quickAction?.let { target ->
            base.allSchedules.firstOrNull { it.id == target.id } ?: target
        }
        base.copy(
            activeLiveActivity = activeLive,
            showPermissionSheet = modal.showPerm,
            showEditorSheet = modal.showEditor,
            editingSchedule = modal.editing,
            quickActionSchedule = syncedQuickAction,
            reReminderVersion = modal.reReminderVersion,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000L),
        initialValue = ScheduleUiState(
            permissionStatus = liveUpdateManager.refreshPermissionStatus(),
            activeLiveActivity = liveUpdateManager.activeLiveActivity.value,
        ),
    )

    init {
        checkFirstLaunchOnboarding()
        startForegroundLiveClock()
    }

    private fun checkFirstLaunchOnboarding() {
        viewModelScope.launch {
            val seen = preferencesRepository.hasSeenOnboarding.first()
            val status = liveUpdateManager.refreshPermissionStatus()
            if (!seen && !status.allRecommendedGranted) {
                _showPermissionSheet.value = true
            }
            repository.syncAllLiveUpdates(rescheduleAlarms = true)
        }
    }

    private var lastLivePhaseKey: String? = null

    private fun startForegroundLiveClock() {
        viewModelScope.launch {
            while (isActive) {
                val now = System.currentTimeMillis()
                _nowMillis.value = now

                val state = uiState.value
                val activeLive = state.activeLiveActivity
                val hero = state.heroSchedule

                val livePhaseKey = when {
                    activeLive != null && activeLive.isInProgress(now) -> "${activeLive.uniqueKey}|ACTIVE"
                    activeLive != null && activeLive.isInReminderWindow(now) -> "${activeLive.uniqueKey}|PREP"
                    hero != null && hero.isInProgress(now) -> "HERO_${hero.id}|ACTIVE"
                    hero != null && hero.isInReminderWindow(now) -> "HERO_${hero.id}|PREP"
                    else -> null
                }

                val purgedExpired = liveUpdateManager.purgeExpiredNotificationIfNeeded(now)
                val transitionOccurred = livePhaseKey != lastLivePhaseKey
                val activeExpired = activeLive != null && now >= activeLive.endTimeMillis
                val expiredHero = hero != null && hero.isExpired(now)

                // Never poll notify() every second: SystemUI Chronometer ticks natively every second.
                // Only sync on actual phase transitions (prep -> active, expiry, or new eligible item).
                if (purgedExpired || transitionOccurred || activeExpired || expiredHero) {
                    lastLivePhaseKey = livePhaseKey
                    lastLiveScheduleId = if (state.isHeroLiveActive) hero?.id else null
                    lastNotificationSyncAt = now
                    repository.syncAllLiveUpdates(nowMillis = now, forceAlert = false)
                }

                delay(1_000L)
            }
        }
    }

    private fun buildTimelineGroups(
        schedules: List<ScheduleEntity>,
        filter: ScheduleFilter,
        nowMillis: Long,
    ): List<ScheduleTimelineGroup> {
        if (schedules.isEmpty()) return emptyList()

        val activeItems = schedules.filter { !it.isCompleted }
        val completedItems = schedules.filter { it.isCompleted }
        val result = mutableListOf<ScheduleTimelineGroup>()

        if (filter == ScheduleFilter.TODAY ||
            filter == ScheduleFilter.TOMORROW ||
            filter == ScheduleFilter.SELECTED_DAY
        ) {
            val buckets = linkedMapOf<String, MutableList<ScheduleEntity>>()
            for (item in activeItems) {
                val cal = Calendar.getInstance().apply { timeInMillis = item.startTimeMillis }
                val hour = cal.get(Calendar.HOUR_OF_DAY)
                val bucketKey = when {
                    hour < 12 -> "morning"
                    hour < 18 -> "afternoon"
                    else -> "evening"
                }
                buckets.getOrPut(bucketKey) { mutableListOf() }.add(item)
            }
            val labels = mapOf(
                "morning" to "上午时段",
                "afternoon" to "下午时段",
                "evening" to "晚间时段",
            )
            buckets.forEach { (key, list) ->
                result.add(
                    ScheduleTimelineGroup(
                        key = "period_$key",
                        title = labels[key] ?: key,
                        items = list,
                    ),
                )
            }
        } else {
            val byDay = linkedMapOf<Long, MutableList<ScheduleEntity>>()
            for (item in activeItems) {
                val dayStart = DateTimeUtils.startOfDayMillis(item.startTimeMillis)
                byDay.getOrPut(dayStart) { mutableListOf() }.add(item)
            }
            byDay.forEach { (dayStart, list) ->
                val dayLabel = when {
                    DateTimeUtils.isToday(dayStart, nowMillis) -> "今天 · ${DateTimeUtils.formatDateShort(dayStart)}"
                    DateTimeUtils.isTomorrow(dayStart, nowMillis) -> "明天 · ${DateTimeUtils.formatDateShort(dayStart)}"
                    else -> DateTimeUtils.formatDateShort(dayStart)
                }
                result.add(
                    ScheduleTimelineGroup(
                        key = "day_$dayStart",
                        title = dayLabel,
                        items = list,
                    ),
                )
            }
        }

        if (completedItems.isNotEmpty()) {
            result.add(
                ScheduleTimelineGroup(
                    key = "completed_group",
                    title = "已完成",
                    isCompletedGroup = true,
                    items = completedItems,
                ),
            )
        }

        return result
    }

    fun cycleThemeMode() {
        viewModelScope.launch {
            val nextMode = themeMode.value.next()
            preferencesRepository.setThemeMode(nextMode)
            emitSnackbar("外观已切换为「${nextMode.label}」")
        }
    }

    fun setThemeMode(mode: AppThemeMode) {
        viewModelScope.launch {
            preferencesRepository.setThemeMode(mode)
        }
    }

    fun onResumeRefresh() {
        val now = System.currentTimeMillis()
        _nowMillis.value = now
        liveUpdateManager.purgeExpiredNotificationIfNeeded(now)
        liveUpdateManager.refreshPermissionStatus()
        viewModelScope.launch {
            repository.syncAllLiveUpdates(nowMillis = now, rescheduleAlarms = true)
        }
    }

    fun selectFilter(filter: ScheduleFilter) {
        _selectedFilter.value = filter
        when (filter) {
            ScheduleFilter.TODAY -> {
                _selectedDateMillis.value = DateTimeUtils.startOfDayMillis(_nowMillis.value)
            }
            ScheduleFilter.TOMORROW -> {
                _selectedDateMillis.value = DateTimeUtils.addDays(_nowMillis.value, 1)
            }
            else -> Unit
        }
    }

    fun selectTagFilter(tagFilter: ScheduleTagFilter) {
        _selectedTagFilter.value = if (_selectedTagFilter.value == tagFilter && tagFilter != ScheduleTagFilter.All) {
            ScheduleTagFilter.All
        } else {
            tagFilter
        }
    }

    fun resetTagFilter() {
        _selectedTagFilter.value = ScheduleTagFilter.All
    }

    fun selectDate(dateMillis: Long) {
        val normalized = DateTimeUtils.startOfDayMillis(dateMillis)
        _selectedDateMillis.value = normalized
        val now = _nowMillis.value
        _selectedFilter.value = when {
            DateTimeUtils.isToday(normalized, now) -> ScheduleFilter.TODAY
            DateTimeUtils.isTomorrow(normalized, now) -> ScheduleFilter.TOMORROW
            else -> ScheduleFilter.SELECTED_DAY
        }
    }

    fun shiftSelectedDate(deltaDays: Int) {
        selectDate(DateTimeUtils.addDays(_selectedDateMillis.value, deltaDays))
    }

    fun jumpToToday() {
        selectDate(DateTimeUtils.startOfDayMillis(System.currentTimeMillis()))
    }

    fun openPermissionSheet() {
        liveUpdateManager.refreshPermissionStatus()
        _showPermissionSheet.value = true
    }

    fun dismissPermissionSheet() {
        _showPermissionSheet.value = false
        viewModelScope.launch {
            preferencesRepository.markOnboardingSeen()
        }
    }

    fun openNewScheduleEditor() {
        _editingSchedule.value = null
        _showEditorSheet.value = true
    }

    fun openEditScheduleEditor(schedule: ScheduleEntity) {
        _editingSchedule.value = schedule
        _showEditorSheet.value = true
    }

    fun dismissEditorSheet() {
        _showEditorSheet.value = false
        _editingSchedule.value = null
    }

    fun openQuickActionMenu(schedule: ScheduleEntity) {
        _quickActionSchedule.value = schedule
    }

    fun dismissQuickActionMenu() {
        _quickActionSchedule.value = null
    }

    fun saveSchedule(
        existingId: Long,
        title: String,
        notes: String,
        startTimeMillis: Long,
        endTimeMillis: Long,
        repeatRule: RepeatRule,
        reminderMinutesBefore: Int,
        category: ScheduleCategory,
        customTagId: String = "",
        customTagName: String = "",
        customTagColorHex: String = "",
    ) {
        val trimmedTitle = title.trim()
        if (trimmedTitle.isEmpty()) return

        viewModelScope.launch {
            val entity = ScheduleEntity(
                id = existingId,
                title = trimmedTitle,
                notes = notes.trim(),
                startTimeMillis = startTimeMillis,
                endTimeMillis = endTimeMillis.coerceAtLeast(startTimeMillis + 60_000L),
                repeatRule = repeatRule,
                reminderMinutesBefore = reminderMinutesBefore,
                category = category,
                customTagId = customTagId.trim(),
                customTagName = customTagName.trim(),
                customTagColorHex = customTagColorHex.trim(),
                isCompleted = false,
                isTestSchedule = false,
                dismissedLiveUpdate = false,
            )
            repository.saveSchedule(entity)
            _showEditorSheet.value = false
            _editingSchedule.value = null
            emitSnackbar(if (existingId == 0L) "已添加日程「$trimmedTitle」" else "已更新日程「$trimmedTitle」")
        }
    }

    fun addCustomTag(name: String, colorHex: String, iconKey: String = "label") {
        viewModelScope.launch {
            val created = preferencesRepository.addCustomScheduleTag(name, colorHex, iconKey)
            if (created != null) {
                emitSnackbar("已添加自定义标签「${created.name}」")
            }
        }
    }

    fun updateCustomTag(id: String, name: String, colorHex: String, iconKey: String = "label") {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            preferencesRepository.updateCustomScheduleTag(id, trimmed, colorHex, iconKey)
            repository.updateCustomTagMetadata(id, trimmed, colorHex)
            emitSnackbar("已更新标签「$trimmed」")
        }
    }

    fun deleteCustomTag(id: String) {
        viewModelScope.launch {
            if (_selectedTagFilter.value == ScheduleTagFilter.Custom(id)) {
                _selectedTagFilter.value = ScheduleTagFilter.All
            }
            preferencesRepository.deleteCustomScheduleTag(id)
            repository.clearDeletedCustomTag(id)
            emitSnackbar("已删除自定义标签")
        }
    }

    fun toggleComplete(schedule: ScheduleEntity) {
        viewModelScope.launch {
            val nextCompleted = !schedule.isCompleted
            repository.toggleComplete(schedule.id, nextCompleted)
            if (nextCompleted) {
                val repeatHint = if (schedule.repeatRule != RepeatRule.NONE) "，已安排下次重复日程" else ""
                emitSnackbar("已完成「${schedule.title}」$repeatHint")
            } else {
                emitSnackbar("已重新打开「${schedule.title}」")
            }
        }
    }

    fun snoozeSchedule10Minutes(schedule: ScheduleEntity) {
        viewModelScope.launch {
            repository.snoozeScheduleBy10Minutes(schedule.id)
            emitSnackbar("「${schedule.title}」已延后 10 分钟")
        }
    }

    fun moveScheduleToDate(schedule: ScheduleEntity, targetDateMillis: Long) {
        viewModelScope.launch {
            val updated = shiftScheduleToDate(schedule, targetDateMillis)
            repository.updateScheduleQuick(updated)
            _quickActionSchedule.value = null
            emitSnackbar("已将「${schedule.title}」移至 ${DateTimeUtils.formatDateShort(updated.startTimeMillis)}")
        }
    }

    fun updateScheduleTag(
        schedule: ScheduleEntity,
        category: ScheduleCategory,
        customTag: CustomScheduleTag? = null,
    ) {
        viewModelScope.launch {
            val updated = applyScheduleTag(schedule, category, customTag)
            repository.updateScheduleQuick(updated)
            _quickActionSchedule.value = null
            val tagLabel = customTag?.name ?: category.label
            emitSnackbar("已将「${schedule.title}」标签改为「$tagLabel」")
        }
    }

    fun updateScheduleReminderLead(schedule: ScheduleEntity, leadMinutes: Int) {
        viewModelScope.launch {
            val safeMinutes = leadMinutes.coerceIn(0, 180)
            repository.updateScheduleQuick(schedule.copy(reminderMinutesBefore = safeMinutes))
            emitSnackbar(
                if (safeMinutes == 0) {
                    "已关闭「${schedule.title}」提前提醒"
                } else {
                    "已设为提前 $safeMinutes 分钟提醒「${schedule.title}」"
                },
            )
        }
    }

    fun scheduleItemReReminder(schedule: ScheduleEntity, delayMinutes: Int) {
        val safeMinutes = delayMinutes.coerceIn(1, 720)
        val triggerAt = repository.scheduleCustomReReminder(schedule, safeMinutes)
        _reReminderVersion.value += 1
        _quickActionSchedule.value = null
        emitSnackbar("已设 ${DateTimeUtils.formatTime(triggerAt)}「${schedule.title}」重提醒闹钟")
    }

    fun cancelItemReReminder(schedule: ScheduleEntity) {
        repository.cancelCustomReReminder(schedule.id)
        _reReminderVersion.value += 1
        emitSnackbar("已取消「${schedule.title}」重提醒")
    }

    fun getScheduleReReminderMillis(scheduleId: Long): Long? {
        return repository.getCustomReReminderMillis(scheduleId)
    }

    fun deleteSchedule(schedule: ScheduleEntity) {
        viewModelScope.launch {
            lastDeletedSchedule = schedule
            repository.deleteSchedule(schedule)
            emitSnackbar("已删除「${schedule.title}」", canUndoDelete = true)
        }
    }

    fun undoDelete() {
        val toRestore = lastDeletedSchedule ?: return
        lastDeletedSchedule = null
        viewModelScope.launch {
            repository.restoreSchedule(toRestore)
            emitSnackbar("已恢复「${toRestore.title}」")
        }
    }

    fun triggerFluidCloudTest(title: String, notes: String) {
        viewModelScope.launch {
            val status = liveUpdateManager.refreshPermissionStatus()
            repository.createTestFluidCloudSchedule(title = title, notes = notes)
            _selectedFilter.value = ScheduleFilter.TODAY
            if (!status.hasPostNotifications) {
                emitSnackbar("临近课程实况通知演示已创建，请先开启通知权限以查看状态栏实况胶囊或实时进度通知")
                _showPermissionSheet.value = true
            } else if (status.isAndroid16OrAbove && !status.canPostPromotedNotifications) {
                emitSnackbar("已推送临近课程通知，前往权限页开启「实时活动」即可晋升至状态栏实况胶囊")
            } else {
                emitSnackbar("已触发临近课程实况通知（15秒课前倒计时 → 自动切入上课），可切换应用查看状态栏实况胶囊或实时进度通知")
            }
        }
    }

    fun stopFluidCloudTest() {
        viewModelScope.launch {
            repository.stopTestFluidCloudSchedules()
            emitSnackbar("已停止实况通知测试并恢复当前课程/日程状态")
        }
    }

    fun consumeSnackbar() {
        _snackbarEvent.value = null
    }

    private fun emitSnackbar(message: String, canUndoDelete: Boolean = false) {
        _snackbarEvent.value = SnackbarEvent(message = message, canUndoDelete = canUndoDelete)
    }

    class Factory(
        private val repository: ScheduleRepository,
        private val preferencesRepository: UserPreferencesRepository,
        private val liveUpdateManager: LiveUpdateManager,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return ScheduleViewModel(
                repository = repository,
                preferencesRepository = preferencesRepository,
                liveUpdateManager = liveUpdateManager,
            ) as T
        }
    }

    companion object {
        fun shiftScheduleToDate(schedule: ScheduleEntity, targetDateMillis: Long): ScheduleEntity {
            val (hour, minute) = DateTimeUtils.extractHourMinute(schedule.startTimeMillis)
            val duration = (schedule.endTimeMillis - schedule.startTimeMillis).coerceAtLeast(60_000L)
            val newStart = DateTimeUtils.combineDateAndTime(targetDateMillis, hour, minute)
            return schedule.copy(
                startTimeMillis = newStart,
                endTimeMillis = newStart + duration,
                dismissedLiveUpdate = false,
            )
        }

        fun applyScheduleTag(
            schedule: ScheduleEntity,
            category: ScheduleCategory,
            customTag: CustomScheduleTag?,
        ): ScheduleEntity {
            return if (customTag != null) {
                schedule.copy(
                    customTagId = customTag.id,
                    customTagName = customTag.name,
                    customTagColorHex = customTag.colorHex,
                    dismissedLiveUpdate = false,
                )
            } else {
                schedule.copy(
                    category = category,
                    customTagId = "",
                    customTagName = "",
                    customTagColorHex = "",
                    dismissedLiveUpdate = false,
                )
            }
        }
    }
}
