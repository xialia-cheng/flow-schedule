<p align="center">
  <img src="app/src/main/res/mipmap-xxxhdpi/ic_launcher.png" width="144" height="144" alt="微澜日程应用图标">
</p>

<h1 align="center">微澜日程 · RipplePlan</h1>

<p align="center">
  面向校园生活的开源 Android 课表与日程应用。<br>
  在一个地方看课表、记待办、开专注计时。界面简洁，全程无广告。
</p>

<p align="center">
  <a href="LICENSE"><img src="https://img.shields.io/badge/License-MIT-4d669a" alt="MIT 开源许可证"></a>
  <a href="https://github.com/xialia-cheng/ripple-plan/releases/latest"><img src="https://img.shields.io/github/v/release/xialia-cheng/ripple-plan?sort=date&amp;label=Release&amp;color=4d669a" alt="最新发布版本"></a>
  <a href="#下载"><img src="https://img.shields.io/badge/Android-7.0%2B-38765e?logo=android&amp;logoColor=white" alt="支持 Android 7.0 及以上版本"></a>
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Kotlin-7652a4?logo=kotlin&amp;logoColor=white" alt="Kotlin">
  <img src="https://img.shields.io/badge/Jetpack%20Compose-4d669a?logo=jetpackcompose&amp;logoColor=white" alt="Jetpack Compose">
</p>

<p align="center">
  <a href="#下载"><strong>下载</strong></a> ·
  <a href="#功能">功能</a> ·
  <a href="#实况通知">实况通知</a> ·
  <a href="#隐私与联网">隐私与联网</a> ·
  <a href="#从源码构建">从源码构建</a>
</p>

---

## 下载

打开 **[GitHub 最新发布](https://github.com/xialia-cheng/ripple-plan/releases/latest)**，在 **Assets** 中下载 `app-release.apk`。应用支持 **Android 7.0（API 24）及以上版本**。

如果系统将「微澜日程」和旧版「流云日程」识别为两个应用，请先在旧版导出 JSON 备份，再在新版导入并核对恢复结果。

## 功能

- **课表**　支持多课表、单双周与不连续周次，自定义学期、每周起始日和 6–16 节上课时间。可设置课程配色，日常查看时也可切换到只读模式。
- **日程与提醒**　管理分组待办和自定义标签，可设置每日、工作日或每周重复提醒。
- **一句话录入**　例如输入「明天下午三点在图书馆自习，提前十分钟提醒」，即可在设备上解析日期、地点和提醒时间。
- **专注与小组件**　可设定专注和休息时长，按需联动系统勿扰模式，也可在桌面小组件中查看当天安排。
- **本地备份**　支持导出 JSON，恢复时可选择合并去重或覆盖导入。

## 实况通知

在支持 Android 16 Live Updates 的设备上，课程、日程和专注倒计时可以显示为系统实况胶囊，包括兼容的 ColorOS 流体云。在 Android 15 及以下版本或系统不提供实况胶囊时，应用使用通知栏进度提醒。实际显示与提醒时效取决于机型、系统版本与通知和精确闹钟权限。

## 隐私与联网

课表、日程和专注记录默认保存在设备上。核心功能离线可用，应用无广告、无统计埋点，无需登录账号。

- **版本检查**　向 GitHub Releases 请求公开版本信息。可在「我的」中关闭启动时自动检查更新。
- **自定义 AI 解析**　默认关闭。启用后，使用一句话录入时，应用会把当次输入和解析所需的时间等上下文发送到你配置的 HTTPS 服务。已有课表和日程保留在设备上。API Key 通过 Android Keystore 加密保存，相关设置已从系统备份和设备迁移规则中排除。
- **锁屏与导出**　锁屏默认隐藏地点、教师和备注等详情。手动导出的 JSON 备份包含课表和日程等明文数据，不含 API Key，请自行保管。

## 从源码构建

需要 **JDK 21**、**Gradle 9.3.1** 和 **Android SDK 36.1**。在仓库根目录执行以下命令。已有本地调试签名文件 `debug.keystore` 时，从第二条命令开始执行。

```sh
keytool -genkeypair -keystore debug.keystore -storepass android -alias androiddebugkey -keypass android -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=Android Debug,O=Android,C=US"
gradle :app:assembleDebug
```

调试 APK 位于 `app/build/outputs/apk/debug/`。执行 `gradle :app:testDebugUnitTest :app:lintDebug` 可运行单元测试和 lint。正式包的构建步骤见 [GitHub Actions 工作流](.github/workflows/main.yml)。

## 许可证与反馈

微澜日程由开发者与 AI 协作开发，项目源码采用 **[MIT 开源许可证](LICENSE)**。使用中遇到问题或有功能建议，欢迎在 [GitHub Issues](https://github.com/xialia-cheng/ripple-plan/issues) 反馈。如果你觉得微澜日程实用，欢迎给仓库点一颗 Star。
