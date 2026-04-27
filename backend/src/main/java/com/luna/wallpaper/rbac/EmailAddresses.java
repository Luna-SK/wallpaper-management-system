package com.luna.wallpaper.rbac;

import java.util.Locale;
import java.util.regex.Pattern;

final class EmailAddresses {

	private static final int MAX_EMAIL_LENGTH = 180;
	private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");

	private EmailAddresses() {
	}

	static String normalizeNullable(String email) {
		if (email == null) {
			return null;
		}
		String normalized = email.trim().toLowerCase(Locale.ROOT);
		if (normalized.isBlank()) {
			return null;
		}
		if (normalized.length() > MAX_EMAIL_LENGTH) {
			throw new IllegalArgumentException("邮箱不能超过 180 个字符");
		}
		if (!EMAIL_PATTERN.matcher(normalized).matches()) {
			throw new IllegalArgumentException("邮箱格式不正确");
		}
		return normalized;
	}

	static String normalizeRequired(String email) {
		String normalized = normalizeNullable(email);
		if (normalized == null) {
			throw new IllegalArgumentException("请输入邮箱");
		}
		return normalized;
	}

	static void requireAvailable(AppUserMapper users, String email, String currentUserId) {
		if (email == null) {
			return;
		}
		boolean occupied = currentUserId == null ? users.hasEmail(email) : users.hasEmailExcludingId(email, currentUserId);
		if (occupied) {
			throw new IllegalArgumentException("邮箱已被使用");
		}
	}
}
