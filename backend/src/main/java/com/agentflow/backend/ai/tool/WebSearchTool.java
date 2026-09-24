package com.agentflow.backend.ai.tool;

import java.util.List;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import com.agentflow.backend.ai.web.search.SearchResultItem;
import com.agentflow.backend.ai.web.search.SearchService;

public class WebSearchTool {

	private static final int MAX_SNIPPET_CHARACTERS = 700;

	private final SearchService searchService;
	private final ToolUsageRecorder toolUsageRecorder;

	public WebSearchTool(SearchService searchService, ToolUsageRecorder toolUsageRecorder) {
		this.searchService = searchService;
		this.toolUsageRecorder = toolUsageRecorder;
	}

	@Tool(description = "Search the public web for latest, current, recent, today, or external factual information. "
			+ "Use this when model knowledge may be outdated. It returns candidate source titles, URLs, and snippets, not final facts.")
	public String searchWeb(@ToolParam(description = "A focused web search query") String query) {
		List<SearchResultItem> results = searchService.search(query);
		toolUsageRecorder.record("WebSearchTool");
		toolUsageRecorder.recordDetail("Search query: " + abbreviate(query, 160));
		return formatSearchResults(results);
	}

	private String formatSearchResults(List<SearchResultItem> results) {
		if (results.isEmpty()) {
			return "No web search results were returned.";
		}

		StringBuilder formatted = new StringBuilder("Web search results:\n");
		for (int index = 0; index < results.size(); index++) {
			SearchResultItem result = results.get(index);
			formatted.append(index + 1).append(". Title: ")
					.append(result.title().isBlank() ? "(untitled)" : result.title())
					.append("\nURL: ").append(result.url())
					.append("\nSnippet: ").append(abbreviate(result.content(), MAX_SNIPPET_CHARACTERS)).append("\n");
		}
		return formatted.toString();
	}

	private String abbreviate(String value, int limit) {
		String normalized = value == null ? "" : value.trim();
		return normalized.length() <= limit ? normalized : normalized.substring(0, limit - 3) + "...";
	}
}
