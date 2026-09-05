"use strict";

const FORBIDDEN_TEXT = ["支付", "充值", "超级擦亮", "推广", "确认交易", "开通服务", "购买", "扣费"];
const SECURITY_TEXT = ["验证码", "人脸验证", "设备验证", "重新登录", "账号登录", "安全验证"];

function containsAny(values, needles) {
  return needles.find(function (needle) {
    return values.some(function (value) { return String(value).indexOf(needle) >= 0; });
  }) || null;
}

function assertNoForbiddenText(visibleTexts, extraForbidden) {
  const hit = containsAny(visibleTexts, FORBIDDEN_TEXT.concat(extraForbidden || []));
  if (hit) throw new Error("FORBIDDEN_UI:" + hit);
}

function classifySecurityPage(visibleTexts) {
  return containsAny(visibleTexts, SECURITY_TEXT) ? "MANUAL_VERIFICATION_REQUIRED" : null;
}

function safeClick(node, options) {
  options = options || {};
  const before = options.visibleTexts ? options.visibleTexts() : [];
  const safetyValues = options.safetyTexts ? options.safetyTexts() : before;
  assertNoForbiddenText(safetyValues, options.forbidden || []);
  if (!node) throw new Error("TARGET_NOT_FOUND");
  if (options.beforeMarkers && !options.beforeMarkers.some(function (v) { return before.indexOf(v) >= 0; })) {
    throw new Error("EXPECTED_PAGE_MARKER_MISSING");
  }
  let clickable = node;
  while (clickable && !clickable.clickable()) clickable = clickable.parent();
  let clicked = !!(clickable && clickable.click());
  if (!clicked && options.coordinateFallback === true) {
    const bounds = node.bounds();
    if (bounds && bounds.width() > 0 && bounds.height() > 0) {
      clicked = click(bounds.centerX(), bounds.centerY());
    }
  }
  if (!clicked) throw new Error("CLICK_FAILED");
  if (options.afterMarkers && options.waitForAnyText) {
    if (!options.waitForAnyText(options.afterMarkers, options.timeoutMs || 5000)) {
      throw new Error("POSTCONDITION_MISSING");
    }
  }
  return true;
}

function safeRelativeClick(node, relativeX, relativeY, options) {
  options = options || {};
  const before = options.visibleTexts ? options.visibleTexts() : [];
  const safetyValues = options.safetyTexts ? options.safetyTexts() : before;
  assertNoForbiddenText(safetyValues, options.forbidden || []);
  if (!node) throw new Error("TARGET_NOT_FOUND");
  if (options.beforeMarkers && !options.beforeMarkers.every(function (v) { return before.indexOf(v) >= 0; })) {
    throw new Error("EXPECTED_PAGE_MARKER_MISSING");
  }
  const bounds = node.bounds();
  if (!bounds || bounds.width() <= 0 || bounds.height() <= 0) throw new Error("INVALID_TARGET_BOUNDS");
  const x = Math.floor(bounds.left + bounds.width() * relativeX);
  const y = Math.floor(bounds.top + bounds.height() * relativeY);
  if (!click(x, y)) throw new Error("CLICK_FAILED");
  if (options.afterMarkers && options.waitForAnyText) {
    if (!options.waitForAnyText(options.afterMarkers, options.timeoutMs || 5000)) {
      throw new Error("POSTCONDITION_MISSING");
    }
  }
  return true;
}

module.exports = {
  FORBIDDEN_TEXT: FORBIDDEN_TEXT,
  SECURITY_TEXT: SECURITY_TEXT,
  assertNoForbiddenText: assertNoForbiddenText,
  classifySecurityPage: classifySecurityPage,
  safeClick: safeClick,
  safeRelativeClick: safeRelativeClick
};
