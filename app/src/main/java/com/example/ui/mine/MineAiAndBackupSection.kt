package com.example.ui.mine

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.FileUpload
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.NetworkCheck
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.SystemUpdateAlt
import androidx.compose.material.icons.rounded.Update
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.BuildConfig
import com.example.R
import com.example.data.CustomAiConfig
import com.example.smart.OpenAiCompatibleClient
import com.example.ui.components.SelectablePillOption
import com.example.ui.components.rememberAppHaptics
import com.example.ui.theme.FlowTheme
import com.example.ui.theme.MonoFontFamily
import com.example.ui.update.AppUpdateUiState

@Composable
internal fun AiAndBackupSectionContent(
    savedConfig: CustomAiConfig,
    isTesting: Boolean,
    testFeedback: String?,
    onSaveAiConfig: (enabled: Boolean, baseUrl: String, apiKey: String, model: String) -> Unit,
    onTestAiConnection: (baseUrl: String, apiKey: String, model: String) -> Unit,
    onClearAiConfig: () -> Unit,
    onExportJsonBackup: () -> Unit,
    onImportJsonBackup: () -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme
    val flowColors = FlowTheme.colors
    val haptics = rememberAppHaptics()

    // 1. Optional Custom OpenAI-compatible AI Config
    CustomAiSettingsBlock(
        savedConfig = savedConfig,
        isTesting = isTesting,
        testFeedback = testFeedback,
        onSave = onSaveAiConfig,
        onTestConnection = onTestAiConnection,
        onClear = onClearAiConfig,
    )

    HorizontalDivider(color = colorScheme.outlineVariant.copy(alpha = 0.7f))

    // 2. Local JSON Backup & Restore
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = "本地 JSON 数据备份与迁移",
            style = MaterialTheme.typography.titleSmall,
            color = colorScheme.onSurface,
        )
        Spacer(modifier = Modifier.height(4.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.small)
                .clickable {
                    haptics.tick()
                    onExportJsonBackup()
                }
                .padding(vertical = 10.dp)
                .testTag("mine_export_json_button"),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.FileUpload,
                    contentDescription = null,
                    tint = colorScheme.primary,
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "导出本地 JSON 备份",
                        style = MaterialTheme.typography.titleSmall,
                        color = colorScheme.onSurface,
                    )
                    Text(
                        text = "明文导出全部多课表、节次与全部日程（含已完成），不含 API Key，请妥善保管",
                        style = MaterialTheme.typography.bodySmall,
                        color = colorScheme.onSurfaceVariant,
                    )
                }
            }
            Icon(
                imageVector = Icons.Rounded.ChevronRight,
                contentDescription = null,
                tint = colorScheme.onSurfaceVariant,
            )
        }

        HorizontalDivider(color = colorScheme.outlineVariant.copy(alpha = 0.55f))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.small)
                .clickable {
                    haptics.tick()
                    onImportJsonBackup()
                }
                .padding(vertical = 10.dp)
                .testTag("mine_import_json_button"),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.FileDownload,
                    contentDescription = null,
                    tint = flowColors.liveAccent,
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "从本地 JSON 文件恢复",
                        style = MaterialTheme.typography.titleSmall,
                        color = colorScheme.onSurface,
                    )
                    Text(
                        text = "先校验文件结构再选择「智能合并」或「清空覆盖」，无需存储权限",
                        style = MaterialTheme.typography.bodySmall,
                        color = colorScheme.onSurfaceVariant,
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
}

