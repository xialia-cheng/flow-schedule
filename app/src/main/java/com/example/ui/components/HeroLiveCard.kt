package com.example.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Snooze
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.CustomScheduleTag
import com.example.data.DateTimeUtils
import com.example.data.RepeatRule
import com.example.data.ScheduleEntity
import com.example.ui.theme.FlowMotion
import com.example.ui.theme.FlowTheme
import com.example.ui.theme.MonoFontFamily

@Composable
fun HeroLiveCard(
    schedule: ScheduleEntity?,
    nowMillis: Long,
    isLiveActive: Boolean,
    customTags: List<CustomScheduleTag> = emptyList(),
    onComplete: (ScheduleEntity) -> Unit,
    onSnooze10Min: (ScheduleEntity) -> Unit,
    onEdit: (ScheduleEntity) -> Unit,
    modifier: Modifier = Modifier,
) {
    var retainedSchedule by remember { mutableStateOf(schedule) }
    if (schedule != null) {
        retainedSchedule = schedule
    }

    AnimatedContent(
        targetState = schedule != null,
        modifier = modifier,
        transitionSpec = {
            val enter = fadeIn(
                animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing),
            ) + expandVertically(
                animationSpec = FlowMotion.standardSpring(),
                expandFrom = Alignment.Top,
            ) + scaleIn(
                initialScale = 0.97f,
                animationSpec = tween(durationMillis = 280, easing = FastOutSlowInEasing),
            )
            val exit = fadeOut(
                animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
            ) + shrinkVertically(
                animationSpec = tween(durationMillis = 240, easing = FastOutSlowInEasing),
                shrinkTowards = Alignment.Top,
            ) + scaleOut(
                targetScale = 0.97f,
                animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing),
            )
            (enter togetherWith exit).using(SizeTransform(clip = false))
        },
        label = "hero_card_presence_transition",
    ) { hasSchedule ->
        val activeItem = if (hasSchedule) (schedule ?: retainedSchedule) else null
        if (activeItem == null) {
            HeroIdleCard()
        } else {
            HeroActiveCard(
                schedule = activeItem,
                nowMillis = nowMillis,
                isLiveActive = isLiveActive,
                customTags = customTags,
                onComplete = onComplete,
                onSnooze10Min = onSnooze10Min,
                onEdit = onEdit,
            )
        }
    }
}

