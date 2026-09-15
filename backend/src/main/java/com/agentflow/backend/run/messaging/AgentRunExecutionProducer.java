package com.agentflow.backend.run.messaging;

import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.stereotype.Component;

@Component
public class AgentRunExecutionProducer {

	public static final String TOPIC = "agent-run-execution";

	private final RocketMQTemplate rocketMQTemplate;

	public AgentRunExecutionProducer(RocketMQTemplate rocketMQTemplate) {
		this.rocketMQTemplate = rocketMQTemplate;
	}

	public void send(Long runId) {
		rocketMQTemplate.convertAndSend(TOPIC, new AgentRunExecutionMessage(runId));
	}
}
