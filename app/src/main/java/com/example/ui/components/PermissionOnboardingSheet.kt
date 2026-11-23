package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DoNotDisturbOn
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.notification.LiveUpdatePermissionStatus
import com.example.ui.theme.FlowTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PermissionOnboardingSheet(
    status: LiveUpdatePermissionStatus,
    onRequestPostNotifications: () -> Unit,
    onOpenPromotedSettings: () -> Unit,
    onOpenExactAlarmSettings: () -> Unit,
    onOpenDndPolicySettings: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val colorScheme = MaterialTheme.colorScheme
    val flowColors = FlowTheme.colors

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = colorScheme.surface,
        contentColor = colorScheme.onSurface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .navigationBarsPadding(),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.AutoAwesome,
                        contentDescription = null,
                        tint = colorScheme.primary,
                        modifier = Modifier.size(24.dp),
                    )
                }
                Column {
                    Text(
                        text = stringResource(R.string.permission_guide_title),
                        style = MaterialTheme.typography.headlineSmall,
                        color = colorScheme.onSurface,
                    )
                    Text(
                        text = "ColorOS 17 流体云 · Android 16+ Live Updates",
                        style = MaterialTheme.typography.bodySmall,
                        color = colorScheme.primary,
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = stringResource(R.string.permission_guide_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = colorScheme.onSurfaceVariant,
            )

            Spacer(modifier = Modifier.height(16.dp))

            PermissionRowCard(
                icon = Icons.Rounded.NotificationsActive,
                title = stringResource(R.string.permission_post_notifications_title),
                description = stringResource(R.string.permission_post_notifications_desc),
                isGranted = status.hasPostNotifications,
                actionLabel = if (status.hasPostNotifications) {
                    stringResource(R.string.permission_granted)
                } else {
                    stringResource(R.string.permission_missing)
                },
                onAction = onRequestPostNotifications,
                buttonTag = "perm_post_notifications_button",
            )

            Spacer(modifier = Modifier.height(10.dp))

            val promotedGranted = if (status.isAndroid16OrAbove) {
                status.canPostPromotedNotifications
            } else {
                status.hasPostNotifications
            }
            val promotedActionText = when {
                !status.isAndroid16OrAbove -> stringResource(R.string.permission_compat_mode)
                status.canPostPromotedNotifications -> stringResource(R.string.permission_granted)
                else -> stringResource(R.string.permission_missing)
            }
            PermissionRowCard(
                icon = Icons.Rounded.Tune,
                title = stringResource(R.string.permission_promoted_title),
                description = stringResource(R.string.permission_promoted_desc),
                isGranted = promotedGranted,
                actionLabel = promotedActionText,
                onAction = onOpenPromotedSettings,
                buttonTag = "perm_promoted_button",
            )

            Spacer(modifier = Modifier.height(10.dp))

            PermissionRowCard(
                icon = Icons.Rounded.Alarm,
                title = stringResource(R.string.permission_exact_alarm_title),
                description = stringResource(R.string.permission_exact_alarm_desc),
                isGranted = status.canScheduleExactAlarms,
                actionLabel = if (status.canScheduleExactAlarms) {
                    stringResource(R.string.permission_granted)
                } else {
                    stringResource(R.string.permission_missing)
                },
                onAction = onOpenExactAlarmSettings,
                buttonTag = "perm_exact_alarm_button",
            )

            Spacer(modifier = Modifier.height(10.dp))

            PermissionRowCard(
                icon = Icons.Rounded.DoNotDisturbOn,
                title = stringResource(R.string.permission_dnd_title),
                description = stringResource(R.string.permission_dnd_desc),
                isGranted = status.hasDndPolicyAccess,
                actionLabel = if (status.hasDndPolicyAccess) {
                    stringResource(R.string.permission_granted)
                } else {
                    stringResource(R.string.permission_missing)
                },
                onAction = onOpenDndPolicySettings,
                buttonTag = "perm_dnd_policy_button",
            )

            if (status.lastNotificationPromotable != null || status.isAndroid16OrAbove || !status.isChannelEligibleForLiveUpdates) {
                Spacer(modifier = Modifier.height(14.dp))
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = flowColors.cardElevated,
                    border = BorderStroke(1.dp, colorScheme.outlineVariant),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Live Updates 协议诊断",
                                style = MaterialTheme.typography.labelLarge,
                                color = colorScheme.onSurface,
                            )
                            Text(
                                text = when {
                                    !status.isChannelEligibleForLiveUpdates ->
                                        "通知渠道重要性过低（静默最小化），系统将拒绝晋升至流体云胶囊，请在系统通知设置恢复高优先级"
                                    status.isSystemPromotedOngoing ->
                                        "FLAG_PROMOTED_ONGOING 已激活：当前课程/日程正以流体云胶囊跨应用运行"
                                    status.lastNotificationPromotable == true ->
                                        "ProgressStyle + EXTRA_REQUEST_PROMOTED_ONGOING 已通过 Android 16 晋升校验，临近课程将自动弹出胶囊"
                                    status.lastNotificationPromotable == false ->
                                        "通知已发送，请在系统设置确认「实时活动 / 流体云」开关已开启"
                                    else ->
                                        "已接入 Android 16 Live Updates API 与跨应用定时唤醒，点击「测试流体云效果」立即体验课程胶囊"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = colorScheme.onSurfaceVariant,
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(
                            imageVector = Icons.Rounded.CheckCircle,
                            contentDescription = null,
                            tint = when {
                                !status.isChannelEligibleForLiveUpdates -> colorScheme.error
                                status.isSystemPromotedOngoing || status.lastNotificationPromotable == true -> flowColors.completedAccent
                                status.lastNotificationPromotable == false -> flowColors.upcomingAccent
                                else -> flowColors.secondaryAccent
                            },
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Surface(
                shape = MaterialTheme.shapes.medium,
                color = colorScheme.surfaceContainerLow,
                border = BorderStroke(1.dp, colorScheme.outlineVariant),
            ) {
                Text(
                    text = "跨机型兼容说明：本应用基于 Android 16 原生 Live Updates 标准协议构建，已在 ColorOS 17 流体云真机完成调优；在其他支持 Android 16 实况通知的机型可自动适配，在 Android 15 及以下或未开放胶囊的系统中会自动降级为高优先级常驻进度倒计时通知。",
                    style = MaterialTheme.typography.bodySmall,
                    color = colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                )
            }

            Spacer(modifier = Modifier.height(18.dp))

            Button(
                onClick = onDismiss,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("permission_guide_done_button"),
                shape = MaterialTheme.shapes.medium,
                colors = ButtonDefaults.buttonColors(
                    containerColor = colorScheme.primary,
                    contentColor = colorScheme.onPrimary,
                ),
            ) {
                Text(
                    text = stringResource(R.string.permission_done_btn),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun PermissionRowCard(
    icon: ImageVector,
    title: String,
    description: String,
    isGranted: Boolean,
    actionLabel: String,
    onAction: () -> Unit,
    buttonTag: String,
) {
    val colorScheme = MaterialTheme.colorScheme
    val flowColors = FlowTheme.colors

    Surface(
        shape = MaterialTheme.shapes.medium,
        color = flowColors.cardElevated,
        border = BorderStroke(
            width = 1.dp,
            color = if (isGranted) {
                flowColors.completedAccent.copy(alpha = 0.4f)
            } else {
                colorScheme.outlineVariant
            },
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(colorScheme.surfaceContainerHigh),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (isGranted) flowColors.completedAccent else colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = colorScheme.onSurface,
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = colorScheme.onSurfaceVariant,
                )
            }

            FilledTonalButton(
                onClick = onAction,
                modifier = Modifier
                    .height(40.dp)
                    .testTag(buttonTag),
                shape = CircleShape,
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp),
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = if (isGranted) {
                        flowColors.completedAccent.copy(alpha = 0.14f)
                    } else {
                        colorScheme.primaryContainer
                    },
                    contentColor = if (isGranted) {
                        flowColors.completedAccent
                    } else {
                        colorScheme.onPrimaryContainer
                    },
                ),
            ) {
                Text(
                    text = actionLabel,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}
