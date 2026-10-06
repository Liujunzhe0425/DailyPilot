"use strict";

const selectors = require("../core/selectors.js");
const safety = require("../core/safety.js");
const navigation = require("../core/navigation.js");
const vision = require("../core/vision.js");
const FORBIDDEN = ["购物", "游戏", "视频", "去完成", "支付", "购买", "充值", "外卖", "银行", "活动"];

function ensurePage() {
  let values = selectors.visibleTexts();
  if (values.some(function (v) { return /^\d+(?:\.\d+)?提现券$/.test(v); })) return values;
  if (currentPackage() === "com.tencent.mm") {
    const currentVisual = vision.waitForKnown(vision.probeCoupon, 1200);
    if (currentVisual.state !== "UNKNOWN") return selectors.visibleTexts();
  }
  // Mini-program canvas nodes are not exposed to accessibility on this phone.
  // Re-open the fixed desktop shortcut instead of spending a second visual
  // probe just to decide whether navigation is necessary.
  if (!navigation.openWechatDesktopShortcut(["微信支付提现笔笔省", "提现笔笔省"], ["提现券"], function () {
    return vision.probeCoupon().state !== "UNKNOWN";
  })) return [];
  return selectors.visibleTexts();
}

function textOfTree(node) {
  const result = [];
  function visit(value) {
    if (!value) return;
    if (value.text()) result.push(String(value.text()));
    if (value.desc()) result.push(String(value.desc()));
    for (let i = 0; i < value.childCount(); i += 1) visit(value.child(i));
  }
  visit(node);
  return result.join(" ");
}

function candidates() {
  const amountNodes = textMatches("^\\d+(?:\\.\\d+)?提现券$").find();
  const result = [];
  amountNodes.forEach(function (amountNode) {
    let card = amountNode;
    let cardText = textOfTree(card);
    for (let depth = 0; depth < 6 && card.parent(); depth += 1) {
      const parent = card.parent();
      const parentText = textOfTree(parent);
      const hasControl = parentText.indexOf("已领取") >= 0 || /(^|\s)领取($|\s)/.test(parentText);
      const amountCount = (parentText.match(/\d+(?:\.\d+)?提现券/g) || []).length;
      card = parent;
      cardText = parentText;
      if (hasControl && amountCount === 1) break;
    }
    if (FORBIDDEN.some(function (word) { return cardText.indexOf(word) >= 0; })) return;
    const claimed = cardText.indexOf("已领取") >= 0;
    let claim = null;
    const descendants = card.find(text("领取"));
    if (descendants && descendants.size() === 1) claim = descendants.get(0);
    result.push({ amount: String(amountNode.text()), card: card, claim: claim, claimed: claimed, text: cardText });
  });
  return result;
}

function compositeCandidates() {
  const result = [];
  const seenBounds = {};
  selectors.nodesContaining("提现券").forEach(function (node) {
    const lines = selectors.splitValues(selectors.subtreeTexts(node));
    const amounts = lines.filter(function (line) { return /^\d+(?:\.\d+)?提现券$/.test(line); })
      .filter(function (value, index, all) { return all.indexOf(value) === index; });
    if (amounts.length !== 1) return;
    const claimed = lines.indexOf("已领取") >= 0;
    const available = lines.indexOf("领取") >= 0;
    if (!claimed && !available) return;
    const cardText = lines.join(" ");
    if (FORBIDDEN.some(function (word) { return cardText.indexOf(word) >= 0; })) return;
    const bounds = node.bounds();
    if (!bounds || bounds.width() <= 0 || bounds.height() <= 0) return;
    const key = [bounds.left, bounds.top, bounds.right, bounds.bottom].join(":");
    if (seenBounds[key]) return;
    seenBounds[key] = true;
    result.push({ amount: amounts[0], card: node, claim: null, claimed: claimed, relative: available, text: cardText });
  });
  result.sort(function (a, b) { return selectors.nodeArea(a.card) - selectors.nodeArea(b.card); });
  if (result.length > 1) {
    const smallestArea = selectors.nodeArea(result[0].card);
    return result.filter(function (entry) { return selectors.nodeArea(entry.card) === smallestArea; });
  }
  return result;
}

function allCandidates() {
  const accessible = candidates();
  const actionable = accessible.filter(function (card) { return card.claimed || card.claim; });
  return actionable.length > 0 ? actionable : compositeCandidates();
}

