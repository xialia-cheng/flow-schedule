package com.example.data

import android.app.NotificationManager
import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.smart.LocalKeystoreCipher
import com.example.smart.OpenAiCompatibleClient
import com.example.smart.SecureStorageException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

enum class AppThemeMode(val label: String) {
    SYSTEM("跟随系统"),
    LIGHT("浅色"),
    DARK("深色");

    fun next(): AppThemeMode = when (this) {
        SYSTEM -> LIGHT
        LIGHT -> DARK
        DARK -> SYSTEM
    }
}

enum class DndLevel(
    val label: String,
    val subtitle: String,
    val interruptionFilter: Int,
) {
    PRIORITY(
        label = "优先打扰",
        subtitle = "仅允许闹钟、日程与星标联系人",
        interruptionFilter = NotificationManager.INTERRUPTION_FILTER_PRIORITY,
    ),
    ALARMS(
        label = "仅闹钟",
        subtitle = "仅允许系统闹钟响铃，屏蔽所有通知",
        interruptionFilter = NotificationManager.INTERRUPTION_FILTER_ALARMS,
    ),
    NONE(
        label = "完全静音",
        subtitle = "彻底静音，包括闹钟与媒体提醒",
        interruptionFilter = NotificationManager.INTERRUPTION_FILTER_NONE,
    ),
}

data class CustomAiConfig(
    val enabled: Boolean = false,
    val baseUrl: String = "",
    val apiKey: String = "",
    val model: String = "",
    val requiresLegacyKeyReentry: Boolean = false,
) {
    val isReady: Boolean
        get() = enabled &&
            OpenAiCompatibleClient.isValidHttpsUrl(baseUrl) &&
            apiKey.isNotBlank() &&
            model.isNotBlank()

    val targetHost: String?
        get() = OpenAiCompatibleClient.extractHostOrNull(baseUrl)
}

data class AppSettings(
    val hasSeenOnboarding: Boolean = false,
    val themeMode: AppThemeMode = AppThemeMode.SYSTEM,
    val useDynamicColor: Boolean = true,
    val compactMode: Boolean = false,
    val showMorningExercise: Boolean = true,
    val extraBannerTitle: String = "早锻炼附加时段",
    val extraBannerStartTime: String = "06:40",
    val extraBannerEndTime: String = "07:30",
    val showNonCurrentWeekCourses: Boolean = true,
    val showWeekend: Boolean = true,
    val weekStartDay: WeekStartDay = WeekStartDay.MONDAY,
    val timetableRowHeightDp: Int = 72,
    val timetableFontScale: Float = 1.0f,
    val courseReminderEnabled: Boolean = true,
    val courseReminderLeadMinutes: Int = 15,
    val dndLevel: DndLevel = DndLevel.PRIORITY,
    val fluidCloudShowLocation: Boolean = true,
    val fluidCloudShowTeacher: Boolean = true,
    val fluidCloudShowNotes: Boolean = false,
    val fluidCloudLockscreenDetails: Boolean = false,
    val morningExerciseCheckedDate: String = "",
    val totalFocusMinutes: Int = 0,
    val focusDurationMinutes: Int = 25,
    val focusBreakMinutes: Int = 5,
    val focusCustomEventName: String = "专注学习",
    val focusLinkFluidCloud: Boolean = true,
    val focusLinkDnd: Boolean = false,
    val customScheduleTags: List<CustomScheduleTag> = emptyList(),
    val customAiConfig: CustomAiConfig = CustomAiConfig(),
)

private val Context.userPreferencesDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "flow_schedule_prefs",
)

