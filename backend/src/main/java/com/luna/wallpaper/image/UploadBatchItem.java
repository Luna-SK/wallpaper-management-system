package com.luna.wallpaper.image;

import java.time.LocalDateTime;
import java.util.UUID;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

@TableName("upload_batch_items")
class UploadBatchItem {

	@TableId(type = IdType.INPUT)
	private String id;

	private String batchId;

	private String imageId;

	private String candidateImageId;

	private String originalFilename;

	private String sha256 = "";

	private String mimeType;

	private Long sizeBytes;

	private Integer width;

	private Integer height;

	private String bucket;

	private String originalObjectKey;

	private String thumbnailObjectKey;

	private String highPreviewObjectKey;

	private String standardPreviewObjectKey;

	private String status = "PROCESSING";

	private int progressPercent;

	private int retryCount;

	private String errorMessage;

	@TableField(fill = FieldFill.INSERT)
	private LocalDateTime createdAt;

	@TableField(fill = FieldFill.INSERT_UPDATE)
	private LocalDateTime updatedAt;

	protected UploadBatchItem() {
	}

	UploadBatchItem(String batchId, String originalFilename) {
		this.id = UUID.randomUUID().toString();
		this.batchId = batchId;
		this.originalFilename = originalFilename;
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
	long sizeBytes() { return sizeBytes == null ? 0 : sizeBytes; }
	String originalObjectKey() { return originalObjectKey; }
	String thumbnailObjectKey() { return thumbnailObjectKey; }
	String highPreviewObjectKey() { return highPreviewObjectKey; }
	String standardPreviewObjectKey() { return standardPreviewObjectKey; }

	void receivedSize(long sizeBytes) {
		this.sizeBytes = Math.max(0, sizeBytes);
	}

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
		this.sizeBytes = null;
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
