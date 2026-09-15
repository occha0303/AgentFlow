package com.agentflow.backend.task.service;

import java.util.List;
import java.util.Locale;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.agentflow.backend.task.mapper.AgentTaskMapper;
import com.agentflow.backend.task.model.AgentTask;
import com.agentflow.backend.task.model.AgentTaskStatus;

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
		AgentTask task = new AgentTask(null, title, AgentTaskStatus.CREATED);
		agentTaskMapper.insert(task);
		return task;
	}

	public AgentTask runTask(Long id) {
		AgentTask task = agentTaskMapper.selectById(id);

		if (task == null) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Task not found");
		}

		if (task.getStatus() != AgentTaskStatus.CREATED) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "Only CREATED tasks can be run");
		}

		task.setStatus(AgentTaskStatus.RUNNING);
		agentTaskMapper.updateById(task);

		try {
			Thread.sleep(2500);
		} catch (InterruptedException exception) {
			Thread.currentThread().interrupt();
			task.setStatus(AgentTaskStatus.FAILED);
			agentTaskMapper.updateById(task);
			throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Task execution was interrupted", exception);
		}

		if (task.getTitle().toLowerCase(Locale.ROOT).contains("fail")) {
			task.setStatus(AgentTaskStatus.FAILED);
		} else {
			task.setStatus(AgentTaskStatus.COMPLETED);
		}

		agentTaskMapper.updateById(task);
		return task;
	}
}
