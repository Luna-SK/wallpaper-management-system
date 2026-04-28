package com.luna.wallpaper.rbac;

import java.time.LocalDateTime;

public final class UserAvatars {
	private UserAvatars() {
	}

	public static String url(AppUser user) {
		if (user == null || user.avatarObjectKey() == null || user.avatarObjectKey().isBlank()) {
			return null;
		}
		return url(user.id(), user.avatarUpdatedAt());
	}

	public static String url(String userId, String avatarObjectKey) {
		return url(userId, avatarObjectKey, null);
	}

	public static String url(String userId, String avatarObjectKey, LocalDateTime updatedAt) {
		if (userId == null || userId.isBlank() || avatarObjectKey == null || avatarObjectKey.isBlank()) {
			return null;
		}
		return url(userId, updatedAt);
	}

	private static String url(String userId, LocalDateTime updatedAt) {
		String version = updatedAt == null ? "0" : updatedAt.toString().replaceAll("[^0-9]", "");
		return "/api/users/" + userId + "/avatar?v=" + version;
	}
}
