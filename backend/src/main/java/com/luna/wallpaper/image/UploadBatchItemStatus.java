package com.luna.wallpaper.image;

import java.util.List;

public enum UploadBatchItemStatus {
	PROCESSING,
	STAGED,
	DUPLICATE,
	FAILED,
	CONFIRMED,
	CANCELLED;

	static List<UploadBatchItemStatus> referencedObjectStatuses() {
		return List.of(PROCESSING, STAGED);
	}

	boolean isRetryable() {
		return this == FAILED;
	}

	boolean isConfirmable() {
		return this == STAGED || this == DUPLICATE;
	}

	boolean canBecomeCancelledWithoutStoredObjects() {
		return this != DUPLICATE && this != FAILED;
	}

	boolean countsAsSuccess() {
		return this == STAGED || this == CONFIRMED;
	}

	boolean countsAsFailure() {
		return this == FAILED;
	}

	boolean countsAsDuplicate() {
		return this == DUPLICATE;
	}
}
