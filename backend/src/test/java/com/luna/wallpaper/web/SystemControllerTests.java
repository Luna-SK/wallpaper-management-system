package com.luna.wallpaper.web;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SystemControllerTests {

	@Test
	void healthEndpointReturnsUpPayload() {
		var response = new SystemController().health();

		assertThat(response.code()).isEqualTo("OK");
		assertThat(response.data())
				.containsEntry("service", "wallpaper-backend")
				.containsEntry("status", "UP")
				.containsKey("time");
	}
}
