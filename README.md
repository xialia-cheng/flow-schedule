<p align="center">
  <img src="app/src/main/res/mipmap-xxxhdpi/ic_launcher.png" width="144" height="144" alt="微澜日程应用图标">
</p>

<h1 align="center">微澜日程 · RipplePlan</h1>

<p align="center">
  面向校园生活的开源 Android 课表与日程应用。<br>
  在一个地方看课表、记待办、开专注计时。界面简洁，全程无广告。
</p>

<p align="center">
  <a href="https://github.com/xialia-cheng/ripple-plan/blob/main/LICENSE"><img src="https://img.shields.io/badge/License-MIT-4d669a" alt="MIT 开源许可证"></a>
  <a href="https://github.com/xialia-cheng/ripple-plan/releases/latest"><img src="https://img.shields.io/github/v/release/xialia-cheng/ripple-plan?sort=date&amp;label=Release&amp;color=4d669a" alt="最新发布版本"></a>
  <a href="https://github.com/xialia-cheng/ripple-plan/blob/main/docs/usage.md"><img src="https://img.shields.io/badge/Android-7.0%2B-38765e?logo=android&amp;logoColor=white" alt="支持 Android 7.0 及以上版本"></a>
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Kotlin-7652a4?logo=kotlin&amp;logoColor=white" alt="Kotlin">
  <img src="https://img.shields.io/badge/Jetpack%20Compose-4d669a?logo=jetpackcompose&amp;logoColor=white" alt="Jetpack Compose">
  <a href="https://github.com/xialia-cheng/ripple-plan/releases"><img src="https://img.shields.io/github/downloads/xialia-cheng/ripple-plan/total?label=%E4%B8%8B%E8%BD%BD%E9%87%8F&amp;color=4d669a" alt="累计下载量"></a>
</p>

<p align="center">
  <a href="https://github.com/xialia-cheng/ripple-plan/releases/latest"><strong>下载 APK</strong></a> ·
  <a href="https://github.com/xialia-cheng/ripple-plan/blob/main/docs/usage.md">功能与实况</a> ·
  <a href="https://github.com/xialia-cheng/ripple-plan/blob/main/docs/privacy.md">隐私与联网</a> ·
  <a href="https://github.com/xialia-cheng/ripple-plan/blob/main/docs/build.md">源码构建</a>
</p>

---

## 下载与更新

