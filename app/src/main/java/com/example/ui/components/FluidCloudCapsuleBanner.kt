package com.example.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.DateTimeUtils
import com.example.notification.LiveActivityItem
import com.example.notification.LiveActivitySourceType
import com.example.notification.LiveUpdatePermissionStatus
import com.example.ui.theme.FlowMotion
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
    val haptics = rememberAppHaptics()

    val isInProgress = item.isInProgress(nowMillis)
    val targetMillis = if (isInProgress) item.endTimeMillis else item.startTimeMillis
    val remainingMillis = (targetMillis - nowMillis).coerceAtLeast(0L)
    val targetAccent = item.accentColorArgb?.let { Color(it) }
        ?: if (isInProgress) colorScheme.primary else flowColors.upcomingAccent
    val targetSecondary = if (isInProgress) {
        flowColors.secondaryAccent
    } else {
        targetAccent.copy(alpha = 0.78f)
    }

    val animatedAccent by animateColorAsState(
        targetValue = targetAccent,
        animationSpec = tween(durationMillis = 480, easing = FastOutSlowInEasing),
        label = "fluid_cloud_accent",
    )
    val animatedSecondary by animateColorAsState(
        targetValue = targetSecondary,
        animationSpec = tween(durationMillis = 480, easing = FastOutSlowInEasing),
        label = "fluid_cloud_secondary",
    )
    val animatedContainerColor by animateColorAsState(
        targetValue = targetAccent.copy(alpha = if (flowColors.isDark) 0.16f else 0.11f),
        animationSpec = tween(durationMillis = 480, easing = FastOutSlowInEasing),
        label = "fluid_cloud_container",
    )
    val animatedBorderColor by animateColorAsState(
        targetValue = targetAccent.copy(alpha = if (isInProgress) 0.52f else 0.38f),
        animationSpec = tween(durationMillis = 480, easing = FastOutSlowInEasing),
        label = "fluid_cloud_border",
    )
    val animatedProgress by animateFloatAsState(
        targetValue = (item.progressPermille(nowMillis) / 1000f).coerceIn(0f, 1f),
        animationSpec = FlowMotion.standardSpring(),
        label = "fluid_cloud_progress",
    )

    val chipText = item.capsuleChipText(nowMillis)
    val locationText = item.rawLocationName()
    val phaseLabel = when {
        item.sourceType == LiveActivitySourceType.FOCUS && item.isBreakPhase -> "实况通知 · 专注休息"
        item.sourceType == LiveActivitySourceType.FOCUS -> "实况通知 · 沉浸专注"
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
    val glowAlpha by pulseTransition.animateFloat(
        initialValue = if (flowColors.isDark) 0.10f else 0.07f,
        targetValue = if (flowColors.isDark) 0.22f else 0.14f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "fluid_cloud_glow_alpha",
    )

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .defaultMinSize(minHeight = 48.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable {
                haptics.tick()
                onOpenPermissions()
            }
            .testTag("timetable_fluid_cloud_banner"),
        shape = RoundedCornerShape(16.dp),
        color = animatedContainerColor,
        border = BorderStroke(
            width = 1.dp,
            brush = Brush.horizontalGradient(
                colors = listOf(
                    animatedBorderColor,
                    animatedSecondary.copy(alpha = if (isInProgress) 0.42f else 0.26f),
                ),
            ),
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .drawBehind {
                    drawRect(
                        brush = Brush.horizontalGradient(
                            colors = listOf(
                                animatedAccent.copy(alpha = glowAlpha * 0.45f),
                                animatedSecondary.copy(alpha = glowAlpha * 0.25f),
                                Color.Transparent,
                            ),
                        ),
                    )
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                animatedAccent.copy(alpha = glowAlpha),
                                Color.Transparent,
                            ),
                            center = Offset(size.width * 0.88f, size.height * 0.2f),
                            radius = size.width * 0.42f,
                        ),
                        radius = size.width * 0.42f,
                        center = Offset(size.width * 0.88f, size.height * 0.2f),
                    )
                },
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
                    color = animatedAccent,
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
                        AnimatedContent(
                            targetState = item.sourceType to isInProgress,
                            transitionSpec = {
                                (fadeIn(tween(240, easing = FastOutSlowInEasing)) +
                                    scaleIn(initialScale = 0.86f, animationSpec = tween(240))) togetherWith
                                    (fadeOut(tween(160)) +
                                        scaleOut(targetScale = 0.86f, animationSpec = tween(160)))
                            },
                            label = "fluid_cloud_capsule_icon",
                        ) { (sourceType, _) ->
                            Icon(
                                imageVector = if (sourceType == LiveActivitySourceType.COURSE) {
                                    Icons.Rounded.School
                                } else {
                                    Icons.Rounded.NotificationsActive
                                },
                                contentDescription = null,
                                tint = colorScheme.onPrimary,
                                modifier = Modifier.size(13.dp),
                            )
                        }
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

                // Course name + @location + phase & remaining countdown
                Column(modifier = Modifier.weight(1f)) {
                    AnimatedContent(
                        targetState = item.uniqueKey to (item.title to locationText),
                        transitionSpec = {
                            (fadeIn(tween(260, easing = FastOutSlowInEasing)) +
                                slideInVertically(tween(240)) { it / 5 }) togetherWith
                                (fadeOut(tween(170, easing = FastOutSlowInEasing)) +
                                    slideOutVertically(tween(170)) { -it / 5 })
                        },
                        label = "fluid_cloud_title_transition",
                    ) { (_, titleAndLoc) ->
                        val (titleValue, locValue) = titleAndLoc
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text(
                                text = titleValue,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false),
                            )
                            AnimatedVisibility(
                                visible = locValue.isNotEmpty(),
                                enter = fadeIn(tween(200)),
                                exit = fadeOut(tween(150)),
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.LocationOn,
                                        contentDescription = null,
                                        tint = animatedAccent,
                                        modifier = Modifier.size(12.dp),
                                    )
                                    Text(
                                        text = locValue,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = animatedAccent,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    val remainingStr = DateTimeUtils.formatCountdownChinese(remainingMillis)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        AnimatedContent(
                            targetState = phaseLabel,
                            transitionSpec = {
                                (fadeIn(tween(260, easing = FastOutSlowInEasing)) +
                                    slideInVertically(tween(240)) { it / 3 }) togetherWith
                                    (fadeOut(tween(170, easing = FastOutSlowInEasing)) +
                                        slideOutVertically(tween(170)) { -it / 3 }) using
                                    SizeTransform(clip = false)
                            },
                            label = "fluid_cloud_phase_label",
                        ) { targetPhase ->
                            Text(
                                text = targetPhase,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = animatedAccent,
                                maxLines = 1,
                                softWrap = false,
                            )
                        }
                        val tailHint = if (permissionStatus.allRecommendedGranted) {
                            "· 剩余 $remainingStr"
                        } else {
                            "· 剩余 $remainingStr · 点击检查权限"
                        }
                        Text(
                            text = tailHint,
                            style = MaterialTheme.typography.labelSmall,
                            color = colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }

            // Sleek live progress track along the bottom edge of the Fluid Cloud banner
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
                    .padding(bottom = 6.dp)
                    .height(3.dp)
                    .clip(CircleShape)
                    .background(colorScheme.surfaceContainerHighest.copy(alpha = 0.7f)),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(animatedProgress.coerceIn(0.03f, 1f))
                        .height(3.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(animatedSecondary, animatedAccent),
                            ),
                        ),
                )
            }
        }
    }
}
