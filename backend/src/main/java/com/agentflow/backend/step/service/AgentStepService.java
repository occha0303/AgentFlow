package com.agentflow.backend.step.service;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;

import com.agentflow.backend.step.mapper.AgentStepMapper;
import com.agentflow.backend.step.model.AgentStep;
import com.agentflow.backend.step.model.AgentStepStatus;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;

@Service
public class AgentStepService {

	private final AgentStepMapper agentStepMapper;

	public AgentStepService(AgentStepMapper agentStepMapper) {
		this.agentStepMapper = agentStepMapper;
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
		return step;
	}

	public void completeStep(Long stepId, String outputSummary) {
		agentStepMapper.update(null, new LambdaUpdateWrapper<AgentStep>()
				.eq(AgentStep::getId, stepId)
				.eq(AgentStep::getStatus, AgentStepStatus.RUNNING)
				.set(AgentStep::getStatus, AgentStepStatus.COMPLETED)
				.set(AgentStep::getOutputSummary, outputSummary)
				.set(AgentStep::getFinishedAt, LocalDateTime.now()));
	}

	public void failStep(Long stepId, String errorMessage) {
		agentStepMapper.update(null, new LambdaUpdateWrapper<AgentStep>()
				.eq(AgentStep::getId, stepId)
				.eq(AgentStep::getStatus, AgentStepStatus.RUNNING)
				.set(AgentStep::getStatus, AgentStepStatus.FAILED)
				.set(AgentStep::getErrorMessage, errorMessage)
				.set(AgentStep::getFinishedAt, LocalDateTime.now()));
	}

	public List<AgentStep> getStepsForRun(Long runId) {
		return agentStepMapper.selectList(new LambdaQueryWrapper<AgentStep>()
				.eq(AgentStep::getRunId, runId)
				.orderByAsc(AgentStep::getStepOrder));
	}
}
