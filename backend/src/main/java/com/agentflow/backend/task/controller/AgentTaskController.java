package com.agentflow.backend.task.controller;

import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.agentflow.backend.task.model.AgentTask;
import com.agentflow.backend.task.service.AgentTaskService;

@RestController
@RequestMapping("/api/tasks")
public class AgentTaskController {

	private final AgentTaskService agentTaskService;

	public AgentTaskController(AgentTaskService agentTaskService) {
		this.agentTaskService = agentTaskService;
	}

	@GetMapping
	public List<AgentTask> getAllTasks() {
		return agentTaskService.getAllTasks();
	}

	@PostMapping
	public AgentTask createTask(@RequestBody Map<String, String> request) {
		return agentTaskService.createTask(request.get("title"));
	}
}
