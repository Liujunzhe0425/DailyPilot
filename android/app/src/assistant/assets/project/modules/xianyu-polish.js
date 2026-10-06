"use strict";

const selectors = require("../core/selectors.js");
const safety = require("../core/safety.js");
const navigation = require("../core/navigation.js");
const FORBIDDEN = ["超级擦亮", "推广", "充值", "支付", "扣费", "权益", "托管无忧卖", "加500曝光"];

function ensurePage() {
  return navigation.openXianyuPublishedItems();
}

function freeDailyCard() {
  const matches = selectors.nodesContaining("一键擦亮").filter(function (node) {
    const cardText = selectors.subtreeTexts(node).join(" ");
    return cardText.indexOf("今日数据") >= 0 && cardText.indexOf("+5曝光") >= 0 &&
      !FORBIDDEN.some(function (word) { return cardText.indexOf(word) >= 0; });
  });
  return matches.length === 1 ? matches[0] : null;
}

function dailyCardText() {
  const nodes = selectors.nodesContaining("今日数据");
  for (let i = 0; i < nodes.length; i += 1) {
    const value = selectors.subtreeTexts(nodes[i]).join(" ");
    if (value.indexOf("宝贝曝光") >= 0 && value.indexOf("宝贝浏览") >= 0) return value;
  }
  return "";
}

function classify(ctx) {
  if (!ensurePage()) {
    const failureValues = selectors.visibleTexts();
    const failureSecurity = safety.classifySecurityPage(failureValues);
    if (failureSecurity) return ctx.response(failureSecurity, "security-page");
    return ctx.response("UNKNOWN", "xianyu-published-page-not-found", true);
  }
  if (textContains("今日已擦亮").findOnce() || descContains("今日已擦亮").findOnce() ||
    textContains("擦亮成功").findOnce() || descContains("擦亮成功").findOnce()) {
    return ctx.response("COMPLETED_TODAY", "official-completed-marker");
  }
  if (freeDailyCard()) return ctx.response("NOT_COMPLETED", "free-daily-one-click-polish:+5-exposure");
  const cardText = dailyCardText();
  if (cardText.indexOf("试试超级擦亮") >= 0 && cardText.indexOf("一键擦亮") < 0) {
    return ctx.response("COMPLETED_TODAY", "free-control-consumed-paid-upsell-visible");
  }
  const values = selectors.visibleTexts();
  const security = safety.classifySecurityPage(values);
  if (security) return ctx.response(security, "security-page");
  const paid = FORBIDDEN.find(function (word) { return values.some(function (value) { return value.indexOf(word) >= 0; }); });
  return ctx.response("UNKNOWN", paid ? "FORBIDDEN_UI:" + paid : "free-polish-control-not-found", false);
}

module.exports.probe = classify;
module.exports.execute = function (ctx) {
  if (ctx.command.readOnly) return ctx.response("FAILED", "read-only-command", false);
  if (!ensurePage()) {
    return ctx.response("UNKNOWN", "xianyu-published-page-not-found", true);
  }
  if (selectors.firstNodeContaining("今日已擦亮") || selectors.firstNodeContaining("擦亮成功")) {
    return ctx.response("COMPLETED_TODAY", "official-completed-marker");
  }
  const target = freeDailyCard();
  if (!target) return classify(ctx);
  safety.safeRelativeClick(target, 0.27, 0.76, {
    visibleTexts: function () { return selectors.splitValues(selectors.subtreeTexts(target)); },
    safetyTexts: function () { return selectors.subtreeTexts(target); },
    beforeMarkers: ["今日数据", "一键擦亮", "+5曝光"],
    afterMarkers: ["今日已擦亮", "擦亮成功", "试试超级擦亮"],
    waitForAnyText: selectors.waitForAnyNodeContaining,
    timeoutMs: 4000,
    forbidden: FORBIDDEN
  });
  return ctx.response("EXECUTED", "clicked-free-one-key-polish:+5-exposure");
};
module.exports.verify = classify;
module.exports.cleanup = function (ctx) { return ctx.response("CLEANED", "no-op"); };
