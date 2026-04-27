package com.luna.wallpaper.interaction;

public enum FeedbackStatus {
	OPEN,
	IN_PROGRESS,
	RESOLVED,
	CLOSED;

	static FeedbackStatus parse(String value) {
		if (value == null || value.isBlank()) {
			return OPEN;
		}
		try {
			return FeedbackStatus.valueOf(value.trim().toUpperCase());
		}
		catch (IllegalArgumentException ex) {
			throw new IllegalArgumentException("反馈状态不正确");
		}
	}
}
