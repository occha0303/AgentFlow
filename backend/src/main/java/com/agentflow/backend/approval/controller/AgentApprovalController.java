package com.agentflow.backend.approval.controller;

import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.agentflow.backend.approval.model.AgentApproval;
import com.agentflow.backend.approval.service.AgentApprovalService;

@RestController
@CrossOrigin(origins = { "http://localhost:5173", "http://127.0.0.1:5173" })
public class AgentApprovalController {

	private final AgentApprovalService approvalService;

	public AgentApprovalController(AgentApprovalService approvalService) {
		this.approvalService = approvalService;
	}

	@GetMapping("/api/runs/{runId}/approvals")
	public List<AgentApproval> forRun(@PathVariable Long runId) {
		return approvalService.getApprovalsForRun(runId);
	}

	@GetMapping("/api/approvals/{approvalId}")
	public AgentApproval get(@PathVariable Long approvalId) {
		return approvalService.getApproval(approvalId);
	}

	@PostMapping("/api/approvals/{approvalId}/approve")
	public ResponseEntity<AgentApproval> approve(@PathVariable Long approvalId) {
		return ResponseEntity.accepted().body(approvalService.approve(approvalId));
	}

	@PostMapping("/api/approvals/{approvalId}/reject")
	public AgentApproval reject(@PathVariable Long approvalId,
			@RequestBody(required = false) Map<String, String> body) {
		return approvalService.reject(approvalId, body == null ? "" : body.get("reason"));
	}
}
