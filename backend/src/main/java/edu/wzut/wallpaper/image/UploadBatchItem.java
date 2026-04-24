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
@Table(name = "upload_batch_items")
class UploadBatchItem {

	@Id
	@Column(nullable = false, length = 36)
	private String id;

	@Column(name = "batch_id", nullable = false, length = 36)
	private String batchId;

	@Column(name = "image_id", length = 36)
	private String imageId;

	@Column(name = "original_filename", nullable = false)
	private String originalFilename;

	@Column(nullable = false, length = 24)
	private String status = "PROCESSING";

	@Column(name = "progress_percent", nullable = false)
	private int progressPercent;

	@Column(name = "retry_count", nullable = false)
	private int retryCount;

	@Column(name = "error_message", length = 1000)
	private String errorMessage;

	@Column(name = "created_at", nullable = false)
	private LocalDateTime createdAt;

	@Column(name = "updated_at", nullable = false)
	private LocalDateTime updatedAt;

	protected UploadBatchItem() {
	}

	UploadBatchItem(String batchId, String originalFilename) {
		this.id = UUID.randomUUID().toString();
		this.batchId = batchId;
		this.originalFilename = originalFilename;
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
	String batchId() { return batchId; }
	String imageId() { return imageId; }
	String originalFilename() { return originalFilename; }
	String status() { return status; }
	int progressPercent() { return progressPercent; }
	int retryCount() { return retryCount; }
	String errorMessage() { return errorMessage; }

	void succeeded(String imageId) {
		this.imageId = imageId;
		this.status = "SUCCESS";
		this.progressPercent = 100;
		this.errorMessage = null;
	}

	void duplicated(String imageId) {
		this.imageId = imageId;
		this.status = "DUPLICATE";
		this.progressPercent = 100;
		this.errorMessage = "图片内容重复，已关联既有图片";
	}

	void failed(String message) {
		this.status = "FAILED";
		this.progressPercent = 100;
		this.errorMessage = message;
	}

	void retryFailed(String message) {
		this.retryCount++;
		failed(message);
	}
}
