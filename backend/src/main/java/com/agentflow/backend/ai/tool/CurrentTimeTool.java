package com.agentflow.backend.ai.tool;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

import org.springframework.ai.tool.annotation.Tool;

public class CurrentTimeTool {

	private final ToolUsageRecorder toolUsageRecorder;

	public CurrentTimeTool(ToolUsageRecorder toolUsageRecorder) {
		this.toolUsageRecorder = toolUsageRecorder;
	}

	@Tool(description = "Get the current server date and time. Always use this tool when the user asks for the current time, current date, or what time it is now.")
	public String getCurrentTime() {
		toolUsageRecorder.record("CurrentTimeTool");
		return ZonedDateTime.now().format(DateTimeFormatter.ISO_ZONED_DATE_TIME);
	}
}
