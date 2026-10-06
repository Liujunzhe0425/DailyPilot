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
  let found = null;
  labels.some(function (label) { found = selectors.exactNode(label); return !!found; });
  return found;
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
      let activePackage = "";
      while (Date.now() < deadline) {
        activePackage = String(currentPackage() || "");
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
            if (readyMarkers.some(function (marker) {
              return !!selectors.firstNodeContaining(marker);
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
  if (String(currentPackage() || "") !== "com.taobao.idlefish") return false;
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
  if (!openDesktopShortcut(["闲鱼"], "com.taobao.idlefish", [], function () {
    if (isXianyuPublishedPage()) return true;
    const mine = selectors.firstExactNode("我的") || selectors.firstExactNode("我");
    if (!mine) return false;
    const bounds = mine.bounds();
    return bounds && bounds.top >= device.height * 0.90 && bounds.width() < device.width * 0.4 &&
      bounds.height() < device.height * 0.1;
  }, 1800)) return false;
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
  // A merged Flutter semantics node can prove the mine page is loaded without
  // ever exposing an exact entry. Don't wait the full exact-node timeout when
  // both mine-page markers have already arrived and remain stable.
  const entryDeadline = Date.now() + 8000;
  let published = null;
  let mergedReadySince = 0;
  let entryReady = false;
  let entryBounds = null;
  let mergedReady = false;
  while (Date.now() < entryDeadline) {
    if (String(currentPackage() || "") !== "com.taobao.idlefish") return false;
    if (isXianyuPublishedPage()) return true;
    published = selectors.firstExactNode("我发布的");
    if (published) {
      entryBounds = published.bounds();
      if (entryBounds && entryBounds.width() > 0 && entryBounds.height() > 0 &&
        entryBounds.bottom > 0 && entryBounds.top < device.height &&
        entryBounds.right > 0 && entryBounds.left < device.width &&
        entryBounds.width() < device.width * 0.45 && entryBounds.height() < device.height * 0.2) {
        entryReady = true;
        break;
      }
      published = null;
    }
    mergedReady = selectors.firstNodeContaining("我的交易") && selectors.firstNodeContaining("我发布的");
    if (mergedReady) {
      if (mergedReadySince === 0) mergedReadySince = Date.now();
      if (Date.now() - mergedReadySince >= 300) { entryReady = true; break; }
    } else {
      mergedReadySince = 0;
    }
    sleep(150);
  }
  if (!entryReady) return false;
  if (published) {
    if (!clickNavigationNode(published, true)) return false;
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
