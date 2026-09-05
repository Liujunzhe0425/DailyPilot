"use strict";

const selectors = require("./selectors.js");
const safety = require("./safety.js");

function reopenPackage(packageName, readyMarkers) {
  app.launchPackage(packageName);
  return selectors.waitForAnyText(readyMarkers || [], 15000);
}

function clickExact(label, beforeMarkers, afterMarkers) {
  return safety.safeClick(selectors.exactNode(label), {
    visibleTexts: selectors.visibleTexts,
    beforeMarkers: beforeMarkers,
    afterMarkers: afterMarkers,
    waitForAnyText: selectors.waitForAnyText,
    timeoutMs: 5000
  });
}

function clickNavigationNode(node, coordinateFallback, verticalOffset) {
  if (!node) return false;
  let clickable = node;
  while (clickable && !clickable.clickable()) clickable = clickable.parent();
  if (clickable && clickable.click()) return true;
  if (coordinateFallback === true) {
    const bounds = node.bounds();
    if (bounds && bounds.width() > 0 && bounds.height() > 0) {
      const y = Math.max(0, Math.min(device.height - 1, bounds.centerY() + (verticalOffset || 0)));
      return !!click(bounds.centerX(), y);
    }
  }
  return false;
}

function exactNodeForAny(labels) {
  for (let i = 0; i < labels.length; i += 1) {
    const node = selectors.exactNode(labels[i]);
    if (node) return node;
  }
  return null;
}

function desktopNodeForAny(labels) {
  let best = null;
  let bestScore = -1;
  function consider(node, label, descriptionMatch) {
    if (!node) return;
    const bounds = node.bounds();
    if (!bounds || bounds.width() <= 0 || bounds.height() <= 0 ||
      bounds.right <= 0 || bounds.bottom <= 0 || bounds.left >= device.width || bounds.top >= device.height) return;
    let score = descriptionMatch ? 80 : 40;
    if (node.clickable()) score += 100;
    if (bounds.width() >= 120 && bounds.height() >= 120 &&
      bounds.width() <= Math.floor(device.width * 0.45) && bounds.height() <= Math.floor(device.height * 0.35)) score += 30;
    if (String(node.desc() || "") === label) score += 20;
    if (score > bestScore) {
      best = node;
      bestScore = score;
    }
  }
  labels.forEach(function (label) {
    consider(desc(label).findOnce(), label, true);
    consider(text(label).findOnce(), label, false);
  });
  return best;
}

function openDesktopShortcut(shortcutLabels, targetPackage, readyMarkers, readyProbe, acceptTargetPackageAfterMs) {
  home();
  sleep(500);
  function tryOpen() {
    const target = desktopNodeForAny(shortcutLabels);
    if (target) {
      if (!clickNavigationNode(target)) return false;
      const deadline = Date.now() + 15000;
      let wrongPackageSince = 0;
      let targetPackageSince = 0;
      while (Date.now() < deadline) {
        const activePackage = String(currentPackage() || "");
        if (activePackage === targetPackage) {
          if (targetPackageSince === 0) targetPackageSince = Date.now();
          if (acceptTargetPackageAfterMs > 0 &&
            Date.now() - targetPackageSince >= acceptTargetPackageAfterMs) return true;
        } else {
          targetPackageSince = 0;
        }
        if (activePackage && activePackage !== targetPackage &&
          activePackage !== "com.miui.home" && activePackage !== "com.android.systemui") {
          if (wrongPackageSince === 0) wrongPackageSince = Date.now();
          if (Date.now() - wrongPackageSince >= 800) {
            home();
            sleep(500);
            return false;
          }
        } else {
          wrongPackageSince = 0;
        }
        if (activePackage === targetPackage) {
          // The mini-program probes are screen-based and do not need a full
          // accessibility-tree traversal. On Flutter pages that traversal can
          // itself take several seconds, so try the focused probe first.
          if (typeof readyProbe === "function" && readyProbe([])) return true;
          if (readyMarkers.length > 0) {
            const values = selectors.visibleTexts();
            if (readyMarkers.some(function (marker) {
              return values.some(function (value) { return value.indexOf(marker) >= 0; });
            })) return true;
          }
        }
        sleep(250);
      }
      return false;
    }
    return null;
  }
  let opened = tryOpen();
  if (opened !== null) return opened;
  // Xiaomi launcher page numbers increase when the finger swipes left. The
  // two WeChat mini-program shortcuts are on page 4, so search forward first.
  for (let page = 0; page < 6; page += 1) {
    swipe(Math.floor(device.width * 0.82), Math.floor(device.height * 0.52),
      Math.floor(device.width * 0.18), Math.floor(device.height * 0.52), 300);
    sleep(300);
    opened = tryOpen();
    if (opened !== null) return opened;
  }
  for (let page = 0; page < 12; page += 1) {
    swipe(Math.floor(device.width * 0.18), Math.floor(device.height * 0.52),
      Math.floor(device.width * 0.82), Math.floor(device.height * 0.52), 300);
    sleep(300);
    opened = tryOpen();
    if (opened !== null) return opened;
  }
  return false;
}

