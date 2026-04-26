package com.luna.wallpaper.audit;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

@TableName("audit_log_archives")
class AuditLogArchiveRun {

	@TableId(type = IdType.INPUT)
	private String id;

	private String triggerType;

	private LocalDateTime cutoffTime;

	private String archiveBucket;

	private String archiveObjectKey;

	private String status;

	private long archivedCount;

	private long deletedCount;

	private LocalDateTime startedAt;

	private LocalDateTime finishedAt;

	private String errorMessage;

	protected AuditLogArchiveRun() {
	}

	AuditLogArchiveRun(String id, String triggerType, LocalDateTime cutoffTime) {
		this.id = id;
		this.triggerType = triggerType;
		this.cutoffTime = cutoffTime;
		this.status = "RUNNING";
		this.startedAt = LocalDateTime.now();
	}

	void succeed(String archiveBucket, String archiveObjectKey, long archivedCount, long deletedCount) {
		this.archiveBucket = archiveBucket;
		this.archiveObjectKey = archiveObjectKey;
		this.archivedCount = archivedCount;
		this.deletedCount = deletedCount;
		this.status = "SUCCESS";
		this.finishedAt = LocalDateTime.now();
		this.errorMessage = null;
	}

	void fail(Exception exception) {
		this.status = "FAILED";
		this.finishedAt = LocalDateTime.now();
		this.errorMessage = exception.getMessage();
	}

	String getId() {
		return id;
	}

	String getTriggerType() {
		return triggerType;
	}

	LocalDateTime getCutoffTime() {
		return cutoffTime;
	}

	String getArchiveBucket() {
		return archiveBucket;
	}

	String getArchiveObjectKey() {
		return archiveObjectKey;
	}

	String getStatus() {
		return status;
	}

	long getArchivedCount() {
		return archivedCount;
	}

	long getDeletedCount() {
		return deletedCount;
	}

	LocalDateTime getStartedAt() {
		return startedAt;
	}

	LocalDateTime getFinishedAt() {
		return finishedAt;
	}

	String getErrorMessage() {
		return errorMessage;
	}
}
