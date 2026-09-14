package com.agentflow.backend.task.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

@TableName("agent_task")
public class AgentTask {

	@TableId(type = IdType.AUTO)
	private Long id;
	private String title;
	private String status;

	public AgentTask() {
	}

	public AgentTask(Long id, String title, String status) {
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

	public String getStatus() {
		return status;
	}

	public void setStatus(String status) {
		this.status = status;
	}
}
