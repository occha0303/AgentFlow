package com.agentflow.backend.step.service;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;
import com.agentflow.backend.run.service.RunEventPublisher;

import com.agentflow.backend.step.mapper.AgentStepMapper;
import com.agentflow.backend.step.model.AgentStep;
import com.agentflow.backend.step.model.AgentStepStatus;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;

@Service
public class AgentStepService {

	private final AgentStepMapper agentStepMapper;
	private final RunEventPublisher events;

	public AgentStepService(AgentStepMapper agentStepMapper, RunEventPublisher events) {
		this.agentStepMapper = agentStepMapper;
		this.events = events;
	}

	public AgentStep startStep(Long runId, Integer stepOrder, String stepType, String inputSummary) {
		LocalDateTime now = LocalDateTime.now();
		AgentStep step = new AgentStep();
		step.setRunId(runId);
		step.setStepOrder(stepOrder);
		step.setStepType(stepType);
		step.setStatus(AgentStepStatus.RUNNING);
		step.setInputSummary(inputSummary);
		step.setCreatedAt(now);
		step.setStartedAt(now);
		agentStepMapper.insert(step);
		events.stepUpdate(step);
		return step;
	}

	public void completeStep(Long stepId, String outputSummary) {
		int updated = agentStepMapper.update(null, new LambdaUpdateWrapper<AgentStep>()
				.eq(AgentStep::getId, stepId)
				.eq(AgentStep::getStatus, AgentStepStatus.RUNNING)
				.set(AgentStep::getStatus, AgentStepStatus.COMPLETED)
				.set(AgentStep::getOutputSummary, outputSummary)
				.set(AgentStep::getFinishedAt, LocalDateTime.now()));
		if (updated == 1) publishStep(stepId);
	}

	public void failStep(Long stepId, String errorMessage) {
		int updated = agentStepMapper.update(null, new LambdaUpdateWrapper<AgentStep>()
				.eq(AgentStep::getId, stepId)
				.eq(AgentStep::getStatus, AgentStepStatus.RUNNING)
				.set(AgentStep::getStatus, AgentStepStatus.FAILED)
				.set(AgentStep::getErrorMessage, errorMessage)
				.set(AgentStep::getFinishedAt, LocalDateTime.now()));
		if (updated == 1) publishStep(stepId);
	}

	public void waitForApproval(Long stepId, String summary) {
		int updated = agentStepMapper.update(null, new LambdaUpdateWrapper<AgentStep>()
				.eq(AgentStep::getId, stepId)
				.eq(AgentStep::getStatus, AgentStepStatus.RUNNING)
				.set(AgentStep::getStatus, AgentStepStatus.WAITING_APPROVAL)
				.set(AgentStep::getOutputSummary, summary));
		if (updated != 1) {
			throw new IllegalStateException("Step is not running while requesting approval");
		}
		publishStep(stepId);
	}

	public void completeWaitingStep(Long stepId, String summary) {
		int updated = agentStepMapper.update(null, new LambdaUpdateWrapper<AgentStep>()
				.eq(AgentStep::getId, stepId)
				.eq(AgentStep::getStatus, AgentStepStatus.WAITING_APPROVAL)
				.set(AgentStep::getStatus, AgentStepStatus.COMPLETED)
				.set(AgentStep::getOutputSummary, summary)
				.set(AgentStep::getFinishedAt, LocalDateTime.now()));
		if (updated != 1) {
			throw new IllegalStateException("Step is not waiting for approval");
		}
		publishStep(stepId);
	}

	public void failWaitingStep(Long stepId, String errorMessage) {
		int updated = agentStepMapper.update(null, new LambdaUpdateWrapper<AgentStep>()
				.eq(AgentStep::getId, stepId)
				.eq(AgentStep::getStatus, AgentStepStatus.WAITING_APPROVAL)
				.set(AgentStep::getStatus, AgentStepStatus.FAILED)
				.set(AgentStep::getErrorMessage, errorMessage)
				.set(AgentStep::getFinishedAt, LocalDateTime.now()));
		if (updated != 1) {
			throw new IllegalStateException("Step is not waiting for approval");
		}
		publishStep(stepId);
	}

	public void failRunningStepsForRun(Long runId, String errorMessage) {
		List<AgentStep> running = agentStepMapper.selectList(new LambdaQueryWrapper<AgentStep>()
				.eq(AgentStep::getRunId, runId).eq(AgentStep::getStatus, AgentStepStatus.RUNNING));
		agentStepMapper.update(null, new LambdaUpdateWrapper<AgentStep>()
				.eq(AgentStep::getRunId, runId)
				.eq(AgentStep::getStatus, AgentStepStatus.RUNNING)
				.set(AgentStep::getStatus, AgentStepStatus.FAILED)
				.set(AgentStep::getErrorMessage, errorMessage)
				.set(AgentStep::getFinishedAt, LocalDateTime.now()));
		running.forEach(step -> publishStep(step.getId()));
	}

	private void publishStep(Long stepId) {
		AgentStep step = agentStepMapper.selectById(stepId);
		if (step != null) events.stepUpdate(step);
	}

	public List<AgentStep> getStepsForRun(Long runId) {
		return agentStepMapper.selectList(new LambdaQueryWrapper<AgentStep>()
				.eq(AgentStep::getRunId, runId)
				.orderByAsc(AgentStep::getStepOrder));
	}
}
