package com.example.ui.timetable

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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Checklist
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.FormatListBulleted
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.CourseEntity
import com.example.data.DateTimeUtils
import com.example.ui.components.rememberAppHaptics
import com.example.ui.theme.FlowTheme
import com.example.ui.theme.MonoFontFamily

@Composable
fun CourseOverviewDialog(
    courses: List<CourseEntity>,
    onEdit: (CourseEntity) -> Unit,
    onDeleteSingle: (CourseEntity) -> Unit,
    onDeleteBatch: (List<CourseEntity>) -> Unit,
    onDismiss: () -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme
    val flowColors = FlowTheme.colors
    val haptics = rememberAppHaptics()

    var isMultiSelect by remember { mutableStateOf(false) }
    var selectedIds by remember(courses) {
        val validIds = courses.map { it.id }.toSet()
        mutableStateOf(emptySet<Long>().filter { it in validIds }.toSet())
    }
    var showBatchDeleteConfirm by remember { mutableStateOf(false) }

    val groupedByDay = remember(courses) {
        (1..7).mapNotNull { isoDay ->
            val dayCourses = courses
                .filter { it.dayOfWeek == isoDay }
                .sortedWith(
                    compareBy<CourseEntity> { it.startSection }
                        .thenBy { it.endSection }
                        .thenBy { it.name },
                )
            if (dayCourses.isEmpty()) null else isoDay to dayCourses
        }
    }

    val allSelected = courses.isNotEmpty() && selectedIds.size == courses.size

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 24.dp)
                .widthIn(max = 560.dp)
                .heightIn(max = 660.dp)
                .testTag("course_overview_dialog"),
            shape = MaterialTheme.shapes.extraLarge,
            color = colorScheme.surface,
            tonalElevation = 4.dp,
            border = BorderStroke(1.dp, colorScheme.outlineVariant.copy(alpha = 0.6f)),
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 20.dp, end = 12.dp, top = 16.dp, bottom = 10.dp),
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
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.FormatListBulleted,
                                contentDescription = null,
                                tint = colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                        Column {
                            Text(
                                text = if (isMultiSelect) "批量管理课程" else "课程总览",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = colorScheme.onSurface,
                            )
                            Text(
                                text = if (isMultiSelect) {
                                    "已选 ${selectedIds.size} / ${courses.size} 门"
                                } else {
                                    "按星期一至星期日分组 · 共 ${courses.size} 门"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        if (courses.isNotEmpty()) {
                            FilledTonalButton(
                                onClick = {
                                    haptics.tick()
                                    if (isMultiSelect) {
                                        isMultiSelect = false
                                        selectedIds = emptySet()
                                    } else {
                                        isMultiSelect = true
                                    }
                                },
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                shape = CircleShape,
                                modifier = Modifier.testTag("course_overview_multiselect_toggle"),
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Checklist,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    text = if (isMultiSelect) "退出多选" else "多选",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                        }
                        IconButton(onClick = {
                            haptics.tick()
                            onDismiss()
                        }) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = "关闭",
                                tint = colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                if (isMultiSelect && courses.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(colorScheme.surfaceContainerLow)
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = "勾选课程后可一键批量删除",
                            style = MaterialTheme.typography.bodySmall,
                            color = colorScheme.onSurfaceVariant,
                        )
                        TextButton(
                            onClick = {
                                haptics.tick()
                                selectedIds = if (allSelected) {
                                    emptySet()
                                } else {
                                    courses.map { it.id }.toSet()
                                }
                            },
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                            modifier = Modifier.testTag("course_overview_select_all"),
                        ) {
                            Text(
                                text = if (allSelected) "取消全选" else "全选 (${courses.size})",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                }

                HorizontalDivider(color = colorScheme.outlineVariant.copy(alpha = 0.7f))

                if (courses.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 40.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "当前课表还没有课程，可开启「编辑课表」点击空白格子加课，或通过右上角「导入」快速导入。",
                            style = MaterialTheme.typography.bodyMedium,
                            color = colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        contentPadding = PaddingValues(vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        groupedByDay.forEach { (isoDay, dayCourses) ->
                            item(key = "day_header_$isoDay") {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 6.dp, bottom = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Text(
                                        text = "星期${DateTimeUtils.dayOfWeekShortChinese(isoDay)}",
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = colorScheme.primary,
                                    )
                                    Text(
                                        text = "${dayCourses.size} 门",
                                        fontFamily = MonoFontFamily,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = colorScheme.onSurfaceVariant,
                                    )
                                }
                            }

                            items(dayCourses, key = { it.id }) { course ->
                                val style = course.resolvedColorStyle()
                                val dotColor = if (flowColors.isDark) style.darkAccent else style.lightAccent
                                val isSelected = course.id in selectedIds
                                val sectionText = if (course.startSection == course.endSection) {
                                    "第${course.startSection}节"
                                } else {
                                    "第${course.startSection}-${course.endSection}节"
                                }
                                val locationText = course.formattedLocation().ifEmpty { "未填地点" }
                                val teacherPart = course.teacher.trim().takeIf { it.isNotEmpty() }?.let { " · $it" } ?: ""

                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(MaterialTheme.shapes.medium)
                                        .clickable {
                                            haptics.tick()
                                            if (isMultiSelect) {
                                                selectedIds = if (isSelected) {
                                                    selectedIds - course.id
                                                } else {
                                                    selectedIds + course.id
                                                }
                                            } else {
                                                onEdit(course)
                                            }
                                        }
                                        .testTag("course_overview_item_${course.id}"),
                                    shape = MaterialTheme.shapes.medium,
                                    color = if (isMultiSelect && isSelected) {
                                        colorScheme.primaryContainer.copy(alpha = 0.48f)
                                    } else {
                                        colorScheme.surfaceContainerLow
                                    },
                                    border = if (isMultiSelect && isSelected) {
                                        BorderStroke(1.dp, colorScheme.primary.copy(alpha = 0.55f))
                                    } else {
                                        BorderStroke(1.dp, colorScheme.outlineVariant.copy(alpha = 0.5f))
                                    },
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
                                            if (isMultiSelect) {
                                                Checkbox(
                                                    checked = isSelected,
                                                    onCheckedChange = { checked ->
                                                        haptics.tick()
                                                        selectedIds = if (checked) {
                                                            selectedIds + course.id
                                                        } else {
                                                            selectedIds - course.id
                                                        }
                                                    },
                                                    modifier = Modifier.size(22.dp),
                                                )
                                            }
                                            Box(
                                                modifier = Modifier
                                                    .size(9.dp)
                                                    .clip(CircleShape)
                                                    .background(dotColor),
                                            )
                                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                                Text(
                                                    text = course.name,
                                                    style = MaterialTheme.typography.titleSmall,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = colorScheme.onSurface,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                )
                                                Text(
                                                    text = "$sectionText · ${course.weekRangeLabel()}",
                                                    fontFamily = MonoFontFamily,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = colorScheme.primary,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                )
                                                Text(
                                                    text = "$locationText$teacherPart",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = colorScheme.onSurfaceVariant,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                )
                                            }
                                        }

                                        if (!isMultiSelect) {
                                            IconButton(
                                                onClick = {
                                                    haptics.tick()
                                                    onDeleteSingle(course)
                                                },
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Rounded.DeleteOutline,
                                                    contentDescription = "删除课程",
                                                    tint = flowColors.deleteAccent,
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

                HorizontalDivider(color = colorScheme.outlineVariant.copy(alpha = 0.7f))

                // Footer action bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (isMultiSelect) {
                        TextButton(
                            onClick = {
                                haptics.tick()
                                isMultiSelect = false
                                selectedIds = emptySet()
                            },
                        ) {
                            Text("取消多选")
                        }
                        Button(
                            onClick = {
                                haptics.tick()
                                showBatchDeleteConfirm = true
                            },
                            enabled = selectedIds.isNotEmpty(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = flowColors.deleteAccent,
                                contentColor = colorScheme.onError,
                            ),
                            modifier = Modifier.testTag("course_overview_batch_delete_button"),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.DeleteOutline,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                            )
                            Spacer(Modifier.width(4.dp))
                            Text("批量删除 (${selectedIds.size})")
                        }
                    } else {
                        Spacer(Modifier.width(1.dp))
                        TextButton(
                            onClick = {
                                haptics.tick()
                                onDismiss()
                            },
                        ) {
                            Text("完成")
                        }
                    }
                }
            }
        }
    }

    if (showBatchDeleteConfirm) {
        val selectedCourses = remember(courses, selectedIds) {
            courses.filter { it.id in selectedIds }
        }
        AlertDialog(
            onDismissRequest = { showBatchDeleteConfirm = false },
            title = { Text("确认批量删除课程") },
            text = {
                Text(
                    text = "确定要删除已选中的 ${selectedCourses.size} 门课程吗？删除后将同步移除对应课前提醒与自定义重提醒闹钟。",
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        haptics.confirm()
                        onDeleteBatch(selectedCourses)
                        selectedIds = emptySet()
                        isMultiSelect = false
                        showBatchDeleteConfirm = false
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = flowColors.deleteAccent,
                        contentColor = colorScheme.onError,
                    ),
                    modifier = Modifier.testTag("course_overview_confirm_batch_delete"),
                ) {
                    Text("确认删除")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        haptics.tick()
                        showBatchDeleteConfirm = false
                    },
                ) {
                    Text("取消")
                }
            },
        )
    }
}
