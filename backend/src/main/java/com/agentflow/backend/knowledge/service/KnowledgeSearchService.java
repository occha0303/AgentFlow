package com.agentflow.backend.knowledge.service;

import java.util.List;

import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.agentflow.backend.knowledge.model.KnowledgeResult;

@Service
public class KnowledgeSearchService {

	private static final int TOP_K = 5;

	private final VectorStore knowledgeVectorStore;

	public KnowledgeSearchService(VectorStore knowledgeVectorStore) {
		this.knowledgeVectorStore = knowledgeVectorStore;
	}

	public List<KnowledgeResult> search(String query) {
		if (!StringUtils.hasText(query)) {
			throw new IllegalArgumentException("KnowledgeSearchTool failed: search query must not be blank");
		}

		try {
			return knowledgeVectorStore.similaritySearch(SearchRequest.builder().query(query.trim()).topK(TOP_K).build())
					.stream()
					.map(this::toKnowledgeResult)
					.toList();
		} catch (RuntimeException exception) {
			if (exception.getMessage() != null && exception.getMessage().startsWith("KnowledgeSearchTool failed:")) {
				throw exception;
			}
			throw new IllegalStateException("KnowledgeSearchTool failed: vector search is unavailable");
		}
	}

	private KnowledgeResult toKnowledgeResult(Document document) {
		Object sourceFileName = document.getMetadata().get("sourceFileName");
		return new KnowledgeResult(document.getText(), sourceFileName == null ? "Unknown source" : sourceFileName.toString());
	}
}
