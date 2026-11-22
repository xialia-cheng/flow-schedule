package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.NotificationsNone
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.CustomScheduleTag
import com.example.data.DateTimeUtils
import com.example.data.RepeatRule
import com.example.data.ScheduleCategory
import com.example.data.ScheduleEntity
import com.example.ui.theme.FlowScheduleTheme
import com.example.ui.theme.FlowTheme
import com.example.ui.theme.MonoFontFamily

/**
 * ColorOS 17 Aquamorphic section header for chronological schedule groups (上午/下午/晚间/日期/已完成).
 */
@Composable
fun ScheduleListSectionHeader(
    title: String,
    count: Int,
    isCompletedGroup: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val flowColors = FlowTheme.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 6.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(
                    if (isCompletedGroup) {
                        flowColors.completedAccent
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                ),
        )
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = if (isCompletedGroup) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                MaterialTheme.colorScheme.onBackground
            },
        )
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            Text(
                text = "$count",
                fontFamily = MonoFontFamily,
                fontWeight = FontWeight.Medium,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            )
        }
        Spacer(modifier = Modifier.weight(1f))
    }
}

/**
 * ColorOS 17 Aquamorphic Timeline Schedule Row:
 * Left monospace time rail + smooth vertical timeline node/connector + elevated 22dp card with swipe-to-delete.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleItemCard(
    schedule: ScheduleEntity,
    nowMillis: Long,
    showDateBadge: Boolean,
    isFirstInGroup: Boolean = true,
    isLastInGroup: Boolean = true,
    customTags: List<CustomScheduleTag> = emptyList(),
    onToggleComplete: (ScheduleEntity) -> Unit,
    onDelete: (ScheduleEntity) -> Unit,
    onClick: (ScheduleEntity) -> Unit,
    modifier: Modifier = Modifier,
) {
    val flowColors = FlowTheme.colors
    val colorScheme = MaterialTheme.colorScheme
    val tagLabel = schedule.resolvedTagLabel(customTags)
    val tagColor = schedule.resolvedTagColor(customTags)

    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart) {
                onDelete(schedule)
                true
            } else {
                false
            }
        },
        positionalThreshold = { totalDistance -> totalDistance * 0.35f },
    )

    val isInProgress = schedule.isInProgress(nowMillis)
    val isImminent = schedule.isInReminderWindow(nowMillis)
    val accent = when {
        schedule.isCompleted -> flowColors.completedAccent
        isInProgress -> flowColors.liveAccent
        isImminent -> flowColors.upcomingAccent
        else -> tagColor
    }

    val pulseTransition = rememberInfiniteTransition(label = "timeline_node_pulse")
    val nodePulseAlpha by pulseTransition.animateFloat(
        initialValue = 0.25f,
        targetValue = 0.7f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "node_pulse_alpha",
    )

    val cardShape = RoundedCornerShape(22.dp)

    SwipeToDismissBox(
        state = dismissState,
        modifier = modifier
            .fillMaxWidth()
            .testTag("schedule_item_${schedule.id}"),
        enableDismissFromStartToEnd = false,
        enableDismissFromEndToStart = true,
        backgroundContent = {
            val isSwiping = dismissState.targetValue == SwipeToDismissBoxValue.EndToStart ||
                dismissState.dismissDirection == SwipeToDismissBoxValue.EndToStart
            val bgColor by animateColorAsState(
                targetValue = if (isSwiping) flowColors.deleteContainer else Color.Transparent,
                label = "swipe_bg",
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = 66.dp, top = 2.dp, bottom = 2.dp)
                    .clip(cardShape)
                    .background(bgColor)
                    .padding(horizontal = 24.dp),
                contentAlignment = Alignment.CenterEnd,
            ) {
                if (isSwiping) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.action_delete),
                            style = MaterialTheme.typography.labelLarge,
                            color = flowColors.deleteAccent,
                        )
                        Icon(
                            imageVector = Icons.Rounded.DeleteOutline,
                            contentDescription = stringResource(R.string.cd_delete_schedule),
                            tint = flowColors.deleteAccent,
                        )
                    }
                }
            }
        },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Left Time Column (ColorOS 17 tabular monospace start & end time)
            Column(
                modifier = Modifier
                    .width(48.dp)
                    .alpha(if (schedule.isCompleted) 0.5f else 1f),
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = DateTimeUtils.formatTime(schedule.startTimeMillis),
                    fontFamily = MonoFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = if (isInProgress || isImminent) accent else colorScheme.onBackground,
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = DateTimeUtils.formatTime(schedule.endTimeMillis),
                    fontFamily = MonoFontFamily,
                    fontWeight = FontWeight.Normal,
                    fontSize = 11.sp,
                    color = colorScheme.onSurfaceVariant,
                )
            }

            // Center Timeline Spine & Fluid Status Node
            Box(
                modifier = Modifier
                    .width(22.dp)
                    .fillMaxHeight(),
                contentAlignment = Alignment.Center,
            ) {
                val spineColor = flowColors.timelineSpine.copy(alpha = if (flowColors.isDark) 0.6f else 0.85f)
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val cx = size.width / 2f
                    val cy = size.height / 2f
                    val strokePx = 2.dp.toPx()
                    val nodeGap = 9.dp.toPx()

                    if (!isFirstInGroup) {
                        drawLine(
                            color = spineColor,
                            start = Offset(cx, 0f),
                            end = Offset(cx, (cy - nodeGap).coerceAtLeast(0f)),
                            strokeWidth = strokePx,
                            cap = StrokeCap.Round,
                        )
                    }
                    if (!isLastInGroup) {
                        drawLine(
                            color = spineColor,
                            start = Offset(cx, (cy + nodeGap).coerceAtMost(size.height)),
                            end = Offset(cx, size.height),
                            strokeWidth = strokePx,
                            cap = StrokeCap.Round,
                        )
                    }

                    if (isInProgress || isImminent) {
                        drawCircle(
                            color = accent.copy(alpha = nodePulseAlpha * 0.45f),
                            radius = 8.dp.toPx(),
                            center = Offset(cx, cy),
                        )
                    }
                    drawCircle(
                        color = accent,
                        radius = if (isInProgress || isImminent) 4.5.dp.toPx() else 3.5.dp.toPx(),
                        center = Offset(cx, cy),
                    )
                }
            }

            // Right ColorOS 17 Aquamorphic Elevated Schedule Card
            val cardModifier = if (!flowColors.isDark && !schedule.isCompleted) {
                Modifier
                    .weight(1f)
                    .padding(vertical = 3.dp)
                    .shadow(
                        elevation = if (isInProgress) 8.dp else 3.dp,
                        shape = cardShape,
                        ambientColor = flowColors.cardShadowColor,
                        spotColor = flowColors.cardShadowColor,
                    )
            } else {
                Modifier
                    .weight(1f)
                    .padding(vertical = 3.dp)
            }

            Surface(
                modifier = cardModifier
                    .clip(cardShape)
                    .clickable { onClick(schedule) },
                shape = cardShape,
                color = if (schedule.isCompleted) {
                    colorScheme.surfaceContainerLow
                } else {
                    flowColors.cardElevated
                },
                border = BorderStroke(
                    width = if (isInProgress || isImminent) 1.2.dp else 1.dp,
                    color = when {
                        isInProgress -> flowColors.liveAccent.copy(alpha = if (flowColors.isDark) 0.55f else 0.6f)
                        isImminent -> flowColors.upcomingAccent.copy(alpha = 0.5f)
                        else -> colorScheme.outlineVariant
                    },
                ),
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 14.dp, end = 6.dp, top = 12.dp, bottom = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // Subtle ColorOS vertical color pill indicator
                        Box(
                            modifier = Modifier
                                .width(3.5.dp)
                                .height(38.dp)
                                .clip(CircleShape)
                                .background(accent.copy(alpha = if (schedule.isCompleted) 0.35f else 0.92f)),
                        )

                        Spacer(modifier = Modifier.width(12.dp))

                        // Title + metadata tags
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .alpha(if (schedule.isCompleted) 0.52f else 1f),
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Text(
                                    text = schedule.title,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = colorScheme.onSurface,
                                    textDecoration = if (schedule.isCompleted) TextDecoration.LineThrough else null,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false),
                                )

                                if (isInProgress || isImminent) {
                                    Surface(
                                        shape = CircleShape,
                                        color = accent.copy(alpha = if (flowColors.isDark) 0.18f else 0.14f),
                                    ) {
                                        Text(
                                            text = if (isInProgress) "进行中" else "即将开始",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.SemiBold,
                                            color = accent,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(5.dp))

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                // Category / Custom tag pill
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = tagColor.copy(
                                        alpha = if (flowColors.isDark) 0.16f else 0.12f,
                                    ),
                                ) {
                                    Text(
                                        text = tagLabel,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = tagColor,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.5.dp),
                                    )
                                }

                                if (showDateBadge) {
                                    Text(
                                        text = DateTimeUtils.formatDateShort(schedule.startTimeMillis),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = colorScheme.onSurfaceVariant,
                                    )
                                }

                                if (schedule.repeatRule != RepeatRule.NONE) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Repeat,
                                            contentDescription = null,
                                            tint = colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(12.dp),
                                        )
                                        Text(
                                            text = schedule.repeatRule.label,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }

                                if (schedule.reminderMinutesBefore > 0) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.NotificationsNone,
                                            contentDescription = null,
                                            tint = colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(12.dp),
                                        )
                                        Text(
                                            text = "${schedule.reminderMinutesBefore}分",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            }

                            if (schedule.notes.isNotBlank()) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = schedule.notes,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }

                        // Right 48dp accessible completion checkbox
                        val toggleCd = stringResource(R.string.cd_toggle_complete)
                        Box(
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .clip(CircleShape)
                                .semantics { contentDescription = toggleCd }
                                .clickable(role = Role.Checkbox) { onToggleComplete(schedule) }
                                .testTag("schedule_checkbox_${schedule.id}"),
                            contentAlignment = Alignment.Center,
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (schedule.isCompleted) flowColors.completedAccent else Color.Transparent,
                                    )
                                    .border(
                                        width = 2.dp,
                                        color = if (schedule.isCompleted) {
                                            flowColors.completedAccent
                                        } else {
                                            colorScheme.outline
                                        },
                                        shape = CircleShape,
                                    ),
                                contentAlignment = Alignment.Center,
                            ) {
                                if (schedule.isCompleted) {
                                    Icon(
                                        imageVector = Icons.Rounded.Check,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(15.dp),
                                    )
                                }
                            }
                        }
                    }

                    // Subtle inline fluid progress hairline for actively running schedules
                    if (isInProgress) {
                        val progress = schedule.uiProgressFraction(nowMillis).coerceIn(0.04f, 1f)
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 14.dp, end = 14.dp, bottom = 10.dp)
                                .height(4.dp)
                                .clip(CircleShape)
                                .background(colorScheme.surfaceContainerHigh),
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(progress)
                                    .height(4.dp)
                                    .clip(CircleShape)
                                    .background(
                                        Brush.horizontalGradient(
                                            colors = listOf(
                                                flowColors.secondaryAccent,
                                                flowColors.liveAccent,
                                            ),
                                        ),
                                    ),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Preview(name = "ColorOS 17 Schedule List - Light", showBackground = true, backgroundColor = 0xFFF3F6FB)
@Composable
private fun PreviewScheduleListLight() {
    val now = 1_760_000_000_000L
    FlowScheduleTheme(darkTheme = false) {
        Column(
            modifier = Modifier
                .background(MaterialTheme.colorScheme.background)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            ScheduleListSectionHeader(title = "上午时段", count = 2)
            ScheduleItemCard(
                schedule = ScheduleEntity(
                    id = 1L,
                    title = "ColorOS 17 流体云交互评审",
                    notes = "三楼星河会议室 · 带上真机演示",
                    startTimeMillis = now - 15 * 60_000L,
                    endTimeMillis = now + 30 * 60_000L,
                    repeatRule = RepeatRule.WORKDAYS,
                    reminderMinutesBefore = 15,
                    category = ScheduleCategory.MEETING,
                ),
                nowMillis = now,
                showDateBadge = false,
                isFirstInGroup = true,
                isLastInGroup = false,
                onToggleComplete = {},
                onDelete = {},
                onClick = {},
            )
            ScheduleItemCard(
                schedule = ScheduleEntity(
                    id = 2L,
                    title = "深度专注：完成浅色主题细节打磨",
                    notes = "核对对比度与卡片圆角阴影",
                    startTimeMillis = now + 60 * 60_000L,
                    endTimeMillis = now + 120 * 60_000L,
                    repeatRule = RepeatRule.NONE,
                    reminderMinutesBefore = 10,
                    category = ScheduleCategory.FOCUS,
                ),
                nowMillis = now,
                showDateBadge = false,
                isFirstInGroup = false,
                isLastInGroup = true,
                onToggleComplete = {},
                onDelete = {},
                onClick = {},
            )
        }
    }
}

@Preview(name = "ColorOS 17 Schedule List - Dark", showBackground = true, backgroundColor = 0xFF090C12)
@Composable
private fun PreviewScheduleListDark() {
    val now = 1_760_000_000_000L
    FlowScheduleTheme(darkTheme = true) {
        Column(
            modifier = Modifier
                .background(MaterialTheme.colorScheme.background)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            ScheduleListSectionHeader(title = "上午时段", count = 2)
            ScheduleItemCard(
                schedule = ScheduleEntity(
                    id = 1L,
                    title = "ColorOS 17 流体云交互评审",
                    notes = "三楼星河会议室 · 带上真机演示",
                    startTimeMillis = now - 15 * 60_000L,
                    endTimeMillis = now + 30 * 60_000L,
                    repeatRule = RepeatRule.WORKDAYS,
                    reminderMinutesBefore = 15,
                    category = ScheduleCategory.MEETING,
                ),
                nowMillis = now,
                showDateBadge = false,
                isFirstInGroup = true,
                isLastInGroup = false,
                onToggleComplete = {},
                onDelete = {},
                onClick = {},
            )
            ScheduleItemCard(
                schedule = ScheduleEntity(
                    id = 2L,
                    title = "晚间有氧慢跑 5 公里",
                    notes = "公园环湖步道",
                    startTimeMillis = now + 60 * 60_000L,
                    endTimeMillis = now + 100 * 60_000L,
                    repeatRule = RepeatRule.DAILY,
                    reminderMinutesBefore = 15,
                    category = ScheduleCategory.HEALTH,
                    isCompleted = true,
                ),
                nowMillis = now,
                showDateBadge = false,
                isFirstInGroup = false,
                isLastInGroup = true,
                onToggleComplete = {},
                onDelete = {},
                onClick = {},
            )
        }
    }
}
