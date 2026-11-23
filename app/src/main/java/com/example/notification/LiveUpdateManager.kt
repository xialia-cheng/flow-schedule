package com.example.notification

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import com.example.data.DateTimeUtils
import com.example.data.DndLevel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Manages Android 16+ Live Updates (Live Activities API) and ColorOS 17 Fluid Cloud notifications.
 * Uses official Notification.ProgressStyle, EXTRA_REQUEST_PROMOTED_ONGOING, setShortCriticalText,
 * and system-level countdown chronometer for cross-app capsule & lock-screen updates.
 */
class LiveUpdateManager(private val context: Context) {

    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    private val alarmManager =
        context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    private val _permissionStatus = MutableStateFlow(queryPermissionStatus())
    val permissionStatus: StateFlow<LiveUpdatePermissionStatus> = _permissionStatus.asStateFlow()

    private val _activeLiveActivity = MutableStateFlow<LiveActivityItem?>(null)
    val activeLiveActivity: StateFlow<LiveActivityItem?> = _activeLiveActivity.asStateFlow()

    private val runtimePrefs =
        context.getSharedPreferences(RUNTIME_PREFS_NAME, Context.MODE_PRIVATE)

    private var currentlyPostedKey: String? = null
    private var currentlyPostedSourceId: Long? = null
    private var currentlyPostedSignature: String? = null
    private var lastPromotableCharacteristics: Boolean? = null

    private var activeFocusItem: LiveActivityItem? = null
    private var progressTickerJob: Job? = null
    private var lastSyncedCandidates: List<LiveActivityItem> = emptyList()

    private var dndEnabledByApp: Boolean
        get() = runtimePrefs.getBoolean(KEY_DND_ENABLED_BY_APP, false)
        set(value) {
            runtimePrefs.edit().putBoolean(KEY_DND_ENABLED_BY_APP, value).apply()
        }

    private var storedDndLevel: DndLevel
        get() = runCatching {
            runtimePrefs.getString(KEY_DND_LEVEL, DndLevel.PRIORITY.name)
                ?.let { DndLevel.valueOf(it) } ?: DndLevel.PRIORITY
        }.getOrDefault(DndLevel.PRIORITY)
        set(value) {
            runtimePrefs.edit().putString(KEY_DND_LEVEL, value.name).apply()
        }

    private var lastPostedEndTimeMillis: Long
        get() = runtimePrefs.getLong(KEY_LAST_POSTED_END_TIME_MILLIS, 0L)
        set(value) {
            runtimePrefs.edit().putLong(KEY_LAST_POSTED_END_TIME_MILLIS, value).apply()
        }

    fun setPreferredDndLevel(level: DndLevel) {
        storedDndLevel = level
        if (dndEnabledByApp && hasDndPolicyAccess() && isDoNotDisturbActive()) {
            runCatching {
                notificationManager.setInterruptionFilter(level.interruptionFilter)
            }
            refreshPermissionStatus()
        }
    }

    fun getPreferredDndLevel(): DndLevel = storedDndLevel

    fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val existing = notificationManager.getNotificationChannel(CHANNEL_ID)
            if (existing == null) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    context.getString(R.string.channel_live_updates_name),
                    NotificationManager.IMPORTANCE_HIGH,
                ).apply {
                    description = context.getString(R.string.channel_live_updates_desc)
                    lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                    setShowBadge(true)
                    enableVibration(true)
                }
                notificationManager.createNotificationChannel(channel)
            }
        }
    }

    fun refreshPermissionStatus(): LiveUpdatePermissionStatus {
        val status = queryPermissionStatus()
        _permissionStatus.value = status
        return status
    }

    fun isDoNotDisturbActive(): Boolean {
        return runCatching {
            notificationManager.currentInterruptionFilter != NotificationManager.INTERRUPTION_FILTER_ALL
        }.getOrDefault(false)
    }

    fun hasDndPolicyAccess(): Boolean {
        return runCatching {
            notificationManager.isNotificationPolicyAccessGranted
        }.getOrDefault(false)
    }

    /**
     * Toggles system Do Not Disturb when policy access is granted;
     * otherwise launches the system Do Not Disturb access settings screen.
     */
    fun toggleDoNotDisturb(): Boolean {
        if (!hasDndPolicyAccess()) {
            runCatching {
                val intent = buildDndPolicySettingsIntent().apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            }
            refreshPermissionStatus()
            return false
        }

        val currentlyDnd = isDoNotDisturbActive()
        runCatching {
            if (currentlyDnd) {
                notificationManager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL)
                dndEnabledByApp = false
            } else {
                notificationManager.setInterruptionFilter(storedDndLevel.interruptionFilter)
                dndEnabledByApp = true
            }
        }
        refreshPermissionStatus()
        return true
    }

    /**
     * Explicitly enables system DND at the user's configured [DndLevel] for a focus/class session.
     * Returns true if DND was enabled, false if policy access is missing.
     */
    fun enableDndForSession(): Boolean {
        if (!hasDndPolicyAccess()) {
            refreshPermissionStatus()
            return false
        }
        runCatching {
            notificationManager.setInterruptionFilter(storedDndLevel.interruptionFilter)
            dndEnabledByApp = true
        }
        refreshPermissionStatus()
        return true
    }

    /**
     * Restores normal interruption filter only if this app previously enabled DND for a live session.
     */
    fun restoreDndIfEnabledByApp() {
        if (dndEnabledByApp && hasDndPolicyAccess()) {
            runCatching {
                notificationManager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL)
            }
            dndEnabledByApp = false
            refreshPermissionStatus()
        }
    }

    private fun queryChannelEligibility(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return true
        val channel = runCatching { notificationManager.getNotificationChannel(CHANNEL_ID) }.getOrNull()
            ?: return true
        return channel.importance > NotificationManager.IMPORTANCE_MIN
    }

    private fun querySystemPromotedOngoingFlag(): Boolean {
        if (Build.VERSION.SDK_INT < ANDROID_16_API_LEVEL) return false
        return runCatching {
            notificationManager.activeNotifications.any { sbn ->
                sbn.id == LIVE_UPDATE_NOTIFICATION_ID &&
                    (sbn.notification.flags and Notification.FLAG_PROMOTED_ONGOING) != 0
            }
        }.getOrDefault(false)
    }

    private fun queryPermissionStatus(): LiveUpdatePermissionStatus {
        val hasPostNotifications = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }

        val isAndroid16 = Build.VERSION.SDK_INT >= ANDROID_16_API_LEVEL
        val canPostPromoted = if (Build.VERSION.SDK_INT >= ANDROID_16_API_LEVEL) {
            runCatching { notificationManager.canPostPromotedNotifications() }.getOrDefault(false)
        } else {
            hasPostNotifications
        }

        val canExactAlarm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            runCatching { alarmManager.canScheduleExactAlarms() }.getOrDefault(true)
        } else {
            true
        }

        return LiveUpdatePermissionStatus(
            hasPostNotifications = hasPostNotifications,
            canPostPromotedNotifications = canPostPromoted,
            canScheduleExactAlarms = canExactAlarm,
            hasDndPolicyAccess = hasDndPolicyAccess(),
            isDndCurrentlyActive = isDoNotDisturbActive(),
            isAndroid16OrAbove = isAndroid16,
            isChannelEligibleForLiveUpdates = queryChannelEligibility(),
            lastNotificationPromotable = lastPromotableCharacteristics,
            isSystemPromotedOngoing = querySystemPromotedOngoingFlag(),
            activeLiveScheduleId = currentlyPostedSourceId,
        )
    }

    fun canPostNotificationsNow(): Boolean = queryPermissionStatus().hasPostNotifications

    /**
     * Checks in-memory state, persisted last-posted end time, and active system notifications.
     * If the live session has already reached or passed its end time, immediately cancels the
     * notification, clears local state, and restores DND if enabled by the app.
     */
    fun purgeExpiredNotificationIfNeeded(nowMillis: Long = System.currentTimeMillis()): Boolean {
        val activeInMemoryExpired = _activeLiveActivity.value?.let { nowMillis >= it.endTimeMillis } == true
        val persistedEndMillis = lastPostedEndTimeMillis
        val persistedExpired = persistedEndMillis > 0L && nowMillis >= persistedEndMillis

        val systemNotificationExpired = runCatching {
            notificationManager.activeNotifications.any { sbn ->
                if (sbn.id != LIVE_UPDATE_NOTIFICATION_ID) return@any false
                val extraEnd = sbn.notification.extras.getLong(EXTRA_LIVE_END_TIME_MILLIS, 0L)
                extraEnd > 0L && nowMillis >= extraEnd
            }
        }.getOrDefault(false)

        if (activeInMemoryExpired || persistedExpired || systemNotificationExpired) {
            cancelLiveUpdate()
            return true
        }
        return false
    }

    fun postFocusLiveActivity(
        title: String,
        isBreak: Boolean,
        startTimeMillis: Long,
        endTimeMillis: Long,
        accentColorArgb: Int,
        nowMillis: Long = System.currentTimeMillis(),
    ): LiveActivityItem {
        val focusItem = LiveActivityItem(
            sourceType = LiveActivitySourceType.FOCUS,
            sourceId = FOCUS_SESSION_SOURCE_ID,
            dateKey = DateTimeUtils.formatDateKey(startTimeMillis),
            title = title.trim().ifEmpty { if (isBreak) "专注休息" else "专注学习" },
            sectionLabel = if (isBreak) "休息时段" else "专注计时",
            startTimeMillis = startTimeMillis,
            endTimeMillis = endTimeMillis.coerceAtLeast(startTimeMillis + 1_000L),
            reminderMinutesBefore = 0,
            isBreakPhase = isBreak,
            accentColorArgb = accentColorArgb,
            showLocation = false,
            showTeacher = false,
            showNotes = false,
        )
        activeFocusItem = focusItem
        syncLiveActivity(
            candidates = listOf(focusItem),
            nowMillis = nowMillis,
            forceAlert = true,
        )
        return focusItem
    }

    fun clearFocusLiveActivity() {
        val wasFocus = activeFocusItem != null || _activeLiveActivity.value?.sourceType == LiveActivitySourceType.FOCUS
        activeFocusItem = null
        if (wasFocus) {
            cancelLiveUpdate()
        }
    }

    /**
     * Evaluates active/imminent courses and schedules, then posts, updates, or dismisses
     * the Android 16+ Live Update / ColorOS 17 Fluid Cloud notification.
     * Returns the currently active [LiveActivityItem] if one was selected, or null if idle.
     */
    fun syncLiveActivity(
        candidates: List<LiveActivityItem>,
        nowMillis: Long = System.currentTimeMillis(),
        forceAlert: Boolean = false,
    ): LiveActivityItem? {
        ensureNotificationChannel()
        purgeExpiredNotificationIfNeeded(nowMillis)

        lastSyncedCandidates = candidates
        val mergedCandidates = buildList {
            addAll(candidates)
            val focus = activeFocusItem
            if (focus != null) {
                if (nowMillis >= focus.endTimeMillis) {
                    activeFocusItem = null
                } else if (none { it.sourceType == LiveActivitySourceType.FOCUS }) {
                    add(focus)
                }
            }
        }

        val activeItem = mergedCandidates
            .filter { it.isLiveEligible(nowMillis) }
            .sortedWith(
                compareByDescending<LiveActivityItem> { it.sourceType == LiveActivitySourceType.FOCUS }
                    .thenByDescending { it.isTest }
                    .thenByDescending { it.isInProgress(nowMillis) }
                    .thenByDescending { it.sourceType == LiveActivitySourceType.COURSE }
                    .thenBy { it.startTimeMillis },
            )
            .firstOrNull()

        if (activeItem == null || nowMillis >= activeItem.endTimeMillis) {
            cancelLiveUpdate()
            return null
        }

        _activeLiveActivity.value = activeItem

        if (!canPostNotificationsNow()) {
            refreshPermissionStatus()
            return activeItem
        }

        val isInProgress = activeItem.isInProgress(nowMillis)
        val targetMillis = if (isInProgress) activeItem.endTimeMillis else activeItem.startTimeMillis
        val progressBucket = activeItem.progressPermille(nowMillis) / 10
        val dndActive = isDoNotDisturbActive()
        val colorKey = activeItem.accentColorArgb ?: 0
        val displayMetaKey = "${activeItem.showLocation}:${activeItem.showTeacher}:${activeItem.showNotes}:${activeItem.title}:${activeItem.isBreakPhase}"
        val stageIconBucket = resolveStageBucket(activeItem, nowMillis)
        val postSignature = "${activeItem.uniqueKey}|$isInProgress|$stageIconBucket|$targetMillis|$dndActive|$progressBucket|$colorKey|$displayMetaKey"

        // Avoid redundant notify() calls that would interrupt SystemUI's native Chronometer ticker
        if (!forceAlert && postSignature == currentlyPostedSignature) {
            return activeItem
        }

        val isSameItem = currentlyPostedKey == activeItem.uniqueKey
        val silentUpdate = isSameItem && !forceAlert

        val notification = buildLiveActivityNotification(activeItem, nowMillis, silentUpdate)

        if (Build.VERSION.SDK_INT >= ANDROID_16_API_LEVEL) {
            lastPromotableCharacteristics = runCatching {
                notification.hasPromotableCharacteristics()
            }.getOrNull()
        }

        currentlyPostedKey = activeItem.uniqueKey
        currentlyPostedSourceId = activeItem.sourceId
        currentlyPostedSignature = postSignature
        lastPostedEndTimeMillis = activeItem.endTimeMillis

        @SuppressLint("MissingPermission")
        runCatching {
            notificationManager.notify(LIVE_UPDATE_NOTIFICATION_ID, notification)
        }
        refreshPermissionStatus()
        ensureProgressTickerRunning()
        return activeItem
    }

    /**
     * Builds the Live Activity notification (exposed internally for unit/Robolectric verification).
     */
    internal fun buildLiveActivityNotification(
        item: LiveActivityItem,
        nowMillis: Long,
        silentUpdate: Boolean,
    ): Notification {
        return if (Build.VERSION.SDK_INT >= ANDROID_16_API_LEVEL) {
            buildAndroid16LiveUpdateNotification(item, nowMillis, silentUpdate)
        } else {
            buildLegacyOngoingNotification(item, nowMillis, silentUpdate)
        }
    }

    private fun ensureProgressTickerRunning() {
        if (progressTickerJob?.isActive == true) return
        progressTickerJob = CoroutineScope(Dispatchers.Default).launch {
            while (_activeLiveActivity.value != null) {
                delay(5_000L)
                val current = _activeLiveActivity.value ?: break
                val now = System.currentTimeMillis()
                if (now >= current.endTimeMillis) {
                    cancelLiveUpdate()
                    break
                }
                syncLiveActivity(
                    candidates = lastSyncedCandidates,
                    nowMillis = now,
                    forceAlert = false,
                )
            }
        }
    }

    fun cancelLiveUpdate() {
        progressTickerJob?.cancel()
        progressTickerJob = null
        notificationManager.cancel(LIVE_UPDATE_NOTIFICATION_ID)
        currentlyPostedKey = null
        currentlyPostedSourceId = null
        currentlyPostedSignature = null
        lastPostedEndTimeMillis = 0L
        _activeLiveActivity.value = null
        restoreDndIfEnabledByApp()
        refreshPermissionStatus()
    }

    private fun resolveStageBucket(item: LiveActivityItem, nowMillis: Long): Int {
        if (!item.isInProgress(nowMillis)) return 0 // 课前准备
        val remainingMillis = (item.endTimeMillis - nowMillis).coerceAtLeast(0L)
        return if (remainingMillis <= 10 * 60_000L) 2 else 1 // 2 = 即将结束(<=10分钟), 1 = 正在进行
    }

    private fun resolveSmallIconRes(item: LiveActivityItem, nowMillis: Long): Int {
        return when (resolveStageBucket(item, nowMillis)) {
            0 -> R.drawable.ic_stat_course_prep
            2 -> R.drawable.ic_stat_course_ending
            else -> if (item.sourceType == LiveActivitySourceType.COURSE) {
                R.drawable.ic_stat_course_live
            } else {
                R.drawable.ic_stat_flow_schedule
            }
        }
    }

    @RequiresApi(ANDROID_16_API_LEVEL)
    private fun buildAndroid16LiveUpdateNotification(
        item: LiveActivityItem,
        nowMillis: Long,
        silentUpdate: Boolean,
    ): Notification {
        val isInProgress = item.isInProgress(nowMillis)
        val targetMillis = if (isInProgress) item.endTimeMillis else item.startTimeMillis
        val progressPermille = item.progressPermille(nowMillis)
        val dndActive = isDoNotDisturbActive()

        // Top compact header metadata: optional section + Start-End time range + phase / DND status
        val timeRange = DateTimeUtils.formatTimeRange(item.startTimeMillis, item.endTimeMillis)
        val phaseBadge = when {
            dndActive -> context.getString(R.string.status_dnd_enabled)
            item.sourceType == LiveActivitySourceType.FOCUS && item.isBreakPhase -> "专注休息"
            item.sourceType == LiveActivitySourceType.FOCUS -> "专注中"
            isInProgress && item.sourceType == LiveActivitySourceType.COURSE -> "正在上课"
            isInProgress -> "进行中"
            else -> "课前准备"
        }
        val topSubText = if (item.sectionLabel.isNotBlank()) {
            "${item.sectionLabel} · $timeRange · $phaseBadge"
        } else {
            "$timeRange · $phaseBadge"
        }

        // Expanded card body: phase + target clock time + location
        val contentText = item.expandedContentText(nowMillis)

        val hasLeadSegment = item.hasLeadPrepSegment()
        val baseAccentColor = item.accentColorArgb
            ?: if (isInProgress) COLOR_ACCENT_ACTIVE else COLOR_ACCENT_PREP
        val prepSegmentColor = if (item.accentColorArgb != null) {
            blendColorArgb(baseAccentColor, Color.WHITE, 0.38f)
        } else {
            COLOR_SEGMENT_PREP
        }
        val activeSegmentColor = baseAccentColor
        val milestonePointColor = blendColorArgb(baseAccentColor, Color.WHITE, 0.62f)
        val themeAccentColor = baseAccentColor
        val smallIconRes = resolveSmallIconRes(item, nowMillis)

        // Distinct 2-stage segmented ProgressStyle dynamically tinted with current course/schedule color
        val progressStyle = Notification.ProgressStyle()
            .setStyledByProgress(true)
            .setProgress(progressPermille)
            .setProgressTrackerIcon(Icon.createWithResource(context, R.drawable.ic_tracker_dot))
            .apply {
                if (hasLeadSegment) {
                    setProgressSegments(
                        listOf(
                            // Stage 1: 课前准备 (0..300)
                            Notification.ProgressStyle.Segment(300).setColor(prepSegmentColor),
                            // Stage 2: 正在上课 / 进行中 (300..1000)
                            Notification.ProgressStyle.Segment(700).setColor(activeSegmentColor),
                        ),
                    )
                    setProgressPoints(emptyList())
                } else {
                    setProgressSegments(
                        listOf(
                            Notification.ProgressStyle.Segment(1000).setColor(activeSegmentColor),
                        ),
                    )
                }
            }

        val toggleDndAction = Notification.Action.Builder(
            Icon.createWithResource(context, R.drawable.ic_action_dnd),
            context.getString(R.string.action_toggle_dnd),
            createActionPendingIntent(LiveUpdateActionReceiver.ACTION_TOGGLE_DND, item, 104),
        ).build()

        val endNowAction = Notification.Action.Builder(
            Icon.createWithResource(context, R.drawable.ic_action_end_now),
            context.getString(R.string.action_end_now),
            createActionPendingIntent(LiveUpdateActionReceiver.ACTION_COMPLETE, item, 101),
        ).build()

        val remainingEndMillis = (item.endTimeMillis - nowMillis).coerceAtLeast(1_000L)
        val promotedExtras = Bundle().apply {
            putBoolean(Notification.EXTRA_REQUEST_PROMOTED_ONGOING, true)
            putLong(EXTRA_LIVE_END_TIME_MILLIS, item.endTimeMillis)
        }

        // Do NOT call setShortCriticalText() during countdowns: leaving it unset allows SystemUI
        // to drive the status bar Fluid Cloud capsule directly from the native Chronometer timer.
        // setTimeoutAfter() guarantees NotificationManagerService forcibly dismisses the notification
        // the instant the session ends even if the app process is frozen in the background.
        return Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(smallIconRes)
            .setColor(themeAccentColor)
            .setSubText(topSubText)
            .setContentTitle(item.titleWithLocation())
            .setContentText(contentText)
            .setStyle(progressStyle)
            .setOngoing(true)
            .setOnlyAlertOnce(silentUpdate)
            .setCategory(Notification.CATEGORY_EVENT)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setContentIntent(createContentPendingIntent(item))
            .setDeleteIntent(
                createActionPendingIntent(LiveUpdateActionReceiver.ACTION_DISMISSED, item, 103),
            )
            // System-level native countdown chronometer for both status bar capsule and expanded card
            .setWhen(targetMillis)
            .setShowWhen(true)
            .setUsesChronometer(true)
            .setChronometerCountDown(true)
            .setTimeoutAfter(remainingEndMillis)
            .addExtras(promotedExtras)
            .addAction(toggleDndAction)
            .addAction(endNowAction)
            .build()
    }

    private fun buildLegacyOngoingNotification(
        item: LiveActivityItem,
        nowMillis: Long,
        silentUpdate: Boolean,
    ): Notification {
        val isInProgress = item.isInProgress(nowMillis)
        val targetMillis = if (isInProgress) item.endTimeMillis else item.startTimeMillis
        val progressPermille = item.progressPermille(nowMillis)
        val dndActive = isDoNotDisturbActive()

        val timeRange = DateTimeUtils.formatTimeRange(item.startTimeMillis, item.endTimeMillis)
        val phaseBadge = when {
            dndActive -> context.getString(R.string.status_dnd_enabled)
            item.sourceType == LiveActivitySourceType.FOCUS && item.isBreakPhase -> "专注休息"
            item.sourceType == LiveActivitySourceType.FOCUS -> "专注中"
            isInProgress && item.sourceType == LiveActivitySourceType.COURSE -> "正在上课"
            isInProgress -> "进行中"
            else -> "课前准备"
        }
        val topSubText = if (item.sectionLabel.isNotBlank()) {
            "${item.sectionLabel} · $timeRange · $phaseBadge"
        } else {
            "$timeRange · $phaseBadge"
        }

        val contentText = item.expandedContentText(nowMillis)
        val themeAccentColor = item.accentColorArgb
            ?: if (isInProgress) COLOR_ACCENT_ACTIVE else COLOR_ACCENT_PREP
        val smallIconRes = resolveSmallIconRes(item, nowMillis)

        val remainingEndMillis = (item.endTimeMillis - nowMillis).coerceAtLeast(1_000L)
        val liveExtras = Bundle().apply {
            putLong(EXTRA_LIVE_END_TIME_MILLIS, item.endTimeMillis)
        }

        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(smallIconRes)
            .setColor(themeAccentColor)
            .setSubText(topSubText)
            .setContentTitle(item.titleWithLocation())
            .setContentText(contentText)
            .setOngoing(true)
            .setOnlyAlertOnce(silentUpdate)
            .setRequestPromotedOngoing(true)
            .setProgress(1000, progressPermille, false)
            .setWhen(targetMillis)
            .setShowWhen(true)
            .setUsesChronometer(true)
            .setChronometerCountDown(true)
            .setTimeoutAfter(remainingEndMillis)
            .addExtras(liveExtras)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_EVENT)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(createContentPendingIntent(item))
            .setDeleteIntent(
                createActionPendingIntent(LiveUpdateActionReceiver.ACTION_DISMISSED, item, 103),
            )
            .addAction(
                R.drawable.ic_action_dnd,
                context.getString(R.string.action_toggle_dnd),
                createActionPendingIntent(LiveUpdateActionReceiver.ACTION_TOGGLE_DND, item, 104),
            )
            .addAction(
                R.drawable.ic_action_end_now,
                context.getString(R.string.action_end_now),
                createActionPendingIntent(LiveUpdateActionReceiver.ACTION_COMPLETE, item, 101),
            )
            .build()
    }

    private fun createContentPendingIntent(item: LiveActivityItem): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_SOURCE_TYPE, item.sourceType.name)
            putExtra(EXTRA_SOURCE_ID, item.sourceId)
        }
        return PendingIntent.getActivity(
            context,
            200,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun createActionPendingIntent(
        action: String,
        item: LiveActivityItem,
        requestCodeOffset: Int,
    ): PendingIntent {
        val intent = Intent(context, LiveUpdateActionReceiver::class.java).apply {
            this.action = action
            putExtra(EXTRA_SOURCE_TYPE, item.sourceType.name)
            putExtra(EXTRA_SOURCE_ID, item.sourceId)
            putExtra(EXTRA_DATE_KEY, item.dateKey)
        }
        val requestCode = item.alarmRequestKey() + requestCodeOffset
        return PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    fun buildPromotedNotificationsSettingsIntent(): Intent {
        if (Build.VERSION.SDK_INT >= ANDROID_16_API_LEVEL) {
            val candidateActions = listOf(
                Settings.ACTION_APP_NOTIFICATION_PROMOTION_SETTINGS,
                ACTION_MANAGE_APP_PROMOTED_NOTIFICATIONS_COMPAT,
            )
            for (action in candidateActions) {
                val promotedIntent = Intent(action).apply {
                    putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                    data = Uri.parse("package:${context.packageName}")
                }
                if (promotedIntent.resolveActivity(context.packageManager) != null) {
                    return promotedIntent
                }
            }
        }
        return Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
            putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        }
    }

    fun buildChannelSettingsIntent(): Intent {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channelIntent = Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS).apply {
                putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                putExtra(Settings.EXTRA_CHANNEL_ID, CHANNEL_ID)
            }
            if (channelIntent.resolveActivity(context.packageManager) != null) {
                return channelIntent
            }
        }
        return Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
            putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        }
    }

    fun buildExactAlarmSettingsIntent(): Intent {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val exactIntent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                data = Uri.parse("package:${context.packageName}")
            }
            if (exactIntent.resolveActivity(context.packageManager) != null) {
                return exactIntent
            }
        }
        return Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.parse("package:${context.packageName}")
        }
    }

    fun buildDndPolicySettingsIntent(): Intent {
        val dndIntent = Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
        return if (dndIntent.resolveActivity(context.packageManager) != null) {
            dndIntent
        } else {
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            }
        }
    }

    companion object {
        const val CHANNEL_ID = "flow_schedule_live_updates"
        const val LIVE_UPDATE_NOTIFICATION_ID = 1001
        const val EXTRA_SCHEDULE_ID = "extra_schedule_id"
        const val EXTRA_SOURCE_TYPE = "extra_source_type"
        const val EXTRA_SOURCE_ID = "extra_source_id"
        const val EXTRA_DATE_KEY = "extra_date_key"
        const val EXTRA_LIVE_END_TIME_MILLIS = "extra_live_end_time_millis"
        private const val RUNTIME_PREFS_NAME = "flow_live_update_runtime"
        private const val KEY_DND_ENABLED_BY_APP = "dnd_enabled_by_app"
        private const val KEY_DND_LEVEL = "preferred_dnd_level"
        private const val KEY_LAST_POSTED_END_TIME_MILLIS = "last_posted_end_time_millis"
        private const val FOCUS_SESSION_SOURCE_ID = 900_001L
        private const val ANDROID_16_API_LEVEL = 36

        private const val ACTION_MANAGE_APP_PROMOTED_NOTIFICATIONS_COMPAT =
            "android.settings.MANAGE_APP_PROMOTED_NOTIFICATIONS"

        private fun blendColorArgb(from: Int, to: Int, ratio: Float): Int {
            val t = ratio.coerceIn(0f, 1f)
            val inv = 1f - t
            val r = (Color.red(from) * inv + Color.red(to) * t).toInt().coerceIn(0, 255)
            val g = (Color.green(from) * inv + Color.green(to) * t).toInt().coerceIn(0, 255)
            val b = (Color.blue(from) * inv + Color.blue(to) * t).toInt().coerceIn(0, 255)
            return Color.rgb(r, g, b)
        }

        // Fallback accent palette when an item does not supply a custom course/schedule color
        private val COLOR_ACCENT_ACTIVE = Color.rgb(107, 139, 255)   // #6B8BFF Gemini Blue-Violet
        private val COLOR_ACCENT_PREP = Color.rgb(245, 158, 11)      // #F59E0B Warm Amber Prep
        private val COLOR_SEGMENT_PREP = Color.rgb(245, 158, 11)     // Stage 1: 课前准备
        private val COLOR_SEGMENT_ACTIVE = Color.rgb(107, 139, 255)  // Stage 2: 正在上课 / 进行中
    }
}
