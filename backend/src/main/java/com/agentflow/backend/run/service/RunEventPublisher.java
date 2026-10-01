package com.agentflow.backend.run.service;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.agentflow.backend.approval.model.AgentApproval;
import com.agentflow.backend.run.model.AgentRun;
import com.agentflow.backend.run.model.AgentRunStatus;
import com.agentflow.backend.step.model.AgentStep;

/** Active connections only. MySQL, not this map, owns every business state. */
@Component
public class RunEventPublisher {

	private static final Logger logger = LoggerFactory.getLogger(RunEventPublisher.class);
	private static final long EMITTER_TIMEOUT_MS = 30 * 60 * 1000L;
	private final Map<Long, CopyOnWriteArrayList<SseEmitter>> connections = new ConcurrentHashMap<>();
	private final Object emissionLock = new Object();

	public SseEmitter subscribe(Long runId, Supplier<RunSnapshot> snapshotSupplier) {
		SseEmitter emitter = new SseEmitter(EMITTER_TIMEOUT_MS);
		emitter.onCompletion(() -> remove(runId, emitter));
		emitter.onTimeout(() -> {
			remove(runId, emitter);
			emitter.complete();
		});
		emitter.onError(error -> remove(runId, emitter));
		synchronized (emissionLock) {
			connections.computeIfAbsent(runId, ignored -> new CopyOnWriteArrayList<>()).add(emitter);
			try {
				RunSnapshot snapshot = snapshotSupplier.get();
				send(emitter, "snapshot", snapshot);
				if (terminal(snapshot.runStatus())) {
					remove(runId, emitter);
					emitter.complete();
				}
			} catch (RuntimeException exception) {
				remove(runId, emitter);
				throw exception;
			}
		}
		return emitter;
	}

	public void runStatus(AgentRun run) {
		afterCommit(() -> publish(run.getRunId(), "run-status",
				Map.of("runId", run.getRunId(), "status", run.getStatus().name())));
	}

	public void stepUpdate(AgentStep step) {
		// AgentStep contains summaries, not full browser pages or RAG chunks.
		afterCommit(() -> publish(step.getRunId(), "step-update", step));
	}

	public void approvalUpdate(AgentApproval approval) {
		afterCommit(() -> publish(approval.getRunId(), "approval-update",
				new ApprovalEvent(approval.getId(), approval.getRunId(), approval.getStatus().name())));
	}

	public void resultReady(AgentRun run) {
		afterCommit(() -> {
			if (run.getStatus() == AgentRunStatus.FAILED) {
				publish(run.getRunId(), "error", new ErrorEvent(run.getRunId(), run.getErrorMessage()));
			}
			publish(run.getRunId(), "result-ready", new ResultEvent(run.getRunId(),
					run.getStatus().name(), run.getResultText(), run.getErrorMessage()));
			closeRun(run.getRunId());
		});
	}

	/** Transport-only comment detects closed tabs and keeps idle approval streams alive. */
	@Scheduled(fixedDelay = 25_000)
	public void keepConnectionsAlive() {
		synchronized (emissionLock) {
			connections.forEach((runId, emitters) -> {
				for (SseEmitter emitter : emitters) {
					try {
						emitter.send(SseEmitter.event().comment("keepalive"));
					} catch (Exception exception) {
						remove(runId, emitter);
					}
				}
			});
		}
	}

	private void afterCommit(Runnable action) {
		if (TransactionSynchronizationManager.isActualTransactionActive()
				&& TransactionSynchronizationManager.isSynchronizationActive()) {
			TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
				@Override
				public void afterCommit() { action.run(); }
			});
		} else {
			action.run();
		}
	}

	private void publish(Long runId, String name, Object payload) {
		synchronized (emissionLock) {
			List<SseEmitter> emitters = connections.get(runId);
			if (emitters == null) return;
			for (SseEmitter emitter : emitters) {
				try {
					send(emitter, name, payload);
				} catch (RuntimeException exception) {
					logger.debug("SSE emitter disconnected for run {}", runId, exception);
					remove(runId, emitter);
				}
			}
		}
	}

	private void send(SseEmitter emitter, String name, Object payload) {
		try {
			emitter.send(SseEmitter.event().name(name).data(payload));
		} catch (IOException exception) {
			throw new IllegalStateException("SSE send failed", exception);
		}
	}

	private void closeRun(Long runId) {
		List<SseEmitter> emitters = connections.remove(runId);
		if (emitters != null) emitters.forEach(SseEmitter::complete);
	}

	private void remove(Long runId, SseEmitter emitter) {
		connections.computeIfPresent(runId, (ignored, emitters) -> {
			emitters.remove(emitter);
			return emitters.isEmpty() ? null : emitters;
		});
	}

	private boolean terminal(AgentRunStatus status) {
		return status == AgentRunStatus.COMPLETED || status == AgentRunStatus.FAILED;
	}

	public record RunSnapshot(Long runId, AgentRunStatus runStatus, String resultText,
			String errorMessage, Long retryOfRunId, List<AgentStep> steps,
			List<ApprovalEvent> approvals) { }
	public record ApprovalEvent(Long approvalId, Long runId, String status) { }
	public record ResultEvent(Long runId, String status, String resultText, String errorMessage) { }
	public record ErrorEvent(Long runId, String message) { }
}
