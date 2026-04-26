package com.luna.wallpaper.image;

import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.luna.wallpaper.taxonomy.Category;
import com.luna.wallpaper.taxonomy.Tag;

@TableName("images")
class ImageAsset {

	@TableId(type = IdType.INPUT)
	private String id;

	private String title;

	private String originalFilename;

	private String sha256;

	private String mimeType;

	private long sizeBytes;

	private Integer width;

	private Integer height;

	private String uploaderId;

	private String sourcePath;

	private ImageStatus status = ImageStatus.ACTIVE;

	private long viewCount;

	private long downloadCount;

	private String currentVersionId;

	private LocalDateTime deletedAt;

	private String categoryId;

	@TableField(exist = false)
	private Category category;

	@TableField(exist = false)
	private Set<Tag> tags = new LinkedHashSet<>();

	@TableField(fill = FieldFill.INSERT)
	private LocalDateTime createdAt;

	@TableField(fill = FieldFill.INSERT_UPDATE)
	private LocalDateTime updatedAt;

	protected ImageAsset() {
	}

	ImageAsset(String title, String originalFilename, String sha256, String mimeType, long sizeBytes, Integer width,
			Integer height) {
		this(UUID.randomUUID().toString(), title, originalFilename, sha256, mimeType, sizeBytes, width, height);
	}

	ImageAsset(String id, String title, String originalFilename, String sha256, String mimeType, long sizeBytes, Integer width,
			Integer height) {
		this.id = id;
		this.title = title;
		this.originalFilename = originalFilename;
		this.sha256 = sha256;
		this.mimeType = mimeType;
		this.sizeBytes = sizeBytes;
		this.width = width;
		this.height = height;
	}

	String id() { return id; }
	String title() { return title; }
	String originalFilename() { return originalFilename; }
	String sha256() { return sha256; }
	String mimeType() { return mimeType; }
	long sizeBytes() { return sizeBytes; }
	Integer width() { return width; }
	Integer height() { return height; }
	ImageStatus status() { return status; }
	long viewCount() { return viewCount; }
	long downloadCount() { return downloadCount; }
	String currentVersionId() { return currentVersionId; }
	String categoryId() { return categoryId; }
	Category category() { return category; }
	Set<Tag> tags() { return tags; }
	LocalDateTime createdAt() { return createdAt; }
	LocalDateTime deletedAt() { return deletedAt; }

	void setCurrentVersionId(String currentVersionId) {
		this.currentVersionId = currentVersionId;
	}

	void replaceTaxonomy(Category category, Set<Tag> tags) {
		this.categoryId = category == null ? null : category.id();
		this.category = category;
		this.tags.clear();
		this.tags.addAll(tags);
	}

	void updateMetadata(String title, ImageStatus status) {
		this.title = title;
		this.status = status;
	}

	void markDeleted() {
		this.status = ImageStatus.DELETED;
		this.deletedAt = LocalDateTime.now();
	}

	void restore() {
		this.status = ImageStatus.ACTIVE;
		this.deletedAt = null;
	}

	void viewed() {
		this.viewCount++;
	}

	void downloaded() {
		this.downloadCount++;
	}
}
