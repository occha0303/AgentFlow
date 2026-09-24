package com.agentflow.backend.ai.tool;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class ToolUsageRecorder {

	private final Set<String> usedTools = new LinkedHashSet<>();

	public void record(String toolName) {
		usedTools.add(toolName);
	}

	public List<String> getUsedTools() {
		return List.copyOf(usedTools);
	}
}
