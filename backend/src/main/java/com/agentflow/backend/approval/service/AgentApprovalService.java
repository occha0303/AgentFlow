package com.agentflow.backend.approval.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.agentflow.backend.approval.mapper.AgentApprovalMapper;
import com.agentflow.backend.ai.browser.BrowserActionPayload;
import com.agentflow.backend.approval.model.AgentApproval;
import com.agentflow.backend.approval.model.AgentApprovalStatus;
import com.agentflow.backend.run.mapper.AgentRunMapper;
import com.agentflow.backend.run.messaging.AgentRunExecutionProducer;
import com.agentflow.backend.run.model.AgentRun;
import com.agentflow.backend.run.model.AgentRunStatus;
import com.agentflow.backend.run.service.RunEventPublisher;
import com.agentflow.backend.step.model.AgentStep;
import com.agentflow.backend.step.service.AgentStepService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class AgentApprovalService {

	private final AgentApprovalMapper approvalMapper;
	private final AgentRunMapper runMapper;
	private final AgentStepService stepService;
	private final AgentRunExecutionProducer producer;
	private final RunEventPublisher events;
	private final ObjectMapper objectMapper = new ObjectMapper();

	public AgentApprovalService(AgentApprovalMapper approvalMapper, AgentRunMapper runMapper,
			AgentStepService stepService, AgentRunExecutionProducer producer, RunEventPublisher events) {
		this.approvalMapper = approvalMapper;
		this.runMapper = runMapper;
		this.stepService = stepService;
		this.producer = producer;
		this.events = events;
	}

	@Transactional
	public AgentApproval prepareMockPublish(Long runId, int stepOrder, String target, String content) {
		if (!"demo platform".equalsIgnoreCase(target == null ? "" : target.trim())) {
			throw new IllegalArgumentException("MockWriteTool only supports demo platform");
		}
		if (content == null || content.isBlank() || content.length() > 10_000) {
			throw new IllegalArgumentException("MockWriteTool content must contain 1 to 10000 characters");
		}
		return prepareAction(runId, stepOrder, "PUBLISH", "Publish content to demo platform",
				Map.of("target", "demo platform", "content", content));
	}

	@Transactional
	public AgentApproval prepareBrowserAction(Long runId, int stepOrder, BrowserActionPayload payload) {
		String actionType = "BROWSER_" + payload.browserAction().name();
		String summary = payload.browserAction().name() + " " + payload.target().tag()
				+ " " + shorten(payload.target().text().isBlank() ? payload.target().ariaLabel()
						: payload.target().text(), 80) + " on " + shorten(payload.url(), 180);
		return prepareAction(runId, stepOrder, actionType, summary, payload);
	}

	private AgentApproval prepareAction(Long runId, int stepOrder, String actionType,
			String summary, Object payload) {
		AgentRun run = runMapper.selectById(runId);
		if (run == null || run.getStatus() != AgentRunStatus.RUNNING || !getApprovalsForRun(runId).isEmpty()) {
			throw new IllegalStateException("Cannot prepare another action for this run");
		}
		AgentStep executeStep = stepService.getStepsForRun(runId).stream()
				.filter(step -> "EXECUTE".equals(step.getStepType()))
				.findFirst()
				.orElseThrow(() -> new IllegalStateException("EXECUTE step does not exist"));

		AgentStep approvalStep = stepService.startStep(runId, stepOrder, "APPROVAL", summary);
		AgentApproval approval = new AgentApproval();
		approval.setRunId(runId);
		approval.setStepId(approvalStep.getId());
		approval.setActionType(actionType);
		approval.setActionSummary(summary);
		try {
			approval.setActionPayload(objectMapper.writeValueAsString(payload));
		} catch (JsonProcessingException exception) {
			throw new IllegalStateException("Could not prepare action payload", exception);
		}
		approval.setStatus(AgentApprovalStatus.PENDING);
		approval.setCreatedAt(LocalDateTime.now());
		approvalMapper.insert(approval);
		stepService.waitForApproval(approvalStep.getId(), "Waiting for approval");
		stepService.waitForApproval(executeStep.getId(), "Action prepared; waiting for approval");
		int updated = runMapper.update(null, new LambdaUpdateWrapper<AgentRun>()
				.eq(AgentRun::getRunId, runId)
				.eq(AgentRun::getStatus, AgentRunStatus.RUNNING)
				.set(AgentRun::getStatus, AgentRunStatus.WAITING_APPROVAL));
		if (updated != 1) {
			throw new IllegalStateException("Run changed while preparing approval");
		}
		events.approvalUpdate(approval);
		events.runStatus(runMapper.selectById(runId));
		return approval;
	}

	public List<AgentApproval> getApprovalsForRun(Long runId) {
		if (runMapper.selectById(runId) == null) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Agent run not found");
		}
		return approvalMapper.selectList(new LambdaQueryWrapper<AgentApproval>()
				.eq(AgentApproval::getRunId, runId)
				.orderByDesc(AgentApproval::getId));
	}

	public AgentApproval getApproval(Long approvalId) {
		AgentApproval approval = approvalMapper.selectById(approvalId);
		if (approval == null) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Approval not found");
		}
		return approval;
	}

	public AgentApproval approve(Long approvalId) {
		AgentApproval approval = getApproval(approvalId);
		AgentRun run = runMapper.selectById(approval.getRunId());
		if (run == null || run.getStatus() != AgentRunStatus.WAITING_APPROVAL) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "Run is not waiting for approval");
		}
		int updated = approvalMapper.update(null, new LambdaUpdateWrapper<AgentApproval>()
				.eq(AgentApproval::getId, approvalId)
				.eq(AgentApproval::getStatus, AgentApprovalStatus.PENDING)
				.set(AgentApproval::getStatus, AgentApprovalStatus.APPROVED)
				.set(AgentApproval::getDecidedAt, LocalDateTime.now()));
		if (updated != 1) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "Approval has already been decided");
		}
		try {
			producer.send(approval.getRunId());
		} catch (RuntimeException exception) {
			approvalMapper.update(null, new LambdaUpdateWrapper<AgentApproval>()
					.eq(AgentApproval::getId, approvalId)
					.eq(AgentApproval::getStatus, AgentApprovalStatus.APPROVED)
					.set(AgentApproval::getStatus, AgentApprovalStatus.PENDING)
					.set(AgentApproval::getDecidedAt, null));
			throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
					"Could not queue approval resume; please try again", exception);
		}
		AgentApproval decided = getApproval(approvalId);
		events.approvalUpdate(decided);
		return decided;
	}

	@Transactional
	public AgentApproval reject(Long approvalId, String reason) {
		AgentApproval approval = getApproval(approvalId);
		AgentRun run = runMapper.selectById(approval.getRunId());
		if (run == null || run.getStatus() != AgentRunStatus.WAITING_APPROVAL) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "Run is not waiting for approval");
		}
		int updated = approvalMapper.update(null, new LambdaUpdateWrapper<AgentApproval>()
				.eq(AgentApproval::getId, approvalId)
				.eq(AgentApproval::getStatus, AgentApprovalStatus.PENDING)
				.set(AgentApproval::getStatus, AgentApprovalStatus.REJECTED)
				.set(AgentApproval::getDecidedAt, LocalDateTime.now())
				.set(AgentApproval::getDecisionReason, shorten(reason == null ? "" : reason.trim(), 500)));
		if (updated != 1) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "Approval has already been decided");
		}
		String error = "Action rejected by user" + (reason == null || reason.isBlank() ? "" : ": " + shorten(reason.trim(), 475));
		stepService.failWaitingStep(approval.getStepId(), error);
		stepService.getStepsForRun(approval.getRunId()).stream()
				.filter(step -> "EXECUTE".equals(step.getStepType()))
				.findFirst()
				.ifPresent(step -> stepService.failWaitingStep(step.getId(), error));
		int runUpdated = runMapper.update(null, new LambdaUpdateWrapper<AgentRun>()
				.eq(AgentRun::getRunId, approval.getRunId())
				.eq(AgentRun::getStatus, AgentRunStatus.WAITING_APPROVAL)
				.set(AgentRun::getStatus, AgentRunStatus.FAILED)
				.set(AgentRun::getErrorMessage, error)
				.set(AgentRun::getFinishedAt, LocalDateTime.now()));
		if (runUpdated != 1) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "Run changed during rejection");
		}
		AgentApproval decided = getApproval(approvalId);
		events.approvalUpdate(decided);
		AgentRun failed = runMapper.selectById(approval.getRunId());
		events.runStatus(failed);
		events.resultReady(failed);
		return decided;
	}

	private String shorten(String text, int limit) {
		return text.length() <= limit ? text : text.substring(0, limit - 3) + "...";
	}
}
