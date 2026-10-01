package com.agentflow.backend.ai.browser;

/** Persisted approval intent; never contains a DOM, selector, or browser session. */
public record BrowserActionPayload(String url, String elementRef, BrowserActionType browserAction,
		String value, BrowserElement target) {
}
