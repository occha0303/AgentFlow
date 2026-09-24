package com.agentflow.backend.ai.web.search;

import java.util.List;

public interface SearchService {

	List<SearchResultItem> search(String query);
}
