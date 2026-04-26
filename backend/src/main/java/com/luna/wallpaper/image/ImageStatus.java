package com.luna.wallpaper.image;

public enum ImageStatus {
	ACTIVE,
	DELETED;

	static ImageStatus parse(String value) {
		if (value == null || value.isBlank()) {
			throw new IllegalArgumentException("图片状态不正确");
		}
		try {
			return valueOf(value.trim().toUpperCase());
		}
		catch (IllegalArgumentException ex) {
			throw new IllegalArgumentException("图片状态不正确", ex);
		}
	}

	static ImageStatus parseOrNull(String value) {
		return value == null || value.isBlank() ? null : parse(value);
	}

	boolean isDeleted() {
		return this == DELETED;
	}

	boolean isRetained() {
		return !isDeleted();
	}
}
