# FlowSchedule 公开发布前 P0/P1 修复指令（可直接复制发给 Google AI Studio）

请基于当前 Android 工程（Kotlin + Jetpack Compose）完成以下公开发布前的安全、数据完整性与合规修复，保持现有课表、日程与 ColorOS 17 流体云功能正常可用：

1. **移除不再使用的内置字体与模板残留**
   - `Type.kt` 已切换为系统默认字体 `FontFamily.Default`，请彻底删除 `app/src/main/res/font/jetbrains_mono.ttf` 与 `app/src/main/res/font/plus_jakarta_sans.ttf` 两个字体文件（以及代码或配置中对它们的任何残留引用），避免公开发布时涉及 OFL 1.1 字体授权声明负担。

2. **修复 API Key 加密与网络传输安全（`LocalKeystoreCipher.kt` & `OpenAiCompatibleClient.kt`）**
   - 在 `LocalKeystoreCipher.kt` 中移除 Keystore 加密失败时静默回退为可逆 Base64 存储的逻辑；当 Android Keystore 加密或解密失败时，抛出明确异常或返回失败状态，由界面提示用户加密失败并**拒绝以明文/Base64 形式保存凭据**；若检测到历史版本遗留的 Base64 格式旧凭据，则提示用户重新输入以使用 Keystore 重新加密。
   - 在 `OpenAiCompatibleClient.kt` 中强制校验自定义 Base URL 必须使用 `https://` 协议（正式发布禁止明文 `http://` 请求），若用户输入非 HTTPS 地址则在保存和测试连接时给出明确错误提示。
   - 在界面设置自定义 AI 并首次启用时，清晰展示目标服务域名与即将发送的数据范围（仅用户输入文本、当前时间与默认周数），并在“一句话创建”输入框旁标明当前是“纯本地离线解析”还是“自定义 AI 在线解析”。

3. **完善 Android 系统自动备份排除规则与锁屏隐私默认项**
   - 更新 `app/src/main/res/xml/backup_rules.xml` 与 `app/src/main/res/xml/data_extraction_rules.xml`，明确将包含加密 API Key 或运行时敏感偏好的 `SharedPreferences` / DataStore 凭据文件排除在 Android 系统自动云备份与设备迁移之外（`exclude`）。
   - 在 `LiveUpdateManager.kt` 中调整锁屏通知隐私默认策略：默认不在锁屏界面暴露详细教室地点、教师与日程备注（或提供独立开关且默认关闭锁屏敏感详情展示，或使用 `VISIBILITY_PRIVATE` 配合精简公共通知）。

4. **补齐 JSON 完整备份范围与去重合并逻辑（`TimetableRepository.kt` & `ScheduleDao.kt` & `TimetableImportExport.kt`）**
   - 检查 JSON 完整备份的导出范围：在 `ScheduleDao` 中新增查询全部日程（包含已完成日程）的方法，使 JSON 备份导出同时覆盖**全部课表、课程、节次作息以及全部日程（含已完成日程）**，并在界面说明中明确列出导出包含的数据项及明文风险提示（含课程地点、教师、日程备注）。
   - 优化 JSON 备份恢复与重复导入逻辑：在恢复导入时对同名课表或同时间同标题课程/日程进行去重或提供“覆盖恢复 / 智能合并”选项，避免连续两次导入同一份备份文件产生重复数据；若解析备份失败，确保现有数据库数据完整保留不被清空。

5. **规范正式包名与 Release 构建配置（`app/build.gradle.kts` & `shortcuts.xml`）**
   - 将 `app/build.gradle.kts` 中的 `applicationId` 从临时 ID `com.aistudio.flowschedule.k7m2x9` 改为正式包名 `io.github.xialia150wq.flowschedule`（同步更新 `app/src/main/res/xml/shortcuts.xml` 中所有 `android:targetPackage` 为 `io.github.xialia150wq.flowschedule`）。
   - 在 `app/build.gradle.kts` 的 `release` 构建类型中开启代码压缩与资源精简（`isMinifyEnabled = true`、`isShrinkResources = true`），并在 `proguard-rules.pro` 中保留 Room、DataStore、Kotlinx Serialization / JSON 反射及 Glance Widget 所需混淆规则，确保 `assembleRelease` 与 `testDebugUnitTest`、`lintDebug` 均能顺利通过。

6. **补充多设备 Live Updates / 流体云兼容说明（`README.md` 与应用内提示）**
   - 在项目根目录的 `README.md` 中明确说明跨品牌兼容策略：本应用基于 Android 16 标准 Live Updates API（`Notification.ProgressStyle`、`EXTRA_REQUEST_PROMOTED_ONGOING`）实现顶部胶囊与锁屏实况更新，已在 **ColorOS 17** 实测完整流体云胶囊效果；在已接管原生 Live Updates 的其他 Android 16+ 机型（如小米、vivo、三星等）上可随系统自动适配；若系统尚未接入原生 Live Updates 或为 Android 15 及以下版本，将平滑降级为标准常驻进度通知，不会与厂商自带“超级岛 / 原子岛”产生冲突，也不影响课表、闹钟与勿扰功能。
   - 在应用内的权限/实况通知引导区域补充一行简短说明，提示非 ColorOS 用户若未看到顶部胶囊，属系统尚未接管 Android 16 原生实况通知，在下拉通知栏与锁屏中仍可正常查看课程倒计时。