function openWechatDesktopShortcut(shortcutLabels, readyMarkers, readyProbe) {
  return openDesktopShortcut(shortcutLabels, "com.tencent.mm", readyMarkers, readyProbe);
}

function isXianyuPublishedPage(values) {
  const publishedNode = selectors.firstNodeContaining("我发布的") || selectors.firstNodeContaining("今日数据");
  const tabNode = selectors.firstNodeContaining("在卖") || selectors.firstNodeContaining("草稿") ||
    selectors.firstNodeContaining("已下架");
  if (publishedNode && tabNode) return true;
  const pageText = (values || []).join("\n");
  return pageText.indexOf("我发布的") >= 0 &&
    (pageText.indexOf("在卖") >= 0 || pageText.indexOf("草稿") >= 0 || pageText.indexOf("已下架") >= 0);
}

function openXianyuPublishedItems() {
  if (isXianyuPublishedPage()) return true;
  // On this Xiaomi build, launching the package can leave the existing task
  // in the background. The launcher icon path is slower but proven reliable.
  if (!openDesktopShortcut(["闲鱼"], "com.taobao.idlefish", [], null, 1800)) return false;
  if (isXianyuPublishedPage()) return true;
  if (String(currentPackage() || "") !== "com.taobao.idlefish") return false;
  const mine = selectors.exactNode("我的") || selectors.exactNode("我");
  // The label itself sits immediately above Android's gesture area. Tap the
  // icon in the same precisely identified bottom-tab cell instead.
  // Flutter may merge the complete bottom bar into one semantics node. Use
  // the fixed safe center of the identified right-most tab when no exact
  // child node exists.
  if (mine) {
    if (!clickNavigationNode(mine, true, -80)) return false;
  } else if (!click(Math.floor(device.width * 0.9), Math.floor(device.height * 0.955))) return false;
  if (isXianyuPublishedPage()) return true;
  // Prefer the small exact entry. Flutter sometimes exposes only a full-page
  // semantics node containing the words "我发布的"; clicking that node's
  // center opens unrelated content. If no exact node arrives, use the fixed
  // center of the first "我的交易" cell verified on the target phone.
  const published = selectors.waitForExactNode("我发布的", 3500);
  if (published) {
    if (!clickNavigationNode(published, true, -80)) return false;
  } else if (!click(Math.floor(device.width * 0.115), Math.floor(device.height * 0.35))) return false;
  const deadline = Date.now() + 8000;
  while (Date.now() < deadline) {
    if (isXianyuPublishedPage()) return true;
    sleep(150);
  }
  return false;
}

function captureFailureEvidence(path) {
  if (!path) return null;
  try {
    requestScreenCapture(false);
    const image = captureScreen();
    images.save(image, path, "png", 90);
    image.recycle();
    return path;
  } catch (ignored) {
    return null;
  }
}

module.exports = {
  reopenPackage: reopenPackage,
  openDesktopShortcut: openDesktopShortcut,
  openWechatDesktopShortcut: openWechatDesktopShortcut,
  openXianyuPublishedItems: openXianyuPublishedItems,
  isXianyuPublishedPage: isXianyuPublishedPage,
  clickExact: clickExact,
  captureFailureEvidence: captureFailureEvidence
};
