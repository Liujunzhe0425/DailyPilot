"use strict";

const RESULT_PREFIX = "ASSISTANT_RESULT:";

function response(command, state, message, retryable, evidencePath) {
  return {
    moduleId: command.moduleId,
    phase: command.phase,
    state: state,
    message: message || "",
    retryable: retryable === true,
    evidencePath: evidencePath || ""
  };
}

function normalize(command, value) {
  if (!value || typeof value.state !== "string") {
    return response(command, "FAILED", "invalid-module-response", false);
  }
  value.moduleId = command.moduleId;
  value.phase = command.phase;
  value.retryable = value.retryable === true;
  return value;
}

function context(command) {
  return {
    command: command,
    response: function (state, message, retryable, evidencePath) {
      return response(command, state, message, retryable, evidencePath);
    }
  };
}

module.exports = { RESULT_PREFIX: RESULT_PREFIX, response: response, normalize: normalize, context: context };
