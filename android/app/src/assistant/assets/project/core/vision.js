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

function findWeihudaTarget(image) {
  if (!image || image.width <= 0 || image.height / image.width < 2 || image.height / image.width > 2.3) {
    return { state: "UNKNOWN", detail: "unsupported-weihuda-screen-geometry" };
  }
  // Either Today or Tomorrow may be selected. Neither the weekday nor the
  // course-tab selection is a fixed page identity. Retain the bottom tab and
  // the sign-card surroundings so other blue WeChat pages are rejected.
  const samples = [[390, 990], [800, 990], [179, 2980], [100, 540], [1350, 740],
    [1100, 620], [1310, 690]].map(function (point) { return rgb(image, point[0], point[1]); });
  const blue = { r: 76, g: 138, b: 198 };
  const signature = (near(samples[0], blue, 32) || near(samples[1], blue, 32)) &&
    near(samples[2], blue, 32) && near(samples[3], { r: 249, g: 249, b: 249 }, 6) &&
    near(samples[4], { r: 255, g: 255, b: 255 }, 5);
  if (!signature) return { state: "UNKNOWN", detail: "page-signature:" + describe(samples) };
  if (near(samples[5], blue, 32) && near(samples[6], blue, 32)) {
    return { state: "NOT_COMPLETED", detail: "visual-blue-sign-button", x: 1180, y: 655 };
  }
  // The old tolerance also accepted pure white, incorrectly treating a blank
  // loading button as completed. Require a real disabled background.
  const gray = { r: 237, g: 239, b: 241 };
  if (near(samples[5], gray, 10) && near(samples[6], gray, 10)) {
    return { state: "COMPLETED_TODAY", detail: "visual-gray-signed-button", x: 1180, y: 655 };
  }
  return { state: "UNKNOWN", detail: "sign-color:" + describe(samples) };
}

