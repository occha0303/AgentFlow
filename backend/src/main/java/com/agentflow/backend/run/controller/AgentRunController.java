package com.agentflow.backend.run.controller;

import java.util.List;

import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.http.ResponseEntity;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.agentflow.backend.approval.service.AgentApprovalService;
import com.agentflow.backend.run.model.AgentRun;
import com.agentflow.backend.run.service.AgentRunService;
import com.agentflow.backend.run.service.RunEventPublisher;
import com.agentflow.backend.run.service.RunEventPublisher.ApprovalEvent;
import com.agentflow.backend.run.service.RunEventPublisher.RunSnapshot;
import com.agentflow.backend.step.model.AgentStep;

@RestController
@RequestMapping("/api/runs")
@CrossOrigin(origins = { "http://localhost:5173", "http://127.0.0.1:5173" })
public class AgentRunController {

	private final AgentRunService agentRunService;
	private final AgentApprovalService approvalService;
	private final RunEventPublisher events;

	public AgentRunController(AgentRunService agentRunService, AgentApprovalService approvalService,
			RunEventPublisher events) {
		this.agentRunService = agentRunService;
		this.approvalService = approvalService;
		this.events = events;
	}

	@GetMapping("/{runId}")
	public AgentRun getRun(@PathVariable Long runId) {
		return agentRunService.getRun(runId);
	}

	@GetMapping("/{runId}/steps")
	public List<AgentStep> getSteps(@PathVariable Long runId) {
		return agentRunService.getSteps(runId);
	}

	@GetMapping(path = "/{runId}/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
	public SseEmitter events(@PathVariable Long runId) {
		// Validate before opening a connection so unknown runs return HTTP 404.
		agentRunService.getRun(runId);
		return events.subscribe(runId, () -> {
			AgentRun run = agentRunService.getRun(runId);
			List<ApprovalEvent> approvals = approvalService.getApprovalsForRun(runId).stream()
					.map(approval -> new ApprovalEvent(approval.getId(), runId, approval.getStatus().name()))
					.toList();
			return new RunSnapshot(runId, run.getStatus(), run.getResultText(), run.getErrorMessage(),
					run.getRetryOfRunId(), agentRunService.getSteps(runId), approvals);
		});
	}

	@PostMapping("/{runId}/retry")
	public ResponseEntity<AgentRun> retryRun(@PathVariable Long runId) {
		return ResponseEntity.accepted().body(agentRunService.retryRun(runId));
	}
}
