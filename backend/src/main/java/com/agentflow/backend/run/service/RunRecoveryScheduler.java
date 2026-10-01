package com.agentflow.backend.run.service;

import java.time.LocalDateTime;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.agentflow.backend.run.mapper.AgentRunMapper;
import com.agentflow.backend.run.model.AgentRun;
import com.agentflow.backend.run.model.AgentRunStatus;
import com.agentflow.backend.step.service.AgentStepService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;

/** Stale RUNNING runs become FAILED; side effects are never replayed automatically. */
@Component
public class RunRecoveryScheduler {

	private static final Logger logger = LoggerFactory.getLogger(RunRecoveryScheduler.class);
	private static final String RUN_ERROR = "Execution interrupted or timed out; manual retry is required.";
	private static final String STEP_ERROR = "Execution interrupted or timed out.";

	private final AgentRunMapper runMapper;
	private final AgentStepService stepService;
	private final RunExecutionLockService lockService;
	private final long staleSeconds;
	private final TransactionTemplate transactionTemplate;

	public RunRecoveryScheduler(AgentRunMapper runMapper, AgentStepService stepService,
			RunExecutionLockService lockService, PlatformTransactionManager transactionManager,
			@Value("${agent.run.stale-seconds:600}") long staleSeconds) {
		if (staleSeconds < 30) {
			throw new IllegalArgumentException("Agent run stale threshold must be at least 30 seconds");
		}
		this.runMapper = runMapper;
		this.stepService = stepService;
		this.lockService = lockService;
		this.staleSeconds = staleSeconds;
		this.transactionTemplate = new TransactionTemplate(transactionManager);
	}

	@Scheduled(fixedDelay = 60_000, initialDelay = 60_000)
	public void recoverStaleRuns() {
		LocalDateTime cutoff = LocalDateTime.now().minusSeconds(staleSeconds);
		List<AgentRun> stale = runMapper.selectList(new LambdaQueryWrapper<AgentRun>()
				.eq(AgentRun::getStatus, AgentRunStatus.RUNNING)
				.and(query -> query.lt(AgentRun::getHeartbeatAt, cutoff)
						.or(nullHeartbeat -> nullHeartbeat.isNull(AgentRun::getHeartbeatAt)
								.lt(AgentRun::getStartedAt, cutoff)))
				.orderByAsc(AgentRun::getHeartbeatAt));
		for (AgentRun run : stale) {
			try {
				if (lockService.isHeld(run.getRunId())) {
					continue; // An active lease may be in a long external call.
				}
				LambdaUpdateWrapper<AgentRun> update = new LambdaUpdateWrapper<AgentRun>()
						.eq(AgentRun::getRunId, run.getRunId())
						.eq(AgentRun::getStatus, AgentRunStatus.RUNNING)
						.set(AgentRun::getStatus, AgentRunStatus.FAILED)
						.set(AgentRun::getErrorMessage, RUN_ERROR)
						.set(AgentRun::getFinishedAt, LocalDateTime.now());
				if (run.getHeartbeatAt() == null) {
					update.isNull(AgentRun::getHeartbeatAt).lt(AgentRun::getStartedAt, cutoff);
				} else {
					update.eq(AgentRun::getHeartbeatAt, run.getHeartbeatAt());
				}
				Boolean recovered = transactionTemplate.execute(status -> {
					if (runMapper.update(null, update) != 1) return false;
					stepService.failRunningStepsForRun(run.getRunId(), STEP_ERROR);
					return true;
				});
				if (Boolean.TRUE.equals(recovered)) {
					logger.warn("Stale run {} recovered as FAILED", run.getRunId());
				}
			} catch (RuntimeException exception) {
				logger.warn("Could not recover stale run {}", run.getRunId(), exception);
			}
		}
	}
}
