"use strict";

const assert = require("assert");
const fs = require("fs");

global.colors = {
  red: function (value) { return (value >>> 16) & 255; },
  green: function (value) { return (value >>> 8) & 255; },
  blue: function (value) { return value & 255; }
};

function packed(r, g, b) { return (r << 16) | (g << 8) | b; }

function syntheticCouponPage(buttonRgb, offsetY) {
  const y = offsetY || 0;
  return {
    width: 1440,
    height: 3200,
    pixel: function (x, py) {
      // A shopping promotion: green artwork and yellow action are side-by-side,
      // so it must never be classified as the free daily coupon.
      if (x >= 100 && x <= 390 && py >= 850 && py <= 1080) return packed(80, 205, 110);
      if (x >= 980 && x <= 1260 && py >= 880 && py <= 1040) return packed(254, 243, 198);
      // The daily coupon can move vertically and its amount text is deliberately
      // not part of recognition.
      if (x >= 160 && x <= 570 && py >= 1510 + y && py <= 1780 + y) return packed(95, 203, 117);
      if (x >= 160 && x <= 570 && py >= 1970 + y && py <= 2130 + y) {
        return packed(buttonRgb[0], buttonRgb[1], buttonRgb[2]);
      }
      return packed(255, 255, 255);
    }
  };
}

function croppedAtBottom(image, croppedHeight) {
  return { width: image.width, height: croppedHeight, pixel: image.pixel };
}

const vision = require("../app/src/assistant/assets/project/core/vision.js");

const available30 = vision.findCouponTarget(syntheticCouponPage([254, 243, 198], 0));
assert.strictEqual(available30.state, "NOT_COMPLETED");
assert.ok(available30.x >= 300 && available30.x <= 440);

const available50Moved = vision.findCouponTarget(syntheticCouponPage([254, 243, 198], 60));
assert.strictEqual(available50Moved.state, "NOT_COMPLETED");
assert.ok(available50Moved.y > available30.y);

const claimed = vision.findCouponTarget(syntheticCouponPage([244, 244, 244], 120));
assert.strictEqual(claimed.state, "COMPLETED_TODAY");

const claimedBottomCropped = vision.findCouponTarget(croppedAtBottom(syntheticCouponPage([244, 244, 244], 120), 3120));
assert.strictEqual(claimedBottomCropped.state, "COMPLETED_TODAY");

const available40MovedUp = vision.findCouponTarget(syntheticCouponPage([254, 243, 198], -120));
assert.strictEqual(available40MovedUp.state, "NOT_COMPLETED");
assert.ok(available40MovedUp.y < available30.y);

function compactCouponPage(state, offsetY) {
  const offset = offsetY || 0;
  return { width: 1440, height: 3200, pixel: function (x, y) {
    if (x === 720 && y === 510) return packed(225, 225, 225);
    if (x === 720 && y === 400) return packed(244, 244, 244);
    if (x >= 105 && x <= 360 && y >= 710 + offset && y <= 890 + offset && state !== "task-only") {
      return packed(95, 203, 117);
    }
    if (y === 950 + offset && x >= 105 && x <= 1335) return packed(217, 217, 217);
    if (x >= 1075 && x <= 1335 && y >= 745 + offset && y <= 855 + offset) {
      if (state === "claimed" && x >= 1140 && x <= 1260 && Math.abs(y - 800 - offset) <= 15) {
        return packed(170, 170, 170);
      }
      if (state === "claimed" || state === "blank-disabled") return packed(244, 244, 244);
      if (state !== "blank") return packed(253, 243, 199);
    }
    // A second generic claim button plus game/shopping green art: these must
    // not supply the action or anchors for the daily coupon above them.
    if (x >= 1075 && x <= 1335 && y >= 1060 && y <= 1170) return packed(253, 243, 199);
    if (x >= 100 && x <= 390 && y >= 1600 && y <= 1810) return packed(95, 203, 117);
    if (x >= 112 && x <= 417 && y >= 2170 && y <= 2380) return packed(95, 203, 117);
    return packed(255, 255, 255);
  } };
}

function resized(image, width, height) {
  const scale = width / image.width;
  return { width: width, height: height, pixel: function (x, y) {
    return image.pixel(Math.round(x / scale), Math.round(y / scale));
  } };
}

