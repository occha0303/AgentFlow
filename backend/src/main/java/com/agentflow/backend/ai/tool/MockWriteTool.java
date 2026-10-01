package com.agentflow.backend.ai.tool;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import com.agentflow.backend.ai.trace.AgentToolTraceRecorder;
import com.agentflow.backend.approval.model.AgentApproval;
import com.agentflow.backend.approval.service.AgentApprovalService;

/** The model can only prepare an action, never execute it. */
public class MockWriteTool {

	private final AgentApprovalService approvalService;
	private final AgentToolTraceRecorder traceRecorder;

	public MockWriteTool(AgentApprovalService approvalService, AgentToolTraceRecorder traceRecorder) {
		this.approvalService = approvalService;
		this.traceRecorder = traceRecorder;
	}

	@Tool(description = "Prepare a proposed publication to the demo platform and request human approval. "
			+ "This never publishes content. The user must approve the stored action before backend execution.")
	public String prepareWriteAction(
			@ToolParam(description = "Must be demo platform") String target,
			@ToolParam(description = "The exact content proposed for publication") String content) {
		AgentApproval approval = approvalService.prepareMockPublish(traceRecorder.runId(),
				traceRecorder.claimStepOrder(), target, content);
		traceRecorder.markApprovalPrepared();
		throw new ApprovalPauseException("Approval " + approval.getId() + " is pending");
	}

	private static class ApprovalPauseException extends RuntimeException {
		private ApprovalPauseException(String message) {
			super(message);
		}
	}
}
