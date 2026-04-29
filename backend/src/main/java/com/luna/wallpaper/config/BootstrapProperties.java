package com.luna.wallpaper.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.bootstrap")
public record BootstrapProperties(String adminPassword, Boolean demoUsersEnabled) {

	public boolean hasAdminPassword() {
		return adminPassword != null && !adminPassword.isBlank();
	}

	public String requiredAdminPassword() {
		String password = adminPassword == null ? "" : adminPassword.trim();
		if (password.length() < 8) {
			throw new IllegalStateException("APP_BOOTSTRAP_ADMIN_PASSWORD must be at least 8 characters");
		}
		return password;
	}

	public boolean safeDemoUsersEnabled() {
		return demoUsersEnabled == null || demoUsersEnabled;
	}
}
