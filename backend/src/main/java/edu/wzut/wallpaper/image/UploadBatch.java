package edu.wzut.wallpaper.image;

import java.time.LocalDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

@Entity
@Table(name = "upload_batches")
class UploadBatch {

	@Id
	@Column(nullable = false, length = 36)
	private String id;

	@Column(nullable = false, length = 24)
	private String status = "PROCESSING";

	@Column(name = "total_count", nullable = false)
	private int totalCount;

	@Column(name = "success_count", nullable = false)
	private int successCount;

	@Column(name = "failed_count", nullable = false)
	private int failedCount;

	@Column(name = "duplicate_count", nullable = false)
	private int duplicateCount;

	@Column(name = "progress_percent", nullable = false)
	private int progressPercent;

	@Column(name = "created_by", length = 36)
	private String createdBy;

	@Column(name = "created_at", nullable = false)
	private LocalDateTime createdAt;

	@Column(name = "updated_at", nullable = false)
	private LocalDateTime updatedAt;

	@Column(name = "finished_at")
	private LocalDateTime finishedAt;

	protected UploadBatch() {
	}

	UploadBatch(int totalCount) {
		this.id = UUID.randomUUID().toString();
		this.totalCount = totalCount;
	}

	@PrePersist
	void prePersist() {
		LocalDateTime now = LocalDateTime.now();
		this.createdAt = now;
		this.updatedAt = now;
	}

	@PreUpdate
	void preUpdate() {
		this.updatedAt = LocalDateTime.now();
	}

	String id() { return id; }
	String status() { return status; }
	int totalCount() { return totalCount; }
	int successCount() { return successCount; }
	int failedCount() { return failedCount; }
	int duplicateCount() { return duplicateCount; }
	int progressPercent() { return progressPercent; }
	LocalDateTime createdAt() { return createdAt; }
	LocalDateTime finishedAt() { return finishedAt; }

	void refreshCounts(int successCount, int failedCount, int duplicateCount, int processed) {
		this.successCount = successCount;
		this.failedCount = failedCount;
		this.duplicateCount = duplicateCount;
		this.progressPercent = totalCount == 0 ? 100 : Math.min(100, processed * 100 / totalCount);
		if (processed >= totalCount) {
			this.status = failedCount > 0 ? "PARTIAL_FAILED" : "COMPLETED";
			this.finishedAt = LocalDateTime.now();
			this.progressPercent = 100;
		}
	}
}
