package com.example.ui.study

import android.graphics.Color as AndroidColor
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.CourseEntity
import com.example.data.DndLevel
import com.example.data.StudyTaskEntity
import com.example.data.TimetableRepository
import com.example.data.UserPreferencesRepository
import com.example.notification.LiveUpdateCoordinator
import com.example.notification.LiveUpdateManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

enum class FocusPhase(val label: String) {
    FOCUS("专注时段"),
    BREAK("休息时段"),
}

data class StudyUiState(
    val tasks: List<StudyTaskEntity> = emptyList(),
    val courseNames: List<String> = emptyList(),
    val totalFocusMinutes: Int = 0,
    val selectedPresetMinutes: Int = 25,
    val breakDurationMinutes: Int = 5,
    val currentPhase: FocusPhase = FocusPhase.FOCUS,
    val remainingSeconds: Int = 25 * 60,
    val isTimerRunning: Boolean = false,
    val activeFocusLabel: String = "专注学习",
    val linkFluidCloud: Boolean = true,
    val linkDnd: Boolean = false,
    val dndLevel: DndLevel = DndLevel.PRIORITY,
    val hasDndPolicyAccess: Boolean = false,
) {
    val activePhaseTotalMinutes: Int
        get() = when (currentPhase) {
            FocusPhase.FOCUS -> selectedPresetMinutes.coerceAtLeast(1)
            FocusPhase.BREAK -> breakDurationMinutes.coerceAtLeast(1)
        }

    val timerProgress: Float
        get() {
            val total = (activePhaseTotalMinutes * 60).coerceAtLeast(1)
            return ((total - remainingSeconds).toFloat() / total.toFloat()).coerceIn(0f, 1f)
        }
}

