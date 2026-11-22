package com.example.notification

data class LiveUpdatePermissionStatus(
    val hasPostNotifications: Boolean,
    val canPostPromotedNotifications: Boolean,
    val canScheduleExactAlarms: Boolean,
    val hasDndPolicyAccess: Boolean = false,
    val isDndCurrentlyActive: Boolean = false,
    val isAndroid16OrAbove: Boolean,
    val isChannelEligibleForLiveUpdates: Boolean = true,
    val lastNotificationPromotable: Boolean? = null,
    val isSystemPromotedOngoing: Boolean = false,
    val activeLiveScheduleId: Long? = null,
) {
    val allRecommendedGranted: Boolean
        get() = hasPostNotifications &&
            isChannelEligibleForLiveUpdates &&
            (!isAndroid16OrAbove || canPostPromotedNotifications) &&
            canScheduleExactAlarms
}