打开 **[GitHub 最新发布](https://github.com/xialia-cheng/ripple-plan/releases/latest)**，在 **Assets** 中下载最新 `APK` 安装包。应用支持 **Android 7.0（API 24）及以上版本**。应用内支持官方 API 与公共镜像**双路并发竞速检查更新**，并提供**官方直连 / 高速镜像 / 稳定镜像**三线路选择下载。

---

## 全功能使用教程

### 一、添加与导入课表（4 种方式）

#### 1. CSV / Excel / HTML 表格批量导入（最推荐整学期导入）

在课表页右上角点击「**导入**」，支持逗号分隔 CSV、直接从 Excel 二维星期表格复制粘贴、WakeUp 课表导出，以及切换至「**HTML 课表**」导入教务系统网页表格源码。

**标准 CSV 表头格式：**

```csv
课程名称,地点,教师,星期(1-7),开始节次,结束节次,起始周,结束周,单双周(全周/单周/双周)
高等数学A,笃行楼 302,陈明远,1,1,2,1,16,全周
数据结构与算法,实验楼 B405,林清妍,2,3,4,1,16,全周
大学物理实验,理学楼 201,周致远,3,5,6,2,16,双周
人工智能导论,明德楼 108,苏景行,4,7,8,1,15,单周
现代界面设计,创新楼 502,许知微,5,3,4,1,14,全周
```

- 支持不连续跳周（如 `5-10,12-18`）、周次组合（如 `1-16周(单)`）、节次范围（`1-2`）或精确时间（`08:00-09:40` 自动映射节次）。
- **实用技巧**：若教务系统只有 PDF 或截图课表，可把上述标准表头模板发给任意 AI，让它整理成 CSV 后直接粘贴导入（导入弹窗顶部支持一键复制模板）。

#### 2. 课表网格点空白手动加课 & 批量管理

- 开启课表页顶部「**编辑课表**」开关后，点击任意空白网格即可加课，点击已有课程可修改或改色（同名课程自动统一配色）。
- 编辑页支持**可视化 1–52 周方格多选**（一键全选、单周、双周或自由点选不连续跳周）。
- 在「**课表控制中心 → 课程总览**」中可按星期一至星期日分组浏览全部课程，并支持一键进入多选模式批量删除。

#### 3. 语音 / 一句话智能加课

- 点击右下角 `✨` 悬浮球，使用语音或文字输入自然语言，例如：`每周一三第1-2节高等数学在禹412，第1到16周`。
- 支持单双周识别与多句连续批量生成草稿，并自动检测时间冲突。

#### 4. 课表分享口令、JSON 完整备份与基础作息设置

- **分享口令（`FLOWTIMETABLE://...`）**：仅分享单张课表的课程与作息时间，适合同学之间互传导入。
- **JSON 完整备份**：包含全部多课表、1–16 节次作息、全部日程（含已完成）、自定义标签及午休配置；恢复时支持「智能合并去重」或「清空覆盖」。
- **作息与分隔带配置**：前往「**课表控制中心 → 课表设置**」设置开学第一周日期、学期总周数、每周起始日（周一/周日）、节次作息时间，以及可选「顶部附加时段 / 课间午休分隔带」。

### 二、日程管理、自定义标签与语音 AI 批量规划

- **自定义标签与循环**：点击「**+ 新建标签**」可自由定制标签名称、图标与 Hex 配色并在日程页一键筛选；每条日程支持 `0–120` 分钟提前提醒及「不重复 / 每日 / 工作日 / 每周」重复规则，长按卡片可快速推迟或 10 分钟后再提醒。
- **✨ 语音 AI 批量规划**：点击右下角 `✨` 悬浮球可语音或一段话同时下达多个任务（默认纯本地离线极速解析；配置自定义 OpenAI 兼容 HTTPS 接口后，AI 可结合你的空闲时段批量生成草稿并自动提示时间冲突）。

### 三、实况通知（Live Updates）、后台常驻与防杀指南

1. **核心权限**：在「我的 → 通知、实况与后台守护 → 权限与诊断」开启**通知权限**、**实时活动（Live Updates）**、**精确闹钟（闹钟和提醒）**，以及**系统勿扰权限**（支持「优先打扰 / 仅闹钟 / 完全静音」3 级可选）。
2. **各品牌后台防杀建议路径**：
   - **ColorOS（OPPO / 一加 / 真我）**：设置 → 应用 → 应用管理 → 微澜日程 → 耗电管理 → 允许完全后台行为 + 开启自启动；多任务卡片下拉「锁定」或开启「从最近任务隐藏」。
   - **HyperOS / MIUI（小米 / Redmi）**：设置 → 应用设置 → 应用管理 → 微澜日程 → 省电策略设为「无限制」+ 开启「自启动」；近期任务长按卡片加锁。
   - **OriginOS（vivo / iQOO）**：设置 → 应用与权限 → 应用管理 → 微澜日程 → 电池 → 后台高耗电设为「允许」+ 权限中开启「自启动」；多任务卡片加锁。
   - **HarmonyOS / EMUI（华为 / 荣耀）**：设置 → 应用和服务 → 应用启动管理 → 关闭「自动管理」，手动开启「允许自启动 / 允许关联启动 / 允许后台活动」；多任务下拉加锁。
3. **自动降级说明**：在未接管 Android 16 原生实况通知胶囊的系统上，应用会自动降级为**标准常驻进度通知**（完整保留 Chronometer 实时倒计时、课程首字徽章与快捷操作，不影响提醒准时触发）。

### 四、学习专注与快捷入口

- **专注计时与联动**：支持 `1–180` 分钟专注与 `0–60` 分钟休息，关联课程与预计时长，并可联动实况胶囊及系统勿扰（结束后自动恢复）。
- **桌面小组件**：在系统桌面添加「微澜日程 · 下一项」，支持多尺寸展示当前与后续课程/日程。
- **下拉控制中心快捷瓷贴**：编辑系统下拉快捷开关面板，拖入「微澜日程」瓷贴即可随时一键直达今日课表。
- **长按应用图标快捷方式**：长按桌面图标直接呼出「一句话智能创建」「新建日程」「今日课表」。

[查看详尽功能文档](https://github.com/xialia-cheng/ripple-plan/blob/main/docs/usage.md)

## 隐私与联网

核心功能离线可用，所有课表与日程默认仅保存在本机。检查更新及下载仅通过官方 GitHub 或所选 HTTPS 公共镜像获取公开 Release，不上传任何个人数据；自定义 AI 解析默认关闭。

[查看数据存储、联网用途与备份说明](https://github.com/xialia-cheng/ripple-plan/blob/main/docs/privacy.md)

## 从源码构建

源码构建需要 JDK 21、Gradle 9.3.1 和 Android SDK 36.1。

[查看构建、单元测试和 lint 命令](https://github.com/xialia-cheng/ripple-plan/blob/main/docs/build.md)

## 许可证与反馈

微澜日程由开发者与 AI 协作开发，项目源码采用 **[MIT 开源许可证](LICENSE)**。使用中遇到问题或有功能建议，欢迎在 [GitHub Issues](https://github.com/xialia-cheng/ripple-plan/issues) 反馈。如果你觉得微澜日程实用，欢迎给仓库点一颗 Star。
