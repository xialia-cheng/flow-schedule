package com.example.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.School
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.DateTimeUtils
import com.example.notification.LiveActivityItem
import com.example.notification.LiveActivitySourceType
import com.example.notification.LiveUpdatePermissionStatus
import com.example.ui.theme.FlowTheme
import com.example.ui.theme.MonoFontFamily

/**
 * Compact real-time banner shown on the Timetable tab whenever a course or schedule is actively
 * running in Android 16 Live Updates / ColorOS 17 Fluid Cloud, mirroring the top status bar capsule
 * (course short name + countdown) alongside full course name, @location, and phase progress.
 */
@Composable
fun FluidCloudCapsuleBanner(
    item: LiveActivityItem,
    permissionStatus: LiveUpdatePermissionStatus,
    nowMillis: Long,
    onOpenPermissions: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colorScheme = MaterialTheme.colorScheme
    val flowColors = FlowTheme.colors

    val isInProgress = item.isInProgress(nowMillis)
    val targetMillis = if (isInProgress) item.endTimeMillis else item.startTimeMillis
    val remainingMillis = (targetMillis - nowMillis).coerceAtLeast(0L)
    val accent = item.accentColorArgb?.let { androidx.compose.ui.graphics.Color(it) }
        ?: if (isInProgress) colorScheme.primary else flowColors.upcomingAccent
    val chipText = item.capsuleChipText(nowMillis)
    val locationText = item.rawLocationName()
    val phaseLabel = when {
        isInProgress && item.sourceType == LiveActivitySourceType.COURSE -> "实况通知 · 正在上课"
        isInProgress -> "实况通知 · 进行中"
        else -> "实况通知 · 课前临近"
    }

    val pulseTransition = rememberInfiniteTransition(label = "fluid_cloud_live_dot")
    val pulseAlpha by pulseTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "fluid_cloud_dot_alpha",
    )

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .defaultMinSize(minHeight = 48.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onOpenPermissions)
            .testTag("timetable_fluid_cloud_banner"),
        shape = RoundedCornerShape(16.dp),
        color = accent.copy(alpha = if (flowColors.isDark) 0.16f else 0.11f),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.42f)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // Live status bar capsule preview pill
            Surface(
                shape = CircleShape,
                color = accent,
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .alpha(pulseAlpha)
                            .clip(CircleShape)
                            .background(colorScheme.onPrimary),
                    )
                    Icon(
                        imageVector = if (item.sourceType == LiveActivitySourceType.COURSE) {
                            Icons.Rounded.School
                        } else {
                            Icons.Rounded.NotificationsActive
                        },
                        contentDescription = null,
                        tint = colorScheme.onPrimary,
                        modifier = Modifier.size(13.dp),
                    )
                    Text(
                        text = chipText,
                        fontFamily = MonoFontFamily,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = colorScheme.onPrimary,
                        maxLines = 1,
                        softWrap = false,
                    )
                }
            }

            // Course name + @location + remaining countdown
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (locationText.isNotEmpty()) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.LocationOn,
                                contentDescription = null,
                                tint = accent,
                                modifier = Modifier.size(12.dp),
                            )
                            Text(
                                text = locationText,
                                style = MaterialTheme.typography.labelSmall,
                                color = accent,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(2.dp))
                val remainingStr = DateTimeUtils.formatCountdownChinese(remainingMillis)
                val statusHint = if (permissionStatus.allRecommendedGranted) {
                    "$phaseLabel · 剩余 $remainingStr"
                } else {
                    "$phaseLabel · 剩余 $remainingStr · 点击检查实况通知权限"
                }
                Text(
                    text = statusHint,
                    style = MaterialTheme.typography.labelSmall,
                    color = colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
