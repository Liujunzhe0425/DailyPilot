"use strict";

const protocol = require("./core/protocol.js");
const command = JSON.parse(String(engines.myEngine().execArgv.commandJson));
const modules = {
  "weihuda-sign": "./modules/weihuda-sign.js",
  "withdrawal-coupon": "./modules/withdrawal-coupon.js",
  "xianyu-polish": "./modules/xianyu-polish.js"
};

function emit(response) {
  return protocol.RESULT_PREFIX + JSON.stringify(response);
}

let finalResponse;
if (!Object.prototype.hasOwnProperty.call(modules, command.moduleId)) {
  finalResponse = protocol.response(command, "UNKNOWN_MODULE", "unknown-module", false);
} else if (Date.now() > command.deadlineEpochMs) {
  finalResponse = protocol.response(command, "DEADLINE_EXCEEDED", "deadline-exceeded", true);
} else {
  try {
    const implementation = require(modules[command.moduleId]);
    const fn = implementation[String(command.phase).toLowerCase()];
    if (typeof fn !== "function") {
      finalResponse = protocol.response(command, "UNKNOWN_PHASE", "phase-not-exported", false);
    } else {
      finalResponse = protocol.normalize(command, fn(protocol.context(command)));
    }
  } catch (error) {
    finalResponse = protocol.response(command, "FAILED", String(error), false);
  }
}

const serializedResponse = emit(finalResponse);
if (command.responsePath) files.write(command.responsePath, serializedResponse);
serializedResponse;