function classify(ctx) {
  if (String(currentPackage() || "") === "com.tencent.mm") {
    const currentVisual = vision.probeCoupon();
    if (currentVisual.state !== "UNKNOWN") return ctx.response(currentVisual.state, currentVisual.detail);
  }
  let values = selectors.visibleTexts();
  const security = safety.classifySecurityPage(values);
  if (security) return ctx.response(security, "security-page");
  let cards = allCandidates();
  const claimed = cards.filter(function (card) { return card.claimed; });
  if (claimed.length === 1) return ctx.response("COMPLETED_TODAY", "claimed:" + claimed[0].amount);
  const available = cards.filter(function (card) { return !card.claimed && (card.claim || card.relative); });
  if (available.length === 1) return ctx.response("NOT_COMPLETED", "available:" + available[0].amount);
  let readyVisual = null;
  if (!navigation.openWechatDesktopShortcut(["微信支付提现笔笔省", "提现笔笔省"], ["提现券"], function () {
    const result = vision.probeCoupon();
    if (result.state === "UNKNOWN") return false;
    readyVisual = result;
    return true;
  })) return ctx.response("UNKNOWN", "coupon-page-not-found", true);
  const visual = readyVisual || vision.waitForKnown(vision.probeCoupon, 1500);
  if (visual.state !== "UNKNOWN") return ctx.response(visual.state, visual.detail);
  values = selectors.visibleTexts();
  const navigatedSecurity = safety.classifySecurityPage(values);
  if (navigatedSecurity) return ctx.response(navigatedSecurity, "security-page");
  cards = allCandidates();
  const navigatedClaimed = cards.filter(function (card) { return card.claimed; });
  if (navigatedClaimed.length === 1) return ctx.response("COMPLETED_TODAY", "claimed:" + navigatedClaimed[0].amount);
  const navigatedAvailable = cards.filter(function (card) { return !card.claimed && (card.claim || card.relative); });
  if (navigatedAvailable.length === 1) return ctx.response("NOT_COMPLETED", "available:" + navigatedAvailable[0].amount);
  return ctx.response("UNKNOWN", visual.detail || "free-daily-candidate-count:" + available.length, values.length === 0);
}

module.exports.probe = classify;
module.exports.execute = function (ctx) {
  if (ctx.command.readOnly) return ctx.response("FAILED", "read-only-command", false);
  const state = classify(ctx);
  if (state.state !== "NOT_COMPLETED") return state;
  if (String(state.message || "").indexOf("visual-") === 0) {
    // The visual click helper repeats the complete card/button precondition
    // immediately before tapping. Avoid two expensive accessibility-tree
    // scans on this canvas-only mini program.
    vision.clickCouponClaim();
    return ctx.response("EXECUTED", "claimed-visual:daily-free-coupon");
  }
  const available = allCandidates().filter(function (card) { return !card.claimed && (card.claim || card.relative); });
  if (available.length === 0) {
    vision.clickCouponClaim();
    return ctx.response("EXECUTED", "claimed-visual:daily-free-coupon");
  }
  if (available.length !== 1) return ctx.response("UNKNOWN", "candidate-not-unique", false);
  if (available[0].claim) {
    safety.safeClick(available[0].claim, {
      visibleTexts: selectors.visibleTexts,
      safetyTexts: function () { return [available[0].text]; },
      beforeMarkers: [available[0].amount],
      afterMarkers: ["已领取"],
      waitForAnyText: selectors.waitForAnyText,
      timeoutMs: 8000,
      coordinateFallback: true,
      forbidden: FORBIDDEN
    });
  } else {
    safety.safeRelativeClick(available[0].card, 0.50, 0.82, {
      visibleTexts: selectors.visibleTexts,
      safetyTexts: function () { return [available[0].text]; },
      beforeMarkers: [available[0].amount, "领取"],
      afterMarkers: ["已领取"],
      waitForAnyText: selectors.waitForAnyText,
      timeoutMs: 8000,
      forbidden: FORBIDDEN
    });
  }
  return ctx.response("EXECUTED", "claimed:" + available[0].amount);
};
module.exports.verify = classify;
module.exports.cleanup = function (ctx) { return ctx.response("CLEANED", "no-op"); };