class UserPreferencesRepository(private val context: Context) {
    private object Keys {
        val ONBOARDING_SHOWN = booleanPreferencesKey("permission_onboarding_shown")
        val THEME_MODE = stringPreferencesKey("app_theme_mode")
        val USE_DYNAMIC_COLOR = booleanPreferencesKey("use_dynamic_color")
        val COMPACT_MODE = booleanPreferencesKey("compact_mode")
        val SHOW_MORNING_EXERCISE = booleanPreferencesKey("show_morning_exercise")
        val EXTRA_BANNER_TITLE = stringPreferencesKey("extra_banner_title")
        val EXTRA_BANNER_START_TIME = stringPreferencesKey("extra_banner_start_time")
        val EXTRA_BANNER_END_TIME = stringPreferencesKey("extra_banner_end_time")
        val SHOW_NON_CURRENT_WEEK = booleanPreferencesKey("show_non_current_week")
        val SHOW_WEEKEND = booleanPreferencesKey("show_weekend")
        val WEEK_START_DAY = stringPreferencesKey("week_start_day")
        val TIMETABLE_ROW_HEIGHT_DP = intPreferencesKey("timetable_row_height_dp")
        val TIMETABLE_FONT_SCALE = floatPreferencesKey("timetable_font_scale")
        val COURSE_REMINDER_ENABLED = booleanPreferencesKey("course_reminder_enabled")
        val COURSE_REMINDER_LEAD_MINUTES = intPreferencesKey("course_reminder_lead_minutes")
        val DND_LEVEL = stringPreferencesKey("dnd_level")
        val FLUID_CLOUD_SHOW_LOCATION = booleanPreferencesKey("fluid_cloud_show_location")
        val FLUID_CLOUD_SHOW_TEACHER = booleanPreferencesKey("fluid_cloud_show_teacher")
        val FLUID_CLOUD_SHOW_NOTES = booleanPreferencesKey("fluid_cloud_show_notes")
        val FLUID_CLOUD_LOCKSCREEN_DETAILS = booleanPreferencesKey("fluid_cloud_lockscreen_details")
        val MORNING_EXERCISE_CHECKED_DATE = stringPreferencesKey("morning_exercise_checked_date")
        val TOTAL_FOCUS_MINUTES = intPreferencesKey("total_focus_minutes")
        val FOCUS_DURATION_MINUTES = intPreferencesKey("focus_duration_minutes")
        val FOCUS_BREAK_MINUTES = intPreferencesKey("focus_break_minutes")
        val FOCUS_CUSTOM_EVENT_NAME = stringPreferencesKey("focus_custom_event_name")
        val FOCUS_LINK_FLUID_CLOUD = booleanPreferencesKey("focus_link_fluid_cloud")
        val FOCUS_LINK_DND = booleanPreferencesKey("focus_link_dnd")
        val CUSTOM_SCHEDULE_TAGS_JSON = stringPreferencesKey("custom_schedule_tags_json")

        // Optional custom OpenAI-compatible AI config (API key encrypted via Android Keystore)
        val CUSTOM_AI_ENABLED = booleanPreferencesKey("custom_ai_enabled")
        val CUSTOM_AI_BASE_URL = stringPreferencesKey("custom_ai_base_url")
        val CUSTOM_AI_API_KEY_ENC = stringPreferencesKey("custom_ai_api_key_enc")
        val CUSTOM_AI_MODEL = stringPreferencesKey("custom_ai_model")
    }

