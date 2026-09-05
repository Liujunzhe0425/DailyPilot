"use strict";

const BASE_WIDTH = 1440;
const BASE_HEIGHT = 3200;

function rgb(image, x, y) {
  const scale = image.width / BASE_WIDTH;
  const sx = Math.max(0, Math.min(image.width - 1, Math.round(x * scale)));
  // AutoJs may crop the bottom navigation bar and return 1440x3120 instead
  // of 1440x3200. The remaining page is top-aligned, not vertically scaled;
  // using image.height / BASE_HEIGHT shifts every business control upward.
  const sy = Math.max(0, Math.min(image.height - 1, Math.round(y * scale)));
  const value = image.pixel(sx, sy);
  return { r: colors.red(value), g: colors.green(value), b: colors.blue(value) };
}

function near(value, target, tolerance) {
  return Math.abs(value.r - target.r) <= tolerance &&
    Math.abs(value.g - target.g) <= tolerance && Math.abs(value.b - target.b) <= tolerance;
}

function describe(points) {
  return points.map(function (value) { return value.r + "," + value.g + "," + value.b; }).join("|");
}

function captureSamples(points) {
  let image = null;
  try {
    image = automator.captureScreen();
    if (!image || image.width <= 0 || image.height <= 0) return null;
    return points.map(function (point) { return rgb(image, point[0], point[1]); });
  } catch (ignored) {
    return null;
  } finally {
    if (image) image.recycle();
  }
}

function probeWeihuda() {
  // Use only page chrome whose position does not move with the selected weekday.
  // The former (720, 435) sample happened to hit Wednesday and failed on other days.
  const samples = captureSamples([[390, 990], [179, 2980], [1180, 655]]);
  if (!samples) return { state: "UNKNOWN", detail: "screenshot-unavailable" };
  const blue = { r: 76, g: 138, b: 198 };
  const signature = near(samples[0], blue, 32) && near(samples[1], blue, 32);
  if (!signature) return { state: "UNKNOWN", detail: "page-signature:" + describe(samples) };
  if (near(samples[2], blue, 32)) return { state: "NOT_COMPLETED", detail: "visual-blue-sign-button" };
  if (near(samples[2], { r: 237, g: 239, b: 241 }, 24)) {
    return { state: "COMPLETED_TODAY", detail: "visual-gray-signed-button" };
  }
  return { state: "UNKNOWN", detail: "sign-color:" + describe(samples) };
}

function isCouponGreen(value) {
  return value.g >= 160 && value.g >= value.r + 45 && value.g >= value.b + 35;
}

function isCouponAvailable(value) {
  return value.r >= 235 && value.g >= 220 && value.b <= 225 && value.r >= value.b + 20;
}

function isCouponClaimed(value) {
  // Reuse the subtraction-based comparison already proven by the Weihuda
  // probe. Rhino's Number(boxedColorChannel) conversion returns an unusable
  // value on this engine even though string diagnostics print the channel.
  return near(value, { r: 244, g: 244, b: 244 }, 8) ||
    near(value, { r: 170, g: 170, b: 170 }, 8);
}

function rowMatches(image, xs, y, predicate, minimumMatches) {
  return xs.filter(function (x) { return predicate(rgb(image, x, y)); }).length >= minimumMatches;
}

function couponKindFromText(value) {
  // `describe` constructs a native JS string from primitive color channels.
  // Decide the button state only from this stable representation; no boxed
  // Java Boolean/Number/String can reach the branch condition.
  if (/^(244,244,244|170,170,170)$/.test(value)) return "C";
  const parts = value.split(",").map(function (part) { return parseInt(part, 10); });
  if (parts[0] >= 235 && parts[1] >= 220 && parts[2] <= 225 && parts[0] >= parts[2] + 20) return "A";
  return "X";
}

function kindsMatchAtLeastTwo(kinds, expected) {
  return new RegExp("^(?:" + expected + expected + ".|" + expected + "." + expected + "|." + expected + expected + ")$").test(kinds);
}

