package com.example.ui.timetable

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.DataObject
import androidx.compose.material.icons.rounded.FileOpen
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.SaveAlt
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.TableChart
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.BackupRestoreMode
import com.example.data.FullBackupPayload
import com.example.data.TimetableImportExport
import com.example.ui.components.BackupRestoreConfirmDialog
import com.example.ui.components.rememberAppHaptics
import com.example.ui.theme.FlowTheme
import com.example.ui.theme.MonoFontFamily
import kotlinx.coroutines.launch

private enum class ImportMode(
    val label: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
) {
    CSV("CSV/Excel", Icons.Rounded.TableChart),
    HTML("HTML课表", Icons.Rounded.Code),
    SHARE_CODE("分享口令", Icons.Rounded.Key),
    JSON_BACKUP("JSON备份", Icons.Rounded.DataObject),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimetableImportSheet(
    onImportCsv: (rawText: String, replaceExisting: Boolean) -> Unit,
    onImportHtml: (htmlText: String, replaceExisting: Boolean) -> Unit,
    onImportShareCode: (code: String, createNewTimetable: Boolean) -> Unit,
    onGenerateBackupJson: suspend () -> String,
    onRestoreBackupJson: (jsonText: String, mode: BackupRestoreMode) -> Unit,
    onNotify: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val colorScheme = MaterialTheme.colorScheme
    val flowColors = FlowTheme.colors
    val haptics = rememberAppHaptics()

    var selectedMode by remember { mutableStateOf(ImportMode.CSV) }
    var textInput by remember { mutableStateOf("") }
    var replaceExisting by remember { mutableStateOf(false) }
    var createNewForCode by remember { mutableStateOf(true) }
    var pendingBackupPreview by remember { mutableStateOf<Pair<String, FullBackupPayload>?>(null) }

    // Zero-permission Storage Access Framework file pickers
    val openFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        runCatching {
            context.contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use {
                it.readText()
            }
        }.onSuccess { content ->
            if (!content.isNullOrBlank()) {
                when (selectedMode) {
                    ImportMode.JSON_BACKUP -> {
                        val parsed = TimetableImportExport.parseFullBackupJson(content)
                        if (parsed == null) {
                            onNotify("JSON 备份格式无效，原有数据未作任何改动")
                        } else {
                            pendingBackupPreview = content to parsed
                        }
                    }
                    else -> textInput = content
                }
            }
        }.onFailure {
            onNotify("读取文件失败，请确认文件编码为 UTF-8")
        }
    }

    val createBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            runCatching {
                val json = onGenerateBackupJson()
                context.contentResolver.openOutputStream(uri)?.bufferedWriter(Charsets.UTF_8)?.use {
                    it.write(json)
                }
            }.onSuccess {
                onNotify("本地 JSON 备份已导出保存")
                onDismiss()
            }.onFailure {
                onNotify("导出备份失败")
            }
        }
    }

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
                .navigationBarsPadding()
                .imePadding(),
        ) {
            Text(
                text = "导入课表与备份",
                style = MaterialTheme.typography.headlineSmall,
                color = colorScheme.onSurface,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "纯本地解析，所有数据仅保存在本机，无需账号登录",
                style = MaterialTheme.typography.bodySmall,
                color = colorScheme.onSurfaceVariant,
            )

            Spacer(modifier = Modifier.height(14.dp))

            // 4-mode selector
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                ImportMode.entries.forEach { mode ->
                    val selected = selectedMode == mode
                    FilterChip(
                        selected = selected,
                        onClick = {
                            haptics.tick()
                            selectedMode = mode
                            textInput = ""
                        },
                        label = {
                            Text(
                                text = mode.label,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("import_mode_${mode.name.lowercase()}"),
                        shape = CircleShape,
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = colorScheme.primaryContainer,
                            selectedLabelColor = colorScheme.onPrimaryContainer,
                        ),
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            when (selectedMode) {
                ImportMode.CSV -> {
                    Text(
                        text = "支持逗号（CSV）/ 制表符（Excel 粘贴）、WakeUp 课表导出、二维课表矩阵，以及节次（1-2节）或精确时间（08:00-09:40）自动匹配",
                        style = MaterialTheme.typography.bodySmall,
                        color = colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilledTonalButton(
                            onClick = {
                                haptics.tick()
                                openFileLauncher.launch(arrayOf("text/*", "text/csv", "*/*"))
                            },
                            shape = CircleShape,
                        ) {
                            Icon(Icons.Rounded.FileOpen, null, Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("选择文件")
                        }
                        OutlinedButton(
                            onClick = {
                                haptics.tick()
                                textInput = TimetableImportExport.SAMPLE_CSV_TEMPLATE
                            },
                            shape = CircleShape,
                            modifier = Modifier.testTag("fill_sample_csv_button"),
                        ) {
                            Text("填入格式示例")
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = textInput,
                        onValueChange = { textInput = it },
                        label = { Text("粘贴 CSV 或 Excel 表格文本") },
                        minLines = 5,
                        maxLines = 8,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("import_csv_input"),
                        shape = MaterialTheme.shapes.medium,
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = "覆盖当前课表已有课程",
                            style = MaterialTheme.typography.bodyMedium,
                            color = colorScheme.onSurface,
                        )
                        Switch(
                            checked = replaceExisting,
                            onCheckedChange = {
                                haptics.tick()
                                replaceExisting = it
                            },
                        )
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                    Button(
                        onClick = {
                            haptics.tick()
                            onImportCsv(textInput, replaceExisting)
                            onDismiss()
                        },
                        enabled = textInput.isNotBlank(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("confirm_import_csv_button"),
                        shape = MaterialTheme.shapes.medium,
                    ) {
                        Text("解析并导入 CSV 课表", fontWeight = FontWeight.Bold)
                    }
                }

                ImportMode.HTML -> {
                    Text(
                        text = "选择或粘贴教务系统导出的 HTML 课表表格源码，系统将自动识别单元格中的课程、地点、教师与单双周信息。",
                        style = MaterialTheme.typography.bodySmall,
                        color = colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    FilledTonalButton(
                        onClick = {
                            haptics.tick()
                            openFileLauncher.launch(arrayOf("text/html", "text/*", "*/*"))
                        },
                        shape = CircleShape,
                    ) {
                        Icon(Icons.Rounded.FileOpen, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("选择本地 HTML 文件")
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = textInput,
                        onValueChange = { textInput = it },
                        label = { Text("粘贴 HTML 课表源码（含 <tr>/<td> 表格）") },
                        minLines = 5,
                        maxLines = 8,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("import_html_input"),
                        shape = MaterialTheme.shapes.medium,
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text("覆盖当前课表已有课程", style = MaterialTheme.typography.bodyMedium)
                        Switch(checked = replaceExisting, onCheckedChange = {
                            haptics.tick()
                            replaceExisting = it
                        })
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                    Button(
                        onClick = {
                            haptics.tick()
                            onImportHtml(textInput, replaceExisting)
                            onDismiss()
                        },
                        enabled = textInput.isNotBlank(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("confirm_import_html_button"),
                        shape = MaterialTheme.shapes.medium,
                    ) {
                        Text("解析并导入 HTML 课表", fontWeight = FontWeight.Bold)
                    }
                }

                ImportMode.SHARE_CODE -> {
                    Text(
                        text = "粘贴好友分享的「FLOWTIMETABLE://...」口令，一键还原完整课程与上下课时间。",
                        style = MaterialTheme.typography.bodySmall,
                        color = colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    FilledTonalButton(
                        onClick = {
                            haptics.tick()
                            val clip = readClipboardText(context)
                            if (clip.isNullOrBlank()) {
                                onNotify("剪贴板为空")
                            } else {
                                textInput = clip
                            }
                        },
                        shape = CircleShape,
                    ) {
                        Icon(Icons.Rounded.ContentPaste, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("从剪贴板读取口令")
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = textInput,
                        onValueChange = { textInput = it },
                        label = { Text("粘贴课表分享口令") },
                        minLines = 3,
                        maxLines = 5,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("import_share_code_input"),
                        shape = MaterialTheme.shapes.medium,
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text("导入为独立新课表", style = MaterialTheme.typography.bodyMedium)
                        Switch(checked = createNewForCode, onCheckedChange = {
                            haptics.tick()
                            createNewForCode = it
                        })
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                    Button(
                        onClick = {
                            haptics.tick()
                            onImportShareCode(textInput, createNewForCode)
                            onDismiss()
                        },
                        enabled = textInput.isNotBlank(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("confirm_import_code_button"),
                        shape = MaterialTheme.shapes.medium,
                    ) {
                        Text("识别并导入分享口令", fontWeight = FontWeight.Bold)
                    }
                }

                ImportMode.JSON_BACKUP -> {
                    Surface(
                        shape = MaterialTheme.shapes.medium,
                        color = flowColors.cardElevated,
                        border = BorderStroke(1.dp, colorScheme.outlineVariant),
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Text(
                                text = "本地完整 JSON 备份",
                                style = MaterialTheme.typography.titleSmall,
                                color = colorScheme.onSurface,
                            )
                            Text(
                                text = "导出范围：全部多课表、课程、1–16 节次作息及全部日程（含已完成），不含 AI API Key 密钥。",
                                style = MaterialTheme.typography.bodySmall,
                                color = colorScheme.onSurfaceVariant,
                            )
                            Surface(
                                shape = MaterialTheme.shapes.small,
                                color = colorScheme.tertiaryContainer.copy(alpha = 0.55f),
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 10.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.Top,
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.WarningAmber,
                                        contentDescription = null,
                                        tint = colorScheme.onTertiaryContainer,
                                        modifier = Modifier.size(16.dp),
                                    )
                                    Text(
                                        text = "明文风险提示：JSON 备份以明文存储（含上课教室、教师与日程备注），请妥善保管并避免上传至公开网络。",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = colorScheme.onTertiaryContainer,
                                    )
                                }
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Button(
                                    onClick = {
                                        haptics.tick()
                                        createBackupLauncher.launch("ripple_plan_backup.json")
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp)
                                        .testTag("export_json_backup_button"),
                                    shape = MaterialTheme.shapes.medium,
                                ) {
                                    Icon(Icons.Rounded.SaveAlt, null, Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("导出备份")
                                }
                                FilledTonalButton(
                                    onClick = {
                                        haptics.tick()
                                        openFileLauncher.launch(arrayOf("application/json", "text/*", "*/*"))
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp)
                                        .testTag("import_json_backup_button"),
                                    shape = MaterialTheme.shapes.medium,
                                ) {
                                    Icon(Icons.Rounded.FileOpen, null, Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("导入恢复")
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))
        }
    }

    pendingBackupPreview?.let { (rawJson, payload) ->
        BackupRestoreConfirmDialog(
            payload = payload,
            onSelectMode = { mode ->
                onRestoreBackupJson(rawJson, mode)
                pendingBackupPreview = null
                onDismiss()
            },
            onDismiss = { pendingBackupPreview = null },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimetableShareSheet(
    timetableName: String,
    courseCount: Int,
    shareCode: String,
    shareSummaryText: String,
    onCopied: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val context = LocalContext.current
    val colorScheme = MaterialTheme.colorScheme
    val flowColors = FlowTheme.colors
    val haptics = rememberAppHaptics()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = colorScheme.surface,
        contentColor = colorScheme.onSurface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .navigationBarsPadding(),
        ) {
            Text(
                text = "分享课表",
                style = MaterialTheme.typography.headlineSmall,
                color = colorScheme.onSurface,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "$timetableName · 共 $courseCount 门课程",
                style = MaterialTheme.typography.bodySmall,
                color = colorScheme.onSurfaceVariant,
            )

            Spacer(modifier = Modifier.height(14.dp))

            Surface(
                shape = MaterialTheme.shapes.medium,
                color = flowColors.cardElevated,
                border = BorderStroke(1.dp, colorScheme.outlineVariant),
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "课表分享口令",
                        style = MaterialTheme.typography.labelMedium,
                        color = colorScheme.primary,
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = shareCode,
                        fontFamily = MonoFontFamily,
                        style = MaterialTheme.typography.bodySmall,
                        color = colorScheme.onSurfaceVariant,
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Button(
                    onClick = {
                        haptics.tick()
                        copyTextToClipboard(context, "课表分享口令", shareCode)
                        onCopied("已复制课表分享口令")
                        onDismiss()
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp)
                        .testTag("copy_share_code_button"),
                    shape = MaterialTheme.shapes.medium,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colorScheme.primary,
                        contentColor = colorScheme.onPrimary,
                    ),
                ) {
                    Icon(Icons.Rounded.ContentCopy, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("复制口令", fontWeight = FontWeight.Bold)
                }

                FilledTonalButton(
                    onClick = {
                        haptics.tick()
                        val sendIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_SUBJECT, timetableName)
                            putExtra(Intent.EXTRA_TEXT, shareSummaryText)
                        }
                        runCatching {
                            context.startActivity(Intent.createChooser(sendIntent, "分享课表"))
                        }
                        onDismiss()
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp)
                        .testTag("system_share_timetable_button"),
                    shape = MaterialTheme.shapes.medium,
                ) {
                    Icon(Icons.Rounded.Share, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("系统分享")
                }
            }

            Spacer(modifier = Modifier.height(18.dp))
        }
    }
}

private fun copyTextToClipboard(context: Context, label: String, text: String) {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText(label, text))
}

private fun readClipboardText(context: Context): String? {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip = cm.primaryClip ?: return null
    if (clip.itemCount == 0) return null
    return clip.getItemAt(0).coerceToText(context)?.toString()
}
