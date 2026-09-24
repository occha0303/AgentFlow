package com.agentflow.backend.knowledge.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.agentflow.backend.knowledge.model.KnowledgeUploadResponse;
import com.agentflow.backend.knowledge.service.KnowledgeIngestionService;

@RestController
@RequestMapping("/api/knowledge/files")
public class KnowledgeController {

	private final KnowledgeIngestionService knowledgeIngestionService;

	public KnowledgeController(KnowledgeIngestionService knowledgeIngestionService) {
		this.knowledgeIngestionService = knowledgeIngestionService;
	}

	@PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	@ResponseStatus(HttpStatus.CREATED)
	public KnowledgeUploadResponse uploadFile(@RequestParam("file") MultipartFile file) {
		return knowledgeIngestionService.ingest(file);
	}
}