@Composable
internal fun AdvancedAndAboutSectionContent(
    hasActiveTestSchedule: Boolean,
    updateState: AppUpdateUiState,
    onTriggerFluidCloudTest: () -> Unit,
    onStopFluidCloudTest: () -> Unit = {},
    onCheckUpdatesClick: () -> Unit,
    onAutoCheckUpdatesChange: (Boolean) -> Unit,
    onOpenAboutSheet: () -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme
    val flowColors = FlowTheme.colors
    val haptics = rememberAppHaptics()

    // 1. Live Update Debug & Drill Card (folded cleanly inside Group 4)
    FluidCloudTestCard(
        hasActiveTest = hasActiveTestSchedule,
        onTriggerTest = onTriggerFluidCloudTest,
        onStopTest = onStopFluidCloudTest,
    )

    HorizontalDivider(color = colorScheme.outlineVariant.copy(alpha = 0.7f))

    // 2. Check Updates Row
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .clickable {
                haptics.tick()
                onCheckUpdatesClick()
            }
            .padding(vertical = 8.dp)
            .testTag("mine_check_update_button"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box {
                Icon(
                    imageVector = Icons.Rounded.SystemUpdateAlt,
                    contentDescription = null,
                    tint = colorScheme.primary,
                )
                if (updateState.availableRelease != null) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .align(Alignment.TopEnd)
                            .background(colorScheme.error, CircleShape)
                            .testTag("mine_check_update_red_dot"),
                    )
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = "检查更新",
                        style = MaterialTheme.typography.titleSmall,
                        color = colorScheme.onSurface,
                    )
                    Surface(
                        shape = CircleShape,
                        color = if (updateState.availableRelease != null) {
                            colorScheme.errorContainer
                        } else {
                            colorScheme.surfaceContainerHighest
                        },
                    ) {
                        Text(
                            text = if (updateState.availableRelease != null) {
                                "新版本 v${updateState.availableRelease.versionName}"
                            } else {
                                "v${BuildConfig.VERSION_NAME}"
                            },
                            fontFamily = MonoFontFamily,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (updateState.availableRelease != null) {
                                colorScheme.onErrorContainer
                            } else {
                                colorScheme.onSurfaceVariant
                            },
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                        )
                    }
                }
                Text(
                    text = when {
                        updateState.isChecking -> "正在从 GitHub Releases 检查最新版本..."
                        updateState.availableRelease != null -> "点击查看更新日志并前往浏览器下载新版本"
                        !updateState.lastStatusHint.isNullOrBlank() -> updateState.lastStatusHint
                        else -> "仅向 GitHub Releases 发起只读检查，不上传个人数据"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = colorScheme.onSurfaceVariant,
                )
            }
        }
        if (updateState.isChecking) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                strokeWidth = 2.dp,
            )
        } else {
            Icon(
                imageVector = Icons.Rounded.ChevronRight,
                contentDescription = null,
                tint = colorScheme.onSurfaceVariant,
            )
        }
    }

    HorizontalDivider(color = colorScheme.outlineVariant.copy(alpha = 0.55f))

    // 3. Auto-check updates on startup switch
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .clickable {
                haptics.tick()
                onAutoCheckUpdatesChange(!updateState.autoCheckUpdates)
            }
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .padding(end = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                imageVector = Icons.Rounded.Update,
                contentDescription = null,
                tint = flowColors.liveAccent,
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "启动时自动检查更新",
                    style = MaterialTheme.typography.titleSmall,
                    color = colorScheme.onSurface,
                )
                Text(
                    text = "每 24 小时最多检查一次；关闭后可保持完全零联网运行",
                    style = MaterialTheme.typography.bodySmall,
                    color = colorScheme.onSurfaceVariant,
                )
            }
        }
        Switch(
            checked = updateState.autoCheckUpdates,
            onCheckedChange = {
                haptics.tick()
                onAutoCheckUpdatesChange(it)
            },
            modifier = Modifier.testTag("mine_auto_check_updates_switch"),
        )
    }

    HorizontalDivider(color = colorScheme.outlineVariant.copy(alpha = 0.55f))

    // 4. About App, Help & MIT Open Source License
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .clickable {
                haptics.tick()
                onOpenAboutSheet()
            }
            .padding(vertical = 8.dp)
            .testTag("mine_about_help_entry"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                imageVector = Icons.Rounded.Info,
                contentDescription = null,
                tint = colorScheme.primary,
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "关于应用、帮助与开源协议",
                    style = MaterialTheme.typography.titleSmall,
                    color = colorScheme.onSurface,
                )
                Text(
                    text = "v${BuildConfig.VERSION_NAME} · 作者、权限指南、隐私说明与 MIT 协议",
                    style = MaterialTheme.typography.bodySmall,
                    color = colorScheme.onSurfaceVariant,
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
private fun FluidCloudTestCard(
    hasActiveTest: Boolean,
    onTriggerTest: () -> Unit,
    onStopTest: () -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme
    val flowColors = FlowTheme.colors
    val haptics = rememberAppHaptics()
    val shape = MaterialTheme.shapes.medium

    val borderStart by animateColorAsState(
        targetValue = if (hasActiveTest) {
            flowColors.liveAccent.copy(alpha = 0.72f)
        } else {
            flowColors.secondaryAccent.copy(alpha = 0.50f)
        },
        animationSpec = tween(durationMillis = 360, easing = FastOutSlowInEasing),
        label = "fluid_test_border_start",
    )
    val borderEnd by animateColorAsState(
        targetValue = if (hasActiveTest) {
            colorScheme.primary.copy(alpha = 0.75f)
        } else {
            flowColors.liveAccent.copy(alpha = 0.50f)
        },
        animationSpec = tween(durationMillis = 360, easing = FastOutSlowInEasing),
        label = "fluid_test_border_end",
    )
    val containerColor by animateColorAsState(
        targetValue = if (hasActiveTest) {
            colorScheme.primaryContainer.copy(alpha = if (flowColors.isDark) 0.24f else 0.18f)
        } else {
            colorScheme.surfaceContainerLow
        },
        animationSpec = tween(durationMillis = 360, easing = FastOutSlowInEasing),
        label = "fluid_test_container",
    )

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .clickable(enabled = !hasActiveTest) {
                haptics.tick()
                onTriggerTest()
            }
            .testTag("mine_test_fluid_cloud_button"),
        shape = shape,
        color = containerColor,
        border = BorderStroke(
            width = 1.dp,
            brush = Brush.horizontalGradient(
                colors = listOf(borderStart, borderEnd),
            ),
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(colorScheme.primary.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.AutoAwesome,
                        contentDescription = null,
                        tint = colorScheme.primary,
                        modifier = Modifier.size(20.dp),
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.test_fluid_cloud_btn),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = colorScheme.onSurface,
                    )
                    AnimatedContent(
                        targetState = hasActiveTest,
                        transitionSpec = {
                            fadeIn(tween(240, easing = FastOutSlowInEasing)) togetherWith
                                fadeOut(tween(160, easing = FastOutSlowInEasing))
                        },
                        label = "fluid_test_subtitle",
                    ) { active ->
                        Text(
                            text = if (active) {
                                "1 分钟测试正在实况通知中倒计时，可随时停止或重发"
                            } else {
                                "实况通知调试与效果演练（含 @地点、分段进度条与快捷操作）"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = colorScheme.onSurfaceVariant,
                        )
                    }
                }
                AnimatedVisibility(
                    visible = !hasActiveTest,
                    enter = fadeIn(tween(220)) + scaleIn(initialScale = 0.85f, animationSpec = tween(220)),
                    exit = fadeOut(tween(160)) + scaleOut(targetScale = 0.85f, animationSpec = tween(160)),
                    label = "fluid_test_play_badge",
                ) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(colorScheme.primary),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.PlayArrow,
                            contentDescription = "发起实况通知测试",
                            tint = colorScheme.onPrimary,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }

            AnimatedVisibility(
                visible = hasActiveTest,
                enter = fadeIn(tween(260, easing = FastOutSlowInEasing)) +
                    expandVertically(
                        animationSpec = tween(260, easing = FastOutSlowInEasing),
                        expandFrom = Alignment.Top,
                    ),
                exit = fadeOut(tween(180, easing = FastOutSlowInEasing)) +
                    shrinkVertically(
                        animationSpec = tween(220, easing = FastOutSlowInEasing),
                        shrinkTowards = Alignment.Top,
                    ),
                label = "fluid_test_actions_visibility",
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilledTonalButton(
                        onClick = {
                            haptics.confirm()
                            onStopTest()
                        },
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .testTag("mine_stop_fluid_cloud_button"),
                        colors = androidx.compose.material3.ButtonDefaults.filledTonalButtonColors(
                            containerColor = colorScheme.errorContainer,
                            contentColor = colorScheme.onErrorContainer,
                        ),
                        shape = MaterialTheme.shapes.small,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Stop,
                            contentDescription = "停止实况通知测试",
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "停止测试",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            softWrap = false,
                        )
                    }

                    FilledTonalButton(
                        onClick = {
                            haptics.tick()
                            onTriggerTest()
                        },
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .testTag("mine_retrigger_fluid_cloud_button"),
                        shape = MaterialTheme.shapes.small,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.PlayArrow,
                            contentDescription = "重新发起实况通知测试",
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "重发测试",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            softWrap = false,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CustomAiSettingsBlock(
    savedConfig: CustomAiConfig,
    isTesting: Boolean,
    testFeedback: String?,
    onSave: (enabled: Boolean, baseUrl: String, apiKey: String, model: String) -> Unit,
    onTestConnection: (baseUrl: String, apiKey: String, model: String) -> Unit,
    onClear: () -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme
    val haptics = rememberAppHaptics()

    var enabled by remember { mutableStateOf(savedConfig.enabled) }
    var baseUrl by remember { mutableStateOf(savedConfig.baseUrl) }
    var apiKey by remember { mutableStateOf(savedConfig.apiKey) }
    var model by remember { mutableStateOf(savedConfig.model) }
    var showApiKey by remember { mutableStateOf(false) }

    LaunchedEffect(savedConfig) {
        enabled = savedConfig.enabled
        baseUrl = savedConfig.baseUrl
        apiKey = savedConfig.apiKey
        model = savedConfig.model
    }

    val isUrlValidHttps = baseUrl.isBlank() || OpenAiCompatibleClient.isValidHttpsUrl(baseUrl)
    val isConfiguredReady = enabled &&
        OpenAiCompatibleClient.isValidHttpsUrl(baseUrl) &&
        apiKey.isNotBlank() &&
        model.isNotBlank()
    val targetHostLabel = OpenAiCompatibleClient.extractHostOrNull(baseUrl) ?: "待填写 HTTPS 接口域名"

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("custom_ai_settings_card"),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.Psychology,
                    contentDescription = null,
                    tint = colorScheme.primary,
                )
                Column {
                    Text(
                        text = "OpenAI 兼容自然语言解析",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = colorScheme.onSurface,
                    )
                    Text(
                        text = "兼容 DeepSeek、通义千问等 /v1/chat/completions；未配置或断网时自动回退本地离线解析",
                        style = MaterialTheme.typography.bodySmall,
                        color = colorScheme.onSurfaceVariant,
                    )
                }
            }
            Switch(
                checked = enabled,
                onCheckedChange = {
                    haptics.tick()
                    enabled = it
                },
                modifier = Modifier.testTag("ai_enable_switch"),
            )
        }

        if (savedConfig.requiresLegacyKeyReentry) {
            Surface(
                shape = MaterialTheme.shapes.small,
                color = colorScheme.errorContainer,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("ai_legacy_key_warning"),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.WarningAmber,
                        contentDescription = null,
                        tint = colorScheme.error,
                        modifier = Modifier.size(18.dp),
                    )
                    Text(
                        text = "检测到旧版兼容格式密钥。为确保硬件级 Android Keystore 安全加密，请重新输入您的 API Key 并保存。",
                        style = MaterialTheme.typography.bodySmall,
                        color = colorScheme.onErrorContainer,
                    )
                }
            }
        }

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = CircleShape,
            color = if (isConfiguredReady) colorScheme.primaryContainer else colorScheme.secondaryContainer,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(
                            if (isConfiguredReady) colorScheme.primary else colorScheme.onSecondaryContainer,
                        ),
                )
                Text(
                    text = if (isConfiguredReady) "当前：优先自定义 AI ($targetHostLabel) · 断网回退离线" else "当前：纯本地离线解析",
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isConfiguredReady) colorScheme.onPrimaryContainer else colorScheme.onSecondaryContainer,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        Surface(
            shape = MaterialTheme.shapes.small,
            color = colorScheme.surfaceContainerLow,
            border = BorderStroke(1.dp, colorScheme.outlineVariant.copy(alpha = 0.7f)),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = "隐私与外发范围声明：仅支持 https:// 安全连接。开启后在“一句话创建”触发解析时，仅将当前输入框文本、当前日期时间与默认学期总周数发送至「$targetHostLabel」，绝不会上传本地已有课表、日程或其它配置。",
                style = MaterialTheme.typography.bodySmall,
                color = colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SelectablePillOption(
                label = "DeepSeek 预设",
                selected = baseUrl.contains("deepseek", ignoreCase = true),
                showCheckIcon = true,
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                onClick = {
                    enabled = true
                    baseUrl = "https://api.deepseek.com/v1"
                    model = "deepseek-chat"
                },
                modifier = Modifier
                    .weight(1f)
                    .testTag("ai_preset_deepseek"),
            )

            SelectablePillOption(
                label = "通义千问预设",
                selected = baseUrl.contains("dashscope", ignoreCase = true),
                showCheckIcon = true,
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                onClick = {
                    enabled = true
                    baseUrl = "https://dashscope.aliyuncs.com/compatible-mode/v1"
                    model = "qwen-plus"
                },
                modifier = Modifier
                    .weight(1f)
                    .testTag("ai_preset_qwen"),
            )
        }

        OutlinedTextField(
            value = baseUrl,
            onValueChange = { baseUrl = it },
            label = { Text("Base URL (仅限 https://)", maxLines = 1, softWrap = false) },
            placeholder = { Text("https://api.deepseek.com/v1", maxLines = 1, overflow = TextOverflow.Ellipsis) },
            isError = !isUrlValidHttps,
            supportingText = if (!isUrlValidHttps) {
                {
                    Text(
                        text = "为保护 API Key 与隐私数据，仅允许使用 https:// 协议接口地址",
                        color = colorScheme.error,
                    )
                }
            } else {
                null
            },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("ai_base_url_input"),
            shape = MaterialTheme.shapes.small,
        )

        OutlinedTextField(
            value = apiKey,
            onValueChange = { apiKey = it },
            label = { Text("API Key", maxLines = 1, softWrap = false) },
            placeholder = { Text("sk-...", maxLines = 1) },
            supportingText = {
                Text(
                    text = "由 Android Keystore AES-256 硬件级加密保存，不包含在导出备份中",
                    style = MaterialTheme.typography.labelSmall,
                    color = colorScheme.onSurfaceVariant,
                )
            },
            singleLine = true,
            visualTransformation = if (showApiKey) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                IconButton(onClick = {
                    haptics.tick()
                    showApiKey = !showApiKey
                }) {
                    Icon(
                        imageVector = if (showApiKey) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                        contentDescription = if (showApiKey) "隐藏密钥" else "显示密钥",
                    )
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("ai_api_key_input"),
            shape = MaterialTheme.shapes.small,
        )

        OutlinedTextField(
            value = model,
            onValueChange = { model = it },
            label = { Text("模型名称（Model）", maxLines = 1, softWrap = false) },
            placeholder = { Text("deepseek-chat / qwen-plus", maxLines = 1, overflow = TextOverflow.Ellipsis) },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("ai_model_input"),
            shape = MaterialTheme.shapes.small,
        )

        if (!testFeedback.isNullOrBlank()) {
            val isError = testFeedback.startsWith("连接失败")
            Surface(
                shape = MaterialTheme.shapes.small,
                color = if (isError) colorScheme.errorContainer else colorScheme.primaryContainer,
            ) {
                Text(
                    text = testFeedback,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isError) colorScheme.onErrorContainer else colorScheme.onPrimaryContainer,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(
                onClick = {
                    haptics.confirm()
                    onSave(enabled, baseUrl, apiKey, model)
                },
                enabled = isUrlValidHttps,
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .testTag("ai_save_config_button"),
                shape = MaterialTheme.shapes.medium,
            ) {
                Icon(
                    imageVector = Icons.Rounded.Save,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("保存配置", fontWeight = FontWeight.SemiBold)
            }

            FilledTonalButton(
                onClick = {
                    haptics.tick()
                    onTestConnection(baseUrl, apiKey, model)
                },
                enabled = !isTesting &&
                    OpenAiCompatibleClient.isValidHttpsUrl(baseUrl) &&
                    apiKey.isNotBlank() &&
                    model.isNotBlank(),
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .testTag("ai_test_connection_button"),
                shape = MaterialTheme.shapes.medium,
            ) {
                if (isTesting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("测试中...")
                } else {
                    Icon(
                        imageVector = Icons.Rounded.NetworkCheck,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("测试接口")
                }
            }
        }

        if (savedConfig.baseUrl.isNotBlank() || savedConfig.apiKey.isNotBlank() || savedConfig.model.isNotBlank()) {
            OutlinedButton(
                onClick = {
                    haptics.tick()
                    enabled = false
                    baseUrl = ""
                    apiKey = ""
                    model = ""
                    onClear()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .testTag("ai_clear_config_button"),
                shape = MaterialTheme.shapes.medium,
            ) {
                Icon(
                    imageVector = Icons.Rounded.DeleteOutline,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("清空配置并恢复纯本地离线模式")
            }
        }
    }
}
