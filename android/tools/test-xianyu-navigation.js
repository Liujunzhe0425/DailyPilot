"use strict";

const assert = require("assert");
const path = require("path");

const selectorsPath = path.resolve(__dirname, "../app/src/assistant/assets/project/core/selectors.js");
const navigationPath = path.resolve(__dirname, "../app/src/assistant/assets/project/core/navigation.js");
const selectors = require(selectorsPath);

let now = 0;
let activePackage = "com.miui.home";
let page = "launcher";
const coordinateClicks = [];
let visibleTextCalls = 0;

function bounds(left, top, right, bottom) {
  return {
    left: left, top: top, right: right, bottom: bottom,
    width: function () { return right - left; },
    height: function () { return bottom - top; },
    centerX: function () { return Math.floor((left + right) / 2); },
    centerY: function () { return Math.floor((top + bottom) / 2); }
  };
}

const launcherIcon = {
  bounds: function () { return bounds(1000, 1800, 1300, 2150); },
  clickable: function () { return true; },
  click: function () { activePackage = "com.taobao.idlefish"; page = "home"; return true; },
  parent: function () { return null; },
  desc: function () { return "闲鱼"; }
};

const publishedEntry = {
  bounds: function () { return bounds(80, 1120, 280, 1280); },
  clickable: function () { return true; },
  click: function () { page = "published"; return true; },
  parent: function () { return null; }
};

global.device = { width: 1440, height: 3200 };
global.home = function () { activePackage = "com.miui.home"; page = "launcher"; };
global.sleep = function (millis) { now += millis; };
global.currentPackage = function () { return activePackage; };
global.click = function (x, y) {
  coordinateClicks.push([x, y]);
  if (activePackage === "com.taobao.idlefish" && y >= 2969) page = "mine";
  else if (page === "mine" && x < 400 && y >= 900 && y <= 1400) page = "published";
  return true;
};
global.swipe = function () { return true; };
global.desc = function (label) {
  return {
    findOnce: function () { return label === "闲鱼" && page === "launcher" ? launcherIcon : null; },
    find: function () { return label === "闲鱼" && page === "launcher" ? [launcherIcon] : []; }
  };
};
global.text = function () { return { findOnce: function () { return null; }, find: function () { return []; } }; };
global.textContains = function (label) {
  return {
    findOnce: function () {
      if (label === "我发布的" && (page === "mine" || page === "published")) return publishedEntry;
      if (["在卖", "草稿", "已下架"].indexOf(label) >= 0 && page === "published") return publishedEntry;
      return null;
    }
  };
};
global.descContains = global.textContains;

const originalNow = Date.now;
Date.now = function () { return now; };

selectors.visibleTexts = function () {
  visibleTextCalls += 1;
  if (page === "mine") return ["我发布的"];
  if (page === "published") return ["我发布的", "在卖", "草稿", "已下架", "今日已擦亮"];
  // Regression condition: Flutter exposes no usable text on the homepage.
  return [];
};
selectors.exactNode = function () { return null; };
selectors.waitForAnyTextContaining = function (markers) {
  const joined = selectors.visibleTexts().join("\n");
  return markers.some(function (marker) { return joined.indexOf(marker) >= 0; });
};

try {
  delete require.cache[navigationPath];
  const navigation = require(navigationPath);
  assert.strictEqual(navigation.openXianyuPublishedItems(), true);
  assert.strictEqual(page, "published");
  assert(now < 15000, "navigation must not wait for the old 15-second homepage timeout");
  assert(coordinateClicks.some(function (point) {
    return point[0] === 1296 && point[1] === 3056;
  }), "fallback must tap inside the real bottom-tab bounds");
  assert(coordinateClicks.some(function (point) {
    return point[0] === 165 && point[1] === 1120;
  }), "merged full-page semantics must use the fixed published-entry cell");
  assert.strictEqual(visibleTextCalls, 0, "successful Flutter navigation must not traverse the full accessibility tree");
  console.log("Xianyu navigation without homepage texts: PASS");
} finally {
  Date.now = originalNow;
}
