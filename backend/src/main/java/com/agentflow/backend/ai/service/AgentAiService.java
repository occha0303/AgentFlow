package com.agentflow.backend.ai.service;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import com.agentflow.backend.ai.model.AgentAiExecutionResult;
import com.agentflow.backend.ai.tool.CalculatorTool;
import com.agentflow.backend.ai.tool.CurrentTimeTool;
import com.agentflow.backend.ai.tool.ToolUsageRecorder;

@Service
public class AgentAiService {

	private final ChatClient chatClient;

	public AgentAiService(ChatClient.Builder chatClientBuilder) {
		this.chatClient = chatClientBuilder
				.defaultSystem("You are AgentFlow's task execution agent. Use CalculatorTool for exact math and "
						+ "always use CurrentTimeTool for current time or date. Answer directly when no tool is needed. "
						+ "Never invent tool results; use a tool result to produce the final answer.")
				.build();
	}

	public AgentAiExecutionResult execute(String task) {
		ToolUsageRecorder toolUsageRecorder = new ToolUsageRecorder();
		String resultText = chatClient.prompt()
				.user(task)
				.tools(new CalculatorTool(toolUsageRecorder), new CurrentTimeTool(toolUsageRecorder))
				.call()
				.content();
		return new AgentAiExecutionResult(resultText, toolUsageRecorder.getUsedTools());
	}
}