@Composable
private fun HeroIdleCard(modifier: Modifier = Modifier) {
    val flowColors = FlowTheme.colors
    val colorScheme = MaterialTheme.colorScheme
    val cardShape = MaterialTheme.shapes.large

    val idleModifier = if (!flowColors.isDark) {
        modifier
            .fillMaxWidth()
            .shadow(
                elevation = 4.dp,
                shape = cardShape,
                ambientColor = flowColors.cardShadowColor,
                spotColor = flowColors.cardShadowColor,
            )
    } else {
        modifier.fillMaxWidth()
    }

    Surface(
        modifier = idleModifier.testTag("hero_idle_card"),
        shape = cardShape,
        color = flowColors.cardElevated,
        border = BorderStroke(1.dp, colorScheme.outlineVariant),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(colorScheme.surfaceContainerHigh),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.Schedule,
                    contentDescription = null,
                    tint = colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(22.dp),
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "当前暂无进行中或临近日程",
                    style = MaterialTheme.typography.titleSmall,
                    color = colorScheme.onSurface,
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "日程进入提前提醒窗口或开始时，将自动激活状态栏实况胶囊或实时进度通知",
                    style = MaterialTheme.typography.bodySmall,
                    color = colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun HeroActiveCard(
    schedule: ScheduleEntity,
    nowMillis: Long,
    isLiveActive: Boolean,
    customTags: List<CustomScheduleTag>,
    onComplete: (ScheduleEntity) -> Unit,
    onSnooze10Min: (ScheduleEntity) -> Unit,
    onEdit: (ScheduleEntity) -> Unit,
    modifier: Modifier = Modifier,
) {
    val flowColors = FlowTheme.colors
    val colorScheme = MaterialTheme.colorScheme
    val haptics = rememberAppHaptics()
    val cardShape = MaterialTheme.shapes.large

    val isInProgress = schedule.isInProgress(nowMillis)
    val isInReminder = schedule.isInReminderWindow(nowMillis)
    val targetAccentColor = when {
        isInProgress -> flowColors.liveAccent
        isInReminder -> flowColors.upcomingAccent
        else -> flowColors.secondaryAccent
    }
    val accentColor by animateColorAsState(
        targetValue = targetAccentColor,
        animationSpec = tween(durationMillis = 480, easing = FastOutSlowInEasing),
        label = "hero_accent_color",
    )
    val secondaryGradientColor by animateColorAsState(
        targetValue = if (isInProgress) flowColors.secondaryAccent else targetAccentColor.copy(alpha = 0.72f),
        animationSpec = tween(durationMillis = 480, easing = FastOutSlowInEasing),
        label = "hero_secondary_gradient_color",
    )
    val borderAlpha by animateFloatAsState(
        targetValue = if (isLiveActive) 0.55f else 0.28f,
        animationSpec = tween(durationMillis = 420, easing = FastOutSlowInEasing),
        label = "hero_border_alpha",
    )
    val glowAlpha by animateFloatAsState(
        targetValue = if (isLiveActive) 0.14f else 0.06f,
        animationSpec = tween(durationMillis = 420, easing = FastOutSlowInEasing),
        label = "hero_glow_alpha",
    )

    val targetMillis = if (isInProgress) schedule.endTimeMillis else schedule.startTimeMillis
    val remainingMillis = (targetMillis - nowMillis).coerceAtLeast(0L)
    val countdownDigital = DateTimeUtils.formatCountdownDigital(remainingMillis)
    val progressTarget = schedule.uiProgressFraction(nowMillis)
    val animatedProgress by animateFloatAsState(
        targetValue = progressTarget,
        animationSpec = FlowMotion.standardSpring(),
        label = "hero_progress",
    )

    val pulseTransition = rememberInfiniteTransition(label = "fluid_pulse")
    val pulseAlpha by pulseTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulse_alpha",
    )

    val statusPillText = when {
        schedule.isTestSchedule && isInProgress -> "实况通知测试 · 进行中"
        isInProgress -> "实况通知激活 · 进行中"
        isInReminder -> "实况通知激活 · 即将开始"
        else -> "下一项待办"
    }

    val countdownPrefix = if (isInProgress) "剩余" else "距开始"

    val elevatedModifier = if (!flowColors.isDark) {
        modifier
            .fillMaxWidth()
            .shadow(
                elevation = if (isLiveActive) 10.dp else 4.dp,
                shape = cardShape,
                ambientColor = flowColors.cardShadowColor,
                spotColor = flowColors.cardShadowColor,
            )
    } else {
        modifier.fillMaxWidth()
    }

    Surface(
        modifier = elevatedModifier
            .testTag("hero_live_card")
            .clip(cardShape)
            .clickable {
                haptics.tick()
                onEdit(schedule)
            },
        shape = cardShape,
        color = flowColors.cardElevated,
        border = BorderStroke(
            width = 1.dp,
            brush = Brush.linearGradient(
                colors = listOf(
                    accentColor.copy(alpha = borderAlpha),
                    colorScheme.outlineVariant,
                ),
            ),
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .drawBehind {
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                accentColor.copy(alpha = glowAlpha),
                                Color.Transparent,
                            ),
                            center = Offset(size.width * 0.85f, size.height * 0.15f),
                            radius = size.width * 0.72f,
                        ),
                        radius = size.width * 0.72f,
                        center = Offset(size.width * 0.85f, size.height * 0.15f),
                    )
                }
                .padding(20.dp),
        ) {
            // ColorOS 17 signature dark Fluid Cloud status capsule (contrasts cleanly on both Light & Dark cards)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Surface(
                    shape = CircleShape,
                    color = flowColors.fluidCapsuleBg,
                    border = BorderStroke(1.dp, accentColor.copy(alpha = 0.48f)),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(
                                    if (isLiveActive) accentColor.copy(alpha = pulseAlpha) else accentColor,
                                ),
                        )
                        AnimatedContent(
                            targetState = statusPillText,
                            transitionSpec = {
                                (fadeIn(tween(260, easing = FastOutSlowInEasing)) +
                                    slideInVertically(tween(240)) { it / 3 }) togetherWith
                                    (fadeOut(tween(180, easing = FastOutSlowInEasing)) +
                                        slideOutVertically(tween(180)) { -it / 3 }) using
                                    SizeTransform(clip = false)
                            },
                            label = "hero_status_pill_text",
                        ) { targetPill ->
                            Text(
                                text = targetPill,
                                style = MaterialTheme.typography.labelMedium,
                                color = accentColor,
                                maxLines = 1,
                                softWrap = false,
                            )
                        }
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    AnimatedContent(
                        targetState = countdownPrefix,
                        transitionSpec = {
                            fadeIn(tween(220)) togetherWith fadeOut(tween(160))
                        },
                        label = "hero_countdown_prefix",
                    ) { targetPrefix ->
                        Text(
                            text = targetPrefix,
                            style = MaterialTheme.typography.labelMedium,
                            color = colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        text = countdownDigital,
                        fontFamily = MonoFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = accentColor,
                        modifier = Modifier.testTag("hero_countdown_text"),
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            AnimatedContent(
                targetState = schedule.id to schedule.title,
                transitionSpec = {
                    fadeIn(tween(260, easing = FastOutSlowInEasing)) togetherWith
                        fadeOut(tween(180, easing = FastOutSlowInEasing))
                },
                label = "hero_schedule_title",
            ) { (_, titleText) ->
                Text(
                    text = titleText,
                    style = MaterialTheme.typography.headlineSmall,
                    color = colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val tagLabel = schedule.resolvedTagLabel(customTags)
                val tagColor = schedule.resolvedTagColor(customTags)
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = tagColor.copy(alpha = if (flowColors.isDark) 0.18f else 0.12f),
                ) {
                    Text(
                        text = tagLabel,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = tagColor,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
                Text(
                    text = DateTimeUtils.formatTimeRange(schedule.startTimeMillis, schedule.endTimeMillis),
                    fontFamily = MonoFontFamily,
                    fontSize = 13.sp,
                    color = colorScheme.onSurfaceVariant,
                )
                if (schedule.repeatRule != RepeatRule.NONE) {
                    MetadataDot()
                    Text(
                        text = schedule.repeatRule.label,
                        style = MaterialTheme.typography.bodySmall,
                        color = colorScheme.onSurfaceVariant,
                    )
                }
                if (schedule.reminderMinutesBefore > 0) {
                    MetadataDot()
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.NotificationsActive,
                            contentDescription = null,
                            tint = colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(13.dp),
                        )
                        Text(
                            text = "提前${schedule.reminderMinutesBefore}分",
                            style = MaterialTheme.typography.bodySmall,
                            color = colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            if (schedule.notes.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = schedule.notes,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(8.dp)
                        .clip(CircleShape)
                        .background(colorScheme.surfaceContainerHigh),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(animatedProgress.coerceIn(0.03f, 1f))
                            .height(8.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.horizontalGradient(
                                    colors = listOf(
                                        secondaryGradientColor,
                                        accentColor,
                                    ),
                                ),
                            ),
                    )
                }
                Text(
                    text = "${(animatedProgress * 100).toInt()}%",
                    fontFamily = MonoFontFamily,
                    fontWeight = FontWeight.Medium,
                    fontSize = 12.sp,
                    color = colorScheme.onSurfaceVariant,
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Button(
                    onClick = {
                        haptics.confirm()
                        onComplete(schedule)
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("hero_complete_button"),
                    shape = MaterialTheme.shapes.medium,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = accentColor,
                        contentColor = if (flowColors.isDark) Color(0xFF041D18) else Color.White,
                    ),
                    contentPadding = PaddingValues(horizontal = 12.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Check,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.action_complete),
                        style = MaterialTheme.typography.labelLarge,
                    )
                }

                FilledTonalButton(
                    onClick = {
                        haptics.tick()
                        onSnooze10Min(schedule)
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("hero_snooze_button"),
                    shape = MaterialTheme.shapes.medium,
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = colorScheme.surfaceContainerHigh,
                        contentColor = colorScheme.onSurface,
                    ),
                    contentPadding = PaddingValues(horizontal = 12.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Snooze,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.action_snooze_10m),
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
        }
    }
}

@Composable
private fun MetadataDot() {
    Box(
        modifier = Modifier
            .size(3.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.outline),
    )
}
