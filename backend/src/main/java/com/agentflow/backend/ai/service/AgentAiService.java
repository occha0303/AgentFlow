package com.agentflow.backend.ai.service;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import com.agentflow.backend.ai.model.AgentAiExecutionResult;
import com.agentflow.backend.ai.tool.CalculatorTool;
import com.agentflow.backend.ai.tool.CurrentTimeTool;
import com.agentflow.backend.ai.tool.KnowledgeSearchTool;
import com.agentflow.backend.ai.tool.ToolUsageRecorder;
import com.agentflow.backend.ai.tool.UrlReaderTool;
import com.agentflow.backend.ai.tool.WebSearchTool;
import com.agentflow.backend.ai.web.UrlReaderService;
import com.agentflow.backend.ai.web.search.SearchService;
import com.agentflow.backend.knowledge.service.KnowledgeSearchService;

@Service
public class AgentAiService {

	private final ChatClient chatClient;
	private final SearchService searchService;
	private final UrlReaderService urlReaderService;
	private final KnowledgeSearchService knowledgeSearchService;

	public AgentAiService(ChatClient.Builder chatClientBuilder, SearchService searchService, UrlReaderService urlReaderService,
			KnowledgeSearchService knowledgeSearchService) {
		this.searchService = searchService;
		this.urlReaderService = urlReaderService;
		this.knowledgeSearchService = knowledgeSearchService;
		this.chatClient = chatClientBuilder
				.defaultSystem("You are AgentFlow's task execution agent. Use CalculatorTool for exact math and "
						+ "always use CurrentTimeTool for current time or date. Use WebSearchTool for latest, current, "
						+ "recent, or public external facts, and UrlReaderTool only when search snippets are insufficient. "
						+ "Use KnowledgeSearchTool for user-uploaded internal documents; do not use it for public updates. "
						+ "Do not call web and knowledge tools together unless the question genuinely needs both. Do not invent "
						+ "tool results. After using web or knowledge tools, include a Sources section with only actual sources "
						+ "returned by those tools. If knowledge search finds nothing, state that clearly. Answer directly when no tool is needed.")
				.build();
	}

	public AgentAiExecutionResult execute(String task) {
		ToolUsageRecorder toolUsageRecorder = new ToolUsageRecorder();
		String resultText = chatClient.prompt()
				.user(task)
				.tools(new CalculatorTool(toolUsageRecorder), new CurrentTimeTool(toolUsageRecorder),
						new WebSearchTool(searchService, toolUsageRecorder),
						new UrlReaderTool(urlReaderService, toolUsageRecorder),
						new KnowledgeSearchTool(knowledgeSearchService, toolUsageRecorder))
				.call()
				.content();
		return new AgentAiExecutionResult(resultText, toolUsageRecorder.getUsedTools(),
				toolUsageRecorder.getUsageDetails());
	}
}
