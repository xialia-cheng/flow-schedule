package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.TimetableImportExport
import com.example.ui.theme.FlowTheme
import com.example.ui.theme.MonoFontFamily

object AppTutorialContent {
    const val CSV_HEADER_ONLY =
        "课程名称,地点,教师,星期(1-7),开始节次,结束节次,起始周,结束周,单双周(全周/单周/双周)"

    val CSV_TEMPLATE_WITH_EXAMPLES = TimetableImportExport.SAMPLE_CSV_TEMPLATE

    const val AI_PDF_TIP =
        "实用技巧：若教务系统只有 PDF 或截图课表，可点击下方按钮复制标准表头模板发给任意 AI，让它整理成 CSV 后直接粘贴导入。"

    const val VOICE_EXAMPLE_COURSE =
        "每周一三第1-2节高等数学在禹412，第1到16周"

    const val VOICE_EXAMPLE_MULTI =
        "每周二下午3-4节大学物理实验在理学楼201双周第2-16周；明天晚上7点去图书馆自习提醒我"
}

@Composable
fun TimetableImportQuickGuideCard(
    onCopyTemplate: (label: String, text: String) -> Unit,
    modifier: Modifier = Modifier,
    initiallyExpanded: Boolean = false,
) {
    val colorScheme = MaterialTheme.colorScheme
    val flowColors = FlowTheme.colors
    val haptics = rememberAppHaptics()
    var expanded by rememberSaveable { mutableStateOf(initiallyExpanded) }
    val arrowRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
        label = "import_quick_guide_arrow",
    )

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = colorScheme.primaryContainer.copy(alpha = if (flowColors.isDark) 0.22f else 0.30f),
        border = BorderStroke(1.dp, colorScheme.primary.copy(alpha = 0.28f)),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 48.dp)
                    .clickable {
                        haptics.tick()
                        expanded = !expanded
                    }
                    .padding(horizontal = 14.dp, vertical = 10.dp)
                    .testTag("import_quick_guide_header"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(colorScheme.primary.copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.MenuBook,
                        contentDescription = null,
                        tint = colorScheme.primary,
                        modifier = Modifier.size(16.dp),
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "新手必看：4 种导入与加课详尽教程",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = colorScheme.onSurface,
                    )
                    Text(
                        text = if (expanded) "点击收起完整步骤与标准 CSV 示例" else "点击展开 · 含标准 CSV 模板、AI 转表技巧与口令说明",
                        style = MaterialTheme.typography.labelSmall,
                        color = colorScheme.onSurfaceVariant,
                    )
                }
                Icon(
                    imageVector = Icons.Rounded.KeyboardArrowDown,
                    contentDescription = if (expanded) "收起导入教程" else "展开导入教程",
                    tint = colorScheme.primary,
                    modifier = Modifier
                        .size(20.dp)
                        .rotate(arrowRotation),
                )
            }

            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(
                    animationSpec = tween(durationMillis = 240, easing = FastOutSlowInEasing),
                ) + fadeIn(animationSpec = tween(durationMillis = 200)),
                exit = shrinkVertically(
                    animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing),
                ) + fadeOut(animationSpec = tween(durationMillis = 150)),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 14.dp, end = 14.dp, bottom = 14.dp)
                        .testTag("import_quick_guide_content"),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    HorizontalDivider(color = colorScheme.primary.copy(alpha = 0.18f))

                    TutorialStepBlock(
                        badge = "方法 1 · 最推荐整学期导入",
                        title = "CSV / Excel / HTML 表格批量导入",
                        body = "点击「选择文件」或粘贴文本即可导入。标准 CSV 表头与实例如下（还支持跳周如 5-10,12-18、直接从 Excel 二维星期表格复制粘贴，以及切换「HTML课表」导入教务系统网页源码）：",
                    )

                    TutorialCodeBlock(text = AppTutorialContent.CSV_TEMPLATE_WITH_EXAMPLES)

                    TutorialTipBanner(text = AppTutorialContent.AI_PDF_TIP)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        FilledTonalButton(
                            onClick = {
                                haptics.tick()
                                onCopyTemplate("标准 CSV 模板", AppTutorialContent.CSV_TEMPLATE_WITH_EXAMPLES)
                            },
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                                .testTag("import_guide_copy_csv_template_button"),
                            shape = MaterialTheme.shapes.small,
                        ) {
                            Icon(Icons.Rounded.ContentCopy, null, Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = "复制 CSV 示例模板",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                        OutlinedButton(
                            onClick = {
                                haptics.tick()
                                onCopyTemplate("标准 CSV 表头", AppTutorialContent.CSV_HEADER_ONLY)
                            },
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .testTag("import_guide_copy_csv_header_button"),
                            shape = MaterialTheme.shapes.small,
                        ) {
                            Text(
                                text = "仅复制表头",
                                style = MaterialTheme.typography.labelMedium,
                            )
                        }
                    }

                    TutorialStepBlock(
                        badge = "方法 2 · 手动精调",
                        title = "网格点空白加课 & 课程总览批量管理",
                        body = "开启课表页顶部「编辑课表」开关后，点击空白格子即可新建课程、点击已有课程可修改。周次支持可视化 1–52 周方格多选（全选/单周/双周/不连续跳周），同名课程自动统一配色；在「课表控制中心 → 课程总览」可按星期分组一键多选批量删除。",
                    )

                    TutorialStepBlock(
                        badge = "方法 3 · 一句话",
                        title = "语音 / 自然语言智能加课",
                        body = "点击右下角 ✨ 悬浮球直接说话或输入，例如：“${AppTutorialContent.VOICE_EXAMPLE_COURSE}”，支持单双周识别与多句连续批量生成草稿。",
                    )

                    TutorialStepBlock(
                        badge = "方法 4 · 分享与备份",
                        title = "课表分享口令 vs JSON 完整备份 & 基础设置",
                        body = "• 分享口令（FLOWTIMETABLE://...）：仅包含单张课表课程与作息，适合同学互传。\n• JSON 备份：包含全部多课表、节次作息、全部日程、自定义标签及午休设置，恢复时支持智能合并去重或清空覆盖。\n• 开学第一周日期、每周起始日（周一/周日）、6–16 节次作息及可选「顶部附加时段 / 课间午休分隔带」，均可在课表页「课表控制中心 → 课表设置」中配置。",
                    )
                }
            }
        }
    }
}

