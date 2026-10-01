package com.agentflow.backend.run.messaging;

import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.concurrent.ScheduledFuture;

import com.agentflow.backend.run.service.AgentRunService;
import com.agentflow.backend.run.service.RunExecutionLockService;
import com.agentflow.backend.run.service.RunExecutionLockService.Lease;
import com.agentflow.backend.run.service.RunExecutionWatchdog;

@Component
@RocketMQMessageListener(topic = AgentRunExecutionProducer.TOPIC, consumerGroup = "agentflow-run-execution-consumer")
public class AgentRunExecutionConsumer implements RocketMQListener<AgentRunExecutionMessage> {

	private static final Logger logger = LoggerFactory.getLogger(AgentRunExecutionConsumer.class);

	private final AgentRunService agentRunService;
	private final RunExecutionLockService lockService;
	private final RunExecutionWatchdog watchdog;

	public AgentRunExecutionConsumer(AgentRunService agentRunService, RunExecutionLockService lockService,
			RunExecutionWatchdog watchdog) {
		this.agentRunService = agentRunService;
		this.lockService = lockService;
		this.watchdog = watchdog;
	}

	@Override
	public void onMessage(AgentRunExecutionMessage message) {
		if (message == null || message.getRunId() == null) {
			logger.warn("Ignoring agent run execution message without a runId");
			return;
		}

		Long runId = message.getRunId();
		if (!agentRunService.canProcessMessage(runId)) {
			return;
		}
		Optional<Lease> acquired = lockService.tryAcquire(runId);
		if (acquired.isEmpty()) {
			logger.info("Run execution lock already held for run {}", runId);
			return;
		}
		Lease lease = acquired.get();
		logger.info("Run execution lock acquired for run {}", runId);
		ScheduledFuture<?> heartbeat = null;
		try {
			heartbeat = watchdog.start(runId, lease);
			// executeRun retains the MySQL status checks and conditional claims.
			agentRunService.executeRun(runId);
		} finally {
			if (heartbeat != null) heartbeat.cancel(false);
			try {
				lockService.release(lease);
				logger.info("Run execution lock released for run {}", runId);
			} catch (RuntimeException exception) {
				logger.warn("Could not release execution lock for run {}; TTL will expire it", runId, exception);
			}
		}
	}
}
