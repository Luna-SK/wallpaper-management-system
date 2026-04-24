package com.luna.wallpaper.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.security")
public record SecurityProperties(String allowedOrigins, String developmentToken) {

	public String[] allowedOriginArray() {
		return allowedOrigins == null || allowedOrigins.isBlank()
				? new String[0]
				: allowedOrigins.split("\\s*,\\s*");
	}

	public boolean hasDevelopmentToken() {
		return developmentToken != null && !developmentToken.isBlank();
	}
}
