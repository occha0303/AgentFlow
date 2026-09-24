package com.agentflow.backend.ai.browser;

/**
 * The small, read-only page snapshot returned to Spring AI. Browser execution
 * traces deliberately retain only a summary of this content.
 */
public record BrowserPage(String url, String title, String content) {
}
