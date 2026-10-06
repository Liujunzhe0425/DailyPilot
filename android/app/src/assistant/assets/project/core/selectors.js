"use strict";

function visibleTexts() {
  const values = [];
  function addValue(value) {
    if (!value) return;
    const full = String(value);
    values.push(full);
    full.split(/\r?\n/).forEach(function (line) {
      const trimmed = line.trim();
      if (trimmed && trimmed !== full) values.push(trimmed);
    });
  }
  // AutoJs6's Rhino bridge expects a string here; passing a native JS RegExp
  // throws IllegalArgumentException on the bundled engine.
  classNameMatches(".*").find().forEach(function (node) {
    const textValue = node.text();
    const description = node.desc();
    addValue(textValue);
    if (description && description !== textValue) addValue(description);
  });
  return values;
}

function waitForAnyText(markers, timeoutMs) {
  const deadline = Date.now() + Math.min(timeoutMs || 15000, 15000);
  let values = [];
  while (Date.now() < deadline) {
    values = visibleTexts();
    if (markers.some(function (marker) { return values.indexOf(marker) >= 0; })) return true;
    sleep(250);
  }
  return false;
}

function waitForAllTexts(markers, timeoutMs) {
  const deadline = Date.now() + Math.min(timeoutMs || 15000, 15000);
  let values = [];
  while (Date.now() < deadline) {
    values = visibleTexts();
    if (markers.every(function (marker) { return values.indexOf(marker) >= 0; })) return true;
    sleep(200);
  }
  return false;
}

function waitForAnyTextContaining(markers, timeoutMs) {
  const deadline = Date.now() + Math.min(timeoutMs || 15000, 15000);
  let values = [];
  while (Date.now() < deadline) {
    values = visibleTexts();
    if (markers.some(function (marker) {
      return values.some(function (value) { return value.indexOf(marker) >= 0; });
    })) return true;
    sleep(250);
  }
  return false;
}

function firstNodeContaining(label) {
  return textContains(label).findOnce() || descContains(label).findOnce();
}

function firstExactNode(label) {
  return text(label).findOnce() || desc(label).findOnce();
}

function waitForAnyNodeContaining(markers, timeoutMs) {
  const deadline = Date.now() + Math.min(timeoutMs || 15000, 15000);
  let node = null;
  while (Date.now() < deadline) {
    markers.some(function (marker) { node = firstNodeContaining(marker); return !!node; });
    if (node) return node;
    sleep(150);
  }
  return null;
}

function waitForExactNode(label, timeoutMs) {
  const deadline = Date.now() + Math.min(timeoutMs || 15000, 15000);
  let node = null;
  while (Date.now() < deadline) {
    node = firstExactNode(label);
    if (node) return node;
    sleep(150);
  }
  return null;
}

function exactNode(label) {
  let result = null;
  function choose(collection) {
    collection.forEach(function (node) {
      if (result) return;
      const bounds = node.bounds();
      if (bounds && bounds.width() > 0 && bounds.height() > 0 &&
        bounds.right > 0 && bounds.bottom > 0 && bounds.left < device.width && bounds.top < device.height) {
        result = node;
      }
    });
  }
  choose(text(label).find());
  if (!result) choose(desc(label).find());
  return result;
}

function nodesContaining(label) {
  const result = [];
  textContains(label).find().forEach(function (node) { result.push(node); });
  descContains(label).find().forEach(function (node) { result.push(node); });
  return result;
}

function subtreeTexts(node) {
  const result = [];
  function visit(value) {
    if (!value) return;
    const textValue = value.text();
    const description = value.desc();
    if (textValue) result.push(String(textValue));
    if (description && description !== textValue) result.push(String(description));
    for (let i = 0; i < value.childCount(); i += 1) visit(value.child(i));
  }
  visit(node);
  return result;
}

function splitValues(values) {
  const result = [];
  (values || []).forEach(function (value) {
    if (!value) return;
    String(value).split(/\r?\n/).forEach(function (line) {
      const trimmed = line.trim();
      if (trimmed) result.push(trimmed);
    });
  });
  return result;
}

function nodeArea(node) {
  if (!node) return Number.MAX_VALUE;
  const bounds = node.bounds();
  return bounds && bounds.width() > 0 && bounds.height() > 0
    ? bounds.width() * bounds.height() : Number.MAX_VALUE;
}

module.exports = {
  visibleTexts: visibleTexts,
  waitForAnyText: waitForAnyText,
  waitForAllTexts: waitForAllTexts,
  waitForAnyTextContaining: waitForAnyTextContaining,
  waitForAnyNodeContaining: waitForAnyNodeContaining,
  waitForExactNode: waitForExactNode,
  firstNodeContaining: firstNodeContaining,
  firstExactNode: firstExactNode,
  exactNode: exactNode,
  nodesContaining: nodesContaining,
  subtreeTexts: subtreeTexts,
  splitValues: splitValues,
  nodeArea: nodeArea
};
