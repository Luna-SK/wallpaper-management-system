package com.luna.wallpaper.rbac;

public enum UserStatus {
	ACTIVE,
	DISABLED;

	static UserStatus parseOrDefault(String value, UserStatus defaultStatus) {
		if (value == null || value.isBlank()) {
			return defaultStatus;
		}
		try {
			return valueOf(value.trim().toUpperCase());
		}
		catch (IllegalArgumentException ex) {
			throw new IllegalArgumentException("用户状态不正确", ex);
		}
	}

	boolean isActive() {
		return this == ACTIVE;
	}
}