    val settings: Flow<AppSettings> = context.userPreferencesDataStore.data.map { prefs ->
        val rawTheme = prefs[Keys.THEME_MODE]
        val theme = runCatching {
            if (rawTheme != null) AppThemeMode.valueOf(rawTheme) else AppThemeMode.SYSTEM
        }.getOrDefault(AppThemeMode.SYSTEM)

        val weekStartDay = runCatching {
            prefs[Keys.WEEK_START_DAY]?.let { WeekStartDay.valueOf(it) } ?: WeekStartDay.MONDAY
        }.getOrDefault(WeekStartDay.MONDAY)

        val dndLevel = runCatching {
            prefs[Keys.DND_LEVEL]?.let { DndLevel.valueOf(it) } ?: DndLevel.PRIORITY
        }.getOrDefault(DndLevel.PRIORITY)

        val encryptedKey = prefs[Keys.CUSTOM_AI_API_KEY_ENC].orEmpty()
        val decryptResult = if (encryptedKey.isNotBlank()) {
            LocalKeystoreCipher.decryptWithStatus(encryptedKey)
        } else {
            com.example.smart.DecryptResult("")
        }

        AppSettings(
            hasSeenOnboarding = prefs[Keys.ONBOARDING_SHOWN] ?: false,
            themeMode = theme,
            useDynamicColor = prefs[Keys.USE_DYNAMIC_COLOR] ?: true,
            compactMode = prefs[Keys.COMPACT_MODE] ?: false,
            showMorningExercise = prefs[Keys.SHOW_MORNING_EXERCISE] ?: true,
            extraBannerTitle = prefs[Keys.EXTRA_BANNER_TITLE]?.takeIf { it.isNotBlank() } ?: "早锻炼附加时段",
            extraBannerStartTime = prefs[Keys.EXTRA_BANNER_START_TIME]?.takeIf { it.isNotBlank() } ?: "06:40",
            extraBannerEndTime = prefs[Keys.EXTRA_BANNER_END_TIME]?.takeIf { it.isNotBlank() } ?: "07:30",
            showNonCurrentWeekCourses = prefs[Keys.SHOW_NON_CURRENT_WEEK] ?: true,
            showWeekend = prefs[Keys.SHOW_WEEKEND] ?: true,
            weekStartDay = weekStartDay,
            timetableRowHeightDp = (prefs[Keys.TIMETABLE_ROW_HEIGHT_DP] ?: 72).coerceIn(54, 108),
            timetableFontScale = (prefs[Keys.TIMETABLE_FONT_SCALE] ?: 1.0f).coerceIn(0.80f, 1.35f),
            courseReminderEnabled = prefs[Keys.COURSE_REMINDER_ENABLED] ?: true,
            courseReminderLeadMinutes = (prefs[Keys.COURSE_REMINDER_LEAD_MINUTES] ?: 15).coerceIn(0, 120),
            dndLevel = dndLevel,
            fluidCloudShowLocation = prefs[Keys.FLUID_CLOUD_SHOW_LOCATION] ?: true,
            fluidCloudShowTeacher = prefs[Keys.FLUID_CLOUD_SHOW_TEACHER] ?: true,
            fluidCloudShowNotes = prefs[Keys.FLUID_CLOUD_SHOW_NOTES] ?: false,
            fluidCloudLockscreenDetails = prefs[Keys.FLUID_CLOUD_LOCKSCREEN_DETAILS] ?: false,
            morningExerciseCheckedDate = prefs[Keys.MORNING_EXERCISE_CHECKED_DATE] ?: "",
            totalFocusMinutes = prefs[Keys.TOTAL_FOCUS_MINUTES] ?: 0,
            focusDurationMinutes = (prefs[Keys.FOCUS_DURATION_MINUTES] ?: 25).coerceIn(1, 180),
            focusBreakMinutes = (prefs[Keys.FOCUS_BREAK_MINUTES] ?: 5).coerceIn(0, 60),
            focusCustomEventName = prefs[Keys.FOCUS_CUSTOM_EVENT_NAME]?.takeIf { it.isNotBlank() } ?: "专注学习",
            focusLinkFluidCloud = prefs[Keys.FOCUS_LINK_FLUID_CLOUD] ?: true,
            focusLinkDnd = prefs[Keys.FOCUS_LINK_DND] ?: false,
            customScheduleTags = CustomScheduleTagCodec.decode(prefs[Keys.CUSTOM_SCHEDULE_TAGS_JSON].orEmpty()),
            customAiConfig = CustomAiConfig(
                enabled = prefs[Keys.CUSTOM_AI_ENABLED] ?: false,
                baseUrl = prefs[Keys.CUSTOM_AI_BASE_URL].orEmpty(),
                apiKey = decryptResult.plainText,
                model = prefs[Keys.CUSTOM_AI_MODEL].orEmpty(),
                requiresLegacyKeyReentry = decryptResult.requiresLegacyMigration,
            ),
        )
    }

    val hasSeenOnboarding: Flow<Boolean> = settings.map { it.hasSeenOnboarding }
    val themeMode: Flow<AppThemeMode> = settings.map { it.themeMode }
    val useDynamicColor: Flow<Boolean> = settings.map { it.useDynamicColor }
    val customAiConfig: Flow<CustomAiConfig> = settings.map { it.customAiConfig }

    suspend fun markOnboardingSeen() {
        context.userPreferencesDataStore.edit { it[Keys.ONBOARDING_SHOWN] = true }
    }

    suspend fun setThemeMode(mode: AppThemeMode) {
        context.userPreferencesDataStore.edit { it[Keys.THEME_MODE] = mode.name }
    }

