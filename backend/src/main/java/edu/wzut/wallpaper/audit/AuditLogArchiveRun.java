package edu.wzut.wallpaper.audit;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "audit_log_archives")
class AuditLogArchiveRun {

	@Id
	@Column(nullable = false, length = 36)
	private String id;

	@Column(name = "trigger_type", nullable = false, length = 24)
	private String triggerType;

	@Column(name = "cutoff_time", nullable = false)
	private LocalDateTime cutoffTime;

	@Column(name = "archive_bucket", length = 120)
	private String archiveBucket;

	@Column(name = "archive_object_key", length = 512)
	private String archiveObjectKey;

	@Column(nullable = false, length = 24)
	private String status;

	@Column(name = "archived_count", nullable = false)
	private long archivedCount;

	@Column(name = "deleted_count", nullable = false)
	private long deletedCount;

	@Column(name = "started_at", nullable = false)
	private LocalDateTime startedAt;

	@Column(name = "finished_at")
	private LocalDateTime finishedAt;

	@Column(name = "error_message", length = 1000)
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
