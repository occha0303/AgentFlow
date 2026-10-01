package com.agentflow.backend.approval.service;

import org.springframework.stereotype.Service;

import com.agentflow.backend.approval.mapper.AgentApprovalMapper;
import com.agentflow.backend.approval.model.AgentApproval;
import com.agentflow.backend.approval.model.AgentApprovalStatus;
import com.agentflow.backend.run.mapper.AgentRunMapper;
import com.agentflow.backend.run.model.AgentRun;
import com.agentflow.backend.run.model.AgentRunStatus;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/** This demo action has no external side effect. It is deliberately not an AI tool. */
@Service
public class MockWriteService {

	private final AgentApprovalMapper approvalMapper;
	private final AgentRunMapper runMapper;
	private final ObjectMapper objectMapper = new ObjectMapper();

	public MockWriteService(AgentApprovalMapper approvalMapper, AgentRunMapper runMapper) {
		this.approvalMapper = approvalMapper;
		this.runMapper = runMapper;
	}

	public String executeApprovedAction(Long approvalId, Long runId) {
		AgentApproval approval = approvalMapper.selectById(approvalId);
		AgentRun run = runMapper.selectById(runId);
		if (approval == null || run == null || !runId.equals(approval.getRunId())
				|| approval.getStatus() != AgentApprovalStatus.APPROVED
				|| run.getStatus() != AgentRunStatus.RUNNING
				|| !"PUBLISH".equals(approval.getActionType())) {
			throw new IllegalStateException("Mock write requires a stored approved action for this running run");
		}
		try {
			JsonNode payload = objectMapper.readTree(approval.getActionPayload());
			if (!"demo platform".equals(payload.path("target").asText()) || payload.path("content").asText().isBlank()) {
				throw new IllegalStateException("Approved action payload is invalid");
			}
			return "Mock publication completed on demo platform: " + payload.path("content").asText();
		} catch (JsonProcessingException exception) {
			throw new IllegalStateException("Approved action payload is invalid", exception);
		}
	}
}
