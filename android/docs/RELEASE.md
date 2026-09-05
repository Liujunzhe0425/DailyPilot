# Release 构建与验收

## 构建环境

- JDK 21（Gradle）和 JDK 17（兼容工具链）
- Android SDK 36
- AutoJs6 6.7.0，固定上游提交 `ed3eb10e88db5a8425fd94bdddefa4176e5e1c94`

## 本地签名

签名密钥和 `sign.properties` 不进入 Git。`android/sign.properties` 的字段为 `storeFile`、`storePassword`、`keyAlias`、`keyPassword`；正式值只保留在构建电脑本地。

```powershell
.\gradlew.bat :app:assembleAssistantRelease
Get-FileHash -Algorithm SHA256 .\app\build\outputs\apk\assistant\release\*.apk
```

## 自动检查

```powershell
.\gradlew.bat :app:testAssistantDebugUnitTest
.\gradlew.bat :app:assembleAssistantDebugAndroidTest
.\gradlew.bat :app:lintAssistantDebug
```

assistant 新增源码不得留下 Fatal/Error Lint 项；固定上游 AutoJs6 的既有报告单独记录，不作为新增代码放行依据。

## 真机验收边界

新版本发布前应在实际目标设备上检查 ADB、UI 树和业务页面，至少覆盖三业务 live-run、锁屏后弹框时延、微信/闲鱼版本变化适配、前台恢复以及无重复/无付费点击边界。v0.3.2 已在小米 14 Pro / Android 16 上完成真机验收；后续版本仍需重新验证。详见 `TEST_MATRIX.md`。
