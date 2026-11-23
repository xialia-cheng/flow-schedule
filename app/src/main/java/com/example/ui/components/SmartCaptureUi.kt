package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DoNotDisturbOn
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.DateTimeUtils
import com.example.data.RepeatRule
import com.example.smart.DraftType
import com.example.smart.ParseSource
import com.example.smart.SmartParsedDraft
import com.example.ui.theme.FlowTheme
import com.example.ui.theme.MonoFontFamily

/**
 * Gemini-inspired floating quick-capture input pill bar for one-sentence schedule & course creation.
 */
@Composable
fun QuickSmartInputBar(
    value: String,
    isParsing: Boolean,
    isCustomAiReady: Boolean,
    onValueChange: (String) -> Unit,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colorScheme = MaterialTheme.colorScheme
    val flowColors = FlowTheme.colors
    val pillShape = RoundedCornerShape(28.dp)

    val outerMod = if (!flowColors.isDark) {
        modifier
            .shadow(
                elevation = 8.dp,
                shape = pillShape,
                ambientColor = flowColors.cardShadowColor,
                spotColor = flowColors.cardShadowColor,
            )
    } else {
        modifier
    }

    Surface(
        modifier = outerMod
            .fillMaxWidth()
            .testTag("quick_smart_input_bar"),
        shape = pillShape,
        color = colorScheme.surfaceContainerHigh,
        border = BorderStroke(
            width = 1.dp,
            brush = Brush.horizontalGradient(
                colors = listOf(
                    colorScheme.primary.copy(alpha = 0.55f),
                    colorScheme.secondary.copy(alpha = 0.55f),
                ),
            ),
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 14.dp, end = 6.dp, top = 5.dp, bottom = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                imageVector = Icons.Rounded.AutoAwesome,
                contentDescription = null,
                tint = colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )

            Box(
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                if (value.isEmpty()) {
                    Text(
                        text = if (isCustomAiReady) {
                            "一句话智能创建（已接入自定义 AI）..."
                        } else {
                            "一句话创建：如 明天早八@教A-301高数 免打扰"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                        color = colorScheme.onSurface,
                    ),
                    cursorBrush = SolidColor(colorScheme.primary),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { onSubmit() }),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("quick_smart_input_field"),
                )
            }

            IconButton(
                onClick = onSubmit,
                enabled = !isParsing,
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(
                        if (value.isNotBlank()) colorScheme.primary else colorScheme.surfaceContainerHighest,
                    )
                    .testTag("quick_smart_submit_button"),
            ) {
                if (isParsing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = colorScheme.onPrimary,
                    )
                } else {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.Send,
                        contentDescription = "解析并预览",
                        tint = if (value.isNotBlank()) colorScheme.onPrimary else colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}

/**
 * On-demand bottom sheet for one-sentence schedule & course creation,
 * opened only when the user taps the compact sparkle (✨) trigger.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SmartQuickCaptureSheet(
    value: String,
    isParsing: Boolean,
    isCustomAiReady: Boolean,
    customAiDomain: String? = null,
    onValueChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val colorScheme = MaterialTheme.colorScheme
    val examples = listOf(
        "明天早八@教A-301高数 免打扰",
        "每周一三五10点在信工楼402上数据结构",
        "后天14:00-16:00在会议室B开评审 提前15分钟",
    )

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
                    Icon(
                        imageVector = Icons.Rounded.AutoAwesome,
                        contentDescription = null,
                        tint = colorScheme.primary,
                        modifier = Modifier.size(22.dp),
                    )
                    Text(
                        text = "一句话智能创建",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = colorScheme.onSurface,
                    )
                }

                Surface(
                    shape = CircleShape,
                    color = if (isCustomAiReady) colorScheme.primaryContainer else colorScheme.secondaryContainer,
                ) {
                    Text(
                        text = if (isCustomAiReady) "自定义 AI 在线解析" else "纯本地离线解析",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isCustomAiReady) colorScheme.onPrimaryContainer else colorScheme.onSecondaryContainer,
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                }
            }

            Text(
                text = if (isCustomAiReady) {
                    "当前使用自定义 AI 在线解析（${customAiDomain ?: "已配置 HTTPS 接口"}），仅发送本条输入文本、当前时间与学期周数；断网时自动回退本地解析。"
                } else {
                    "纯本地离线解析：所有文字均仅在设备本地识别日期、节次、@地点、提醒与免打扰，不联网。"
                },
                style = MaterialTheme.typography.bodySmall,
                color = colorScheme.onSurfaceVariant,
            )

            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                label = { Text("输入日程或课程描述", maxLines = 1, softWrap = false) },
                placeholder = { Text("例：明天下午2点半在教A-301上数据结构 提前15分钟 免打扰") },
                minLines = 2,
                maxLines = 4,
                trailingIcon = if (value.isNotEmpty()) {
                    {
                        IconButton(onClick = { onValueChange("") }) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = "清空输入",
                            )
                        }
                    }
                } else {
                    null
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { onSubmit() }),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("quick_smart_input_field"),
                shape = MaterialTheme.shapes.medium,
            )

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "快捷示例（点击填入）",
                    style = MaterialTheme.typography.labelMedium,
                    color = colorScheme.onSurfaceVariant,
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    examples.forEach { sample ->
                        Surface(
                            onClick = { onValueChange(sample) },
                            shape = CircleShape,
                            color = colorScheme.surfaceContainerHigh,
                            border = BorderStroke(1.dp, colorScheme.outlineVariant),
                        ) {
                            Text(
                                text = sample,
                                style = MaterialTheme.typography.labelSmall,
                                color = colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            )
                        }
                    }
                }
            }

            Button(
                onClick = onSubmit,
                enabled = !isParsing && value.isNotBlank(),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp, bottom = 16.dp)
                    .height(50.dp)
                    .testTag("quick_smart_submit_button"),
                shape = MaterialTheme.shapes.medium,
            ) {
                if (isParsing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = colorScheme.onPrimary,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("正在智能解析与检测冲突...", fontWeight = FontWeight.Bold)
                } else {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.Send,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("解析并预览（自动检测冲突）", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SmartDraftPreviewSheet(
    drafts: List<SmartParsedDraft>,
    parseSource: ParseSource,
    parseNotice: String?,
    isSaving: Boolean,
    onUpdateDraft: (SmartParsedDraft) -> Unit,
    onRemoveDraft: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val colorScheme = MaterialTheme.colorScheme
    val flowColors = FlowTheme.colors
    val totalConflicts = drafts.sumOf { it.conflicts.size }

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
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // Sheet Header + Parse Engine Source Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "智能解析预览（共 ${drafts.size} 项）",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = colorScheme.onSurface,
                    )
                    Text(
                        text = "可微调信息后一键写入并注册精确闹钟",
                        style = MaterialTheme.typography.bodySmall,
                        color = colorScheme.onSurfaceVariant,
                    )
                }
                Surface(
                    shape = CircleShape,
                    color = when (parseSource) {
                        ParseSource.CUSTOM_OPENAI_API -> colorScheme.primaryContainer
                        ParseSource.OFFLINE_FALLBACK -> colorScheme.tertiaryContainer
                        ParseSource.OFFLINE_LOCAL -> colorScheme.secondaryContainer
                    },
                ) {
                    Text(
                        text = parseSource.label,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = when (parseSource) {
                            ParseSource.CUSTOM_OPENAI_API -> colorScheme.onPrimaryContainer
                            ParseSource.OFFLINE_FALLBACK -> colorScheme.onTertiaryContainer
                            ParseSource.OFFLINE_LOCAL -> colorScheme.onSecondaryContainer
                        },
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    )
                }
            }

            if (!parseNotice.isNullOrBlank()) {
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = colorScheme.tertiaryContainer.copy(alpha = 0.6f),
                ) {
                    Text(
                        text = parseNotice,
                        style = MaterialTheme.typography.bodySmall,
                        color = colorScheme.onTertiaryContainer,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                    )
                }
            }

            // Individual adjustable draft cards
            drafts.forEach { draft ->
                AdjustableDraftCard(
                    draft = draft,
                    canRemove = drafts.size > 1,
                    onUpdate = onUpdateDraft,
                    onRemove = { onRemoveDraft(draft.id) },
                )
            }

            // Confirm & Cancel footer
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp, bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                FilledTonalButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .weight(0.35f)
                        .height(50.dp),
                    shape = MaterialTheme.shapes.medium,
                ) {
                    Text("取消")
                }

                Button(
                    onClick = onConfirm,
                    enabled = !isSaving && drafts.any { it.title.isNotBlank() },
                    modifier = Modifier
                        .weight(0.65f)
                        .height(50.dp)
                        .testTag("smart_preview_confirm_button"),
                    shape = MaterialTheme.shapes.medium,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (totalConflicts > 0) flowColors.upcomingAccent else colorScheme.primary,
                        contentColor = if (totalConflicts > 0) colorScheme.onTertiary else colorScheme.onPrimary,
                    ),
                ) {
                    Text(
                        text = when {
                            isSaving -> "正在保存..."
                            totalConflicts > 0 -> "忽略 $totalConflicts 处冲突并创建"
                            else -> "确认创建并注册闹钟"
                        },
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AdjustableDraftCard(
    draft: SmartParsedDraft,
    canRemove: Boolean,
    onUpdate: (SmartParsedDraft) -> Unit,
    onRemove: () -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme
    val flowColors = FlowTheme.colors

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("smart_draft_card"),
        shape = MaterialTheme.shapes.large,
        color = flowColors.cardElevated,
        border = BorderStroke(
            width = if (draft.hasConflicts) 1.5.dp else 1.dp,
            color = if (draft.hasConflicts) colorScheme.error else colorScheme.outlineVariant,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // High-visibility Conflict Warning Banner
            AnimatedVisibility(visible = draft.hasConflicts) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("smart_conflict_banner"),
                    shape = MaterialTheme.shapes.small,
                    color = colorScheme.errorContainer,
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.WarningAmber,
                                contentDescription = "时间冲突警示",
                                tint = colorScheme.error,
                                modifier = Modifier.size(18.dp),
                            )
                            Text(
                                text = "检测到 ${draft.conflicts.size} 处时间冲突",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = colorScheme.onErrorContainer,
                            )
                        }
                        draft.conflicts.forEach { conflict ->
                            val tag = if (conflict.isCourse) "课程" else "日程"
                            val locPart = if (conflict.detail.isNotBlank()) " ${conflict.detail}" else ""
                            Text(
                                text = "• [$tag] ${conflict.title}$locPart（${conflict.timeRange}）",
                                style = MaterialTheme.typography.bodySmall,
                                color = colorScheme.onErrorContainer,
                            )
                        }
                    }
                }
            }

            // Type Toggle (Schedule vs Course) + Remove
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DraftType.entries.forEach { type ->
                        FilterChip(
                            selected = draft.draftType == type,
                            onClick = { onUpdate(draft.copy(draftType = type)) },
                            label = { Text(type.label) },
                            shape = CircleShape,
                        )
                    }
                }
                if (canRemove) {
                    IconButton(
                        onClick = onRemove,
                        modifier = Modifier.minimumInteractiveComponentSize(),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "移除此条预览",
                            tint = colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            // Title & Location Inputs
            OutlinedTextField(
                value = draft.title,
                onValueChange = { onUpdate(draft.copy(title = it)) },
                label = { Text(if (draft.draftType == DraftType.COURSE) "课程名称" else "日程标题") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.small,
            )

            OutlinedTextField(
                value = draft.location,
                onValueChange = { onUpdate(draft.copy(location = it.removePrefix("@"))) },
                label = { Text("地点（自动带 @ 前缀）") },
                placeholder = { Text("如：教A-301 / 会议室B") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.small,
            )

            // Time / Weekday / Section adjustments
            if (draft.draftType == DraftType.COURSE) {
                Text(
                    text = "上课星期（可多选，如每周一三五）",
                    style = MaterialTheme.typography.labelMedium,
                    color = colorScheme.onSurfaceVariant,
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    (1..7).forEach { isoDay ->
                        val activeDays = draft.daysOfWeek.ifEmpty {
                            listOf(DateTimeUtils.isoDayOfWeek(draft.startTimeMillis))
                        }
                        val selected = isoDay in activeDays
                        FilterChip(
                            selected = selected,
                            onClick = {
                                val nextDays = if (selected && activeDays.size > 1) {
                                    activeDays - isoDay
                                } else if (!selected) {
                                    (activeDays + isoDay).sorted()
                                } else {
                                    activeDays
                                }
                                onUpdate(draft.copy(daysOfWeek = nextDays))
                            },
                            label = { Text(DateTimeUtils.dayOfWeekFullChinese(isoDay)) },
                            shape = CircleShape,
                        )
                    }
                }

                // Section stepper row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = "节次：第 ${draft.startSection} - ${draft.endSection} 节",
                        fontFamily = MonoFontFamily,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp,
                        color = colorScheme.onSurface,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilledTonalButton(
                            onClick = {
                                val ns = (draft.startSection - 1).coerceAtLeast(1)
                                val ne = (draft.endSection - 1).coerceAtLeast(ns)
                                onUpdate(draft.copy(startSection = ns, endSection = ne))
                            },
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                            modifier = Modifier.height(34.dp),
                        ) { Text("前移") }
                        FilledTonalButton(
                            onClick = {
                                val ne = (draft.endSection + 1).coerceAtMost(12)
                                val ns = (draft.startSection + 1).coerceAtMost(ne)
                                onUpdate(draft.copy(startSection = ns, endSection = ne))
                            },
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                            modifier = Modifier.height(34.dp),
                        ) { Text("后移") }
                    }
                }
            } else {
                // Schedule time & quick shift controls
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = colorScheme.surfaceContainerHigh,
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column {
                            Text(
                                text = DateTimeUtils.formatDateShort(draft.startTimeMillis),
                                style = MaterialTheme.typography.labelMedium,
                                color = colorScheme.onSurfaceVariant,
                            )
                            Text(
                                text = DateTimeUtils.formatTimeRange(draft.startTimeMillis, draft.endTimeMillis),
                                fontFamily = MonoFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = colorScheme.primary,
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            FilledTonalButton(
                                onClick = {
                                    onUpdate(
                                        draft.copy(
                                            startTimeMillis = draft.startTimeMillis - 30 * 60_000L,
                                            endTimeMillis = draft.endTimeMillis - 30 * 60_000L,
                                        ),
                                    )
                                },
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                                modifier = Modifier.height(34.dp),
                            ) { Text("-30分") }
                            FilledTonalButton(
                                onClick = {
                                    onUpdate(
                                        draft.copy(
                                            startTimeMillis = draft.startTimeMillis + 30 * 60_000L,
                                            endTimeMillis = draft.endTimeMillis + 30 * 60_000L,
                                        ),
                                    )
                                },
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                                modifier = Modifier.height(34.dp),
                            ) { Text("+30分") }
                        }
                    }
                }

                // Repeat rule selector
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    RepeatRule.entries.forEach { rule ->
                        FilterChip(
                            selected = draft.repeatRule == rule,
                            onClick = { onUpdate(draft.copy(repeatRule = rule)) },
                            label = { Text(rule.label) },
                            shape = CircleShape,
                        )
                    }
                }
            }

            // Advance Reminder + Do Not Disturb chips
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                listOf(0 to "不提醒", 5 to "提前5分", 15 to "提前15分", 30 to "提前30分").forEach { (mins, lbl) ->
                    FilterChip(
                        selected = draft.reminderMinutesBefore == mins,
                        onClick = { onUpdate(draft.copy(reminderMinutesBefore = mins)) },
                        label = { Text(lbl) },
                        shape = CircleShape,
                    )
                }

                FilterChip(
                    selected = draft.enableDnd,
                    onClick = { onUpdate(draft.copy(enableDnd = !draft.enableDnd)) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Rounded.DoNotDisturbOn,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                    },
                    label = { Text("免打扰") },
                    shape = CircleShape,
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = colorScheme.tertiaryContainer,
                        selectedLabelColor = colorScheme.onTertiaryContainer,
                        selectedLeadingIconColor = colorScheme.onTertiaryContainer,
                    ),
                )
            }
        }
    }
}
