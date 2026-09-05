param(
    [Parameter(Mandatory = $true)][string]$Name,
    [string]$Adb = "adb",
    [string]$OutputRoot = "$PSScriptRoot\..\build\ui-captures"
)

$ErrorActionPreference = "Stop"
$devices = & $Adb devices | Select-String "\tdevice$"
if ($devices.Count -ne 1) { throw "必须且只能连接一台已授权 Android 设备。" }
$model = (& $Adb shell getprop ro.product.model).Trim()
$release = (& $Adb shell getprop ro.build.version.release).Trim()
$safeName = $Name -replace '[^A-Za-z0-9._-]', '_'
$target = Join-Path $OutputRoot $safeName
New-Item -ItemType Directory -Force -Path $target | Out-Null
& $Adb exec-out screencap -p > (Join-Path $target "screen.png")
& $Adb shell uiautomator dump /sdcard/assistant-window.xml | Out-Null
& $Adb pull /sdcard/assistant-window.xml (Join-Path $target "window.xml") | Out-Null
@{ capturedAt = (Get-Date).ToString("o"); model = $model; androidRelease = $release; name = $Name } |
    ConvertTo-Json | Set-Content -Encoding UTF8 (Join-Path $target "metadata.json")
Write-Output "只读采集完成：$target。请人工脱敏后再复制到测试 fixtures；原始截图不得提交。"
