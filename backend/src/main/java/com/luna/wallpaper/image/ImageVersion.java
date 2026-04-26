package com.luna.wallpaper.image;

import java.time.LocalDateTime;
import java.util.UUID;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

@TableName("image_versions")
public class ImageVersion {

	@TableId(type = IdType.INPUT)
	private String id;

	private String imageId;

	private int versionNo;

	private String sourceVersionId;

	private String operationType;

	private String originalFilename;

	private String sha256;

	private String mimeType;

	private long sizeBytes;

	private Integer width;
	private Integer height;

	private String bucket;

	private String originalObjectKey;

	private String thumbnailObjectKey;

	private String highPreviewObjectKey;

	private String standardPreviewObjectKey;

	private boolean currentFlag = true;

	private String createdBy;

	@TableField(fill = FieldFill.INSERT)
	private LocalDateTime createdAt;

	protected ImageVersion() {
	}

	ImageVersion(String imageId, int versionNo, String operationType, StoredImage stored) {
		this(imageId, versionNo, operationType, stored, null);
	}

	ImageVersion(String imageId, int versionNo, String operationType, StoredImage stored, String sourceVersionId) {
		this.id = UUID.randomUUID().toString();
		this.imageId = imageId;
		this.versionNo = versionNo;
		this.sourceVersionId = sourceVersionId;
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

	String id() { return id; }
	String imageId() { return imageId; }
	int versionNo() { return versionNo; }
	String sourceVersionId() { return sourceVersionId; }
	String operationType() { return operationType; }
	String sha256() { return sha256; }
	String bucket() { return bucket; }
	String originalObjectKey() { return originalObjectKey; }
	String thumbnailObjectKey() { return thumbnailObjectKey; }
	String highPreviewObjectKey() { return highPreviewObjectKey; }
	String standardPreviewObjectKey() { return standardPreviewObjectKey; }
	String mimeType() { return mimeType; }
	String originalFilename() { return originalFilename; }
	long sizeBytes() { return sizeBytes; }
	Integer width() { return width; }
	Integer height() { return height; }
	boolean currentFlag() { return currentFlag; }
	LocalDateTime createdAt() { return createdAt; }
}
