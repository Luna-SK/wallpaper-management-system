package com.luna.wallpaper.image;

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

	@Column(name = "candidate_image_id", length = 36)
	private String candidateImageId;

	@Column(name = "original_filename", nullable = false)
	private String originalFilename;

	@Column(nullable = false, length = 64)
	private String sha256 = "";

	@Column(name = "mime_type", length = 120)
	private String mimeType;

	@Column(name = "size_bytes")
	private Long sizeBytes;

	private Integer width;

	private Integer height;

	@Column(length = 120)
	private String bucket;

	@Column(name = "original_object_key", length = 512)
	private String originalObjectKey;

	@Column(name = "thumbnail_object_key", length = 512)
	private String thumbnailObjectKey;

	@Column(name = "high_preview_object_key", length = 512)
	private String highPreviewObjectKey;

	@Column(name = "standard_preview_object_key", length = 512)
	private String standardPreviewObjectKey;

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
	String candidateImageId() { return candidateImageId; }
	String originalFilename() { return originalFilename; }
	String sha256() { return sha256; }
	String status() { return status; }
	int progressPercent() { return progressPercent; }
	int retryCount() { return retryCount; }
	String errorMessage() { return errorMessage; }
	String originalObjectKey() { return originalObjectKey; }
	String thumbnailObjectKey() { return thumbnailObjectKey; }
	String highPreviewObjectKey() { return highPreviewObjectKey; }
	String standardPreviewObjectKey() { return standardPreviewObjectKey; }

	void staged(String candidateImageId, StoredImage stored) {
		this.candidateImageId = candidateImageId;
		if (stored.originalFilename() != null && !stored.originalFilename().isBlank()) {
			this.originalFilename = stored.originalFilename();
		}
		this.sha256 = stored.sha256();
		this.mimeType = stored.mimeType();
		this.sizeBytes = stored.sizeBytes();
		this.width = stored.width();
		this.height = stored.height();
		this.bucket = stored.bucket();
		this.originalObjectKey = stored.originalObjectKey();
		this.thumbnailObjectKey = stored.thumbnailObjectKey();
		this.highPreviewObjectKey = stored.highPreviewObjectKey();
		this.standardPreviewObjectKey = stored.standardPreviewObjectKey();
		this.status = "STAGED";
		this.progressPercent = 100;
		this.errorMessage = null;
	}

	void duplicated(String imageId) {
		duplicated(imageId, this.sha256);
	}

	void duplicated(String imageId, String sha256) {
		this.imageId = imageId;
		this.sha256 = sha256 == null ? "" : sha256;
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

	void retrying(String originalFilename) {
		this.retryCount++;
		this.originalFilename = originalFilename;
		this.sha256 = "";
		this.status = "PROCESSING";
		this.progressPercent = 0;
		this.errorMessage = null;
		clearStoredObjects();
	}

	void confirmed(String imageId) {
		this.imageId = imageId;
		this.status = "CONFIRMED";
		this.progressPercent = 100;
		this.errorMessage = null;
	}

	void cancelled() {
		this.status = "CANCELLED";
		this.progressPercent = 100;
		clearStoredObjects();
	}

	boolean hasStoredObjects() {
		return bucket != null && originalObjectKey != null;
	}

	StoredImage storedImage() {
		return new StoredImage(originalFilename, sha256, mimeType, sizeBytes == null ? 0 : sizeBytes, width, height, bucket,
				originalObjectKey, thumbnailObjectKey, highPreviewObjectKey, standardPreviewObjectKey);
	}

	private void clearStoredObjects() {
		this.candidateImageId = null;
		this.bucket = null;
		this.originalObjectKey = null;
		this.thumbnailObjectKey = null;
		this.highPreviewObjectKey = null;
		this.standardPreviewObjectKey = null;
	}
}
