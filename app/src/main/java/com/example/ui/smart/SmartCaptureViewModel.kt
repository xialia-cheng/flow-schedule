package com.example.ui.smart

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.CustomAiConfig
import com.example.data.UserPreferencesRepository
import com.example.smart.OpenAiCompatibleClient
import com.example.smart.ParseSource
import com.example.smart.SecureStorageException
import com.example.smart.SmartCaptureOrchestrator
import com.example.smart.SmartParsedDraft
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SmartCaptureUiState(
    val inputText: String = "",
    val isParsing: Boolean = false,
    val isSaving: Boolean = false,
    val showInputSheet: Boolean = false,
    val showPreviewSheet: Boolean = false,
    val parseSource: ParseSource = ParseSource.OFFLINE_LOCAL,
    val parseNotice: String? = null,
    val drafts: List<SmartParsedDraft> = emptyList(),
    val customAiConfig: CustomAiConfig = CustomAiConfig(),
    val isTestingAi: Boolean = false,
    val aiTestFeedback: String? = null,
) {
    val totalConflictCount: Int
        get() = drafts.sumOf { it.conflicts.size }
}

class SmartCaptureViewModel(
    private val orchestrator: SmartCaptureOrchestrator,
    private val preferencesRepository: UserPreferencesRepository,
) : ViewModel() {

    private val _inputText = MutableStateFlow("")
    private val _isParsing = MutableStateFlow(false)
    private val _isSaving = MutableStateFlow(false)
    private val _showInputSheet = MutableStateFlow(false)
    private val _showPreview = MutableStateFlow(false)
    private val _parseSource = MutableStateFlow(ParseSource.OFFLINE_LOCAL)
    private val _parseNotice = MutableStateFlow<String?>(null)
    private val _drafts = MutableStateFlow<List<SmartParsedDraft>>(emptyList())
    private val _isTestingAi = MutableStateFlow(false)
    private val _aiTestFeedback = MutableStateFlow<String?>(null)

    private val draftFlow = combine(
        _showInputSheet,
        _showPreview,
        _parseSource,
        _parseNotice,
        _drafts,
    ) { showInput, showPreview, source, notice, drafts ->
        DraftOverlayState(showInput, showPreview, source, notice, drafts)
    }

    private val aiTestFlow = combine(
        preferencesRepository.customAiConfig,
        _isTestingAi,
        _aiTestFeedback,
    ) { config, testing, feedback ->
        AiSettingsState(config, testing, feedback)
    }

    val uiState: StateFlow<SmartCaptureUiState> = combine(
        _inputText,
        _isParsing,
        _isSaving,
        draftFlow,
        aiTestFlow,
    ) { input, parsing, saving, overlay, ai ->
        SmartCaptureUiState(
            inputText = input,
            isParsing = parsing,
            isSaving = saving,
            showInputSheet = overlay.showInputSheet,
            showPreviewSheet = overlay.showPreview,
            parseSource = overlay.source,
            parseNotice = overlay.notice,
            drafts = overlay.drafts,
            customAiConfig = ai.config,
            isTestingAi = ai.isTesting,
            aiTestFeedback = ai.feedback,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000L),
        initialValue = SmartCaptureUiState(),
    )

    fun openInputSheet() {
        _showInputSheet.value = true
    }

    fun dismissInputSheet() {
        _showInputSheet.value = false
    }

    fun updateInput(text: String) {
        _inputText.value = text
    }

    fun submitQuickInput(onEmptyOrError: (String) -> Unit) {
        val trimmed = _inputText.value.trim()
        if (trimmed.isEmpty()) {
            onEmptyOrError("请输入一句话，例如：明天早八在教A-301上高数 提前15分钟 免打扰")
            return
        }
        if (_isParsing.value) return

        viewModelScope.launch {
            _isParsing.value = true
            try {
                val result = orchestrator.parseAndCheckConflicts(trimmed)
                if (result.drafts.isEmpty()) {
                    onEmptyOrError("未能识别有效日程或课程，请补充时间或事件名称")
                } else {
                    _parseSource.value = result.source
                    _parseNotice.value = result.notice
                    _drafts.value = result.drafts
                    _showInputSheet.value = false
                    _showPreview.value = true
                }
            } finally {
                _isParsing.value = false
            }
        }
    }

    fun updateDraft(updated: SmartParsedDraft) {
        val currentList = _drafts.value.map { if (it.id == updated.id) updated else it }
        _drafts.value = currentList
        viewModelScope.launch {
            _drafts.value = orchestrator.refreshConflicts(currentList)
        }
    }

    fun removeDraft(draftId: String) {
        val remaining = _drafts.value.filterNot { it.id == draftId }
        _drafts.value = remaining
        if (remaining.isEmpty()) {
            _showPreview.value = false
        }
    }

    fun dismissPreview() {
        _showPreview.value = false
    }

    fun confirmAndCommit(onResult: (String) -> Unit) {
        val toSave = _drafts.value
        if (toSave.isEmpty() || _isSaving.value) return

        viewModelScope.launch {
            _isSaving.value = true
            try {
                val (schedulesAdded, coursesAdded) = orchestrator.commitDrafts(toSave)
                _showPreview.value = false
                _drafts.value = emptyList()
                _inputText.value = ""

                val parts = buildList {
                    if (coursesAdded > 0) add("$coursesAdded 门课程")
                    if (schedulesAdded > 0) add("$schedulesAdded 条日程")
                }
                val summary = if (parts.isNotEmpty()) {
                    "已创建 ${parts.joinToString("、")} 并注册精确闹钟"
                } else {
                    "未保存有效内容"
                }
                onResult(summary)
            } finally {
                _isSaving.value = false
            }
        }
    }

    fun saveCustomAiConfig(
        enabled: Boolean,
        baseUrl: String,
        apiKey: String,
        model: String,
        onResult: (String) -> Unit,
    ) {
        val cleanUrl = baseUrl.trim()
        val cleanKey = apiKey.trim()
        val cleanModel = model.trim()
        if (cleanUrl.isNotBlank() && !OpenAiCompatibleClient.isValidHttpsUrl(cleanUrl)) {
            onResult("保存失败：Base URL 必须使用 https:// 安全连接（已禁止 http:// 明文）")
            return
        }
        viewModelScope.launch {
            try {
                preferencesRepository.saveCustomAiConfig(
                    enabled = enabled,
                    baseUrl = cleanUrl,
                    apiKey = cleanKey,
                    model = cleanModel,
                )
                _aiTestFeedback.value = null
                onResult(
                    if (enabled && OpenAiCompatibleClient.isValidHttpsUrl(cleanUrl) && cleanKey.isNotBlank() && cleanModel.isNotBlank()) {
                        "自定义 AI 接口已通过 Android Keystore 加密保存"
                    } else {
                        "AI 配置已更新（当前使用纯本地离线解析）"
                    },
                )
            } catch (e: SecureStorageException) {
                onResult("保存失败：${e.message ?: "设备安全密钥库不可用，拒绝明文保存密钥"}")
            } catch (e: IllegalArgumentException) {
                onResult("保存失败：${e.message ?: "配置格式无效"}")
            }
        }
    }

    fun clearCustomAiConfig(onResult: (String) -> Unit) {
        viewModelScope.launch {
            preferencesRepository.clearCustomAiConfig()
            _aiTestFeedback.value = null
            onResult("已清空自定义 AI 接口配置，切换回纯本地离线解析")
        }
    }

    fun testCustomAiConnection(
        baseUrl: String,
        apiKey: String,
        model: String,
    ) {
        if (_isTestingAi.value) return
        viewModelScope.launch {
            _isTestingAi.value = true
            _aiTestFeedback.value = null
            try {
                val testConfig = CustomAiConfig(
                    enabled = true,
                    baseUrl = baseUrl.trim(),
                    apiKey = apiKey.trim(),
                    model = model.trim(),
                )
                val res = OpenAiCompatibleClient.testConnection(testConfig)
                _aiTestFeedback.value = res.fold(
                    onSuccess = { it },
                    onFailure = { "连接失败：${it.message ?: "请检查网络或配置"}" },
                )
            } finally {
                _isTestingAi.value = false
            }
        }
    }

    private data class DraftOverlayState(
        val showInputSheet: Boolean,
        val showPreview: Boolean,
        val source: ParseSource,
        val notice: String?,
        val drafts: List<SmartParsedDraft>,
    )

    private data class AiSettingsState(
        val config: CustomAiConfig,
        val isTesting: Boolean,
        val feedback: String?,
    )

    class Factory(
        private val orchestrator: SmartCaptureOrchestrator,
        private val preferencesRepository: UserPreferencesRepository,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return SmartCaptureViewModel(
                orchestrator = orchestrator,
                preferencesRepository = preferencesRepository,
            ) as T
        }
    }
}
