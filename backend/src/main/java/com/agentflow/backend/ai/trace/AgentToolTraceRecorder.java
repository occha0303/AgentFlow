package com.agentflow.backend.ai.trace;

import com.agentflow.backend.ai.browser.BrowserPageSnapshot;
import com.agentflow.backend.run.service.RunHeartbeatService;
import com.agentflow.backend.step.model.AgentStep;
import com.agentflow.backend.step.service.AgentStepService;

/**
 * Records tool-specific execution steps for one AgentRun. It is created per
 * run, so concurrent Consumer executions never share trace state.
 */
public class AgentToolTraceRecorder {

	private final Long runId;
	private final AgentStepService agentStepService;
	private final RunHeartbeatService heartbeatService;
	private int nextStepOrder;
	private boolean approvalPrepared;
	private BrowserPageSnapshot browserSnapshot;
	private String browserRequestedUrl;

	public AgentToolTraceRecorder(Long runId, AgentStepService agentStepService,
			RunHeartbeatService heartbeatService, int firstStepOrder) {
		this.runId = runId;
		this.agentStepService = agentStepService;
		this.heartbeatService = heartbeatService;
		this.nextStepOrder = firstStepOrder;
	}

	public AgentStep startBrowserStep(String url) {
		heartbeatService.touchRunning(runId);
		return agentStepService.startStep(runId, nextStepOrder++, "BROWSER", abbreviate(url, 500));
	}

	public void completeBrowserStep(AgentStep step, String outputSummary) {
		agentStepService.completeStep(step.getId(), abbreviate(outputSummary, 500));
		heartbeatService.touchRunning(runId);
	}

	public void failBrowserStep(AgentStep step, String errorMessage) {
		agentStepService.failStep(step.getId(), abbreviate(errorMessage, 500));
		heartbeatService.touchRunning(runId);
	}

	public int nextStepOrder() {
		return nextStepOrder;
	}

	public int claimStepOrder() {
		return nextStepOrder++;
	}

	public Long runId() {
		return runId;
	}

	public void markApprovalPrepared() {
		approvalPrepared = true;
	}

	public boolean approvalPrepared() {
		return approvalPrepared;
	}

	public void rememberBrowserSnapshot(String requestedUrl, BrowserPageSnapshot snapshot) {
		this.browserRequestedUrl = requestedUrl;
		this.browserSnapshot = snapshot;
	}

	public BrowserPageSnapshot browserSnapshotFor(String url) {
		if (browserSnapshot == null || (!browserSnapshot.url().equals(url)
				&& !browserRequestedUrl.equals(url))) {
			throw new IllegalArgumentException("Browser action requires reading this URL first");
		}
		return browserSnapshot;
	}

	private String abbreviate(String value, int limit) {
		if (value == null) {
			return "";
		}
		return value.length() <= limit ? value : value.substring(0, limit - 3) + "...";
	}
}
