package com.agentflow.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class AgentflowBackendApplication {

	public static void main(String[] args) {
		SpringApplication.run(AgentflowBackendApplication.class, args);
	}

}
