package com.luna.wallpaper.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.security")
public record SecurityProperties(String allowedOrigins, String developmentToken, boolean developmentTokenEnabled,
		String jwtSecret, Duration accessTokenTtl, Duration refreshTokenTtl) {

	private static final Duration DEFAULT_ACCESS_TOKEN_TTL = Duration.ofMinutes(15);
	private static final Duration DEFAULT_REFRESH_TOKEN_TTL = Duration.ofDays(7);

	public String[] allowedOriginArray() {
		return allowedOrigins == null || allowedOrigins.isBlank()
				? new String[0]
				: allowedOrigins.split("\\s*,\\s*");
	}

	public boolean hasDevelopmentToken() {
		return developmentTokenEnabled && developmentToken != null && !developmentToken.isBlank();
	}

	public Duration safeAccessTokenTtl() {
		return accessTokenTtl == null || accessTokenTtl.isNegative() || accessTokenTtl.isZero()
				? DEFAULT_ACCESS_TOKEN_TTL
				: accessTokenTtl;
	}

	public Duration safeRefreshTokenTtl() {
		return refreshTokenTtl == null || refreshTokenTtl.isNegative() || refreshTokenTtl.isZero()
				? DEFAULT_REFRESH_TOKEN_TTL
				: refreshTokenTtl;
	}

	public String requiredJwtSecret() {
		if (jwtSecret == null || jwtSecret.isBlank() || jwtSecret.length() < 32) {
			throw new IllegalStateException("APP_SECURITY_JWT_SECRET must be at least 32 characters");
		}
		return jwtSecret;
	}
}
