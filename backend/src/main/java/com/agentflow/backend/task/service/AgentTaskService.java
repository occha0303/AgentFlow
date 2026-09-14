package com.agentflow.backend.task.service;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;

import com.agentflow.backend.task.model.AgentTask;

@Service
public class AgentTaskService {

	private final List<AgentTask> tasks = new ArrayList<>();
	private long nextId = 1L;

	public List<AgentTask> getAllTasks() {
		return tasks;
	}

	public AgentTask createTask(String title) {
		AgentTask task = new AgentTask(nextId++, title, "CREATED");
		tasks.add(task);
		return task;
	}
}
