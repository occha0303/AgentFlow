package com.agentflow.backend.ai.model;

import java.util.List;

public record AgentAiExecutionResult(String resultText, List<String> usedTools) {
}
