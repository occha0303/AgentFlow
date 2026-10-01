package com.agentflow.backend.ai.browser;

import java.util.List;

public record BrowserPageSnapshot(String url, String title, String contentSummary,
		List<BrowserElement> elements) {
}
