package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
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
import com.example.data.TagIconCatalog
import com.example.ui.theme.FlowTheme
import com.example.ui.theme.MonoFontFamily

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CustomTagManagerDialog(
    customTags: List<CustomScheduleTag>,
    onAddTag: (name: String, colorHex: String, iconKey: String) -> Unit,
    onUpdateTag: (id: String, name: String, colorHex: String, iconKey: String) -> Unit,
    onDeleteTag: (id: String) -> Unit,
    onDismiss: () -> Unit,
    initialEditingTagId: String? = null,
    onSelectTag: ((CustomScheduleTag) -> Unit)? = null,
) {
    val colorScheme = MaterialTheme.colorScheme
    val flowColors = FlowTheme.colors
    val haptics = rememberAppHaptics()

    val initialTag = remember(initialEditingTagId, customTags) {
        initialEditingTagId?.let { id -> customTags.firstOrNull { it.id == id } }
    }
    var editingTagId by remember(initialEditingTagId) { mutableStateOf(initialTag?.id) }
    var nameInput by remember(initialEditingTagId) { mutableStateOf(initialTag?.name.orEmpty()) }
    var selectedHex by remember(initialEditingTagId) {
        mutableStateOf(initialTag?.colorHex ?: CustomScheduleTagCodec.presetColorHexes.first())
    }
    var selectedIconKey by remember(initialEditingTagId) {
        mutableStateOf(initialTag?.iconKey ?: TagIconCatalog.options.first().key)
    }
    var hexText by remember(initialEditingTagId) {
        mutableStateOf(initialTag?.colorHex ?: CustomScheduleTagCodec.presetColorHexes.first())
    }
    var hexError by remember { mutableStateOf(false) }
    var showCustomHexField by remember { mutableStateOf(false) }

    val resolvedPreviewColor = CourseColorPalette.parseHexColorOrNull(selectedHex)
        ?: colorScheme.primary
    val resolvedPreviewIcon = TagIconCatalog.iconForKey(selectedIconKey)

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colorScheme.surfaceContainerHigh,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = if (editingTagId == null) "自定义日程标签" else "编辑日程标签",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = resolvedPreviewColor.copy(alpha = if (flowColors.isDark) 0.18f else 0.11f),
                    border = BorderStroke(1.dp, resolvedPreviewColor.copy(alpha = 0.35f)),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Icon(
                            imageVector = resolvedPreviewIcon,
                            contentDescription = null,
                            tint = resolvedPreviewColor,
                            modifier = Modifier.size(13.dp),
                        )
                        Text(
                            text = nameInput.trim().ifEmpty { "标签预览" },
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = resolvedPreviewColor,
                            maxLines = 1,
                        )
                    }
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 470.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedTextField(
                    value = nameInput,
                    onValueChange = { if (it.length <= 12) nameInput = it },
                    label = { Text(if (editingTagId == null) "标签名称" else "修改名称") },
                    placeholder = { Text("例如：科研、社团、备考、兼职") },
                    singleLine = true,
                    leadingIcon = {
                        Icon(
                            imageVector = resolvedPreviewIcon,
                            contentDescription = null,
                            tint = resolvedPreviewColor,
                            modifier = Modifier.size(18.dp),
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("custom_tag_name_input"),
                    shape = MaterialTheme.shapes.medium,
                )

                // Restrained icon picker (10 minimal Material icons)
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "选择图标",
                        style = MaterialTheme.typography.labelMedium,
                        color = colorScheme.onSurfaceVariant,
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        TagIconCatalog.options.forEach { option ->
                            val isSelected = selectedIconKey == option.key
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (isSelected) {
                                    resolvedPreviewColor.copy(alpha = if (flowColors.isDark) 0.22f else 0.13f)
                                } else {
                                    colorScheme.surface
                                },
                                border = BorderStroke(
                                    width = if (isSelected) 1.5.dp else 1.dp,
                                    color = if (isSelected) resolvedPreviewColor else colorScheme.outlineVariant,
                                ),
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable {
                                        haptics.tick()
                                        selectedIconKey = option.key
                                    }
                                    .testTag("custom_tag_icon_${option.key}"),
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = option.icon,
                                        contentDescription = option.label,
                                        tint = if (isSelected) resolvedPreviewColor else colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(18.dp),
                                    )
                                }
                            }
                        }
                    }
                }

                // Curated 8-color restrained palette + optional custom hex toggle
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = "选择主题色",
                            style = MaterialTheme.typography.labelMedium,
                            color = colorScheme.onSurfaceVariant,
                        )
                        TextButton(
                            onClick = {
                                haptics.tick()
                                showCustomHexField = !showCustomHexField
                            },
                            modifier = Modifier.height(28.dp),
                        ) {
                            Text(
                                text = if (showCustomHexField) "收起色值" else "自定义色值",
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }
                    }
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        CustomScheduleTagCodec.presetColorHexes.forEach { hex ->
                            val color = CourseColorPalette.parseHexColorOrNull(hex) ?: colorScheme.primary
                            val isSelected = selectedHex.equals(hex, ignoreCase = true)
                            Box(
                                modifier = Modifier
                                    .size(30.dp)
                                    .clip(CircleShape)
                                    .background(color)
                                    .border(
                                        width = if (isSelected) 2.5.dp else 1.dp,
                                        color = if (isSelected) colorScheme.onSurface else Color.White.copy(alpha = 0.45f),
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
                                        modifier = Modifier.size(15.dp),
                                    )
                                }
                            }
                        }
                    }

                    AnimatedVisibility(visible = showCustomHexField) {
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
                            placeholder = { Text("#3B82F6") },
                            singleLine = true,
                            isError = hexError,
                            textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = MonoFontFamily),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("custom_tag_hex_input"),
                            shape = MaterialTheme.shapes.medium,
                        )
                    }
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
                                selectedIconKey = TagIconCatalog.options.first().key
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
                                onUpdateTag(currentEditId, trimmed, validHex, selectedIconKey)
                                editingTagId = null
                            } else {
                                onAddTag(trimmed, validHex, selectedIconKey)
                            }
                            nameInput = ""
                            selectedIconKey = TagIconCatalog.options.first().key
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
                        text = "暂无自定义标签，可在上方设置名称、图标与颜色后添加",
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
                                                .size(28.dp)
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(tagColor.copy(alpha = if (flowColors.isDark) 0.18f else 0.11f)),
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            Icon(
                                                imageVector = tag.resolvedIcon(),
                                                contentDescription = null,
                                                tint = tagColor,
                                                modifier = Modifier.size(16.dp),
                                            )
                                        }
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
                                                selectedIconKey = tag.iconKey
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
