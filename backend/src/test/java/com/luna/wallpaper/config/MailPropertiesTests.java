package com.luna.wallpaper.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import org.junit.jupiter.api.Test;

class MailPropertiesTests {

	@Test
	void invalidMailTransportValuesFallBackWithoutFailing() {
		MailProperties properties = new MailProperties("true", " ", "not-a-port", null, null,
				"not-a-boolean", "not-a-boolean", " ", " http://localhost/ ", "bad-duration");

		assertThat(properties.safeEnabled()).isTrue();
		assertThat(properties.safeHost()).isEqualTo("localhost");
		assertThat(properties.safePort()).isEqualTo(25);
		assertThat(properties.safeFrom()).isEqualTo("no-reply@example.local");
		assertThat(properties.safeFrontendBaseUrl()).isEqualTo("http://localhost");
		assertThat(properties.safePasswordResetTokenTtl()).isEqualTo(Duration.ofMinutes(30));
		assertThat(properties.safeSmtpAuth()).isFalse();
		assertThat(properties.safeSmtpStarttls()).isFalse();
	}
}
