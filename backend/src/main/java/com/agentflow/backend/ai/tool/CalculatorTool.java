package com.agentflow.backend.ai.tool;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

public class CalculatorTool {

	private final ToolUsageRecorder toolUsageRecorder;

	public CalculatorTool(ToolUsageRecorder toolUsageRecorder) {
		this.toolUsageRecorder = toolUsageRecorder;
	}

	@Tool(description = "Add two numbers. Use this for exact arithmetic instead of estimating mentally.")
	public double add(@ToolParam(description = "The first number") double left,
			@ToolParam(description = "The second number") double right) {
		toolUsageRecorder.record("CalculatorTool");
		return left + right;
	}

	@Tool(description = "Subtract the second number from the first number. Use this for exact arithmetic.")
	public double subtract(@ToolParam(description = "The number to subtract from") double left,
			@ToolParam(description = "The number to subtract") double right) {
		toolUsageRecorder.record("CalculatorTool");
		return left - right;
	}

	@Tool(description = "Multiply two numbers. Use this for exact arithmetic instead of estimating mentally.")
	public double multiply(@ToolParam(description = "The first number") double left,
			@ToolParam(description = "The second number") double right) {
		toolUsageRecorder.record("CalculatorTool");
		return left * right;
	}

	@Tool(description = "Divide the first number by the second number. Use this for exact arithmetic when the divisor is not zero.")
	public double divide(@ToolParam(description = "The dividend") double left,
			@ToolParam(description = "The non-zero divisor") double right) {
		toolUsageRecorder.record("CalculatorTool");
		if (right == 0) {
			throw new IllegalArgumentException("CalculatorTool cannot divide by zero");
		}
		return left / right;
	}
}
