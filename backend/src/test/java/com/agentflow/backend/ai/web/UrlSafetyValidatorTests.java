package com.agentflow.backend.ai.web;

import static org.junit.jupiter.api.Assertions.assertThrows;

import java.net.URI;

import org.junit.jupiter.api.Test;

class UrlSafetyValidatorTests {

	@Test
	void blocksNonHttpProtocol() {
		assertThrows(IllegalArgumentException.class,
				() -> UrlSafetyValidator.validatePublicHttpUrl(URI.create("file:///etc/passwd")));
	}

	@Test
	void blocksLoopbackAddresses() {
		assertThrows(IllegalArgumentException.class,
				() -> UrlSafetyValidator.validatePublicHttpUrl(URI.create("http://127.0.0.1:8080/api/tasks")));
		assertThrows(IllegalArgumentException.class,
				() -> UrlSafetyValidator.validatePublicHttpUrl(URI.create("http://[::1]/")));
	}

	@Test
	void blocksPrivateIpv4Address() {
		assertThrows(IllegalArgumentException.class,
				() -> UrlSafetyValidator.validatePublicHttpUrl(URI.create("http://192.168.1.10/internal")));
	}
}
