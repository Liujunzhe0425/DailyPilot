"use strict";

const selectors = require("../core/selectors.js");
const safety = require("../core/safety.js");
const navigation = require("../core/navigation.js");
const vision = require("../core/vision.js");

function ensurePage() {
  let values = selectors.visibleTexts();
  if (values.indexOf("已签") >= 0 || values.indexOf("签到") >= 0 || values.some(function (v) { return v.indexOf("当前积分") >= 0; })) return values;
  if (currentPackage() === "com.tencent.mm" && vision.waitForKnown(vision.probeWeihuda, 800).state !== "UNKNOWN") return values;
  if (!navigation.openWechatDesktopShortcut(["湖南大学微生活"], ["当前积分", "已签", "签到"], function () {
    return vision.probeWeihuda().state !== "UNKNOWN";
  })) return [];
  return selectors.visibleTexts();
}

function classify(ctx) {
  if (String(currentPackage() || "") === "com.tencent.mm") {
    const currentVisual = vision.probeWeihuda();
    if (currentVisual.state !== "UNKNOWN") return ctx.response(currentVisual.state, currentVisual.detail);
  }
  let values = selectors.visibleTexts();
  const security = safety.classifySecurityPage(values);
  if (security) return ctx.response(security, "security-page");
  if (values.indexOf("已签") >= 0) return ctx.response("COMPLETED_TODAY", "text:已签");
  if (values.indexOf("签到") >= 0) return ctx.response("NOT_COMPLETED", "text:签到");
  let readyVisual = null;
  if (!navigation.openWechatDesktopShortcut(["湖南大学微生活"], ["当前积分", "已签", "签到"], function () {
    const result = vision.probeWeihuda();
    if (result.state === "UNKNOWN") return false;
    readyVisual = result;
    return true;
  })) return ctx.response("UNKNOWN", "weihuda-page-not-found", true);
  // This result was captured during this navigation call, not persisted across
  // phases. The click helper still takes a fresh screenshot before acting.
  const visual = readyVisual || vision.waitForKnown(vision.probeWeihuda, 1500);
  if (visual.state === "UNKNOWN") {
    values = selectors.visibleTexts();
    const navigatedSecurity = safety.classifySecurityPage(values);
    if (navigatedSecurity) return ctx.response(navigatedSecurity, "security-page");
    if (values.indexOf("已签") >= 0) return ctx.response("COMPLETED_TODAY", "text:已签");
    if (values.indexOf("签到") >= 0) return ctx.response("NOT_COMPLETED", "text:签到");
  }
  return ctx.response(visual.state, visual.detail, visual.state === "UNKNOWN");
}

function compositeSignCard() {
  const matches = selectors.nodesContaining("当前积分").filter(function (node) {
    const lines = selectors.splitValues(selectors.subtreeTexts(node));
    return lines.some(function (line) { return line.indexOf("当前积分") >= 0; }) &&
      lines.indexOf("签到") >= 0 && lines.indexOf("已签") < 0;
  });
  matches.sort(function (a, b) { return selectors.nodeArea(a) - selectors.nodeArea(b); });
  return matches.length > 0 ? matches[0] : null;
}

module.exports.probe = classify;
module.exports.execute = function (ctx) {
  if (ctx.command.readOnly) return ctx.response("FAILED", "read-only-command", false);
  const state = classify(ctx);
  if (state.state !== "NOT_COMPLETED") return state;
  if (String(state.message || "").indexOf("visual-") === 0) {
    // The visual helper revalidates the exact page signature and button color
    // before clicking, so a full accessibility scan adds latency but no safety.
    vision.clickWeihudaSign();
    return ctx.response("EXECUTED", "clicked-visual:签到");
  }
  const target = selectors.exactNode("签到");
  if (target) {
    safety.safeClick(target, {
      visibleTexts: selectors.visibleTexts,
      safetyTexts: function () { return selectors.subtreeTexts(target); },
      beforeMarkers: ["签到"],
      afterMarkers: ["已签"],
      waitForAnyText: selectors.waitForAnyText,
      timeoutMs: 8000,
      coordinateFallback: true,
      forbidden: ["积分兑换", "兑换"]
    });
    return ctx.response("EXECUTED", "clicked-exact:签到");
  }
  const card = compositeSignCard();
  if (!card) {
    vision.clickWeihudaSign();
    return ctx.response("EXECUTED", "clicked-visual:签到");
  }
  safety.safeRelativeClick(card, 0.86, 0.50, {
    visibleTexts: selectors.visibleTexts,
    safetyTexts: function () { return selectors.subtreeTexts(card); },
    beforeMarkers: ["签到"],
    afterMarkers: ["已签"],
    waitForAnyText: selectors.waitForAnyText,
    timeoutMs: 8000,
    forbidden: ["积分兑换", "兑换"]
  });
  return ctx.response("EXECUTED", "clicked-composite-card:签到");
};
module.exports.verify = classify;
module.exports.cleanup = function (ctx) { return ctx.response("CLEANED", "no-op"); };
