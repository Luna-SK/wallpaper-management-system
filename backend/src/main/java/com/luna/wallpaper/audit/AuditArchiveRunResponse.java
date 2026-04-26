package com.luna.wallpaper.audit;

import java.time.LocalDateTime;

public record AuditArchiveRunResponse(
		String id,
		String triggerType,
		LocalDateTime cutoffTime,
		String archiveBucket,
		String archiveObjectKey,
		String status,
		long archivedCount,
		long deletedCount,
		LocalDateTime startedAt,
		LocalDateTime finishedAt,
		String errorMessage
) {

	static AuditArchiveRunResponse from(AuditLogArchiveRun run) {
		return new AuditArchiveRunResponse(
				run.getId(),
				run.getTriggerType(),
				run.getCutoffTime(),
				run.getArchiveBucket(),
				run.getArchiveObjectKey(),
				run.getStatus().name(),
				run.getArchivedCount(),
				run.getDeletedCount(),
				run.getStartedAt(),
				run.getFinishedAt(),
				run.getErrorMessage());
	}
}
