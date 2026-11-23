package com.example.ui.update

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.BuildConfig
import com.example.data.UserPreferencesRepository
import com.example.update.AppUpdateChecker
import com.example.update.ReleaseInfo
import com.example.update.UpdateCheckResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AppUpdateUiState(
    val isChecking: Boolean = false,
    val autoCheckUpdates: Boolean = true,
    val lastCheckMillis: Long = 0L,
    val availableRelease: ReleaseInfo? = null,
    val showUpdateDialog: Boolean = false,
    val lastStatusHint: String? = null,
)

class AppUpdateViewModel(
    private val preferencesRepository: UserPreferencesRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AppUpdateUiState())
    val uiState: StateFlow<AppUpdateUiState> = _uiState.asStateFlow()

    private var startupCheckAttempted = false

    init {
        viewModelScope.launch {
            preferencesRepository.settings.collect { settings ->
                _uiState.update { state ->
                    state.copy(
                        autoCheckUpdates = settings.autoCheckUpdates,
                        lastCheckMillis = settings.lastUpdateCheckMillis,
                    )
                }
            }
        }
    }

    fun maybeAutoCheckOnStartup() {
        if (startupCheckAttempted) return
        startupCheckAttempted = true
        viewModelScope.launch {
            val settings = preferencesRepository.settings.first()
            if (!settings.autoCheckUpdates) return@launch
            val now = System.currentTimeMillis()
            if (now - settings.lastUpdateCheckMillis < AppUpdateChecker.AUTO_CHECK_INTERVAL_MILLIS) {
                return@launch
            }
            performCheck(manual = false, onShowMessage = {})
        }
    }

    fun checkForUpdatesManually(onShowMessage: (String) -> Unit) {
        if (_uiState.value.isChecking) return
        viewModelScope.launch {
            performCheck(manual = true, onShowMessage = onShowMessage)
        }
    }

    fun setAutoCheckUpdates(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.setAutoCheckUpdates(enabled)
        }
    }

    fun openUpdateDialog() {
        if (_uiState.value.availableRelease != null) {
            _uiState.update { it.copy(showUpdateDialog = true) }
        }
    }

    fun dismissUpdateDialog() {
        _uiState.update { it.copy(showUpdateDialog = false) }
    }

    private suspend fun performCheck(
        manual: Boolean,
        onShowMessage: (String) -> Unit,
    ) {
        _uiState.update { it.copy(isChecking = true) }
        val result = AppUpdateChecker.fetchLatestRelease(BuildConfig.VERSION_NAME)
        val now = System.currentTimeMillis()
        when (result) {
            is UpdateCheckResult.UpdateAvailable -> {
                preferencesRepository.setLastUpdateCheckMillis(now)
                _uiState.update {
                    it.copy(
                        isChecking = false,
                        availableRelease = result.release,
                        showUpdateDialog = true,
                        lastStatusHint = "发现新版本 v${result.release.versionName}",
                    )
                }
            }

            is UpdateCheckResult.UpToDate -> {
                preferencesRepository.setLastUpdateCheckMillis(now)
                val msg = "当前已是最新版本 v${result.currentVersion}"
                _uiState.update {
                    it.copy(
                        isChecking = false,
                        availableRelease = null,
                        showUpdateDialog = false,
                        lastStatusHint = msg,
                    )
                }
                if (manual) onShowMessage(msg)
            }

            is UpdateCheckResult.Error -> {
                _uiState.update {
                    it.copy(
                        isChecking = false,
                        lastStatusHint = if (manual) result.message else it.lastStatusHint,
                    )
                }
                if (manual) onShowMessage(result.message)
            }
        }
    }

    class Factory(
        private val preferencesRepository: UserPreferencesRepository,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return AppUpdateViewModel(preferencesRepository) as T
        }
    }
}
