package com.agentflow.backend.ai.browser;

/** A short-lived reference to a visible, interactive element in one snapshot. */
public record BrowserElement(String ref, String tag, String role, String text,
		String ariaLabel, String inputType, String href) {
}
