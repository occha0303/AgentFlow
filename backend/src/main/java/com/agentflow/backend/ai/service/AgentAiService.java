package com.agentflow.backend.ai.service;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import com.agentflow.backend.ai.model.AgentAiExecutionResult;
import com.agentflow.backend.ai.tool.CalculatorTool;
import com.agentflow.backend.ai.tool.CurrentTimeTool;
import com.agentflow.backend.ai.tool.ToolUsageRecorder;
import com.agentflow.backend.ai.tool.UrlReaderTool;
import com.agentflow.backend.ai.tool.WebSearchTool;
import com.agentflow.backend.ai.web.UrlReaderService;
import com.agentflow.backend.ai.web.search.SearchService;

@Service
public class AgentAiService {

	private final ChatClient chatClient;
	private final SearchService searchService;
	private final UrlReaderService urlReaderService;

	public AgentAiService(ChatClient.Builder chatClientBuilder, SearchService searchService, UrlReaderService urlReaderService) {
		this.searchService = searchService;
		this.urlReaderService = urlReaderService;
		this.chatClient = chatClientBuilder
				.defaultSystem("You are AgentFlow's task execution agent. Use CalculatorTool for exact math and "
						+ "always use CurrentTimeTool for current time or date. Use WebSearchTool for latest, current, "
						+ "recent, or external facts, and UrlReaderTool only when search snippets are insufficient. "
						+ "Do not invent tool results. After using web tools, include a Sources section with only actual "
						+ "titles and URLs returned by those tools. Answer directly when no tool is needed.")
				.build();
	}

	public AgentAiExecutionResult execute(String task) {
		ToolUsageRecorder toolUsageRecorder = new ToolUsageRecorder();
		String resultText = chatClient.prompt()
				.user(task)
				.tools(new CalculatorTool(toolUsageRecorder), new CurrentTimeTool(toolUsageRecorder),
						new WebSearchTool(searchService, toolUsageRecorder),
						new UrlReaderTool(urlReaderService, toolUsageRecorder))
				.call()
				.content();
		return new AgentAiExecutionResult(resultText, toolUsageRecorder.getUsedTools(),
				toolUsageRecorder.getUsageDetails());
	}
}