function findCouponTarget(image) {
  // The first release is accepted only on Xiaomi 14 Pro / Android 16. The
  // free daily coupon card keeps the same geometry when its amount changes
  // (verified with real 30 and 50 coupon pages). Sample only this wide card,
  // never the shopping/task cards above or the paid activities below. Keeping
  // the JNI/Rhino crossings below 30 is essential: a full-height pixel scan
  // takes tens of seconds on this device.
  const greenXs = [180, 370, 540];
  // The activity card above the daily coupon changes from day to day, moving
  // the coupon vertically. Scan only the narrow band where the free card is
  // allowed to appear; the three-wide-green-anchor rule rejects the shopping
  // banner and the paid activities below it.
  const greenRows = [];
  for (let y = 1370; y <= 1850; y += 60) greenRows.push(y);
  const matchingRows = greenRows.filter(function (y) {
    return rowMatches(image, greenXs, y, isCouponGreen, 3);
  });
  if (matchingRows.length < 2) {
    return { state: "UNKNOWN", detail: "daily-coupon-green-anchors:" + matchingRows.length };
  }
  const greenY = matchingRows[matchingRows.length - 1];
  const buttonXs = [190, 300, 410];
  // AutoJs screen capture can exclude system bars (3120 px instead of 3200),
  // shifting the physical button farther down in reference coordinates.
  const buttonRows = [];
  for (let offset = 150; offset <= 420; offset += 30) buttonRows.push(greenY + offset);
  // Rhino repeatedly returns element zero when this array is indexed from a
  // classic for-loop. Array.map is verified on-device to visit every row.
  const kindRows = buttonRows.map(function (y) {
    const points = buttonXs.map(function (x) { return rgb(image, x, y); });
    const kinds = describe(points).split("|").map(couponKindFromText).join("");
    return y + "=" + kinds;
  }).join(";");
  const availableMatch = /(?:^|;)(\d+)=(?:AA.|A.A|.AA)(?:;|$)/.exec(kindRows);
  const claimedMatch = /(?:^|;)(\d+)=(?:CC.|C.C|.CC)(?:;|$)/.exec(kindRows);
  let target = availableMatch
    ? { state: "NOT_COMPLETED", y: parseInt(availableMatch[1], 10) }
    : claimedMatch ? { state: "COMPLETED_TODAY", y: parseInt(claimedMatch[1], 10) } : null;
  if (!target) {
    const diagnostics = buttonRows.map(function (y) {
      const points = buttonXs.map(function (x) { return rgb(image, x, y); });
      const availableMatches = points.filter(isCouponAvailable).length;
      const claimedMatches = points.filter(isCouponClaimed).length;
      return y + "=" + describe(points) + "/a" + availableMatches + "c" + claimedMatches;
    }).join(";");
    return {
      state: "UNKNOWN",
      detail: "daily-coupon-button-not-matched;image:" + image.width + "x" + image.height +
        ";green-bottom:" + greenY + ";kinds:" + kindRows + ";buttons:" + diagnostics
    };
  }
  target.x = 370;
  target.detail = target.state === "NOT_COMPLETED"
    ? "visual-dynamic-yellow-free-claim-button;green-bottom:" + greenY
    : "visual-dynamic-gray-claimed-button;green-bottom:" + greenY;
  return target;
}

function probeCoupon() {
  let image = null;
  try {
    image = automator.captureScreen();
    if (!image || image.width <= 0 || image.height <= 0) {
      return { state: "UNKNOWN", detail: "screenshot-unavailable" };
    }
    const target = findCouponTarget(image);
    return { state: target.state, detail: target.detail };
  } catch (error) {
    return { state: "UNKNOWN", detail: "screenshot-error:" + String(error) };
  } finally {
    if (image) image.recycle();
  }
}

function waitFor(probe, expected, timeoutMs) {
  const deadline = Date.now() + Math.min(timeoutMs || 8000, 15000);
  while (Date.now() < deadline) {
    sleep(250);
    const result = probe();
    if (result.state === expected) return result;
  }
  return null;
}

function waitForKnown(probe, timeoutMs) {
  const deadline = Date.now() + Math.min(timeoutMs || 8000, 15000);
  let last = null;
  while (Date.now() < deadline) {
    last = probe();
    if (last && last.state !== "UNKNOWN") return last;
    sleep(250);
  }
  return last || { state: "UNKNOWN", detail: "probe-timeout" };
}

function clickWeihudaSign() {
  const before = probeWeihuda();
  if (before.state !== "NOT_COMPLETED") throw new Error("VISUAL_PRECONDITION:" + before.detail);
  if (!click(1180, 655)) throw new Error("CLICK_FAILED");
  if (!waitFor(probeWeihuda, "COMPLETED_TODAY", 8000)) throw new Error("VISUAL_POSTCONDITION:sign-not-completed");
  return true;
}

function clickCouponClaim() {
  let image = null;
  let target;
  try {
    image = automator.captureScreen();
    if (!image || image.width <= 0 || image.height <= 0) throw new Error("screenshot-unavailable");
    target = findCouponTarget(image);
  } finally {
    if (image) image.recycle();
  }
  if (!target || target.state !== "NOT_COMPLETED") {
    throw new Error("VISUAL_PRECONDITION:" + (target ? target.detail : "target-missing"));
  }
  const clickX = Math.round(target.x * device.width / BASE_WIDTH);
  const clickY = Math.round(target.y * device.height / BASE_HEIGHT);
  if (!click(clickX, clickY)) throw new Error("CLICK_FAILED");
  if (!waitFor(probeCoupon, "COMPLETED_TODAY", 8000)) throw new Error("VISUAL_POSTCONDITION:coupon-not-claimed");
  return true;
}

module.exports = {
  probeWeihuda: probeWeihuda,
  probeCoupon: probeCoupon,
  waitForKnown: waitForKnown,
  findCouponTarget: findCouponTarget,
  clickWeihudaSign: clickWeihudaSign,
  clickCouponClaim: clickCouponClaim
};