function probeWeihuda() {
  let image = null;
  try {
    image = automator.captureScreen();
    if (!image || image.width <= 0 || image.height <= 0) return { state: "UNKNOWN", detail: "screenshot-unavailable" };
    return findWeihudaTarget(image);
  } catch (error) {
    return { state: "UNKNOWN", detail: "screenshot-error:" + String(error) };
  } finally {
    if (image) image.recycle();
  }
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

function findCompactCouponTarget(image) {
  // October 2026: the daily coupon is the FIRST row of a two-row white card.
  // Its small green artwork is on the left and its action is on the right.
  // Require the unscrolled balance header and row divider as well as artwork
  // edges. A green shopping/game illustration alone must never enable a tap.
  if (!near(rgb(image, 720, 510), { r: 225, g: 225, b: 225 }, 10) ||
    !near(rgb(image, 720, 400), { r: 244, g: 244, b: 244 }, 8)) return null;
  const rows = [];
  for (let y = 650; y <= 980; y += 30) rows.push(y);
  const greenRows = rows.filter(function (y) {
    return rowMatches(image, [120, 340], y, isCouponGreen, 2);
  });
  if (greenRows.length < 4 || greenRows.length > 7) return null;
  const first = greenRows[0];
  const last = greenRows[greenRows.length - 1];
  if (last - first !== (greenRows.length - 1) * 30) {
    return { state: "UNKNOWN", detail: "compact-coupon-green-not-unique" };
  }
  const centerY = Math.round((first + last) / 2);
  const white = { r: 255, g: 255, b: 255 };
  const edges = [80, 380, 1060, 1345].every(function (x) {
    return near(rgb(image, x, centerY), white, 5);
  });
  // Scan a few nearby rows to tolerate antialiasing and sparse green samples.
  const divider = [140, 150, 160].some(function (offset) {
    return [110, 720, 1320].every(function (x) {
      return near(rgb(image, x, centerY + offset), { r: 217, g: 217, b: 217 }, 15);
    });
  });
  if (!edges || !divider) return null;
  const buttonPoints = [centerY - 35, centerY + 35].map(function (y) {
    return [1100, 1200, 1310].map(function (x) { return rgb(image, x, y); });
  });
  const available = buttonPoints.every(function (points) {
    return kindsMatchAtLeastTwo(describe(points).split("|").map(couponKindFromText).join(""), "A");
  });
  if (available) return {
    state: "NOT_COMPLETED", x: 1200, y: centerY,
    detail: "visual-compact-yellow-free-claim-button"
  };
  // A blank white row/loading frame is not a claimed coupon. Require both
  // the disabled background and several pixels of its gray status label.
  const disabled = buttonPoints.every(function (points) {
    return points.every(function (value) {
      return near(value, { r: 244, g: 244, b: 244 }, 8);
    });
  });
  const labelPoints = [];
  [centerY - 15, centerY, centerY + 15].forEach(function (y) {
    [1140, 1160, 1180, 1200, 1220, 1240, 1260].forEach(function (x) {
      labelPoints.push(rgb(image, x, y));
    });
  });
  const grayLabel = labelPoints.filter(function (value) {
    return near(value, { r: 170, g: 170, b: 170 }, 20);
  }).length >= 4;
  if (disabled && grayLabel) return {
    state: "COMPLETED_TODAY", x: 1200, y: centerY,
    detail: "visual-compact-gray-claimed-button"
  };
  return { state: "UNKNOWN", detail: "compact-coupon-button-not-matched:" +
    buttonPoints.map(describe).join(";") };
}

function findCouponTarget(image) {
  if (image.width <= 0 || image.height / image.width < 2 || image.height / image.width > 2.3) {
    return { state: "UNKNOWN", detail: "unsupported-coupon-screen-geometry" };
  }
  const compact = findCompactCouponTarget(image);
  if (compact) return compact;
  return findLegacyCouponTarget(image);
}

function findLegacyCouponTarget(image) {
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
  // The bundled Rhino interpreter retains a loop-scoped const's first value.
  // Assign a binding declared outside the loop so later screenshots replace
  // the initial NOT_COMPLETED/loading result instead of waiting to timeout.
  let result = null;
  while (Date.now() < deadline) {
    result = probe();
    if (result.state === expected) return result;
    sleep(250);
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
  if (String(currentPackage() || "") !== "com.tencent.mm") throw new Error("VISUAL_PRECONDITION:wrong-package");
  const before = probeWeihuda();
  if (before.state !== "NOT_COMPLETED") throw new Error("VISUAL_PRECONDITION:" + before.detail);
  if (!click(Math.round(before.x * device.width / BASE_WIDTH),
    Math.round(before.y * device.width / BASE_WIDTH))) throw new Error("CLICK_FAILED");
  if (!waitFor(probeWeihuda, "COMPLETED_TODAY", 8000)) throw new Error("VISUAL_POSTCONDITION:sign-not-completed");
  return true;
}

function clickCouponClaim() {
  if (String(currentPackage() || "") !== "com.tencent.mm") throw new Error("VISUAL_PRECONDITION:wrong-package");
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
  // Screenshot coordinates are top-aligned and scaled by width, including
  // when AutoJs crops the bottom system bar.
  const clickY = Math.round(target.y * device.width / BASE_WIDTH);
  if (!click(clickX, clickY)) throw new Error("CLICK_FAILED");
  if (!waitFor(probeCoupon, "COMPLETED_TODAY", 8000)) throw new Error("VISUAL_POSTCONDITION:coupon-not-claimed");
  return true;
}

module.exports = {
  probeWeihuda: probeWeihuda,
  probeCoupon: probeCoupon,
  waitForKnown: waitForKnown,
  findCouponTarget: findCouponTarget,
  findWeihudaTarget: findWeihudaTarget,
  clickWeihudaSign: clickWeihudaSign,
  clickCouponClaim: clickCouponClaim
};
