package com.agentflow.backend.run.service;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.agentflow.backend.run.service.RunExecutionLockService.Lease;

/** Keeps the lease and a coarse MySQL heartbeat alive during long tool calls. */
@Component
public class RunExecutionWatchdog {

	private static final Logger logger = LoggerFactory.getLogger(RunExecutionWatchdog.class);
	private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2, task -> {
		Thread thread = new Thread(task, "agentflow-run-heartbeat");
		thread.setDaemon(true);
		return thread;
	});
	private final RunExecutionLockService lockService;
	private final RunHeartbeatService heartbeatService;

	public RunExecutionWatchdog(RunExecutionLockService lockService, RunHeartbeatService heartbeatService) {
		this.lockService = lockService;
		this.heartbeatService = heartbeatService;
	}

	public ScheduledFuture<?> start(Long runId, Lease lease) {
		long interval = lockService.renewalIntervalSeconds();
		return scheduler.scheduleAtFixedRate(() -> {
			try {
				if (lockService.renew(lease)) {
					heartbeatService.touchRunning(runId);
				} else {
					logger.error("Run {} execution lock ownership was lost", runId);
				}
			} catch (RuntimeException exception) {
				logger.warn("Could not refresh execution lease/heartbeat for run {}", runId, exception);
			}
		}, interval, interval, TimeUnit.SECONDS);
	}

	@PreDestroy
	public void shutdown() {
		scheduler.shutdownNow();
	}
}
