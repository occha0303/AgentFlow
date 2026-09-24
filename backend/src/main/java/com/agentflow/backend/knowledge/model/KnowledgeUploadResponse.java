package com.agentflow.backend.knowledge.model;

public record KnowledgeUploadResponse(String fileName, int chunkCount, String status) {
}
