"use strict";

const assert = require("assert");
const fs = require("fs");
const vision = require("../app/src/assistant/assets/project/core/vision.js");
const selectors = require("../app/src/assistant/assets/project/core/selectors.js");
const navigation = require("../app/src/assistant/assets/project/core/navigation.js");
const sign = require("../app/src/assistant/assets/project/modules/weihuda-sign.js");

global.colors = {
  red: function (n) { return (n >>> 16) & 255; },
  green: function (n) { return (n >>> 8) & 255; },
  blue: function (n) { return n & 255; }
};
function packed(rgb) { return (rgb[0] << 16) | (rgb[1] << 8) | rgb[2]; }
const blue = packed([76, 138, 198]);
const white = packed([255, 255, 255]);
const gray = packed([237, 239, 241]);

function fixture(tab, state) {
  return { width: 1440, height: 3200, pixel: function (x, y) {
    if (y === 990 && ((x === 390 && tab === "today") || (x === 800 && tab === "tomorrow"))) return blue;
    if (x >= 178 && x <= 180 && y === 2980) return blue;
    if (x === 100 && y === 540) return packed([249, 249, 249]);
    if ((x === 1100 && y === 620) || (x === 1310 && y === 690)) {
      return state === "available" ? blue : state === "claimed" ? gray : white;
    }
    return white;
  } };
}

["today", "tomorrow"].forEach(function (tab) {
  assert.strictEqual(vision.findWeihudaTarget(fixture(tab, "available")).state, "NOT_COMPLETED");
  assert.strictEqual(vision.findWeihudaTarget(fixture(tab, "claimed")).state, "COMPLETED_TODAY");
  assert.strictEqual(vision.findWeihudaTarget(fixture(tab, "blank")).state, "UNKNOWN");
});
assert.strictEqual(vision.findWeihudaTarget(fixture("none", "available")).state, "UNKNOWN");
const available = fixture("tomorrow", "available");
const cropped = { width: 1440, height: 3120, pixel: available.pixel };
assert.strictEqual(vision.findWeihudaTarget(cropped).state, "NOT_COMPLETED");
const half = { width: 720, height: 1600, pixel: function (x, y) { return available.pixel(x * 2, y * 2); } };
assert.strictEqual(vision.findWeihudaTarget(half).state, "NOT_COMPLETED");

let image = available;
let now = 0;
let tappedAt = null;
let captures = 0;
let recycled = 0;
let ticks = 0;
const taps = [];
global.device = { width: 720, height: 1600 };
global.currentPackage = function () { return "com.tencent.mm"; };
global.automator = { captureScreen: function () {
  captures += 1;
  return { width: image.width, height: image.height, pixel: image.pixel,
    recycle: function () { recycled += 1; } };
} };
global.sleep = function (ms) {
  now += ms;
  ticks += 1;
  if (tappedAt !== null && now - tappedAt >= 1000) image = fixture("tomorrow", "claimed");
};
global.click = function (x, y) { taps.push([x, y]); tappedAt = now; return true; };
const originalNow = Date.now;
Date.now = function () { return now; };
const ctx = { command: { readOnly: true }, response: function (state, message) { return { state: state, message: message }; } };
try {
  assert.strictEqual(sign.probe(ctx).state, "NOT_COMPLETED");
  assert.strictEqual(sign.execute(ctx).message, "read-only-command");
  assert.strictEqual(taps.length, 0);
  vision.clickWeihudaSign();
  assert.deepStrictEqual(taps, [[590, 328]]);
  assert(now >= 1000, "must wait until the official button changes after the tap");
  assert(ticks >= 4);
  assert.strictEqual(recycled, captures);
  assert.throws(function () { vision.clickWeihudaSign(); }, /VISUAL_PRECONDITION/);
  assert.strictEqual(taps.length, 1);
  image = available;
  global.currentPackage = function () { return "com.example.other"; };
  assert.throws(function () { vision.clickWeihudaSign(); }, /wrong-package/);
  assert.strictEqual(taps.length, 1);

  // Readiness arrives late. Reuse only that fresh, same-call probe result;
  // don't traverse the complete tree or capture again after navigation.
  selectors.visibleTexts = function () { return []; };
  let readyProbeCalls = 0;
  const probe = vision.probeWeihuda;
  const open = navigation.openWechatDesktopShortcut;
  vision.probeWeihuda = function () {
    readyProbeCalls += 1;
    return now >= 6000 ? { state: "NOT_COMPLETED", detail: "visual-blue-sign-button" }
      : { state: "UNKNOWN", detail: "loading" };
  };
  navigation.openWechatDesktopShortcut = function (labels, markers, ready) {
    while (now < 10000) {
      if (ready()) return true;
      global.sleep(250);
    }
    return false;
  };
  now = 0;
  const response = sign.probe(ctx);
  assert.strictEqual(response.state, "NOT_COMPLETED");
  assert.strictEqual(now, 6000);
  assert.strictEqual(readyProbeCalls, 25, "the navigation's successful result must not be captured again");
  vision.probeWeihuda = probe;
  navigation.openWechatDesktopShortcut = open;
} finally {
  Date.now = originalNow;
}

["--available", "--claimed"].forEach(function (flag) {
  const index = process.argv.indexOf(flag);
  if (index < 0) return;
  const decoded = require("pngjs").PNG.sync.read(fs.readFileSync(process.argv[index + 1]));
  const target = vision.findWeihudaTarget({ width: decoded.width, height: decoded.height, pixel: function (x, y) {
    const offset = (y * decoded.width + x) * 4;
    return packed([decoded.data[offset], decoded.data[offset + 1], decoded.data[offset + 2]]);
  } });
  assert.strictEqual(target.state, flag === "--available" ? "NOT_COMPLETED" : "COMPLETED_TODAY", target.detail);
});
console.log("Weihuda Today/Tomorrow, loading and safe click: PASS");
