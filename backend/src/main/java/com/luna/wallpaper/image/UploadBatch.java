package com.luna.wallpaper.image;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

@TableName("upload_batches")
class UploadBatch {

	@TableId(type = IdType.INPUT)
	private String id;

	private UploadBatchStatus status = UploadBatchStatus.CREATED;

	private String mode = "BATCH";

	private String categoryId;

	@TableField(exist = false)
	private List<String> tagIds = List.of();

	private int totalCount;

	private int successCount;

	private int failedCount;

	private int duplicateCount;

	private int progressPercent;

	private String createdBy;

	@TableField(fill = FieldFill.INSERT)
	private LocalDateTime createdAt;

	@TableField(fill = FieldFill.INSERT_UPDATE)
	private LocalDateTime updatedAt;

	private LocalDateTime finishedAt;

	private LocalDateTime expiresAt;

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
		this.tagIds = normalizeTagIds(tagIds);
		this.expiresAt = LocalDateTime.now().plusHours(24);
	}

	String id() { return id; }
	UploadBatchStatus status() { return status; }
	String mode() { return mode; }
	String categoryId() { return categoryId; }
	List<String> tagIds() {
		return tagIds;
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
			this.status = failedCount > 0 ? UploadBatchStatus.PARTIAL_FAILED : UploadBatchStatus.STAGED;
			this.finishedAt = LocalDateTime.now();
			this.progressPercent = 100;
		}
		else {
			this.status = UploadBatchStatus.STAGING;
		}
	}

	void markConfirmed() {
		this.status = UploadBatchStatus.CONFIRMED;
		this.confirmedAt = LocalDateTime.now();
		this.finishedAt = this.confirmedAt;
		this.progressPercent = 100;
	}

	void markCancelled() {
		this.status = UploadBatchStatus.CANCELLED;
		this.finishedAt = LocalDateTime.now();
	}

	void markExpired() {
		this.status = UploadBatchStatus.EXPIRED;
		this.finishedAt = LocalDateTime.now();
	}

	boolean isTerminal() {
		return status.isTerminal();
	}

	void replaceTagIds(Collection<String> tagIds) {
		this.tagIds = normalizeTagIds(tagIds);
	}

	private static List<String> normalizeTagIds(Collection<String> tagIds) {
		if (tagIds == null || tagIds.isEmpty()) {
			return List.of();
		}
		return tagIds.stream()
				.filter(tagId -> tagId != null && !tagId.isBlank())
				.map(String::trim)
				.collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new))
				.stream()
				.toList();
	}
}
