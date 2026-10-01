package com.agentflow.backend.ai.browser;

import org.springframework.stereotype.Service;

import com.agentflow.backend.approval.mapper.AgentApprovalMapper;
import com.agentflow.backend.approval.model.AgentApproval;
import com.agentflow.backend.approval.model.AgentApprovalStatus;
import com.agentflow.backend.run.mapper.AgentRunMapper;
import com.agentflow.backend.run.model.AgentRun;
import com.agentflow.backend.run.model.AgentRunStatus;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

/** Not a model tool. Only the approved-run Consumer can call this service. */
@Service
public class BrowserWriteService {

	private final AgentApprovalMapper approvalMapper;
	private final AgentRunMapper runMapper;
	private final BrowserService browserService;
	private final ObjectMapper objectMapper = new ObjectMapper();

	public BrowserWriteService(AgentApprovalMapper approvalMapper, AgentRunMapper runMapper,
			BrowserService browserService) {
		this.approvalMapper = approvalMapper;
		this.runMapper = runMapper;
		this.browserService = browserService;
	}

	public BrowserActionResult executeApprovedBrowserAction(Long approvalId, Long runId) {
		AgentApproval approval = approvalMapper.selectById(approvalId);
		AgentRun run = runMapper.selectById(runId);
		if (approval == null || run == null || !runId.equals(approval.getRunId())
				|| approval.getStatus() != AgentApprovalStatus.APPROVED
				|| run.getStatus() != AgentRunStatus.RUNNING
				|| !approval.getActionType().startsWith("BROWSER_")) {
			throw new IllegalStateException("Browser action requires a stored approval for this running run");
		}
		try {
			BrowserActionPayload payload = objectMapper.readValue(approval.getActionPayload(), BrowserActionPayload.class);
			if (payload.browserAction() == null || !approval.getActionType().equals("BROWSER_" + payload.browserAction().name())) {
				throw new IllegalStateException("Approved browser action type does not match payload");
			}
			return browserService.executeApproved(payload);
		} catch (JsonProcessingException exception) {
			throw new IllegalStateException("Approved browser action payload is invalid", exception);
		}
	}
}
