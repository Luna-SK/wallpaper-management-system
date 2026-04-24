package edu.wzut.wallpaper.image;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
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
	private String status = "CREATED";

	@Column(nullable = false, length = 16)
	private String mode = "BATCH";

	@Column(name = "category_id", length = 36)
	private String categoryId;

	@Column(name = "tag_ids", length = 2000)
	private String tagIds;

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

	@Column(name = "expires_at")
	private LocalDateTime expiresAt;

	@Column(name = "confirmed_at")
	private LocalDateTime confirmedAt;

	protected UploadBatch() {
	}

	UploadBatch(int totalCount) {
		this("BATCH", totalCount, null, List.of());
	}

	UploadBatch(String mode, int totalCount, String categoryId, Collection<String> tagIds) {
		this.id = UUID.randomUUID().toString();
		this.mode = mode == null || mode.isBlank() ? "BATCH" : mode.trim().toUpperCase();
		this.totalCount = totalCount;
		this.categoryId = categoryId;
		this.tagIds = tagIds == null ? "" : String.join(",", tagIds);
		this.expiresAt = LocalDateTime.now().plusHours(24);
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
	String mode() { return mode; }
	String categoryId() { return categoryId; }
	List<String> tagIds() {
		if (tagIds == null || tagIds.isBlank()) {
			return List.of();
		}
		return java.util.Arrays.stream(tagIds.split(","))
				.filter(tagId -> !tagId.isBlank())
				.toList();
	}
	int totalCount() { return totalCount; }
	int successCount() { return successCount; }
	int failedCount() { return failedCount; }
	int duplicateCount() { return duplicateCount; }
	int progressPercent() { return progressPercent; }
	LocalDateTime createdAt() { return createdAt; }
	LocalDateTime finishedAt() { return finishedAt; }
	LocalDateTime expiresAt() { return expiresAt; }
	LocalDateTime confirmedAt() { return confirmedAt; }

	void refreshCounts(int successCount, int failedCount, int duplicateCount, int processed) {
		if (isTerminal()) {
			return;
		}
		this.successCount = successCount;
		this.failedCount = failedCount;
		this.duplicateCount = duplicateCount;
		this.progressPercent = totalCount == 0 ? 100 : Math.min(100, processed * 100 / totalCount);
		if (processed >= totalCount) {
			this.status = failedCount > 0 ? "PARTIAL_FAILED" : "STAGED";
			this.finishedAt = LocalDateTime.now();
			this.progressPercent = 100;
		}
		else {
			this.status = "STAGING";
		}
	}

	void markConfirmed() {
		this.status = "CONFIRMED";
		this.confirmedAt = LocalDateTime.now();
		this.finishedAt = this.confirmedAt;
		this.progressPercent = 100;
	}

	void markCancelled() {
		this.status = "CANCELLED";
		this.finishedAt = LocalDateTime.now();
	}

	void markExpired() {
		this.status = "EXPIRED";
		this.finishedAt = LocalDateTime.now();
	}

	boolean isTerminal() {
		return "CONFIRMED".equals(status) || "CANCELLED".equals(status) || "EXPIRED".equals(status);
	}
}
