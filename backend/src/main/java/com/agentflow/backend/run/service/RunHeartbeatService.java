package com.agentflow.backend.run.service;

import java.time.LocalDateTime;

import org.springframework.stereotype.Service;

import com.agentflow.backend.run.mapper.AgentRunMapper;
import com.agentflow.backend.run.model.AgentRun;
import com.agentflow.backend.run.model.AgentRunStatus;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;

@Service
public class RunHeartbeatService {

	private final AgentRunMapper runMapper;

	public RunHeartbeatService(AgentRunMapper runMapper) {
		this.runMapper = runMapper;
	}

	/** Waiting approvals and terminal runs are intentionally never heartbeated. */
	public void touchRunning(Long runId) {
		runMapper.update(null, new LambdaUpdateWrapper<AgentRun>()
				.eq(AgentRun::getRunId, runId)
				.eq(AgentRun::getStatus, AgentRunStatus.RUNNING)
				.set(AgentRun::getHeartbeatAt, LocalDateTime.now()));
	}
}