    suspend fun setUseDynamicColor(enabled: Boolean) {
        context.userPreferencesDataStore.edit { it[Keys.USE_DYNAMIC_COLOR] = enabled }
    }

    suspend fun setCompactMode(enabled: Boolean) {
        context.userPreferencesDataStore.edit { it[Keys.COMPACT_MODE] = enabled }
    }

    suspend fun setShowMorningExercise(enabled: Boolean) {
        context.userPreferencesDataStore.edit { it[Keys.SHOW_MORNING_EXERCISE] = enabled }
    }

    suspend fun setExtraBannerConfig(title: String, startTime: String, endTime: String) {
        context.userPreferencesDataStore.edit { prefs ->
            prefs[Keys.EXTRA_BANNER_TITLE] = title.trim().ifEmpty { "早锻炼附加时段" }
            prefs[Keys.EXTRA_BANNER_START_TIME] = startTime.trim().ifEmpty { "06:40" }
            prefs[Keys.EXTRA_BANNER_END_TIME] = endTime.trim().ifEmpty { "07:30" }
        }
    }

    suspend fun setShowNonCurrentWeekCourses(enabled: Boolean) {
        context.userPreferencesDataStore.edit { it[Keys.SHOW_NON_CURRENT_WEEK] = enabled }
    }

    suspend fun setShowWeekend(enabled: Boolean) {
        context.userPreferencesDataStore.edit { it[Keys.SHOW_WEEKEND] = enabled }
    }

    suspend fun setWeekStartDay(weekStartDay: WeekStartDay) {
        context.userPreferencesDataStore.edit { it[Keys.WEEK_START_DAY] = weekStartDay.name }
    }

    suspend fun setTimetableRowHeightDp(heightDp: Int) {
        context.userPreferencesDataStore.edit {
            it[Keys.TIMETABLE_ROW_HEIGHT_DP] = heightDp.coerceIn(54, 108)
        }
    }

    suspend fun setTimetableFontScale(scale: Float) {
        context.userPreferencesDataStore.edit {
            it[Keys.TIMETABLE_FONT_SCALE] = scale.coerceIn(0.80f, 1.35f)
        }
    }

    suspend fun setCourseReminderEnabled(enabled: Boolean) {
        context.userPreferencesDataStore.edit { it[Keys.COURSE_REMINDER_ENABLED] = enabled }
    }

    suspend fun setCourseReminderLeadMinutes(minutes: Int) {
        context.userPreferencesDataStore.edit {
            it[Keys.COURSE_REMINDER_LEAD_MINUTES] = minutes.coerceIn(0, 120)
        }
    }

    suspend fun setDndLevel(level: DndLevel) {
        context.userPreferencesDataStore.edit { it[Keys.DND_LEVEL] = level.name }
    }

    suspend fun setFluidCloudDisplayOptions(
        showLocation: Boolean,
        showTeacher: Boolean,
        showNotes: Boolean,
    ) {
        context.userPreferencesDataStore.edit { prefs ->
            prefs[Keys.FLUID_CLOUD_SHOW_LOCATION] = showLocation
            prefs[Keys.FLUID_CLOUD_SHOW_TEACHER] = showTeacher
            prefs[Keys.FLUID_CLOUD_SHOW_NOTES] = showNotes
        }
    }

    suspend fun setFluidCloudLockscreenDetails(enabled: Boolean) {
        context.userPreferencesDataStore.edit { prefs ->
            prefs[Keys.FLUID_CLOUD_LOCKSCREEN_DETAILS] = enabled
        }
    }

    suspend fun setMorningExerciseCheckedDate(dateKey: String) {
        context.userPreferencesDataStore.edit { it[Keys.MORNING_EXERCISE_CHECKED_DATE] = dateKey }
    }

    suspend fun addFocusMinutes(minutes: Int) {
        context.userPreferencesDataStore.edit { prefs ->
            val current = prefs[Keys.TOTAL_FOCUS_MINUTES] ?: 0
            prefs[Keys.TOTAL_FOCUS_MINUTES] = current + minutes.coerceAtLeast(0)
        }
    }

