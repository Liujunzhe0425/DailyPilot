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

// Optional real-phone screenshot fixture. The 2026-08-12 frame contains a
// dynamic 40-yuan coupon before the user claims it; keeping this assertion
// makes today's vertical-layout regression reproducible when pngjs is present.
const realFrame = process.argv[2];
if (realFrame && fs.existsSync(realFrame)) {
  const PNG = require("pngjs").PNG;
  const png = PNG.sync.read(fs.readFileSync(realFrame));
  const image = {
    width: png.width,
    height: png.height,
    pixel: function (x, y) {
      const index = (y * png.width + x) * 4;
      return packed(png.data[index], png.data[index + 1], png.data[index + 2]);
    }
  };
  const actual40 = vision.findCouponTarget(image);
  assert.strictEqual(actual40.state, "NOT_COMPLETED");
  assert.ok(actual40.y >= 1950 && actual40.y <= 2200);
}

const claimedFrame = process.argv[3];
if (claimedFrame && fs.existsSync(claimedFrame)) {
  const PNG = require("pngjs").PNG;
  const png = PNG.sync.read(fs.readFileSync(claimedFrame));
  const image = {
    width: png.width,
    height: png.height,
    pixel: function (x, y) {
      const index = (y * png.width + x) * 4;
      return packed(png.data[index], png.data[index + 1], png.data[index + 2]);
    }
  };
  assert.strictEqual(vision.findCouponTarget(image).state, "COMPLETED_TODAY");
}

process.stdout.write("Dynamic coupon vision: PASS\n");
