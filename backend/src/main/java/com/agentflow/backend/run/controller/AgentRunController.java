package com.agentflow.backend.run.controller;

import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.agentflow.backend.run.model.AgentRun;
import com.agentflow.backend.run.service.AgentRunService;

@RestController
@RequestMapping("/api/runs")
@CrossOrigin(origins = { "http://localhost:5173", "http://127.0.0.1:5173" })
public class AgentRunController {

	private final AgentRunService agentRunService;

	public AgentRunController(AgentRunService agentRunService) {
		this.agentRunService = agentRunService;
	}

	@GetMapping("/{runId}")
	public AgentRun getRun(@PathVariable Long runId) {
		return agentRunService.getRun(runId);
	}
}
