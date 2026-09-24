package com.agentflow.backend.ai.tool;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import com.agentflow.backend.knowledge.model.KnowledgeResult;
import com.agentflow.backend.knowledge.service.KnowledgeSearchService;

public class KnowledgeSearchTool {

	private static final int MAX_CHUNK_CHARACTERS = 1_200;

	private final KnowledgeSearchService knowledgeSearchService;
	private final ToolUsageRecorder toolUsageRecorder;

	public KnowledgeSearchTool(KnowledgeSearchService knowledgeSearchService, ToolUsageRecorder toolUsageRecorder) {
		this.knowledgeSearchService = knowledgeSearchService;
		this.toolUsageRecorder = toolUsageRecorder;
	}

	@Tool(description = "Search the user's uploaded internal documents, PDFs, project materials, manuals, and private knowledge. "
			+ "Use this when the question is about uploaded internal content. It returns relevant chunks and real source file names.")
	public String searchKnowledge(@ToolParam(description = "A focused query about uploaded internal knowledge") String query) {
		List<KnowledgeResult> results = knowledgeSearchService.search(query);
		toolUsageRecorder.record("KnowledgeSearchTool");
		toolUsageRecorder.recordDetail("RAG query: " + abbreviate(query, 160));
		if (results.isEmpty()) {
			return "No relevant internal knowledge was found.";
		}

		Set<String> sources = new LinkedHashSet<>();
		StringBuilder formatted = new StringBuilder("Internal knowledge search results:\n");
		for (int index = 0; index < results.size(); index++) {
			KnowledgeResult result = results.get(index);
			sources.add(result.sourceFileName());
			formatted.append(index + 1).append(". Source: ").append(result.sourceFileName())
					.append("\nContent: ").append(abbreviate(result.content(), MAX_CHUNK_CHARACTERS)).append("\n");
		}
		toolUsageRecorder.recordDetail("RAG sources: " + String.join(", ", sources));
		return formatted.toString();
	}

	private String abbreviate(String value, int limit) {
		String normalized = value == null ? "" : value.trim();
		return normalized.length() <= limit ? normalized : normalized.substring(0, limit - 3) + "...";
	}
}
