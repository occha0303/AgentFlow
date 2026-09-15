package com.agentflow.backend.task.controller;

import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.CrossOrigin;

import com.agentflow.backend.task.model.AgentTask;
import com.agentflow.backend.task.service.AgentTaskService;

@RestController
@RequestMapping("/api/tasks")
@CrossOrigin(origins = { "http://localhost:5173", "http://127.0.0.1:5173" })
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

	@PostMapping("/{id}/run")
	public AgentTask runTask(@PathVariable Long id) {
		return agentTaskService.runTask(id);
	}
}