@Composable
fun ComprehensiveTutorialSections(
    onCopyText: (label: String, text: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var timetableExpanded by rememberSaveable { mutableStateOf(true) }
    var scheduleAiExpanded by rememberSaveable { mutableStateOf(false) }
    var liveGuardExpanded by rememberSaveable { mutableStateOf(false) }
    var focusShortcutsExpanded by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("about_comprehensive_tutorial"),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        ExpandableTutorialCard(
            icon = Icons.Rounded.CalendarMonth,
            title = "板块一：添加与导入课表（4 种方法全流程）",
            summary = "CSV/Excel/HTML 批量导入、1–52 周手动格选、一句话加课与口令/备份",
            expanded = timetableExpanded,
            onToggle = { timetableExpanded = !timetableExpanded },
            headerTestTag = "tutorial_section_1_header",
            contentTestTag = "tutorial_section_1_content",
        ) {
            TutorialStepBlock(
                badge = "方法 1 · 最推荐整学期导入",
                title = "CSV / Excel 表格 & HTML 网页导入",
                body = "前往「课表页右上角 导入」，支持逗号分隔 CSV、从 Excel 二维课表直接复制粘贴（含星期一至星期日表头）、WakeUp 导出文件，以及「HTML课表」页导入教务系统网页表格源码。\n标准 CSV 表头（支持跳周如 5-10,12-18 及 08:00-09:40 时间自动匹配节次）：",
            )

            TutorialCodeBlock(text = AppTutorialContent.CSV_TEMPLATE_WITH_EXAMPLES)

            TutorialTipBanner(text = AppTutorialContent.AI_PDF_TIP)

            FilledTonalButton(
                onClick = { onCopyText("标准 CSV 模板", AppTutorialContent.CSV_TEMPLATE_WITH_EXAMPLES) },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .testTag("about_guide_copy_csv_template_button"),
                shape = MaterialTheme.shapes.small,
            ) {
                Icon(Icons.Rounded.ContentCopy, null, Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("一键复制标准 CSV 表头与示例模板", fontWeight = FontWeight.SemiBold)
            }

            TutorialStepBlock(
                badge = "方法 2 · 自由加课与批量删课",
                title = "开启「编辑课表」点空白加课 & 课程总览",
                body = "1. 打开课表页顶部「编辑课表」开关（浏览模式下默认防误触锁定）。\n2. 点击任意空白时段格子添加课程，或点击现有课程卡片修改详情与自定义配色（同名课程自动统一配色）。\n3. 编辑弹窗内支持「可视化 1–52 周方格多选」：一键全选、单周、双周或点选不连续跳周。\n4. 需要大量删改时，进入「课表控制中心 → 课程总览」右上角进入多选模式，可按星期一至星期日分组勾选并批量删除。",
            )

            TutorialStepBlock(
                badge = "方法 3 · 语音 / 一句话",
                title = "自然语言直接生成课程草稿",
                body = "点击右下角 ✨ 悬浮球，使用键盘语音输入或打字下达指令，例如：\n“${AppTutorialContent.VOICE_EXAMPLE_COURSE}”\n支持单双周识别与多句连续批量解析，生成草稿后会自动校验冲突，核对后一键保存。",
            )

            TutorialStepBlock(
                badge = "方法 4 · 分享与基础设置",
                title = "课表口令 vs JSON 备份 · 作息与分隔带配置",
                body = "• 课表分享口令（FLOWTIMETABLE://...）：仅分享当前单张课表的课程与上下课时间，适合班级同学一键导入为新课表。\n• JSON 完整备份：在「导入导出与备份」或「我的」页导出，完整包含全部多课表、1–16 节次作息、全部日程、自定义标签及午休配置；恢复时可选「智能合并去重」或「清空覆盖」。\n• 基础作息设置：在「课表控制中心 → 课表设置」中可配置「开学第一周日期」「学期总周数（1–52 周）」「每周起始日（周一 / 周日）」「节次作息时间」，以及「可选附加时段（顶部附加时段条 / 课间午休分隔带位置与时间）」。",
            )
        }

        ExpandableTutorialCard(
            icon = Icons.Rounded.AutoAwesome,
            title = "板块二：日程管理、自定义标签与语音 AI 批量规划",
            summary = "自定义标签配色、0–120 分钟提醒、重复规则与 ✨ 多任务草稿排程",
            expanded = scheduleAiExpanded,
            onToggle = { scheduleAiExpanded = !scheduleAiExpanded },
            headerTestTag = "tutorial_section_2_header",
            contentTestTag = "tutorial_section_2_content",
        ) {
            TutorialStepBlock(
                badge = "精细化分类与提醒",
                title = "+ 自定义标签、Hex 配色与重复日程",
                body = "1. 在「日程页」或「我的 → 外观、交互与标签管理」中点击「+ 新建标签」，可自定义名称、图标与任意 Hex 颜色值，并在日程页顶部一键按标签筛选。\n2. 每条日程支持设置「0–120 分钟」提前提醒（支持提前进入实况通知倒计时）、起止时间与备注，并可配置「不重复 / 每日 / 工作日（周一至周五）/ 每周」循环规则。\n3. 长按日程卡片可快速推迟、改标签、标记完成或设置 10 分钟后再提醒。",
            )

            TutorialStepBlock(
                badge = "✨ 语音 AI 批量规划",
                title = "多句同时录入 + 空闲时段排程 + 自动冲突检测",
                body = "点击右下角 ✨ 悬浮球，可用语音或一段话同时下达多个课程/日程任务，例如：\n“${AppTutorialContent.VOICE_EXAMPLE_MULTI}”\n• 系统默认使用本地离线规则解析（极速零联网）；若在「我的」配置了 OpenAI 兼容 HTTPS 接口，AI 会结合你的空闲时段批量生成可微调草稿。\n• 草稿预览页会自动与你已有的全部课程及日程比对，若存在时间重叠将高亮提示时间冲突，确认无误后再保存。",
            )
        }

        ExpandableTutorialCard(
            icon = Icons.Rounded.NotificationsActive,
            title = "板块三：实况通知、后台常驻与各品牌防杀指南",
            summary = "权限配置、3 级免打扰，ColorOS / HyperOS / OriginOS / HarmonyOS 防杀路径",
            expanded = liveGuardExpanded,
            onToggle = { liveGuardExpanded = !liveGuardExpanded },
            headerTestTag = "tutorial_section_3_header",
            contentTestTag = "tutorial_section_3_content",
        ) {
            TutorialStepBlock(
                badge = "第 1 步 · 核心权限",
                title = "通知、精确闹钟与 3 级系统免打扰",
                body = "在「我的 → 通知、实况与后台守护 → 权限与诊断」中逐项检查：\n• 发送通知 & 实时活动（Live Updates）：用于展示课前/进行中/即将结束三阶段实况胶囊及锁屏倒计时。\n• 精确闹钟（闹钟和提醒）：确保 00:00 阶段切换、提前提醒与下课自动消隐在息屏状态准时触发。\n• 系统免打扰（勿扰权限）：支持「优先打扰（允许闹钟/星标）」「仅闹钟」「完全静音」3 级可选，上课或专注期间自动开启、结束后自动恢复。",
            )

            TutorialStepBlock(
                badge = "第 2 步 · 品牌防杀设置",
                title = "国产系统后台保活推荐路径",
                body = "若需息屏跨阶段自动切换（或已开启「后台守护服务」），建议按机型完成以下设置：\n• ColorOS（OPPO / 一加 / 真我）：设置 → 应用 → 应用管理 → 微澜日程 → 耗电管理 → 选择「允许完全后台行为」并开启「允许自启动」；在最近任务下拉应用卡片「锁定」，或开启「后台守护卡片从最近任务隐藏」。\n• HyperOS / MIUI（小米 / Redmi）：设置 → 应用设置 → 应用管理 → 微澜日程 → 省电策略设为「无限制」+ 开启「自启动」；多任务界面长按卡片点击锁头锁定。\n• OriginOS（vivo / iQOO）：设置 → 应用与权限 → 应用管理 → 微澜日程 → 电池 → 后台高耗电设为「允许」，权限中开启「自启动」；多任务下滑或上滑加锁。\n• HarmonyOS / EMUI（华为 / 荣耀）：设置 → 应用和服务 → 应用启动管理 → 关闭微澜日程「自动管理」，手动开启「允许自启动 / 允许关联启动 / 允许后台活动」；多任务下拉卡片加锁。",
            )

            TutorialStepBlock(
                badge = "第 3 步 · 自动兼容降级",
                title = "未接管 Android 16 原生实况胶囊的系统",
                body = "微澜日程基于 Android 16+ 原生 Live Updates 接口构建，支持已接管该标准的系统实况胶囊（如 ColorOS 流体云等）。在未适配或未开放实况胶囊的系统上，应用会自动优雅降级为「标准常驻进度通知」，完整保留 Chronometer 实时倒计时、课程首字彩色徽章与快捷操作，核心提醒功能不受影响。",
            )
        }

        ExpandableTutorialCard(
            icon = Icons.Rounded.Timer,
            title = "板块四：学习专注与快捷入口",
            summary = "自由专注联动免打扰、桌面小组件、下拉快捷瓷贴与长按图标直达",
            expanded = focusShortcutsExpanded,
            onToggle = { focusShortcutsExpanded = !focusShortcutsExpanded },
            headerTestTag = "tutorial_section_4_header",
            contentTestTag = "tutorial_section_4_content",
        ) {
            TutorialStepBlock(
                badge = "专注计时",
                title = "自由专注 + 实况胶囊与免打扰联动",
                body = "在「学习页」可自定义专注任务名称（支持关联课程与预计时长）、1–180 分钟专注与 0–60 分钟休息时长。开启专注后可自动联动实况通知倒计时与系统免打扰，专注结束自动恢复响铃并累计专注时长。",
            )

            TutorialStepBlock(
                badge = "高频快捷入口",
                title = "桌面小组件、控制中心瓷贴与图标快捷方式",
                body = "• 桌面小组件：在系统桌面长按添加「微澜日程 · 下一项」，自动展示今天正在进行或即将开始的课程/日程（拉大尺寸可展示更多后续安排）。\n• 下拉控制中心快捷瓷贴：下拉系统控制中心点击编辑，将「微澜日程」瓷贴拖入面板，随时随地一键查看当前进度并直达今日课表。\n• 长按桌面图标快捷方式：在桌面长按「微澜日程」图标，可直接呼出「一句话智能创建」「新建日程」「今日课表」三大快捷跳转。",
            )
        }
    }
}

@Composable
private fun ExpandableTutorialCard(
    icon: ImageVector,
    title: String,
    summary: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    headerTestTag: String,
    contentTestTag: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme
    val haptics = rememberAppHaptics()
    val rotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
        label = "tutorial_card_arrow_$headerTestTag",
    )

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, colorScheme.outlineVariant),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 48.dp)
                    .clickable {
                        haptics.tick()
                        onToggle()
                    }
                    .padding(horizontal = 12.dp, vertical = 10.dp)
                    .testTag(headerTestTag),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(16.dp),
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = colorScheme.onSurface,
                    )
                    Text(
                        text = summary,
                        style = MaterialTheme.typography.bodySmall,
                        color = colorScheme.onSurfaceVariant,
                    )
                }
                Icon(
                    imageVector = Icons.Rounded.KeyboardArrowDown,
                    contentDescription = if (expanded) "收起本节教程" else "展开本节教程",
                    tint = colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .size(20.dp)
                        .rotate(rotation),
                )
            }

            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(
                    animationSpec = tween(durationMillis = 240, easing = FastOutSlowInEasing),
                ) + fadeIn(animationSpec = tween(durationMillis = 200)),
                exit = shrinkVertically(
                    animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing),
                ) + fadeOut(animationSpec = tween(durationMillis = 150)),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 12.dp, end = 12.dp, bottom = 12.dp)
                        .testTag(contentTestTag),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    HorizontalDivider(color = colorScheme.outlineVariant.copy(alpha = 0.7f))
                    content()
                }
            }
        }
    }
}

