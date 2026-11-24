package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.CourseColorPalette
import com.example.data.CustomScheduleTag
import com.example.data.CustomScheduleTagCodec
import com.example.ui.theme.MonoFontFamily

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CustomTagManagerDialog(
    customTags: List<CustomScheduleTag>,
    onAddTag: (name: String, colorHex: String) -> Unit,
    onUpdateTag: (id: String, name: String, colorHex: String) -> Unit,
    onDeleteTag: (id: String) -> Unit,
    onDismiss: () -> Unit,
    onSelectTag: ((CustomScheduleTag) -> Unit)? = null,
) {
    val colorScheme = MaterialTheme.colorScheme
    val haptics = rememberAppHaptics()
    var editingTagId by remember { mutableStateOf<String?>(null) }
    var nameInput by remember { mutableStateOf("") }
    var selectedHex by remember { mutableStateOf(CustomScheduleTagCodec.presetColorHexes.first()) }
    var hexText by remember { mutableStateOf(CustomScheduleTagCodec.presetColorHexes.first()) }
    var hexError by remember { mutableStateOf(false) }

    val resolvedPreviewColor = CourseColorPalette.parseHexColorOrNull(selectedHex)
        ?: colorScheme.primary

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colorScheme.surfaceContainerHigh,
        title = {
            Text(
                text = if (editingTagId == null) "管理自定义标签" else "编辑自定义标签",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 460.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // Input form: tag name
                OutlinedTextField(
                    value = nameInput,
                    onValueChange = { if (it.length <= 14) nameInput = it },
                    label = { Text(if (editingTagId == null) "新建标签名称" else "标签名称") },
                    placeholder = { Text("例如：社团、考研、实验、兼职") },
                    singleLine = true,
                    leadingIcon = {
                        Box(
                            modifier = Modifier
                                .size(14.dp)
                                .clip(CircleShape)
                                .background(resolvedPreviewColor),
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("custom_tag_name_input"),
                    shape = MaterialTheme.shapes.medium,
                )

                // Color presets + custom Hex input
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "选择标签颜色",
                        style = MaterialTheme.typography.labelMedium,
                        color = colorScheme.onSurfaceVariant,
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        CustomScheduleTagCodec.presetColorHexes.forEach { hex ->
                            val color = CourseColorPalette.parseHexColorOrNull(hex) ?: colorScheme.primary
                            val isSelected = selectedHex.equals(hex, ignoreCase = true)
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(color)
                                    .border(
                                        width = if (isSelected) 2.5.dp else 1.dp,
                                        color = if (isSelected) colorScheme.onSurface else Color.White.copy(alpha = 0.5f),
                                        shape = CircleShape,
                                    )
                                    .clickable {
                                        haptics.tick()
                                        selectedHex = hex
                                        hexText = hex
                                        hexError = false
                                    }
                                    .testTag("custom_tag_color_${hex.removePrefix("#")}"),
                                contentAlignment = Alignment.Center,
                            ) {
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Rounded.Check,
                                        contentDescription = "已选颜色",
                                        tint = Color.White,
                                        modifier = Modifier.size(16.dp),
                                    )
                                }
                            }
                        }
                    }

                    OutlinedTextField(
                        value = hexText,
                        onValueChange = { raw ->
                            val cleaned = raw.trim().uppercase()
                            if (cleaned.length <= 7) {
                                hexText = cleaned
                                val parsed = CourseColorPalette.parseHexColorOrNull(cleaned)
                                if (parsed != null) {
                                    selectedHex = CourseColorPalette.formatHex(parsed)
                                    hexError = false
                                } else {
                                    hexError = cleaned.length >= 6
                                }
                            }
                        },
                        label = { Text("自定义 Hex 色值") },
                        placeholder = { Text("#0B72FF") },
                        singleLine = true,
                        isError = hexError,
                        textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = MonoFontFamily),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("custom_tag_hex_input"),
                        shape = MaterialTheme.shapes.medium,
                    )
                }

                // Submit / Cancel edit buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (editingTagId != null) {
                        OutlinedButton(
                            onClick = {
                                haptics.tick()
                                editingTagId = null
                                nameInput = ""
                                selectedHex = CustomScheduleTagCodec.presetColorHexes.first()
                                hexText = selectedHex
                                hexError = false
                            },
                            modifier = Modifier.weight(1f),
                        ) {
                            Text("取消编辑")
                        }
                    }
                    Button(
                        onClick = {
                            val trimmed = nameInput.trim()
                            if (trimmed.isEmpty()) return@Button
                            haptics.confirm()
                            val validHex = CourseColorPalette.parseHexColorOrNull(selectedHex)
                                ?.let { CourseColorPalette.formatHex(it) }
                                ?: CustomScheduleTagCodec.presetColorHexes.first()
                            val currentEditId = editingTagId
                            if (currentEditId != null) {
                                onUpdateTag(currentEditId, trimmed, validHex)
                                editingTagId = null
                            } else {
                                onAddTag(trimmed, validHex)
                            }
                            nameInput = ""
                            hexError = false
                        },
                        enabled = nameInput.isNotBlank() && !hexError,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("custom_tag_submit_button"),
                    ) {
                        Icon(
                            imageVector = if (editingTagId == null) Icons.Rounded.Add else Icons.Rounded.Check,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(if (editingTagId == null) "添加标签" else "保存修改")
                    }
                }

                HorizontalDivider(color = colorScheme.outlineVariant)

                Text(
                    text = "已创建的自定义标签（${customTags.size}）",
                    style = MaterialTheme.typography.labelMedium,
                    color = colorScheme.onSurfaceVariant,
                )

                if (customTags.isEmpty()) {
                    Text(
                        text = "暂无自定义标签，可在上方输入名称与颜色一键创建",
                        style = MaterialTheme.typography.bodySmall,
                        color = colorScheme.onSurfaceVariant,
                    )
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        customTags.forEach { tag ->
                            val tagColor = tag.resolvedColor()
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = colorScheme.surface,
                                border = BorderStroke(
                                    width = if (editingTagId == tag.id) 1.5.dp else 1.dp,
                                    color = if (editingTagId == tag.id) tagColor else colorScheme.outlineVariant,
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable(enabled = onSelectTag != null) {
                                        haptics.tick()
                                        onSelectTag?.invoke(tag)
                                    }
                                    .testTag("custom_tag_row_${tag.id}"),
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(start = 12.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Row(
                                        modifier = Modifier.weight(1f),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(12.dp)
                                                .clip(CircleShape)
                                                .background(tagColor),
                                        )
                                        Text(
                                            text = tag.name,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            color = colorScheme.onSurface,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        IconButton(
                                            onClick = {
                                                haptics.tick()
                                                editingTagId = tag.id
                                                nameInput = tag.name
                                                selectedHex = tag.colorHex
                                                hexText = tag.colorHex
                                                hexError = false
                                            },
                                            modifier = Modifier.testTag("custom_tag_edit_${tag.id}"),
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.Edit,
                                                contentDescription = "编辑标签 ${tag.name}",
                                                tint = colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(18.dp),
                                            )
                                        }
                                        IconButton(
                                            onClick = {
                                                haptics.tick()
                                                if (editingTagId == tag.id) {
                                                    editingTagId = null
                                                    nameInput = ""
                                                }
                                                onDeleteTag(tag.id)
                                            },
                                            modifier = Modifier.testTag("custom_tag_delete_${tag.id}"),
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.DeleteOutline,
                                                contentDescription = "删除标签 ${tag.name}",
                                                tint = colorScheme.error,
                                                modifier = Modifier.size(18.dp),
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                haptics.tick()
                onDismiss()
            }) {
                Text("完成", fontWeight = FontWeight.Bold)
            }
        },
    )
}
