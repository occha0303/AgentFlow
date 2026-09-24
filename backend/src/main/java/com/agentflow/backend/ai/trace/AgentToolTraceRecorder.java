package com.agentflow.backend.ai.trace;

import com.agentflow.backend.step.model.AgentStep;
import com.agentflow.backend.step.service.AgentStepService;

/**
 * Records tool-specific execution steps for one AgentRun. It is created per
 * run, so concurrent Consumer executions never share trace state.
 */
public class AgentToolTraceRecorder {

	private final Long runId;
	private final AgentStepService agentStepService;
	private int nextStepOrder;

	public AgentToolTraceRecorder(Long runId, AgentStepService agentStepService, int firstStepOrder) {
		this.runId = runId;
		this.agentStepService = agentStepService;
		this.nextStepOrder = firstStepOrder;
	}

	public AgentStep startBrowserStep(String url) {
		return agentStepService.startStep(runId, nextStepOrder++, "BROWSER", abbreviate(url, 500));
	}

	public void completeBrowserStep(AgentStep step, String outputSummary) {
		agentStepService.completeStep(step.getId(), abbreviate(outputSummary, 500));
	}

	public void failBrowserStep(AgentStep step, String errorMessage) {
		agentStepService.failStep(step.getId(), abbreviate(errorMessage, 500));
	}

	public int nextStepOrder() {
		return nextStepOrder;
	}

	private String abbreviate(String value, int limit) {
		if (value == null) {
			return "";
		}
		return value.length() <= limit ? value : value.substring(0, limit - 3) + "...";
	}
}
