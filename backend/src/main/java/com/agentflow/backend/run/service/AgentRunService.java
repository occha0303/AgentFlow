package com.agentflow.backend.run.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.agentflow.backend.run.mapper.AgentRunMapper;
import com.agentflow.backend.run.messaging.AgentRunExecutionProducer;
import com.agentflow.backend.run.model.AgentRun;
import com.agentflow.backend.run.model.AgentRunStatus;
import com.agentflow.backend.step.model.AgentStep;
import com.agentflow.backend.step.service.AgentStepService;
import com.agentflow.backend.task.mapper.AgentTaskMapper;
import com.agentflow.backend.task.model.AgentTask;

@Service
public class AgentRunService {

	private static final Logger logger = LoggerFactory.getLogger(AgentRunService.class);

	private final AgentTaskMapper agentTaskMapper;
	private final AgentRunMapper agentRunMapper;
	private final AgentRunExecutionProducer agentRunExecutionProducer;
	private final AgentStepService agentStepService;

	public AgentRunService(AgentTaskMapper agentTaskMapper, AgentRunMapper agentRunMapper,
			AgentRunExecutionProducer agentRunExecutionProducer, AgentStepService agentStepService) {
		this.agentTaskMapper = agentTaskMapper;
		this.agentRunMapper = agentRunMapper;
		this.agentRunExecutionProducer = agentRunExecutionProducer;
		this.agentStepService = agentStepService;
	}

	public AgentRun requestRun(Long taskId) {
		if (agentTaskMapper.selectById(taskId) == null) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Task not found");
		}

		AgentRun run = new AgentRun();
		run.setTaskId(taskId);
		run.setStatus(AgentRunStatus.QUEUED);
		run.setCreatedAt(LocalDateTime.now());
		agentRunMapper.insert(run);

		try {
			agentRunExecutionProducer.send(run.getRunId());
		} catch (RuntimeException exception) {
			markQueuedRunAsFailed(run.getRunId(), "Agent run could not be queued");
			throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
					"Agent run could not be queued", exception);
		}

		return run;
	}

	public List<AgentStep> getSteps(Long runId) {
		getRun(runId);
		return agentStepService.getStepsForRun(runId);
	}

	public AgentRun getRun(Long runId) {
		AgentRun run = agentRunMapper.selectById(runId);

		if (run == null) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Agent run not found");
		}

		return run;
	}

	public void executeRun(Long runId) {
		AgentRun run = agentRunMapper.selectById(runId);

		if (run == null) {
			logger.error("Ignoring agent run execution message because run {} does not exist", runId);
			return;
		}

		if (run.getStatus() == AgentRunStatus.COMPLETED || run.getStatus() == AgentRunStatus.FAILED) {
			logger.info("Ignoring duplicate agent run execution message for terminal run {} with status {}", runId,
					run.getStatus());
			return;
		}

		if (run.getStatus() == AgentRunStatus.RUNNING) {
			logger.info("Ignoring duplicate agent run execution message for already running run {}", runId);
			return;
		}

		if (run.getStatus() != AgentRunStatus.QUEUED) {
			logger.warn("Ignoring agent run execution message for run {} with unexpected status {}", runId,
					run.getStatus());
			return;
		}

		int startedRows = agentRunMapper.update(null, new LambdaUpdateWrapper<AgentRun>()
				.eq(AgentRun::getRunId, runId)
				.eq(AgentRun::getStatus, AgentRunStatus.QUEUED)
				.set(AgentRun::getStatus, AgentRunStatus.RUNNING)
				.set(AgentRun::getStartedAt, LocalDateTime.now()));

		if (startedRows == 0) {
			logger.info("Agent run {} was already claimed by another consumer", runId);
			return;
		}

		AgentStep currentStep = null;
		try {
			AgentTask task = agentTaskMapper.selectById(run.getTaskId());

			if (task == null) {
				currentStep = agentStepService.startStep(runId, 1, "PLAN", "Load the related task");
				String errorMessage = "The related task no longer exists";
				agentStepService.failStep(currentStep.getId(), errorMessage);
				finishRun(runId, AgentRunStatus.FAILED, errorMessage);
				return;
			}

			currentStep = agentStepService.startStep(runId, 1, "PLAN", "Prepare the execution plan");
			Thread.sleep(300);
			agentStepService.completeStep(currentStep.getId(), "Execution plan prepared");

			currentStep = agentStepService.startStep(runId, 2, "EXECUTE", task.getTitle());
			Thread.sleep(3000);

			if (task.getTitle().toLowerCase(Locale.ROOT).contains("fail")) {
				String errorMessage = "Task title contains 'fail'";
				agentStepService.failStep(currentStep.getId(), errorMessage);
				finishRun(runId, AgentRunStatus.FAILED, errorMessage);
				return;
			}

			agentStepService.completeStep(currentStep.getId(), "Simulated task execution completed");

			currentStep = agentStepService.startStep(runId, 3, "FINALIZE", "Finalize the execution result");
			Thread.sleep(300);
			agentStepService.completeStep(currentStep.getId(), "Execution result finalized");
			finishRun(runId, AgentRunStatus.COMPLETED, null);
		} catch (InterruptedException exception) {
			Thread.currentThread().interrupt();
			logger.warn("Agent run {} was interrupted", runId, exception);
			String errorMessage = "Agent run was interrupted";
			if (currentStep != null) {
				agentStepService.failStep(currentStep.getId(), errorMessage);
			}
			finishRun(runId, AgentRunStatus.FAILED, errorMessage);
		}
	}

	private void markQueuedRunAsFailed(Long runId, String errorMessage) {
		agentRunMapper.update(null, new LambdaUpdateWrapper<AgentRun>()
				.eq(AgentRun::getRunId, runId)
				.eq(AgentRun::getStatus, AgentRunStatus.QUEUED)
				.set(AgentRun::getStatus, AgentRunStatus.FAILED)
				.set(AgentRun::getErrorMessage, errorMessage)
				.set(AgentRun::getFinishedAt, LocalDateTime.now()));
	}

	private void finishRun(Long runId, AgentRunStatus status, String errorMessage) {
		LambdaUpdateWrapper<AgentRun> updateWrapper = new LambdaUpdateWrapper<AgentRun>()
				.eq(AgentRun::getRunId, runId)
				.eq(AgentRun::getStatus, AgentRunStatus.RUNNING)
				.set(AgentRun::getStatus, status)
				.set(AgentRun::getFinishedAt, LocalDateTime.now());

		if (status == AgentRunStatus.FAILED) {
			updateWrapper.set(AgentRun::getErrorMessage, errorMessage);
		}

		int updatedRows = agentRunMapper.update(null, updateWrapper);

		if (updatedRows == 0) {
			logger.info("Agent run {} was already finished; final update was ignored", runId);
		}
	}
}
