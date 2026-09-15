package com.agentflow.backend.step.model;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

@TableName("agent_step")
public class AgentStep {

	@TableId(value = "id", type = IdType.AUTO)
	private Long id;
	private Long runId;
	private Integer stepOrder;
	private String stepType;
	private AgentStepStatus status;
	private String inputSummary;
	private String outputSummary;
	private String errorMessage;
	private LocalDateTime createdAt;
	private LocalDateTime startedAt;
	private LocalDateTime finishedAt;

	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public Long getRunId() {
		return runId;
	}

	public void setRunId(Long runId) {
		this.runId = runId;
	}

	public Integer getStepOrder() {
		return stepOrder;
	}

	public void setStepOrder(Integer stepOrder) {
		this.stepOrder = stepOrder;
	}

	public String getStepType() {
		return stepType;
	}

	public void setStepType(String stepType) {
		this.stepType = stepType;
	}

	public AgentStepStatus getStatus() {
		return status;
	}

	public void setStatus(AgentStepStatus status) {
		this.status = status;
	}

	public String getInputSummary() {
		return inputSummary;
	}

	public void setInputSummary(String inputSummary) {
		this.inputSummary = inputSummary;
	}

	public String getOutputSummary() {
		return outputSummary;
	}

	public void setOutputSummary(String outputSummary) {
		this.outputSummary = outputSummary;
	}

	public String getErrorMessage() {
		return errorMessage;
	}

	public void setErrorMessage(String errorMessage) {
		this.errorMessage = errorMessage;
	}

	public LocalDateTime getCreatedAt() {
		return createdAt;
	}

	public void setCreatedAt(LocalDateTime createdAt) {
		this.createdAt = createdAt;
	}

	public LocalDateTime getStartedAt() {
		return startedAt;
	}

	public void setStartedAt(LocalDateTime startedAt) {
		this.startedAt = startedAt;
	}

	public LocalDateTime getFinishedAt() {
		return finishedAt;
	}

	public void setFinishedAt(LocalDateTime finishedAt) {
		this.finishedAt = finishedAt;
	}
}