class StudyViewModel(
    private val timetableRepository: TimetableRepository,
    private val preferencesRepository: UserPreferencesRepository,
    private val liveUpdateManager: LiveUpdateManager? = null,
    private val liveUpdateCoordinator: LiveUpdateCoordinator? = null,
) : ViewModel() {

    private val _presetMinutes = MutableStateFlow(25)
    private val _breakMinutes = MutableStateFlow(5)
    private val _phase = MutableStateFlow(FocusPhase.FOCUS)
    private val _remainingSeconds = MutableStateFlow(25 * 60)
    private val _isRunning = MutableStateFlow(false)
    private val _activeLabel = MutableStateFlow("专注学习")
    private val _linkFluidCloud = MutableStateFlow(true)
    private val _linkDnd = MutableStateFlow(false)
    private val _toastMessage = MutableStateFlow<String?>(null)
    val toastMessage: StateFlow<String?> = _toastMessage

    private var timerJob: Job? = null

    init {
        viewModelScope.launch {
            val saved = preferencesRepository.settings.first()
            _presetMinutes.value = saved.focusDurationMinutes.coerceIn(1, 180)
            _breakMinutes.value = saved.focusBreakMinutes.coerceIn(0, 60)
            _activeLabel.value = saved.focusCustomEventName.ifBlank { "专注学习" }
            _linkFluidCloud.value = saved.focusLinkFluidCloud
            _linkDnd.value = saved.focusLinkDnd
            if (!_isRunning.value) {
                _remainingSeconds.value = _presetMinutes.value * 60
            }
        }
    }

    private val timerCoreFlow = combine(
        _presetMinutes,
        _breakMinutes,
        _phase,
        _remainingSeconds,
        _isRunning,
    ) { focusMin, breakMin, phase, remaining, running ->
        TimerCore(focusMin, breakMin, phase, remaining, running)
    }

    private val timerOptionsFlow = combine(
        _activeLabel,
        _linkFluidCloud,
        _linkDnd,
    ) { label, linkCloud, linkDnd ->
        TimerOptions(label, linkCloud, linkDnd)
    }

    val uiState: StateFlow<StudyUiState> = combine(
        timetableRepository.studyTasks,
        timetableRepository.activeCourses,
        preferencesRepository.settings,
        timerCoreFlow,
        timerOptionsFlow,
    ) { tasks, courses, settings, core, options ->
        StudyUiState(
            tasks = tasks,
            courseNames = courses.map(CourseEntity::name).distinct(),
            totalFocusMinutes = settings.totalFocusMinutes,
            selectedPresetMinutes = core.focusMinutes,
            breakDurationMinutes = core.breakMinutes,
            currentPhase = core.phase,
            remainingSeconds = core.remainingSeconds,
            isTimerRunning = core.isRunning,
            activeFocusLabel = options.label,
            linkFluidCloud = options.linkFluidCloud,
            linkDnd = options.linkDnd,
            dndLevel = settings.dndLevel,
            hasDndPolicyAccess = liveUpdateManager?.hasDndPolicyAccess() ?: false,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000L),
        initialValue = StudyUiState(),
    )

    fun selectPresetMinutes(minutes: Int) {
        if (_isRunning.value) return
        val safe = minutes.coerceIn(1, 180)
        _presetMinutes.value = safe
        if (_phase.value == FocusPhase.FOCUS) {
            _remainingSeconds.value = safe * 60
        }
        persistFocusSettings()
    }

    fun setBreakMinutes(minutes: Int) {
        if (_isRunning.value) return
        val safe = minutes.coerceIn(0, 60)
        _breakMinutes.value = safe
        if (_phase.value == FocusPhase.BREAK) {
            if (safe == 0) {
                _phase.value = FocusPhase.FOCUS
                _remainingSeconds.value = _presetMinutes.value * 60
            } else {
                _remainingSeconds.value = safe * 60
            }
        }
        persistFocusSettings()
    }

    fun switchPhase(phase: FocusPhase) {
        if (_isRunning.value) return
        if (phase == FocusPhase.BREAK && _breakMinutes.value <= 0) {
            _breakMinutes.value = 5
        }
        _phase.value = phase
        _remainingSeconds.value = when (phase) {
            FocusPhase.FOCUS -> _presetMinutes.value * 60
            FocusPhase.BREAK -> _breakMinutes.value.coerceAtLeast(1) * 60
        }
        persistFocusSettings()
    }

    fun selectFocusSubject(label: String) {
        val clean = label.trim().ifBlank { "专注学习" }
        _activeLabel.value = clean
        persistFocusSettings()
    }

    fun setLinkFluidCloud(enabled: Boolean) {
        _linkFluidCloud.value = enabled
        persistFocusSettings()
        if (_isRunning.value) {
            if (enabled) {
                val now = System.currentTimeMillis()
                postFocusFluidCloud(now, now + _remainingSeconds.value * 1_000L)
            } else {
                clearFocusFluidCloudAndRestore()
            }
        }
    }

    fun setLinkDnd(enabled: Boolean) {
        _linkDnd.value = enabled
        persistFocusSettings()
        if (_isRunning.value && _phase.value == FocusPhase.FOCUS) {
            if (enabled) {
                val granted = liveUpdateManager?.enableDndForSession() ?: false
                if (!granted) {
                    _toastMessage.value = "请先在通知设置中授予「免打扰控制」权限"
                }
            } else {
                liveUpdateManager?.restoreDndIfEnabledByApp()
            }
        }
    }

    fun applyCustomFocusConfig(
        focusMinutes: Int,
        breakMinutes: Int,
        eventName: String,
        linkFluidCloud: Boolean,
        linkDnd: Boolean,
    ) {
        val safeFocus = focusMinutes.coerceIn(1, 180)
        val safeBreak = breakMinutes.coerceIn(0, 60)
        val safeName = eventName.trim().ifBlank { "专注学习" }
        _presetMinutes.value = safeFocus
        _breakMinutes.value = safeBreak
        _activeLabel.value = safeName
        _linkFluidCloud.value = linkFluidCloud
        _linkDnd.value = linkDnd
        if (!_isRunning.value) {
            if (_phase.value == FocusPhase.BREAK && safeBreak == 0) {
                _phase.value = FocusPhase.FOCUS
            }
            _remainingSeconds.value = when (_phase.value) {
                FocusPhase.FOCUS -> safeFocus * 60
                FocusPhase.BREAK -> safeBreak.coerceAtLeast(1) * 60
            }
        }
        persistFocusSettings()
        _toastMessage.value = "专注配置已更新：专注 $safeFocus 分钟 / 休息 $safeBreak 分钟"
    }

    fun toggleTimer() {
        if (_isRunning.value) {
            pauseTimer()
        } else {
            startTimer()
        }
    }

    private fun startTimer() {
        val currentPhase = _phase.value
        val defaultSecs = when (currentPhase) {
            FocusPhase.FOCUS -> _presetMinutes.value * 60
            FocusPhase.BREAK -> _breakMinutes.value.coerceAtLeast(1) * 60
        }
        if (_remainingSeconds.value <= 0) {
            _remainingSeconds.value = defaultSecs
        }
        _isRunning.value = true

        val startMillis = System.currentTimeMillis()
        val endMillis = startMillis + _remainingSeconds.value * 1_000L

        if (_linkFluidCloud.value) {
            postFocusFluidCloud(startMillis, endMillis)
        }
        if (_linkDnd.value && currentPhase == FocusPhase.FOCUS) {
            val enabled = liveUpdateManager?.enableDndForSession() ?: false
            if (!enabled) {
                _toastMessage.value = "已开始专注（尚未开启免打扰控制权限，可在设置页一键授权）"
            }
        }

        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            while (isActive && _remainingSeconds.value > 0) {
                delay(1_000L)
                _remainingSeconds.value = (_remainingSeconds.value - 1).coerceAtLeast(0)
            }
            if (_remainingSeconds.value == 0) {
                _isRunning.value = false
                clearFocusFluidCloudAndRestore()
                liveUpdateManager?.restoreDndIfEnabledByApp()

                if (currentPhase == FocusPhase.FOCUS) {
                    val completedMinutes = _presetMinutes.value
                    preferencesRepository.addFocusMinutes(completedMinutes)
                    if (_breakMinutes.value > 0) {
                        _phase.value = FocusPhase.BREAK
                        _remainingSeconds.value = _breakMinutes.value * 60
                        _toastMessage.value = "专注完成！已计入 $completedMinutes 分钟，可开始 ${_breakMinutes.value} 分钟休息"
                    } else {
                        _remainingSeconds.value = _presetMinutes.value * 60
                        _toastMessage.value = "专注完成！已累计计入 $completedMinutes 分钟"
                    }
                } else {
                    _phase.value = FocusPhase.FOCUS
                    _remainingSeconds.value = _presetMinutes.value * 60
                    _toastMessage.value = "休息结束，准备好开始下一轮专注"
                }
            }
        }
    }

    private fun pauseTimer() {
        timerJob?.cancel()
        _isRunning.value = false
        clearFocusFluidCloudAndRestore()
        liveUpdateManager?.restoreDndIfEnabledByApp()
    }

    fun resetTimer() {
        timerJob?.cancel()
        _isRunning.value = false
        clearFocusFluidCloudAndRestore()
        liveUpdateManager?.restoreDndIfEnabledByApp()
        _remainingSeconds.value = when (_phase.value) {
            FocusPhase.FOCUS -> _presetMinutes.value * 60
            FocusPhase.BREAK -> _breakMinutes.value.coerceAtLeast(1) * 60
        }
    }

    private fun postFocusFluidCloud(startMillis: Long, endMillis: Long) {
        val isBreak = _phase.value == FocusPhase.BREAK
        val accent = if (isBreak) {
            AndroidColor.rgb(16, 185, 129) // Mint Green for break
        } else {
            AndroidColor.rgb(124, 58, 237) // Focus Violet
        }
        val title = if (isBreak) "${_activeLabel.value} · 休息" else _activeLabel.value
        liveUpdateManager?.postFocusLiveActivity(
            title = title,
            isBreak = isBreak,
            startTimeMillis = startMillis,
            endTimeMillis = endMillis,
            accentColorArgb = accent,
            nowMillis = startMillis,
        )
    }

    private fun clearFocusFluidCloudAndRestore() {
        liveUpdateManager?.clearFocusLiveActivity()
        viewModelScope.launch {
            liveUpdateCoordinator?.syncAll(rescheduleAlarms = false)
        }
    }

    private fun persistFocusSettings() {
        viewModelScope.launch {
            preferencesRepository.saveFocusSettings(
                focusMinutes = _presetMinutes.value,
                breakMinutes = _breakMinutes.value,
                eventName = _activeLabel.value,
                linkFluidCloud = _linkFluidCloud.value,
                linkDnd = _linkDnd.value,
            )
        }
    }

    fun consumeToast() {
        _toastMessage.value = null
    }

    fun addTask(title: String, courseTag: String, targetMinutes: Int) {
        viewModelScope.launch {
            timetableRepository.addStudyTask(title, courseTag, targetMinutes)
        }
    }

    fun toggleTask(task: StudyTaskEntity) {
        viewModelScope.launch {
            timetableRepository.toggleStudyTask(task)
        }
    }

    fun deleteTask(task: StudyTaskEntity) {
        viewModelScope.launch {
            timetableRepository.deleteStudyTask(task.id)
        }
    }

    override fun onCleared() {
        super.onCleared()
        timerJob?.cancel()
        if (_isRunning.value) {
            liveUpdateManager?.clearFocusLiveActivity()
            liveUpdateManager?.restoreDndIfEnabledByApp()
        }
    }

    private data class TimerCore(
        val focusMinutes: Int,
        val breakMinutes: Int,
        val phase: FocusPhase,
        val remainingSeconds: Int,
        val isRunning: Boolean,
    )

    private data class TimerOptions(
        val label: String,
        val linkFluidCloud: Boolean,
        val linkDnd: Boolean,
    )

    class Factory(
        private val timetableRepository: TimetableRepository,
        private val preferencesRepository: UserPreferencesRepository,
        private val liveUpdateManager: LiveUpdateManager? = null,
        private val liveUpdateCoordinator: LiveUpdateCoordinator? = null,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return StudyViewModel(
                timetableRepository = timetableRepository,
                preferencesRepository = preferencesRepository,
                liveUpdateManager = liveUpdateManager,
                liveUpdateCoordinator = liveUpdateCoordinator,
            ) as T
        }
    }
}
