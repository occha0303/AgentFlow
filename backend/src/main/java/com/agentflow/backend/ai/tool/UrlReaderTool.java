package com.agentflow.backend.ai.tool;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import com.agentflow.backend.ai.web.UrlReaderService;
import com.agentflow.backend.ai.web.WebPageContent;

public class UrlReaderTool {

	private final UrlReaderService urlReaderService;
	private final ToolUsageRecorder toolUsageRecorder;

	public UrlReaderTool(UrlReaderService urlReaderService, ToolUsageRecorder toolUsageRecorder) {
		this.urlReaderService = urlReaderService;
		this.toolUsageRecorder = toolUsageRecorder;
	}

	@Tool(description = "Read a public HTTP or HTTPS web page when a web search snippet is insufficient. "
			+ "Never use it for localhost, private addresses, files, or non-web protocols.")
	public String readUrl(@ToolParam(description = "A public http or https URL to read") String url) {
		WebPageContent page = urlReaderService.read(url);
		toolUsageRecorder.record("UrlReaderTool");
		toolUsageRecorder.recordDetail("Read URL: " + abbreviate(page.url(), 180));
		return "Title: " + (page.title().isBlank() ? "(untitled)" : page.title()) + "\nURL: " + page.url()
				+ "\nContent:\n" + page.content();
	}

	private String abbreviate(String value, int limit) {
		return value.length() <= limit ? value : value.substring(0, limit - 3) + "...";
	}
}
