# 小米 14 Pro / Android 16 测试矩阵

更新日期：2026-08-18

v0.3.2 已在用户小米 14 Pro / Android 16 上完成两轮最终验收。仓库中的 `synthetic` fixture 仍只用于自动化安全边界测试；线上页面随第三方 App 版本变化，发布新版本后仍应复测。

| 模块 | 状态 | 预期 | 证据状态 | 真机验收 |
|---|---|---|---|---|
| 微生活 | 未签到 | `NOT_COMPLETED`，仅可点精确“签到” | synthetic + 真机 | 已验收 |
| 微生活 | 已签到 | `COMPLETED_TODAY` | synthetic + 真机 | 已验收 |
| 微生活 | 安全验证 | `MANUAL_VERIFICATION_REQUIRED` | synthetic | 已覆盖 |
| 提现券 | 唯一免费可领 | 动态读取面额，唯一候选才可点击 | synthetic + 真机 | 已验收 |
| 提现券 | 已领取 | `COMPLETED_TODAY` | synthetic + 真机 | 已验收 |
| 提现券 | 多候选/任务/支付 | 安全失败、零点击 | synthetic | 已覆盖 |
| 闲鱼 | 明确免费擦亮 | 只点精确免费“擦亮” | synthetic + 真机 | 已验收 |
| 闲鱼 | 今日已擦亮 | `COMPLETED_TODAY` | synthetic + 真机 | 已验收 |
| 闲鱼 | 超级擦亮/推广/付费 | `FORBIDDEN_UI`、零点击 | synthetic + 真机 | 已覆盖 |

采集命令示例：

```powershell
.\tools\capture-ui-state.ps1 -Name weihuda-not-completed
```

真机验收必须补齐目标 App 版本、页面节点树、锁屏/解锁延迟、点击后官方成功状态以及七日运行记录。
