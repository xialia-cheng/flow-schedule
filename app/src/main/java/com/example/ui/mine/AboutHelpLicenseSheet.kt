package com.example.ui.mine

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.HelpOutline
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.SystemUpdateAlt
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.BuildConfig
import com.example.R
import com.example.ui.components.ComprehensiveTutorialSections
import com.example.ui.components.rememberAppHaptics
import com.example.ui.theme.FlowTheme
import com.example.ui.theme.MonoFontFamily
import com.example.ui.update.AppUpdateUiState

private const val AUTHOR_NAME = "xialia cheng"
private const val AUTHOR_EMAIL = "zly080701@gmail.com"
private const val REPO_URL = "https://github.com/xialia-cheng/ripple-plan"

private val MIT_LICENSE_FULL_TEXT = """
MIT License

Copyright (c) 2026 xialia cheng

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
""".trimIndent()

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutHelpLicenseSheet(
    updateState: AppUpdateUiState = AppUpdateUiState(),
    onCheckUpdates: () -> Unit = {},
    onDismiss: () -> Unit,
    onShowMessage: (String) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val colorScheme = MaterialTheme.colorScheme
    val flowColors = FlowTheme.colors
    val context = LocalContext.current
    val haptics = rememberAppHaptics()
    var licenseExpanded by rememberSaveable { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp)
                .testTag("about_help_license_sheet"),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // Sheet Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "关于应用、使用教程与开源协议",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = colorScheme.onSurface,
                    )
                    Text(
                        text = "微澜日程（RipplePlan）· 全功能使用教程、防杀指南与开源信息",
                        style = MaterialTheme.typography.bodySmall,
                        color = colorScheme.onSurfaceVariant,
                    )
                }
                TextButton(
                    onClick = {
                        haptics.tick()
                        onDismiss()
                    },
                    modifier = Modifier.testTag("about_sheet_close"),
                ) {
                    Text("关闭")
                }
            }

            // 1. 关于应用与作者
            AboutSectionCard(
                icon = Icons.Rounded.Info,
                title = "1. 关于应用与作者",
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "微澜日程（RipplePlan）",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = colorScheme.onSurface,
                            )
                            Text(
                                text = "专注高校课表、日程倒计时与实时状态通知",
                                style = MaterialTheme.typography.bodySmall,
                                color = colorScheme.onSurfaceVariant,
                            )
                        }
                        Surface(
                            shape = CircleShape,
                            color = colorScheme.primaryContainer,
                        ) {
                            Text(
                                text = "v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                                fontFamily = MonoFontFamily,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = colorScheme.onPrimaryContainer,
                                modifier = Modifier
                                    .padding(horizontal = 9.dp, vertical = 4.dp)
                                    .testTag("about_version_text"),
                            )
                        }
                    }

                    HorizontalDivider(color = colorScheme.outlineVariant)

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Person,
                            contentDescription = "作者",
                            tint = colorScheme.primary,
                            modifier = Modifier.size(18.dp),
                        )
                        Text(
                            text = "作者：$AUTHOR_NAME",
                            style = MaterialTheme.typography.bodyMedium,
                            color = colorScheme.onSurface,
                        )
                    }

                    Surface(
                        shape = MaterialTheme.shapes.medium,
                        color = colorScheme.surfaceContainerLow,
                        border = BorderStroke(1.dp, colorScheme.outlineVariant),
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = 48.dp)
                            .clip(MaterialTheme.shapes.medium)
                            .clickable {
                                haptics.tick()
                                val intent = Intent(Intent.ACTION_SENDTO).apply {
                                    data = Uri.parse("mailto:$AUTHOR_EMAIL")
                                    putExtra(Intent.EXTRA_SUBJECT, "微澜日程（RipplePlan v${BuildConfig.VERSION_NAME}）反馈")
                                }
                                runCatching { context.startActivity(intent) }.onFailure {
                                    onShowMessage("未检测到系统邮件应用，请手动联系 $AUTHOR_EMAIL")
                                }
                            }
                            .testTag("about_email_button"),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Email,
                                    contentDescription = "联系邮箱",
                                    tint = colorScheme.primary,
                                    modifier = Modifier.size(18.dp),
                                )
                                Column {
                                    Text(
                                        text = "联系邮箱",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = colorScheme.onSurfaceVariant,
                                    )
                                    Text(
                                        text = AUTHOR_EMAIL,
                                        fontFamily = MonoFontFamily,
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Medium,
                                        color = colorScheme.primary,
                                    )
                                }
                            }
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.OpenInNew,
                                contentDescription = "打开系统邮件应用",
                                tint = colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }

                    Surface(
                        shape = MaterialTheme.shapes.medium,
                        color = colorScheme.surfaceContainerLow,
                        border = BorderStroke(1.dp, colorScheme.outlineVariant),
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = 48.dp)
                            .clip(MaterialTheme.shapes.medium)
                            .clickable {
                                haptics.tick()
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(REPO_URL))
                                runCatching { context.startActivity(intent) }.onFailure {
                                    onShowMessage("无法打开浏览器，请访问：$REPO_URL")
                                }
                            }
                            .testTag("about_repo_button"),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Code,
                                    contentDescription = "开源仓库",
                                    tint = flowColors.liveAccent,
                                    modifier = Modifier.size(18.dp),
                                )
                                Column {
                                    Text(
                                        text = "GitHub 开源仓库",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = colorScheme.onSurfaceVariant,
                                    )
                                    Text(
                                        text = REPO_URL,
                                        fontFamily = MonoFontFamily,
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Medium,
                                        color = colorScheme.primary,
                                    )
                                }
                            }
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.OpenInNew,
                                contentDescription = "在浏览器中打开仓库",
                                tint = colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }

                    Surface(
                        shape = MaterialTheme.shapes.medium,
                        color = colorScheme.surfaceContainerLow,
                        border = BorderStroke(1.dp, colorScheme.outlineVariant),
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = 48.dp)
                            .clip(MaterialTheme.shapes.medium)
                            .clickable(enabled = !updateState.isChecking) {
                                haptics.tick()
                                onCheckUpdates()
                            }
                            .testTag("about_check_update_button"),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Box {
                                    Icon(
                                        imageVector = Icons.Rounded.SystemUpdateAlt,
                                        contentDescription = "检查版本更新",
                                        tint = colorScheme.primary,
                                        modifier = Modifier.size(18.dp),
                                    )
                                    if (updateState.availableRelease != null) {
                                        Box(
                                            modifier = Modifier
                                                .size(7.dp)
                                                .align(Alignment.TopEnd)
                                                .background(colorScheme.error, CircleShape)
                                                .testTag("about_check_update_red_dot"),
                                        )
                                    }
                                }
                                Column {
                                    Text(
                                        text = if (updateState.availableRelease != null) {
                                            "检查更新 · 发现新版本 v${updateState.availableRelease.versionName}"
                                        } else {
                                            "检查更新（当前 v${BuildConfig.VERSION_NAME}）"
                                        },
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.SemiBold,
                                        color = colorScheme.onSurface,
                                    )
                                    Text(
                                        text = when {
                                            updateState.isChecking -> "正在双路竞速请求 GitHub Releases 公开版本信息..."
                                            updateState.availableRelease != null -> "点击查看更新日志并前往浏览器下载"
                                            !updateState.lastStatusHint.isNullOrBlank() -> updateState.lastStatusHint
                                            else -> "双路竞速读取 GitHub 公开 Releases 元数据"
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                            if (updateState.isChecking) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.OpenInNew,
                                    contentDescription = "检查版本更新",
                                    tint = colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                        }
                    }
                }
            }

            // 2. 详尽使用教程与避坑指南
            AboutSectionCard(
                icon = Icons.AutoMirrored.Rounded.HelpOutline,
                title = "2. 详尽使用教程与避坑指南",
            ) {
                ComprehensiveTutorialSections(
                    onCopyText = { label, text ->
                        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        cm.setPrimaryClip(ClipData.newPlainText(label, text))
                        onShowMessage("已复制$label，可直接粘贴或发给 AI 整理课表")
                    },
                )
            }

            // 3. 隐私与网络说明
            AboutSectionCard(
                icon = Icons.Rounded.Lock,
                title = "3. 隐私与网络说明（本地优先 · 零数据收集）",
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    HelpBulletItem(
                        heading = "本地优先 · 零数据收集",
                        body = "您的全部课表、节次作息、日程记录、专注统计与提醒设置均仅保存在设备本地（Room / DataStore），无自建后端服务器、无广告 SDK，也无任何统计埋点或后台数据上传。以下列出全部唯二可控的联网场景：",
                    )
                    HelpBulletItem(
                        heading = "检查版本更新（可控联网场景 1/2）",
                        body = "检查更新及下载仅通过官方 GitHub 或所选 HTTPS 公共镜像获取公开 Release，不上传任何个人数据；在「我的」页面关闭“启动时自动检查更新”后即可保持完全零联网运行。",
                    )
                    HelpBulletItem(
                        heading = "自定义 AI 解析（可控联网场景 2/2）",
                        body = "默认关闭。仅当您主动开启「自定义 AI」并填写自己的 https:// 接口地址与密钥时，才会发送单次输入文本、当前日期时间与默认学期总周数，绝不会上传本地已有课表或日程。",
                    )
                    HelpBulletItem(
                        heading = "Android 自动备份凭据隔离",
                        body = "已在系统自动备份与数据提取规则中明确排除本地加密偏好文件与运行态缓存，防止敏感凭据或 API Key 密文同步至云端；密钥经由 Android Keystore AES-256 硬件级加密保存。",
                    )
                }
            }

            // 4. 开源协议（MIT License）
            AboutSectionCard(
                icon = Icons.Rounded.Description,
                title = "4. 开源协议（MIT License）",
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "本项目遵循 MIT License 开源许可协议发布：",
                        style = MaterialTheme.typography.bodySmall,
                        color = colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = "Copyright (c) 2026 $AUTHOR_NAME",
                        fontFamily = MonoFontFamily,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        color = colorScheme.onSurface,
                    )

                    Surface(
                        shape = MaterialTheme.shapes.medium,
                        color = colorScheme.surfaceContainerLow,
                        border = BorderStroke(1.dp, colorScheme.outlineVariant),
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = 48.dp)
                            .clip(MaterialTheme.shapes.medium)
                            .clickable {
                                haptics.tick()
                                licenseExpanded = !licenseExpanded
                            }
                            .testTag("about_mit_toggle"),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                text = if (licenseExpanded) "收起 MIT License 全文" else "点击展开查看标准 MIT 协议全文",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = colorScheme.primary,
                            )
                            Icon(
                                imageVector = if (licenseExpanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                                contentDescription = if (licenseExpanded) "收起协议全文" else "展开协议全文",
                                tint = colorScheme.primary,
                            )
                        }
                    }

                    AnimatedVisibility(
                        visible = licenseExpanded,
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut(),
                    ) {
                        Surface(
                            shape = MaterialTheme.shapes.small,
                            color = colorScheme.surfaceContainerHighest.copy(alpha = 0.65f),
                            border = BorderStroke(1.dp, colorScheme.outlineVariant),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("about_mit_full_text"),
                        ) {
                            Text(
                                text = MIT_LICENSE_FULL_TEXT,
                                fontFamily = MonoFontFamily,
                                fontSize = 11.sp,
                                lineHeight = 16.sp,
                                color = colorScheme.onSurface,
                                modifier = Modifier.padding(12.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AboutSectionCard(
    icon: ImageVector,
    title: String,
    content: @Composable () -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme
    val flowColors = FlowTheme.colors

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = flowColors.cardElevated,
        border = BorderStroke(1.dp, colorScheme.outlineVariant),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(
                    modifier = Modifier.size(28.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = colorScheme.primary,
                        modifier = Modifier.size(20.dp),
                    )
                }
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = colorScheme.onSurface,
                )
            }
            content()
        }
    }
}

@Composable
private fun HelpBulletItem(
    heading: String,
    body: String,
) {
    val colorScheme = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(
            text = "• $heading",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = colorScheme.onSurface,
        )
        Text(
            text = body,
            style = MaterialTheme.typography.bodySmall,
            lineHeight = 18.sp,
            color = colorScheme.onSurfaceVariant,
        )
    }
}
