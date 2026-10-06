# DailyPilot（每日领航）

DailyPilot 是一个面向 Android 手机的本地低频自动化助手。它只通过用户可见的官方界面，按日检查并执行已经启用的任务，完成后记录状态并避免重复操作。

当前版本：**v0.3.4（versionCode 25）**

v0.3.4 修复微生活选中“明日”时无法签到的问题，以及脚本引擎等待循环保留首次结果造成的超时；减少重复截图和闲鱼加载后的固定等待，保留点击前后校验和加载超时。继承 v0.3.3 的笔笔省新版适配。已在小米 14 Pro 上完成真实签到、全模块防重复及只读验收，详见 [`android/docs/TEST_MATRIX.md`](android/docs/TEST_MATRIX.md)。

## 当前模块

- **湖南大学微生活**：打开微信小程序并完成每日签到。
- **微信支付「提现笔笔省」**：只识别并领取当天唯一、明确免费的提现额度券；面额动态识别，可能是 30、40、50 等，不写死金额。
- **闲鱼免费擦亮**：进入「我发布的」，只执行明确免费的普通擦亮；不触碰「超级擦亮」、推广、充值、支付或交易页面。

已在小米 14 Pro / Android 16 上完成真机验收。不同手机、系统版本、微信/闲鱼版本或账号状态可能导致页面入口变化，首次使用请先运行“只检查今日状态”。

## 安装与首次配置

1. 从 [GitHub Releases](https://github.com/Liujunzhe0425/DailyPilot/releases/latest) 下载 APK，或按下方说明自行构建。当前正式发布包为 `arm64-v8a`，适用于小米 14 Pro 等主流 64 位 ARM 手机。
2. 安装后打开“每日领航”，在“设置与权限”中按提示开启无障碍、悬浮窗、精确定时、通知、电池不限制和系统自启动。
3. 确认微信、闲鱼已经登录；在微信中把“湖南大学微生活”和“提现笔笔省”添加到手机桌面，并保留桌面快捷方式的默认名称。手机桌面还需要保留“闲鱼”图标。
4. 先点击“只检查今日状态”确认页面识别正确，再运行全部模块或单独运行某个模块。

更详细的设备设置、电脑测试和发布流程见 [`android/docs/`](android/docs/)。

## 从源码构建

要求：Windows/macOS/Linux、Android SDK 36、JDK 17 以上（建议 JDK 21）。

```powershell
cd android
.\gradlew.bat :app:testAssistantDebugUnitTest
.\gradlew.bat :app:assembleAssistantDebug
```

构建产物位于：

```text
android/app/build/outputs/apk/assistant/debug/
```

通常可直接安装 `dailypilot-v0.3.4-arm64-v8a.apk`。使用 `adb install -r` 覆盖安装可以保留本机运行记录和大部分权限；真机部署脚本见 [`android/tools/deploy-to-phone.ps1`](android/tools/deploy-to-phone.ps1)。正式 Release 签名需要在本机自行准备未纳入 Git 的 `android/sign.properties` 和签名密钥，字段说明见 [`android/docs/RELEASE.md`](android/docs/RELEASE.md)。

## 运行原则与安全边界

- 每个模块先读取“今日是否已完成”，已完成就跳过；状态不明确时不点击。
- 只操作官方 UI，不抓包、不调用未公开接口、不保存或上传 Token、Cookie、密码。
- 不绕过登录、验证码、人脸或设备安全验证；遇到这些情况交给人工处理。
- 不执行付费任务、推广、充值、支付、交易或任何需要确认扣费的操作。
- 应用数据只保存在手机本地；日志和失败证据按应用内保留策略清理。

自动化操作可能受第三方 App 页面改版、网络、账号风控和系统后台策略影响。请在自己的设备和账号上使用，并在启用新模块前先进行只读检查。

## 后续扩展

项目会在保持上述安全边界的前提下，后续可能新增更多手机上“每日签到、每日领取或其他低频日常任务”性质的模块。新模块必须先具备明确的官方 UI 入口、可验证的成功状态和“仅免费/不付费”的安全规则，经过测试后再加入默认启用列表。

## 开源与第三方归属

本项目基于 [AutoJs6 6.7.0](https://github.com/SuperMonster003/AutoJs6) 构建。上游及其衍生文件遵循 Mozilla Public License 2.0，完整文本和归属声明见 [`android/LICENSE`](android/LICENSE) 与 [`android/docs/UPSTREAM_AUTOJS6.md`](android/docs/UPSTREAM_AUTOJS6.md)。第三方组件的许可证和声明随源码保留。

## 免责声明

本项目仅供个人学习和在本人设备上的自动化使用。使用者应自行遵守相关平台的服务条款、法律法规和账号安全要求；作者不对第三方页面变化、账号限制或使用后果负责。
