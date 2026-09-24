package com.agentflow.backend.ai.service;

import java.util.regex.Pattern;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import com.agentflow.backend.ai.model.AgentAiExecutionResult;
import com.agentflow.backend.ai.browser.BrowserService;
import com.agentflow.backend.ai.trace.AgentToolTraceRecorder;
import com.agentflow.backend.ai.tool.BrowserTool;
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

	private static final Pattern EXPLICIT_WEB_URL = Pattern.compile("(?i)\\bhttps?://\\S+");

	private final ChatClient chatClient;
	private final SearchService searchService;
	private final UrlReaderService urlReaderService;
	private final KnowledgeSearchService knowledgeSearchService;
	private final BrowserService browserService;

	public AgentAiService(ChatClient.Builder chatClientBuilder, SearchService searchService, UrlReaderService urlReaderService,
			KnowledgeSearchService knowledgeSearchService, BrowserService browserService) {
		this.searchService = searchService;
		this.urlReaderService = urlReaderService;
		this.knowledgeSearchService = knowledgeSearchService;
		this.browserService = browserService;
		this.chatClient = chatClientBuilder
				.defaultSystem("You are AgentFlow's task execution agent. Use CalculatorTool for exact math and "
						+ "always use CurrentTimeTool for current time or date. Use WebSearchTool for latest, current, "
						+ "recent, or public external facts, and UrlReaderTool only when search snippets are insufficient. "
						+ "Use BrowserTool only when the user provides a specific public URL or requires rendered page state; "
						+ "BrowserTool is read-only and cannot click, type, log in, or change data. "
						+ "Use KnowledgeSearchTool for user-uploaded internal documents; do not use it for public updates. "
						+ "Do not call web and knowledge tools together unless the question genuinely needs both. Do not invent "
						+ "tool results. After using web or knowledge tools, include a Sources section with only actual sources "
						+ "returned by those tools. If knowledge search finds nothing, state that clearly. Answer directly when no tool is needed.")
				.build();
	}

	public AgentAiExecutionResult execute(String task, AgentToolTraceRecorder traceRecorder) {
		ToolUsageRecorder toolUsageRecorder = new ToolUsageRecorder();
		Object[] tools = createTools(task, toolUsageRecorder, traceRecorder);
		String resultText = chatClient.prompt()
				.user(task)
				.tools(tools)
				.call()
				.content();
		return new AgentAiExecutionResult(resultText, toolUsageRecorder.getUsedTools(),
				toolUsageRecorder.getUsageDetails());
	}

	private Object[] createTools(String task, ToolUsageRecorder toolUsageRecorder, AgentToolTraceRecorder traceRecorder) {
		Object[] baseTools = {
				new CalculatorTool(toolUsageRecorder),
				new CurrentTimeTool(toolUsageRecorder),
				new WebSearchTool(searchService, toolUsageRecorder),
				new UrlReaderTool(urlReaderService, toolUsageRecorder),
				new KnowledgeSearchTool(knowledgeSearchService, toolUsageRecorder)
		};

		if (!hasExplicitWebUrl(task)) {
			return baseTools;
		}

		return new Object[] {
				baseTools[0],
				baseTools[1],
				baseTools[2],
				baseTools[3],
				new BrowserTool(browserService, toolUsageRecorder, traceRecorder),
				baseTools[4]
		};
	}

	private boolean hasExplicitWebUrl(String task) {
		return task != null && EXPLICIT_WEB_URL.matcher(task).find();
	}
}
