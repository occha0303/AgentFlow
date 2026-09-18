package com.agentflow.backend.ai.service;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

@Service
public class AgentAiService {

	private final ChatClient chatClient;

	public AgentAiService(ChatClient.Builder chatClientBuilder) {
		this.chatClient = chatClientBuilder
				.defaultSystem("You are a helpful assistant. Answer the user's task directly and clearly.")
				.build();
	}

	public String execute(String task) {
		return chatClient.prompt()
				.user(task)
				.call()
				.content();
	}
}
