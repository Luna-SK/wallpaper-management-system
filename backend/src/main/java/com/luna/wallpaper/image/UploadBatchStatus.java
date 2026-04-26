package com.luna.wallpaper.image;

import java.util.List;

public enum UploadBatchStatus {
	CREATED,
	STAGING,
	STAGED,
	PARTIAL_FAILED,
	CONFIRMED,
	CANCELLED,
	EXPIRED;

	static List<UploadBatchStatus> expirableStatuses() {
		return List.of(CREATED, STAGING, STAGED, PARTIAL_FAILED);
	}

	boolean isTerminal() {
		return this == CONFIRMED || this == CANCELLED || this == EXPIRED;
	}

	boolean isConfirmed() {
		return this == CONFIRMED;
	}

	boolean isClosedWithoutConfirm() {
		return this == CANCELLED || this == EXPIRED;
	}
}
