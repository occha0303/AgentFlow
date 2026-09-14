package com.agentflow.backend.task.service;

import java.util.List;

import org.springframework.stereotype.Service;

import com.agentflow.backend.task.mapper.AgentTaskMapper;
import com.agentflow.backend.task.model.AgentTask;

@Service
public class AgentTaskService {

	private final AgentTaskMapper agentTaskMapper;

	public AgentTaskService(AgentTaskMapper agentTaskMapper) {
		this.agentTaskMapper = agentTaskMapper;
	}

	public List<AgentTask> getAllTasks() {
		return agentTaskMapper.selectList(null);
	}

	public AgentTask createTask(String title) {
		AgentTask task = new AgentTask(null, title, "CREATED");
		agentTaskMapper.insert(task);
		return task;
	}
}