    suspend fun saveFocusSettings(
        focusMinutes: Int,
        breakMinutes: Int,
        eventName: String,
        linkFluidCloud: Boolean,
        linkDnd: Boolean,
    ) {
        context.userPreferencesDataStore.edit { prefs ->
            prefs[Keys.FOCUS_DURATION_MINUTES] = focusMinutes.coerceIn(1, 180)
            prefs[Keys.FOCUS_BREAK_MINUTES] = breakMinutes.coerceIn(0, 60)
            prefs[Keys.FOCUS_CUSTOM_EVENT_NAME] = eventName.trim().ifEmpty { "专注学习" }
            prefs[Keys.FOCUS_LINK_FLUID_CLOUD] = linkFluidCloud
            prefs[Keys.FOCUS_LINK_DND] = linkDnd
        }
    }

    suspend fun addCustomScheduleTag(name: String, colorHex: String): CustomScheduleTag? {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return null
        val newTag = CustomScheduleTag(
            id = "tag_${System.currentTimeMillis()}_${trimmed.hashCode().and(0xFFFF)}",
            name = trimmed.take(12),
            colorHex = colorHex.trim().ifEmpty { "#0B72FF" },
        )
        context.userPreferencesDataStore.edit { prefs ->
            val current = CustomScheduleTagCodec.decode(prefs[Keys.CUSTOM_SCHEDULE_TAGS_JSON].orEmpty())
            prefs[Keys.CUSTOM_SCHEDULE_TAGS_JSON] = CustomScheduleTagCodec.encode(current + newTag)
        }
        return newTag
    }

    suspend fun updateCustomScheduleTag(id: String, name: String, colorHex: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        context.userPreferencesDataStore.edit { prefs ->
            val current = CustomScheduleTagCodec.decode(prefs[Keys.CUSTOM_SCHEDULE_TAGS_JSON].orEmpty())
            val updated = current.map { tag ->
                if (tag.id == id) {
                    tag.copy(name = trimmed.take(12), colorHex = colorHex.trim().ifEmpty { tag.colorHex })
                } else {
                    tag
                }
            }
            prefs[Keys.CUSTOM_SCHEDULE_TAGS_JSON] = CustomScheduleTagCodec.encode(updated)
        }
    }

    suspend fun deleteCustomScheduleTag(id: String) {
        context.userPreferencesDataStore.edit { prefs ->
            val current = CustomScheduleTagCodec.decode(prefs[Keys.CUSTOM_SCHEDULE_TAGS_JSON].orEmpty())
            val filtered = current.filterNot { it.id == id }
            prefs[Keys.CUSTOM_SCHEDULE_TAGS_JSON] = CustomScheduleTagCodec.encode(filtered)
        }
    }

    @Throws(SecureStorageException::class, IllegalArgumentException::class)
    suspend fun saveCustomAiConfig(
        enabled: Boolean,
        baseUrl: String,
        apiKey: String,
        model: String,
    ) {
        val cleanUrl = baseUrl.trim()
        val cleanKey = apiKey.trim()
        val cleanModel = model.trim()
        if (cleanUrl.isNotBlank() && !OpenAiCompatibleClient.isValidHttpsUrl(cleanUrl)) {
            throw IllegalArgumentException("Base URL 必须使用 https:// 安全连接")
        }
        val encryptedKey = if (cleanKey.isNotBlank()) LocalKeystoreCipher.encrypt(cleanKey) else ""

        context.userPreferencesDataStore.edit { prefs ->
            prefs[Keys.CUSTOM_AI_ENABLED] = enabled
            prefs[Keys.CUSTOM_AI_BASE_URL] = cleanUrl
            prefs[Keys.CUSTOM_AI_API_KEY_ENC] = encryptedKey
            prefs[Keys.CUSTOM_AI_MODEL] = cleanModel
        }
    }

    suspend fun clearCustomAiConfig() {
        context.userPreferencesDataStore.edit { prefs ->
            prefs[Keys.CUSTOM_AI_ENABLED] = false
            prefs[Keys.CUSTOM_AI_BASE_URL] = ""
            prefs[Keys.CUSTOM_AI_API_KEY_ENC] = ""
            prefs[Keys.CUSTOM_AI_MODEL] = ""
        }
    }
}
