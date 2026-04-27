package com.luna.wallpaper.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.mail")
public record MailProperties(boolean enabled, String host, Integer port, String username, String password,
		boolean smtpAuth, boolean smtpStarttls, String from, String frontendBaseUrl, Duration passwordResetTokenTtl) {

	private static final Duration DEFAULT_PASSWORD_RESET_TOKEN_TTL = Duration.ofMinutes(30);

	public Duration safePasswordResetTokenTtl() {
		return passwordResetTokenTtl == null || passwordResetTokenTtl.isNegative() || passwordResetTokenTtl.isZero()
				? DEFAULT_PASSWORD_RESET_TOKEN_TTL
				: passwordResetTokenTtl;
	}

	public String safeFrom() {
		return from == null || from.isBlank() ? "no-reply@example.local" : from.trim();
	}

	public String safeHost() {
		return host == null || host.isBlank() ? "localhost" : host.trim();
	}

	public int safePort() {
		return port == null || port <= 0 ? 25 : port;
	}

	public boolean hasUsername() {
		return username != null && !username.isBlank();
	}

	public String safeFrontendBaseUrl() {
		String baseUrl = frontendBaseUrl == null || frontendBaseUrl.isBlank()
				? "http://localhost:5173"
				: frontendBaseUrl.trim();
		while (baseUrl.endsWith("/")) {
			baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
		}
		return baseUrl;
	}
}
