# 从源码构建微澜日程

[返回微澜日程项目主页](https://github.com/xialia-cheng/ripple-plan)

需要 **JDK 21**、**Gradle 9.3.1** 和 **Android SDK 36.1**。在仓库根目录执行以下命令。已有本地调试签名文件 `debug.keystore` 时，从第二条命令开始执行。

```sh
keytool -genkeypair -keystore debug.keystore -storepass android -alias androiddebugkey -keypass android -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=Android Debug,O=Android,C=US"
gradle :app:assembleDebug
```

调试 APK 位于 `app/build/outputs/apk/debug/`。执行 `gradle :app:testDebugUnitTest :app:lintDebug` 可运行单元测试和 lint。正式包的构建步骤见 [GitHub Actions 工作流](../.github/workflows/main.yml)。
