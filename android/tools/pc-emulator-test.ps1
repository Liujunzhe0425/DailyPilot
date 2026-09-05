param(
    [string]$Serial = "emulator-5556",
    [string]$Avd = "dailyAutomationApi36b",
    [string]$SdkRoot = "D:\AndroidSdk",
    [string]$JavaHome = "C:\Program Files\Microsoft\jdk-21.0.11.10-hotspot",
    [switch]$NoWindow
)

$ErrorActionPreference = "Stop"
$root = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$adb = Join-Path $SdkRoot "platform-tools\adb.exe"
$env:JAVA_HOME = $JavaHome
$env:ANDROID_SDK_ROOT = $SdkRoot
$env:GRADLE_USER_HOME = "D:\GradleHome"

Push-Location $root
try {
    & .\gradlew.bat '-Porg.gradle.java.installations.paths=C:\Program Files\Microsoft\jdk-17.0.19.10-hotspot,C:\Program Files\Microsoft\jdk-21.0.11.10-hotspot' :app:testAssistantDebugUnitTest :app:assembleAssistantDebug :app:assembleAssistantDebugAndroidTest --quiet
    if ($LASTEXITCODE -ne 0) { throw "Gradle 构建或单元测试失败。" }

    $state = (& $adb -s $Serial get-state 2>$null).Trim()
    if ($state -ne "device") {
        $port = [int]($Serial -replace '^emulator-', '')
        $arguments = @('-avd', $Avd, '-port', $port, '-no-audio', '-no-snapshot', '-gpu', 'swiftshader_indirect')
        if ($NoWindow) { $arguments += '-no-window' }
        Start-Process (Join-Path $SdkRoot 'emulator\emulator.exe') -ArgumentList $arguments
        $deadline = (Get-Date).AddMinutes(3)
        do {
            Start-Sleep -Seconds 2
            $state = (& $adb -s $Serial get-state 2>$null).Trim()
        } while ($state -ne 'device' -and (Get-Date) -lt $deadline)
        if ($state -ne 'device') { throw "Android 16 模拟器 $Avd 未能在 3 分钟内启动。" }
    }
    while ((& $adb -s $Serial shell getprop sys.boot_completed).Trim() -ne "1") { Start-Sleep -Seconds 2 }

    $app = Join-Path $root "app\build\outputs\apk\assistant\debug\dailypilot-v0.2.8-x86_64.apk"
    $test = Join-Path $root "app\build\outputs\apk\androidTest\assistant\debug\app-assistant-debug-androidTest.apk"
    & $adb -s $Serial install -r -t $app
    if ($LASTEXITCODE -ne 0) { throw "安装测试 APK 失败。若签名变化，请先卸载旧测试版。" }
    & $adb -s $Serial install -r -t $test
    if ($LASTEXITCODE -ne 0) { throw "安装 AndroidTest APK 失败。" }

    & $adb -s $Serial shell appops set com.local.dailyautomation android:system_alert_window deny
    & $adb -s $Serial logcat -c
    & $adb -s $Serial shell am instrument -w -r -e class com.local.dailyautomation.overlay.RunControlsServiceTest com.local.dailyautomation.test/androidx.test.runner.AndroidJUnitRunner
    if ($LASTEXITCODE -ne 0) { throw "无悬浮窗权限回归测试失败。" }

    & $adb -s $Serial shell appops set com.local.dailyautomation android:system_alert_window allow
    & $adb -s $Serial shell pm grant com.local.dailyautomation android.permission.POST_NOTIFICATIONS 2>$null
    & $adb -s $Serial shell am instrument -w -r com.local.dailyautomation.test/androidx.test.runner.AndroidJUnitRunner
    if ($LASTEXITCODE -ne 0) { throw "设备测试失败。" }

    & $adb -s $Serial shell am force-stop com.local.dailyautomation
    & $adb -s $Serial shell am start -W -n com.local.dailyautomation/com.local.dailyautomation.ui.DashboardActivity
    Start-Sleep -Seconds 2
    $crash = & $adb -s $Serial logcat -d -t 500 | Select-String "FATAL EXCEPTION|ForegroundServiceDidNotStartInTimeException"
    if ($crash) { throw "检测到崩溃：$($crash -join [Environment]::NewLine)" }
    Write-Output "PASS：构建、单元测试、无权限前台服务回归、设备测试和首页冷启动均通过。"
} finally {
    Pop-Location
}
