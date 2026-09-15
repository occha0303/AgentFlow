package com.agentflow.backend.run.messaging;

public class AgentRunExecutionMessage {

	private Long runId;

	public AgentRunExecutionMessage() {
	}

	public AgentRunExecutionMessage(Long runId) {
		this.runId = runId;
	}

	public Long getRunId() {
		return runId;
	}

	public void setRunId(Long runId) {
		this.runId = runId;
	}
}
