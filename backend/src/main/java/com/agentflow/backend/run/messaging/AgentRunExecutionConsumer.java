package com.agentflow.backend.run.messaging;

import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.agentflow.backend.run.service.AgentRunService;

@Component
@RocketMQMessageListener(topic = AgentRunExecutionProducer.TOPIC, consumerGroup = "agentflow-run-execution-consumer")
public class AgentRunExecutionConsumer implements RocketMQListener<AgentRunExecutionMessage> {

	private static final Logger logger = LoggerFactory.getLogger(AgentRunExecutionConsumer.class);

	private final AgentRunService agentRunService;

	public AgentRunExecutionConsumer(AgentRunService agentRunService) {
		this.agentRunService = agentRunService;
	}

	@Override
	public void onMessage(AgentRunExecutionMessage message) {
		if (message == null || message.getRunId() == null) {
			logger.warn("Ignoring agent run execution message without a runId");
			return;
		}

		agentRunService.executeRun(message.getRunId());
	}
}
