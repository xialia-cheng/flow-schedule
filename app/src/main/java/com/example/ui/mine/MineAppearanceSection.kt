package com.example.ui.mine

import android.os.Build
import android.view.View
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Label
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.AppSettings
import com.example.data.AppThemeMode
import com.example.data.CustomScheduleTag
import com.example.data.ScheduleCategory
import com.example.data.TagIconCatalog
import com.example.data.WeekStartDay
import com.example.ui.components.AppHaptics
import com.example.ui.components.SelectablePillOption
import com.example.ui.components.SwitchSettingRow
import com.example.ui.components.rememberAppHaptics
import com.example.ui.theme.FlowTheme

@Composable
internal fun AppearanceAndInteractionSectionContent(
    settings: AppSettings,
    view: View,
    onThemeModeChange: (AppThemeMode) -> Unit,
    onUseDynamicColorChange: (Boolean) -> Unit,
    onHapticFeedbackEnabledChange: (Boolean) -> Unit,
    onWeekStartDayChange: (WeekStartDay) -> Unit,
    onCompactModeChange: (Boolean) -> Unit,
    onShowMorningExerciseChange: (Boolean) -> Unit,
    onShowLunchBreakChange: (Boolean) -> Unit,
    onShowWeekendChange: (Boolean) -> Unit,
    onShowNonCurrentWeekCoursesChange: (Boolean) -> Unit,
    onAddTagClick: () -> Unit,
    onEditTagClick: (String) -> Unit,
    onDeleteTagClick: (String) -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme

    // 1. Theme Mode + Dynamic Color + Haptics
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "主题模式",
                style = MaterialTheme.typography.titleSmall,
                color = colorScheme.onSurface,
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                listOf(
                    colorScheme.primary,
                    colorScheme.secondary,
                    colorScheme.tertiary,
                    colorScheme.surfaceContainerHighest,
                ).forEach { swatch ->
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .clip(CircleShape)
                            .background(swatch),
                    )
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            AppThemeMode.entries.forEach { mode ->
                SelectablePillOption(
                    label = mode.label,
                    selected = settings.themeMode == mode,
                    onClick = { onThemeModeChange(mode) },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("mine_theme_${mode.name.lowercase()}"),
                )
            }
        }

        SwitchSettingRow(
            title = "系统壁纸动态取色（Dynamic Color）",
            subtitle = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (settings.useDynamicColor) {
                    "已开启 Material You · 界面随系统壁纸自动配色"
                } else {
                    "已关闭 · 当前使用微澜默认配色"
                }
            } else {
                "需 Android 12 及以上系统，当前自动使用默认主题"
            },
            checked = settings.useDynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S,
            onCheckedChange = onUseDynamicColorChange,
            switchTestTag = "mine_switch_dynamic_color",
            useLargeTitle = true,
        )

        SwitchSettingRow(
            title = "应用内触觉反馈",
            subtitle = "在切换标签、勾选完成与点击微调时提供清脆触感（尊重系统全局开关）",
            checked = settings.hapticFeedbackEnabled,
            onCheckedChange = { enabled ->
                if (enabled && !settings.hapticFeedbackEnabled) {
                    view.performHapticFeedback(AppHaptics.resolveTickConstant())
                }
                onHapticFeedbackEnabledChange(enabled)
            },
            switchTestTag = "mine_switch_haptic_feedback",
            useLargeTitle = true,
        )
    }

    HorizontalDivider(color = colorScheme.outlineVariant.copy(alpha = 0.7f))

    // 2. Timetable & Calendar Display Preferences
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = "每周起始日（课表与日程日期条同步生效）",
            style = MaterialTheme.typography.titleSmall,
            color = colorScheme.onSurface,
        )
        Spacer(modifier = Modifier.height(2.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            WeekStartDay.entries.forEach { startDay ->
                SelectablePillOption(
                    label = "从${startDay.label}开始",
                    selected = settings.weekStartDay == startDay,
                    onClick = { onWeekStartDayChange(startDay) },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("mine_week_start_${startDay.name.lowercase()}"),
                )
            }
        }

        Spacer(modifier = Modifier.height(2.dp))

        SwitchSettingRow(
            title = "简洁模式",
            subtitle = "隐藏课表非本周灰显课程、附加时段条与下课时间",
            checked = settings.compactMode,
            onCheckedChange = onCompactModeChange,
            switchTestTag = "mine_switch_compact",
            useLargeTitle = true,
        )
        SwitchSettingRow(
            title = if (settings.extraBannerTitle.isNotBlank()) {
                "顶部附加时段条（${settings.extraBannerTitle}）"
            } else {
                "顶部附加时段条"
            },
            subtitle = if (settings.showMorningExercise) {
                "当前时段 ${settings.extraBannerStartTime} - ${settings.extraBannerEndTime}，可在课表设置中自定义"
            } else {
                "默认关闭；开启后在周视图顶部显示自定义打卡时段"
            },
            checked = settings.showMorningExercise,
            onCheckedChange = onShowMorningExerciseChange,
            switchTestTag = "mine_switch_morning",
            useLargeTitle = true,
        )
        SwitchSettingRow(
            title = "课间午休分隔带（${settings.lunchBreakTitle.ifBlank { "午休" }}）",
            subtitle = if (settings.showLunchBreak) {
                "当前显示在第 ${settings.lunchAfterSection} 节后，可在课表设置中自定义"
            } else {
                "默认关闭；开启后在指定节次之间插入午休分隔带"
            },
            checked = settings.showLunchBreak,
            onCheckedChange = onShowLunchBreakChange,
            switchTestTag = "mine_switch_lunch_break",
            useLargeTitle = true,
        )
        SwitchSettingRow(
            title = "显示周末列（周六、周日）",
            subtitle = "关闭后仅展示周一至周五课表",
            checked = settings.showWeekend,
            onCheckedChange = onShowWeekendChange,
            switchTestTag = "mine_switch_weekend",
            useLargeTitle = true,
        )
        SwitchSettingRow(
            title = "显示非本周课程（灰显角标）",
            subtitle = "半透明展示非本周课程并在底部标注“非本周”",
            checked = settings.showNonCurrentWeekCourses,
            onCheckedChange = onShowNonCurrentWeekCoursesChange,
            switchTestTag = "mine_switch_non_current",
            useLargeTitle = true,
        )
    }

    HorizontalDivider(color = colorScheme.outlineVariant.copy(alpha = 0.7f))

    // 3. Schedule Category & Custom Tag Management
    ScheduleTagManagementBlock(
        customTags = settings.customScheduleTags,
        onAddTagClick = onAddTagClick,
        onEditTagClick = onEditTagClick,
        onDeleteTagClick = onDeleteTagClick,
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ScheduleTagManagementBlock(
    customTags: List<CustomScheduleTag>,
    onAddTagClick: () -> Unit,
    onEditTagClick: (String) -> Unit,
    onDeleteTagClick: (String) -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme
    val flowColors = FlowTheme.colors
    val haptics = rememberAppHaptics()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("mine_tag_management_card"),
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
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.Label,
                    contentDescription = null,
                    tint = colorScheme.primary,
                    modifier = Modifier.size(18.dp),
                )
                Column {
                    Text(
                        text = "日程分类与标签管理",
                        style = MaterialTheme.typography.titleSmall,
                        color = colorScheme.onSurface,
                    )
                    Text(
                        text = "5 个默认分类 · ${customTags.size} 个自定义标签",
                        style = MaterialTheme.typography.bodySmall,
                        color = colorScheme.onSurfaceVariant,
                    )
                }
            }
            FilledTonalButton(
                onClick = {
                    haptics.tick()
                    onAddTagClick()
                },
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                modifier = Modifier.testTag("mine_add_tag_button"),
            ) {
                Icon(
                    imageVector = Icons.Rounded.Add,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "新建标签",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    softWrap = false,
                )
            }
        }

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            ScheduleCategory.entries
                .filter { it != ScheduleCategory.TEST }
                .forEach { cat ->
                    Surface(
                        shape = CircleShape,
                        color = colorScheme.surfaceContainerLow,
                        border = BorderStroke(1.dp, colorScheme.outlineVariant.copy(alpha = 0.7f)),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Icon(
                                imageVector = TagIconCatalog.iconForCategory(cat),
                                contentDescription = null,
                                tint = cat.accentColor.copy(alpha = 0.85f),
                                modifier = Modifier.size(12.dp),
                            )
                            Text(
                                text = cat.label,
                                style = MaterialTheme.typography.labelSmall,
                                color = colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                softWrap = false,
                            )
                        }
                    }
                }
        }

        if (customTags.isEmpty()) {
            Text(
                text = "暂未添加自定义标签；可点击右上角「新建标签」自由定制名称、颜色与图标，并在日程页一键筛选。",
                style = MaterialTheme.typography.bodySmall,
                color = colorScheme.onSurfaceVariant,
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                customTags.forEach { tag ->
                    val tagColor = tag.resolvedColor()
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = colorScheme.surfaceContainerLow,
                        border = BorderStroke(1.dp, colorScheme.outlineVariant.copy(alpha = 0.75f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("mine_tag_row_${tag.id}"),
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
                                        .size(30.dp)
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
                                Column {
                                    Text(
                                        text = tag.name,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        text = tag.colorHex,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(
                                    onClick = {
                                        haptics.tick()
                                        onEditTagClick(tag.id)
                                    },
                                    modifier = Modifier.testTag("mine_tag_edit_${tag.id}"),
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
                                        onDeleteTagClick(tag.id)
                                    },
                                    modifier = Modifier.testTag("mine_tag_delete_${tag.id}"),
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
}
