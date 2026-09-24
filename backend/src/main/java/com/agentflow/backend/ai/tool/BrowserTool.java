package com.agentflow.backend.ai.tool;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import com.agentflow.backend.ai.browser.BrowserPage;
import com.agentflow.backend.ai.browser.BrowserService;
import com.agentflow.backend.ai.trace.AgentToolTraceRecorder;
import com.agentflow.backend.step.model.AgentStep;

public class BrowserTool {

	private final BrowserService browserService;
	private final ToolUsageRecorder toolUsageRecorder;
	private final AgentToolTraceRecorder traceRecorder;

	public BrowserTool(BrowserService browserService, ToolUsageRecorder toolUsageRecorder,
			AgentToolTraceRecorder traceRecorder) {
		this.browserService = browserService;
		this.toolUsageRecorder = toolUsageRecorder;
		this.traceRecorder = traceRecorder;
	}

	@Tool(description = "Open and read one specific public HTTP or HTTPS web page in a read-only browser. "
			+ "Use it only when a task explicitly gives a URL or needs rendered page state. "
			+ "It cannot log in, click, type, submit forms, download files, or modify data. "
			+ "Never use it for localhost, private addresses, files, or non-web protocols.")
	public BrowserPage openWebPage(@ToolParam(description = "A specific public http or https URL to open") String url) {
		AgentStep browserStep = traceRecorder.startBrowserStep(url);
		try {
			BrowserPage page = browserService.open(url);
			String title = browserService.getTitle(page);
			String content = browserService.getText(page);
			toolUsageRecorder.record("BrowserTool");
			toolUsageRecorder.recordDetail("Visited URL: " + abbreviate(page.url(), 180));
			toolUsageRecorder.recordDetail("Page title: " + abbreviate(title, 120));
			traceRecorder.completeBrowserStep(browserStep,
					"Page title: " + displayTitle(title) + "\nContent summary: " + abbreviate(content, 300));
			return page;
		} catch (RuntimeException exception) {
			traceRecorder.failBrowserStep(browserStep, failureMessage(exception));
			throw exception;
		}
	}

	private String displayTitle(String title) {
		return title == null || title.isBlank() ? "(untitled)" : abbreviate(title, 150);
	}

	private String failureMessage(RuntimeException exception) {
		return exception.getMessage() == null || exception.getMessage().isBlank()
				? "BrowserTool failed"
				: exception.getMessage();
	}

	private String abbreviate(String value, int limit) {
		if (value == null || value.isBlank()) {
			return "(empty)";
		}
		return value.length() <= limit ? value : value.substring(0, limit - 3) + "...";
	}
}
