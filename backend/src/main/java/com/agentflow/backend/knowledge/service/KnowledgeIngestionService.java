package com.agentflow.backend.knowledge.service;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.ai.document.Document;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import com.agentflow.backend.knowledge.model.KnowledgeUploadResponse;

@Service
public class KnowledgeIngestionService {

	private static final long MAX_FILE_SIZE = 10 * 1024 * 1024;
	private static final Set<String> ALLOWED_EXTENSIONS = Set.of("pdf", "docx", "txt", "md", "markdown");
	private static final Map<String, Set<String>> ALLOWED_CONTENT_TYPES = Map.of(
			"pdf", Set.of("application/pdf"),
			"docx", Set.of("application/vnd.openxmlformats-officedocument.wordprocessingml.document"),
			"txt", Set.of("text/plain"),
			"md", Set.of("text/markdown", "text/plain"),
			"markdown", Set.of("text/markdown", "text/plain"));

	private final VectorStore knowledgeVectorStore;
	private final TokenTextSplitter tokenTextSplitter = TokenTextSplitter.builder()
			.withChunkSize(800)
			.withMinChunkSizeChars(350)
			.withMinChunkLengthToEmbed(10)
			.withMaxNumChunks(1_000)
			.withKeepSeparator(true)
			.build();

	public KnowledgeIngestionService(VectorStore knowledgeVectorStore) {
		this.knowledgeVectorStore = knowledgeVectorStore;
	}

	public KnowledgeUploadResponse ingest(MultipartFile file) {
		String fileName = validateAndGetFileName(file);
		String contentType = file.getContentType() == null ? "" : file.getContentType();
		String documentId = UUID.randomUUID().toString();
		List<Document> documents = readDocuments(file, fileName);
		List<Document> chunks = tokenTextSplitter.apply(addMetadata(documents, fileName, contentType, documentId));

		if (chunks.isEmpty()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The uploaded file contains no indexable text");
		}

		try {
			knowledgeVectorStore.add(chunks);
		} catch (RuntimeException exception) {
			throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
					"Could not index the file. Verify PGvector and embedding model configuration.", exception);
		}

		return new KnowledgeUploadResponse(fileName, chunks.size(), "INDEXED");
	}

	private String validateAndGetFileName(MultipartFile file) {
		if (file == null || file.isEmpty()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A non-empty file is required");
		}
		if (file.getSize() > MAX_FILE_SIZE) {
			throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "File size must not exceed 10 MB");
		}

		String fileName = sanitizeFileName(file.getOriginalFilename());
		String extension = extensionOf(fileName);
		if (!ALLOWED_EXTENSIONS.contains(extension)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Only PDF, DOCX, TXT, and Markdown files are supported");
		}

		String contentType = normalizedContentType(file.getContentType());
		if (StringUtils.hasText(contentType) && !"application/octet-stream".equals(contentType)
				&& !ALLOWED_CONTENT_TYPES.get(extension).contains(contentType)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The file MIME type does not match its extension");
		}
		return fileName;
	}

	private List<Document> readDocuments(MultipartFile file, String fileName) {
		try {
			ByteArrayResource resource = new ByteArrayResource(file.getBytes()) {
				@Override
				public String getFilename() {
					return fileName;
				}
			};
			return new TikaDocumentReader(resource).read();
		} catch (IOException | RuntimeException exception) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The file could not be parsed as text", exception);
		}
	}

	private List<Document> addMetadata(List<Document> documents, String fileName, String contentType,
			String documentId) {
		return documents.stream()
				.map(document -> {
					Map<String, Object> metadata = new HashMap<>(document.getMetadata());
					metadata.put("sourceFileName", fileName);
					metadata.put("contentType", contentType);
					metadata.put("documentId", documentId);
					return new Document(document.getText(), metadata);
				})
				.toList();
	}

	private String sanitizeFileName(String originalFileName) {
		if (!StringUtils.hasText(originalFileName)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A file name is required");
		}
		String cleaned = StringUtils.cleanPath(originalFileName);
		String fileName = cleaned.substring(Math.max(cleaned.lastIndexOf('/'), cleaned.lastIndexOf('\\')) + 1);
		if (!StringUtils.hasText(fileName) || fileName.length() > 255 || fileName.contains("..")
				|| fileName.chars().anyMatch(Character::isISOControl)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid file name");
		}
		return fileName;
	}

	private String extensionOf(String fileName) {
		int extensionStart = fileName.lastIndexOf('.');
		return extensionStart < 1 || extensionStart == fileName.length() - 1 ? ""
				: fileName.substring(extensionStart + 1).toLowerCase();
	}

	private String normalizedContentType(String contentType) {
		if (!StringUtils.hasText(contentType)) {
			return "";
		}
		int parameterStart = contentType.indexOf(';');
		return (parameterStart >= 0 ? contentType.substring(0, parameterStart) : contentType).trim().toLowerCase();
	}
}
