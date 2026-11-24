package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
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
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.BackupRestoreMode
import com.example.data.FullBackupPayload
import com.example.ui.theme.FlowTheme

@Composable
fun SelectablePillOption(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    showCheckIcon: Boolean = false,
    contentPadding: PaddingValues = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
) {
    val colorScheme = MaterialTheme.colorScheme
    val haptics = rememberAppHaptics()
    val containerColor = if (selected) colorScheme.primaryContainer else colorScheme.surfaceContainerLow
    val contentColor = if (selected) colorScheme.onPrimaryContainer else colorScheme.onSurfaceVariant
    val borderColor = if (selected) colorScheme.primary.copy(alpha = 0.55f) else colorScheme.outlineVariant

    Surface(
        onClick = {
            haptics.tick()
            onClick()
        },
        modifier = modifier.height(38.dp),
        shape = CircleShape,
        color = containerColor,
        contentColor = contentColor,
        border = BorderStroke(1.dp, borderColor),
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            if (showCheckIcon && selected) {
                Icon(
                    imageVector = Icons.Rounded.Check,
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(14.dp),
                )
                Spacer(modifier = Modifier.width(4.dp))
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontSize = 12.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                color = contentColor,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Clip,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
fun SwitchSettingRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    switchTestTag: String? = null,
    useLargeTitle: Boolean = false,
) {
    val colorScheme = MaterialTheme.colorScheme
    val haptics = rememberAppHaptics()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(if (useLargeTitle) Modifier.padding(vertical = 6.dp) else Modifier),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = 12.dp),
        ) {
            Text(
                text = title,
                style = if (useLargeTitle) MaterialTheme.typography.bodyLarge else MaterialTheme.typography.bodyMedium,
                fontWeight = if (useLargeTitle) FontWeight.Normal else FontWeight.Medium,
                color = colorScheme.onSurface,
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = colorScheme.onSurfaceVariant,
                )
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = {
                haptics.tick()
                onCheckedChange(it)
            },
            modifier = if (switchTestTag != null) Modifier.testTag(switchTestTag) else Modifier,
        )
    }
}

@Composable
fun SettingsGroupCard(
    icon: ImageVector,
    title: String,
    trailing: @Composable (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme
    val flowColors = FlowTheme.colors

    Surface(
        shape = MaterialTheme.shapes.large,
        color = flowColors.cardElevated,
        border = BorderStroke(1.dp, colorScheme.outlineVariant),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = colorScheme.onSurface,
                    )
                }
                trailing?.invoke()
            }
            HorizontalDivider(color = colorScheme.outlineVariant.copy(alpha = 0.65f))
            content()
        }
    }
}

@Composable
fun ExpandableSettingSection(
    icon: ImageVector,
    title: String,
    summary: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    headerTestTag: String? = null,
    summaryColor: Color? = null,
    trailingBadge: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme
    val flowColors = FlowTheme.colors
    val haptics = rememberAppHaptics()
    val arrowRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
        label = "expandable_section_arrow_rotation",
    )

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = flowColors.cardElevated,
        border = BorderStroke(1.dp, colorScheme.outlineVariant),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            val headerModifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 64.dp)
                .clickable {
                    haptics.tick()
                    onToggle()
                }
                .let { base ->
                    if (headerTestTag != null) base.testTag(headerTestTag) else base
                }
                .padding(horizontal = 16.dp, vertical = 14.dp)

            Row(
                modifier = headerModifier,
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(
                            colorScheme.primaryContainer.copy(
                                alpha = if (flowColors.isDark) 0.45f else 0.78f,
                            ),
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = colorScheme.primary,
                        modifier = Modifier.size(19.dp),
                    )
                }

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = summary,
                        style = MaterialTheme.typography.bodySmall,
                        color = summaryColor ?: colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                trailingBadge?.invoke()

                Icon(
                    imageVector = Icons.Rounded.KeyboardArrowDown,
                    contentDescription = if (expanded) "收起$title" else "展开$title",
                    tint = colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .size(22.dp)
                        .rotate(arrowRotation),
                )
            }

            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(
                    animationSpec = tween(durationMillis = 240, easing = FastOutSlowInEasing),
                ) + fadeIn(animationSpec = tween(durationMillis = 200)),
                exit = shrinkVertically(
                    animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing),
                ) + fadeOut(animationSpec = tween(durationMillis = 150)),
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    HorizontalDivider(color = colorScheme.outlineVariant.copy(alpha = 0.65f))
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        content = content,
                    )
                }
            }
        }
    }
}

@Composable
fun SettingsNavigationRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colorScheme = MaterialTheme.colorScheme
    val haptics = rememberAppHaptics()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .clickable {
                haptics.tick()
                onClick()
            }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = colorScheme.primary,
                modifier = Modifier.size(18.dp),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = colorScheme.onSurface,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Icon(
            imageVector = Icons.Rounded.ChevronRight,
            contentDescription = null,
            tint = colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
fun BackupRestoreConfirmDialog(
    payload: FullBackupPayload,
    onSelectMode: (BackupRestoreMode) -> Unit,
    onDismiss: () -> Unit,
    testTagPrefix: String = "backup_restore_",
) {
    val colorScheme = MaterialTheme.colorScheme
    val haptics = rememberAppHaptics()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "选择 JSON 备份恢复方式",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val lunchSuffix = if (payload.lunchBreakBackup != null) "及午休分隔带配置" else ""
                Text(
                    text = "已解析到 ${payload.timetables.size} 张课表、${payload.courses.size} 门课程、${payload.schedules.size} 条日程（含已完成记录）$lunchSuffix。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colorScheme.onSurface,
                )
                Text(
                    text = "• 智能合并（推荐）：保留现有本地数据并自动跳过完全重复的课程与日程。\n• 清空覆盖：先清空当前所有课表、节次与日程，再按备份完全还原。",
                    style = MaterialTheme.typography.bodySmall,
                    color = colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    haptics.confirm()
                    onSelectMode(BackupRestoreMode.SMART_MERGE)
                },
                modifier = Modifier.testTag("${testTagPrefix}merge"),
            ) {
                Text("智能合并")
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                TextButton(
                    onClick = {
                        haptics.tick()
                        onDismiss()
                    },
                    modifier = Modifier.testTag("${testTagPrefix}cancel"),
                ) {
                    Text("取消")
                }
                TextButton(
                    onClick = {
                        haptics.tick()
                        onSelectMode(BackupRestoreMode.OVERWRITE_ALL)
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = colorScheme.error),
                    modifier = Modifier.testTag("${testTagPrefix}overwrite"),
                ) {
                    Text("清空覆盖")
                }
            }
        },
    )
}
