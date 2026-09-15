package com.agentflow.backend.task.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

@TableName("agent_task")
public class AgentTask {

	@TableId(type = IdType.AUTO)
	private Long id;
	private String title;
	private AgentTaskStatus status;

	public AgentTask() {
	}

	public AgentTask(Long id, String title, AgentTaskStatus status) {
		this.id = id;
		this.title = title;
		this.status = status;
	}

	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public String getTitle() {
		return title;
	}

	public void setTitle(String title) {
		this.title = title;
	}

	public AgentTaskStatus getStatus() {
		return status;
	}

	public void setStatus(AgentTaskStatus status) {
		this.status = status;
	}
}