const compact = compactCouponPage("available");
const compactTarget = vision.findCouponTarget(compact);
assert.strictEqual(compactTarget.state, "NOT_COMPLETED");
assert.strictEqual(compactTarget.x, 1200);
assert.strictEqual(compactTarget.y, 800);
assert.strictEqual(vision.findCouponTarget(compactCouponPage("available", 60)).y, 860);
assert.strictEqual(vision.findCouponTarget(compactCouponPage("available", -60)).y, 740);
assert.strictEqual(vision.findCouponTarget(compactCouponPage("claimed")).state, "COMPLETED_TODAY");
assert.strictEqual(vision.findCouponTarget(resized(compact, 720, 1600)).state, "NOT_COMPLETED");
assert.strictEqual(vision.findCouponTarget(croppedAtBottom(compact, 3120)).state, "NOT_COMPLETED");
["task-only", "blank", "blank-disabled"].forEach(function (state) {
  assert.strictEqual(vision.findCouponTarget(compactCouponPage(state)).state, "UNKNOWN", state);
});
const withoutHeader = { width: 1440, height: 3200, pixel: function (x, y) {
  return y <= 510 ? packed(255, 255, 255) : compact.pixel(x, y);
} };
assert.strictEqual(vision.findCouponTarget(withoutHeader).state, "UNKNOWN");
assert.strictEqual(vision.findCouponTarget(resized(compact, 3200, 1440)).state, "UNKNOWN");

// Exercise the shipped click helper: exactly one first-row tap, fresh
// precondition, width-based coordinates with cropped captures, and a real
// postcondition. The module's read-only entry must never click.
let activeImage = croppedAtBottom(compact, 3120);
let taps = [];
let recycled = 0;
global.currentPackage = function () { return "com.tencent.mm"; };
global.device = { width: 720, height: 1600 };
global.automator = { captureScreen: function () {
  return { width: activeImage.width, height: activeImage.height, pixel: activeImage.pixel,
    recycle: function () { recycled += 1; } };
} };
global.sleep = function () {};
global.click = function (x, y) {
  taps.push([x, y]);
  activeImage = compactCouponPage("claimed");
  return true;
};
const couponModule = require("../app/src/assistant/assets/project/modules/withdrawal-coupon.js");
const readOnly = { command: { readOnly: true }, response: function (state, message) {
  return { state: state, message: message };
} };
assert.strictEqual(couponModule.probe(readOnly).state, "NOT_COMPLETED");
assert.strictEqual(taps.length, 0);
assert.strictEqual(couponModule.execute(readOnly).message, "read-only-command");
assert.strictEqual(taps.length, 0);
vision.clickCouponClaim();
assert.deepStrictEqual(taps, [[600, 400]]);
assert.ok(recycled >= 3);
assert.throws(function () { vision.clickCouponClaim(); }, /VISUAL_PRECONDITION/);
assert.strictEqual(taps.length, 1);
global.currentPackage = function () { return "com.example.other"; };
assert.throws(function () { vision.clickCouponClaim(); }, /wrong-package/);
assert.strictEqual(taps.length, 1);

// No successful result if the official button fails to change after a tap.
global.currentPackage = function () { return "com.tencent.mm"; };
activeImage = compact;
global.click = function () { return true; };
const originalNow = Date.now;
let fakeNow = 0;
Date.now = function () { return fakeNow; };
global.sleep = function (ms) { fakeNow += ms; };
try {
  assert.throws(function () { vision.clickCouponClaim(); }, /VISUAL_POSTCONDITION/);
} finally {
  Date.now = originalNow;
}

function loadPhoneImage(path) {
  const decoded = /\.jpe?g$/i.test(path)
    ? require("jpeg-js").decode(fs.readFileSync(path))
    : require("pngjs").PNG.sync.read(fs.readFileSync(path));
  return { width: decoded.width, height: decoded.height, pixel: function (x, y) {
    const index = (y * decoded.width + x) * 4;
    return packed(decoded.data[index], decoded.data[index + 1], decoded.data[index + 2]);
  } };
}

["--compact-available", "--compact-claimed"].forEach(function (flag) {
  const index = process.argv.indexOf(flag);
  if (index < 0) return;
  const target = vision.findCouponTarget(loadPhoneImage(process.argv[index + 1]));
  assert.strictEqual(target.state, flag === "--compact-available" ? "NOT_COMPLETED" : "COMPLETED_TODAY", target.detail);
  assert.strictEqual(target.x, 1200);
  assert.ok(target.y >= 740 && target.y <= 860);
});

// Optional real-phone screenshot fixture. The 2026-08-12 frame contains a
// dynamic 40-yuan coupon before the user claims it; keeping this assertion
// makes today's vertical-layout regression reproducible when pngjs is present.
const realFrame = process.argv[2] && !process.argv[2].startsWith("--") ? process.argv[2] : null;
if (realFrame && fs.existsSync(realFrame)) {
  const image = loadPhoneImage(realFrame);
  const actual40 = vision.findCouponTarget(image);
  assert.strictEqual(actual40.state, "NOT_COMPLETED");
  assert.ok(actual40.y >= 1950 && actual40.y <= 2200);
}

const claimedFrame = realFrame ? process.argv[3] : null;
if (claimedFrame && fs.existsSync(claimedFrame)) {
  const image = loadPhoneImage(claimedFrame);
  assert.strictEqual(vision.findCouponTarget(image).state, "COMPLETED_TODAY");
}

process.stdout.write("Dynamic coupon vision: PASS\n");
