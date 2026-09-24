package com.agentflow.backend.ai.web.search;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import com.fasterxml.jackson.databind.JsonNode;

@Service
public class TavilySearchService implements SearchService {

	private static final int MAX_RESULTS = 5;

	private final RestClient restClient;
	private final String apiKey;

	public TavilySearchService(RestClient.Builder restClientBuilder,
			@Value("${search.api.base-url:https://api.tavily.com}") String baseUrl,
			@Value("${search.api.key:}") String apiKey) {
		this.restClient = restClientBuilder.baseUrl(removeTrailingSlash(baseUrl)).build();
		this.apiKey = apiKey;
	}

	@Override
	public List<SearchResultItem> search(String query) {
		if (!StringUtils.hasText(query)) {
			throw new IllegalArgumentException("WebSearchTool failed: search query must not be blank");
		}
		if (!StringUtils.hasText(apiKey)) {
			throw new IllegalStateException("WebSearchTool failed: Search API key is not configured");
		}

		try {
			JsonNode response = restClient.post()
					.uri("/search")
					.header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
					.contentType(MediaType.APPLICATION_JSON)
					.body(Map.of("query", query.trim(), "max_results", MAX_RESULTS, "search_depth", "basic",
							"include_answer", false, "include_raw_content", false, "include_images", false))
					.retrieve()
					.body(JsonNode.class);
			return toSearchResults(response);
		} catch (RestClientResponseException exception) {
			throw new IllegalStateException("WebSearchTool failed: Tavily API returned "
					+ exception.getStatusCode().value());
		} catch (ResourceAccessException exception) {
			throw new IllegalStateException("WebSearchTool failed: network request failed");
		}
	}

	private List<SearchResultItem> toSearchResults(JsonNode response) {
		List<SearchResultItem> results = new ArrayList<>();
		if (response == null || !response.path("results").isArray()) {
			return results;
		}

		for (JsonNode result : response.path("results")) {
			if (results.size() == MAX_RESULTS) {
				break;
			}
			String url = result.path("url").asText("").trim();
			if (!StringUtils.hasText(url)) {
				continue;
			}
			results.add(new SearchResultItem(result.path("title").asText("").trim(), url,
					result.path("content").asText("").trim()));
		}
		return results;
	}

	private String removeTrailingSlash(String baseUrl) {
		return baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
	}
}
