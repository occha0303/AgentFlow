package com.agentflow.backend.approval.model;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

@TableName("agent_approval")
public class AgentApproval {

	@TableId(value = "id", type = IdType.AUTO)
	private Long id;
	private Long runId;
	private Long stepId;
	private String actionType;
	private String actionSummary;
	private String actionPayload;
	private AgentApprovalStatus status;
	private LocalDateTime createdAt;
	private LocalDateTime decidedAt;
	private String decisionReason;

	public Long getId() { return id; }
	public void setId(Long id) { this.id = id; }
	public Long getRunId() { return runId; }
	public void setRunId(Long runId) { this.runId = runId; }
	public Long getStepId() { return stepId; }
	public void setStepId(Long stepId) { this.stepId = stepId; }
	public String getActionType() { return actionType; }
	public void setActionType(String actionType) { this.actionType = actionType; }
	public String getActionSummary() { return actionSummary; }
	public void setActionSummary(String actionSummary) { this.actionSummary = actionSummary; }
	public String getActionPayload() { return actionPayload; }
	public void setActionPayload(String actionPayload) { this.actionPayload = actionPayload; }
	public AgentApprovalStatus getStatus() { return status; }
	public void setStatus(AgentApprovalStatus status) { this.status = status; }
	public LocalDateTime getCreatedAt() { return createdAt; }
	public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
	public LocalDateTime getDecidedAt() { return decidedAt; }
	public void setDecidedAt(LocalDateTime decidedAt) { this.decidedAt = decidedAt; }
	public String getDecisionReason() { return decisionReason; }
	public void setDecisionReason(String decisionReason) { this.decisionReason = decisionReason; }
}
