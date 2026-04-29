package com.luna.wallpaper.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.convert.DurationStyle;

@ConfigurationProperties(prefix = "app.mail")
public record MailProperties(String enabled, String host, String port, String username, String password,
		String smtpAuth, String smtpStarttls, String smtpSsl, String from, String frontendBaseUrl,
		String passwordResetTokenTtl) {

	private static final Duration DEFAULT_PASSWORD_RESET_TOKEN_TTL = Duration.ofMinutes(30);

	public Duration safePasswordResetTokenTtl() {
		try {
			Duration value = passwordResetTokenTtl == null || passwordResetTokenTtl.isBlank()
					? DEFAULT_PASSWORD_RESET_TOKEN_TTL
					: DurationStyle.detectAndParse(passwordResetTokenTtl.trim());
			return value.isNegative() || value.isZero() ? DEFAULT_PASSWORD_RESET_TOKEN_TTL : value;
		}
		catch (RuntimeException ex) {
			return DEFAULT_PASSWORD_RESET_TOKEN_TTL;
		}
	}

	public boolean safeEnabled() {
		return Boolean.parseBoolean(enabled);
	}

	public String safeFrom() {
		return from == null || from.isBlank() ? "no-reply@example.local" : from.trim();
	}

	public String safeHost() {
		return host == null || host.isBlank() ? "localhost" : host.trim();
	}

	public int safePort() {
		try {
			int value = Integer.parseInt(port == null || port.isBlank() ? "25" : port.trim());
			return value <= 0 ? 25 : value;
		}
		catch (NumberFormatException ex) {
			return 25;
		}
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

	public boolean safeSmtpAuth() {
		return Boolean.parseBoolean(smtpAuth);
	}

	public boolean safeSmtpStarttls() {
		return Boolean.parseBoolean(smtpStarttls);
	}

	public boolean safeSmtpSsl() {
		return Boolean.parseBoolean(smtpSsl);
	}
}
