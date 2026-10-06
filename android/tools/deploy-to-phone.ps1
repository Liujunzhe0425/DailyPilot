param(
    [Parameter(Mandatory = $true)][string]$Serial,
    [string]$Apk = (Join-Path $PSScriptRoot "..\app\build\outputs\apk\assistant\debug\dailypilot-v0.3.4-arm64-v8a.apk"),
    [string]$Adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
)

$ErrorActionPreference = "Stop"
if (-not (Test-Path -LiteralPath $Adb)) { throw "ADB was not found: $Adb" }
if (-not (Test-Path -LiteralPath $Apk)) { throw "APK was not found: $Apk" }
if ((& $Adb -s $Serial get-state 2>$null).Trim() -ne "device") { throw "Phone is disconnected or USB debugging is not authorized: $Serial" }
$model = (& $Adb -s $Serial shell getprop ro.product.model).Trim()
$android = (& $Adb -s $Serial shell getprop ro.build.version.release).Trim()
Write-Output "Target device: $model / Android $android"
& $Adb -s $Serial install -r $Apk
if ($LASTEXITCODE -ne 0) { throw "APK installation failed. Check whether the installed app uses the same signing key." }
& $Adb -s $Serial shell am start -W -n com.local.dailyautomation/com.local.dailyautomation.ui.DashboardActivity
if ($LASTEXITCODE -ne 0) { throw "The app was installed but could not be launched." }
Write-Output "Deployment complete. Existing app data and permissions were preserved."
if (Get-Command scrcpy -ErrorAction SilentlyContinue) { Start-Process scrcpy -ArgumentList '-s', $Serial }
