package edu.wzut.wallpaper.image;

import java.time.LocalDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

@Entity
@Table(name = "image_versions")
class ImageVersion {

	@Id
	@Column(nullable = false, length = 36)
	private String id;

	@Column(name = "image_id", nullable = false, length = 36)
	private String imageId;

	@Column(name = "version_no", nullable = false)
	private int versionNo;

	@Column(name = "source_version_id", length = 36)
	private String sourceVersionId;

	@Column(name = "operation_type", nullable = false, length = 40)
	private String operationType;

	@Column(name = "original_filename", nullable = false)
	private String originalFilename;

	@Column(nullable = false, length = 64)
	private String sha256;

	@Column(name = "mime_type", nullable = false, length = 120)
	private String mimeType;

	@Column(name = "size_bytes", nullable = false)
	private long sizeBytes;

	private Integer width;
	private Integer height;

	@Column(nullable = false, length = 120)
	private String bucket;

	@Column(name = "original_object_key", nullable = false, length = 512)
	private String originalObjectKey;

	@Column(name = "thumbnail_object_key", nullable = false, length = 512)
	private String thumbnailObjectKey;

	@Column(name = "high_preview_object_key", nullable = false, length = 512)
	private String highPreviewObjectKey;

	@Column(name = "standard_preview_object_key", nullable = false, length = 512)
	private String standardPreviewObjectKey;

	@Column(name = "current_flag", nullable = false, columnDefinition = "tinyint(1)")
	private boolean currentFlag = true;

	@Column(name = "created_by", length = 36)
	private String createdBy;

	@Column(name = "created_at", nullable = false)
	private LocalDateTime createdAt;

	protected ImageVersion() {
	}

	ImageVersion(String imageId, int versionNo, String operationType, StoredImage stored) {
		this.id = UUID.randomUUID().toString();
		this.imageId = imageId;
		this.versionNo = versionNo;
		this.operationType = operationType;
		this.originalFilename = stored.originalFilename();
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
	}

	@PrePersist
	void prePersist() {
		this.createdAt = LocalDateTime.now();
	}

	String id() { return id; }
	String imageId() { return imageId; }
	int versionNo() { return versionNo; }
	String bucket() { return bucket; }
	String originalObjectKey() { return originalObjectKey; }
	String thumbnailObjectKey() { return thumbnailObjectKey; }
	String highPreviewObjectKey() { return highPreviewObjectKey; }
	String standardPreviewObjectKey() { return standardPreviewObjectKey; }
	String mimeType() { return mimeType; }
	String originalFilename() { return originalFilename; }
}
