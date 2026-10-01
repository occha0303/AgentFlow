package com.agentflow.backend.ai.tool;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import com.agentflow.backend.ai.browser.BrowserActionPayload;
import com.agentflow.backend.ai.browser.BrowserActionType;
import com.agentflow.backend.ai.browser.BrowserService;
import com.agentflow.backend.ai.trace.AgentToolTraceRecorder;
import com.agentflow.backend.approval.model.AgentApproval;
import com.agentflow.backend.approval.service.AgentApprovalService;

/** Spring AI may prepare an intent, but cannot execute any browser action. */
public class BrowserWriteTool {

	private final BrowserService browserService;
	private final AgentApprovalService approvalService;
	private final AgentToolTraceRecorder traceRecorder;

	public BrowserWriteTool(BrowserService browserService, AgentApprovalService approvalService,
			AgentToolTraceRecorder traceRecorder) {
		this.browserService = browserService;
		this.approvalService = approvalService;
		this.traceRecorder = traceRecorder;
	}

	@Tool(description = "Prepare one browser CLICK, TYPE, or SUBMIT action on a public page for human approval. "
			+ "First call BrowserTool.openWebPage and use one of its element refs (e1, e2, etc.). "
			+ "This tool only creates a pending approval; it never clicks, types, or submits. "
			+ "TYPE is limited to ordinary non-secret text, and SUBMIT to public demo forms. "
			+ "Never use this for login, payment, checkout, deletion, credentials, or private URLs.")
	public String prepareBrowserAction(
			@ToolParam(description = "The public page URL that was read") String url,
			@ToolParam(description = "An element ref from the prior BrowserTool snapshot, such as e1") String elementRef,
			@ToolParam(description = "CLICK, TYPE, or SUBMIT") BrowserActionType action,
			@ToolParam(description = "Plain text for TYPE; empty for CLICK or SUBMIT") String value) {
		BrowserActionPayload payload = browserService.prepareAction(url, elementRef, action, value,
				traceRecorder.browserSnapshotFor(url));
		AgentApproval approval = approvalService.prepareBrowserAction(traceRecorder.runId(),
				traceRecorder.claimStepOrder(), payload);
		traceRecorder.markApprovalPrepared();
		throw new ApprovalPauseException("Browser action approval " + approval.getId() + " is pending");
	}

	private static class ApprovalPauseException extends RuntimeException {
		private ApprovalPauseException(String message) { super(message); }
	}
}
