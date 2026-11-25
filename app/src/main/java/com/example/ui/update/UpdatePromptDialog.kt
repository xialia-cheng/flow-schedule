package com.example.ui.update

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.NewReleases
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.BuildConfig
import com.example.data.UpdateMirrorSource
import com.example.ui.components.SelectablePillOption
import com.example.ui.components.rememberAppHaptics
import com.example.ui.theme.MonoFontFamily
import com.example.update.AppUpdateChecker
import com.example.update.ReleaseInfo

@Composable
fun UpdateMirrorChipRow(
    selectedSource: UpdateMirrorSource,
    onSelectSource: (UpdateMirrorSource) -> Unit,
    tagPrefix: String,
    modifier: Modifier = Modifier,
) {
    val colorScheme = MaterialTheme.colorScheme
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = "下载线路",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = colorScheme.onSurface,
            )
            Text(
                text = selectedSource.description,
                style = MaterialTheme.typography.labelSmall,
                color = colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            UpdateMirrorSource.entries.forEach { source ->
                SelectablePillOption(
                    label = source.label,
                    selected = source == selectedSource,
                    showCheckIcon = true,
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp),
                    onClick = { onSelectSource(source) },
                    modifier = Modifier
                        .weight(1f)
                        .minimumInteractiveComponentSize()
                        .testTag("${tagPrefix}_mirror_${source.name.lowercase()}"),
                )
            }
        }
    }
}

@Composable
fun UpdateAvailableDialog(
    release: ReleaseInfo,
    selectedMirrorSource: UpdateMirrorSource = UpdateMirrorSource.OFFICIAL_DIRECT,
    onSelectMirrorSource: (UpdateMirrorSource) -> Unit = {},
    onDismiss: () -> Unit,
    onShowMessage: (String) -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme
    val context = LocalContext.current
    val haptics = rememberAppHaptics()
    val resolvedDownloadUrl = AppUpdateChecker.resolveDownloadUrl(release, selectedMirrorSource)

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("update_available_dialog"),
        icon = {
            Icon(
                imageVector = Icons.Rounded.NewReleases,
                contentDescription = null,
                tint = colorScheme.primary,
            )
        },
        title = {
            Text(
                text = "发现新版本 v${release.versionName}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Surface(
                        shape = CircleShape,
                        color = colorScheme.surfaceContainerHighest,
                    ) {
                        Text(
                            text = "当前 v${BuildConfig.VERSION_NAME}",
                            fontFamily = MonoFontFamily,
                            style = MaterialTheme.typography.labelSmall,
                            color = colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        )
                    }
                    Text(
                        text = "→",
                        style = MaterialTheme.typography.labelMedium,
                        color = colorScheme.onSurfaceVariant,
                    )
                    Surface(
                        shape = CircleShape,
                        color = colorScheme.primaryContainer,
                    ) {
                        Text(
                            text = "最新 v${release.versionName}",
                            fontFamily = MonoFontFamily,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        )
                    }
                }

                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = colorScheme.surfaceContainerLow,
                    border = BorderStroke(1.dp, colorScheme.outlineVariant),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 200.dp),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                            .padding(12.dp),
                    ) {
                        Text(
                            text = release.releaseNotes,
                            style = MaterialTheme.typography.bodySmall,
                            lineHeight = 18.sp,
                            color = colorScheme.onSurface,
                        )
                    }
                }

                if (release.hasApkAsset) {
                    UpdateMirrorChipRow(
                        selectedSource = selectedMirrorSource,
                        onSelectSource = onSelectMirrorSource,
                        tagPrefix = "update_dialog",
                    )
                }

                Text(
                    text = if (release.hasApkAsset) {
                        "将通过「${selectedMirrorSource.label}」在系统浏览器下载公开 Release 安装包；若下载超时或失败，可点选上方另一条线路重试。"
                    } else {
                        "将打开 GitHub Releases 发布页面查看并下载最新版本。"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    haptics.tick()
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(resolvedDownloadUrl))
                    runCatching { context.startActivity(intent) }.onFailure {
                        onShowMessage("无法打开系统浏览器，可切换下载线路或访问：${release.releasePageUrl}")
                    }
                    onDismiss()
                },
                modifier = Modifier.testTag("update_dialog_browser_download"),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.OpenInNew,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("浏览器下载")
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (release.downloadUrl != release.releasePageUrl) {
                    TextButton(
                        onClick = {
                            haptics.tick()
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(release.releasePageUrl))
                            runCatching { context.startActivity(intent) }.onFailure {
                                onShowMessage("无法打开浏览器，请访问：${release.releasePageUrl}")
                            }
                        },
                        modifier = Modifier.testTag("update_dialog_release_page"),
                    ) {
                        Text("发布页")
                    }
                }
                TextButton(
                    onClick = {
                        haptics.tick()
                        onDismiss()
                    },
                    modifier = Modifier.testTag("update_dialog_dismiss"),
                ) {
                    Text("稍后再说")
                }
            }
        },
    )
}