@Composable
private fun TutorialStepBlock(
    badge: String,
    title: String,
    body: String,
) {
    val colorScheme = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Surface(
            shape = CircleShape,
            color = colorScheme.secondaryContainer,
        ) {
            Text(
                text = badge,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = colorScheme.onSecondaryContainer,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            )
        }
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
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

@Composable
private fun TutorialCodeBlock(text: String) {
    val colorScheme = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.small,
        color = colorScheme.surfaceContainerHighest.copy(alpha = 0.75f),
        border = BorderStroke(1.dp, colorScheme.outlineVariant),
    ) {
        Text(
            text = text,
            fontFamily = MonoFontFamily,
            fontSize = 11.sp,
            lineHeight = 16.sp,
            color = colorScheme.onSurface,
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .padding(10.dp),
        )
    }
}

@Composable
private fun TutorialTipBanner(text: String) {
    val colorScheme = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.small,
        color = colorScheme.tertiaryContainer.copy(alpha = 0.55f),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Icon(
                imageVector = Icons.Rounded.Lightbulb,
                contentDescription = null,
                tint = colorScheme.onTertiaryContainer,
                modifier = Modifier
                    .padding(top = 1.dp)
                    .size(16.dp),
            )
            Text(
                text = text,
                style = MaterialTheme.typography.bodySmall,
                lineHeight = 18.sp,
                color = colorScheme.onTertiaryContainer,
            )
        }
    }
}
