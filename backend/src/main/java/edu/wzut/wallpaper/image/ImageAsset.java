package edu.wzut.wallpaper.image;

import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

import edu.wzut.wallpaper.taxonomy.Category;
import edu.wzut.wallpaper.taxonomy.Tag;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

@Entity
@Table(name = "images")
class ImageAsset {

	@Id
	@Column(nullable = false, length = 36)
	private String id;

	@Column(nullable = false)
	private String title;

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

	@Column(name = "uploader_id", length = 36)
	private String uploaderId;

	@Column(name = "source_path", length = 512)
	private String sourcePath;

	@Column(nullable = false, length = 24)
	private String status = "ACTIVE";

	@Column(name = "view_count", nullable = false)
	private long viewCount;

	@Column(name = "download_count", nullable = false)
	private long downloadCount;

	@Column(name = "current_version_id", length = 36)
	private String currentVersionId;

	@Column(name = "deleted_at")
	private LocalDateTime deletedAt;

	@ManyToMany
	@JoinTable(name = "image_categories", joinColumns = @JoinColumn(name = "image_id"), inverseJoinColumns = @JoinColumn(name = "category_id"))
	private Set<Category> categories = new LinkedHashSet<>();

	@ManyToMany
	@JoinTable(name = "image_tags", joinColumns = @JoinColumn(name = "image_id"), inverseJoinColumns = @JoinColumn(name = "tag_id"))
	private Set<Tag> tags = new LinkedHashSet<>();

	@Column(name = "created_at", nullable = false)
	private LocalDateTime createdAt;

	@Column(name = "updated_at", nullable = false)
	private LocalDateTime updatedAt;

	protected ImageAsset() {
	}

	ImageAsset(String title, String originalFilename, String sha256, String mimeType, long sizeBytes, Integer width,
			Integer height) {
		this.id = UUID.randomUUID().toString();
		this.title = title;
		this.originalFilename = originalFilename;
		this.sha256 = sha256;
		this.mimeType = mimeType;
		this.sizeBytes = sizeBytes;
		this.width = width;
		this.height = height;
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
	String title() { return title; }
	String originalFilename() { return originalFilename; }
	String sha256() { return sha256; }
	String mimeType() { return mimeType; }
	long sizeBytes() { return sizeBytes; }
	Integer width() { return width; }
	Integer height() { return height; }
	String status() { return status; }
	long viewCount() { return viewCount; }
	long downloadCount() { return downloadCount; }
	String currentVersionId() { return currentVersionId; }
	Set<Category> categories() { return categories; }
	Set<Tag> tags() { return tags; }
	LocalDateTime createdAt() { return createdAt; }

	void setCurrentVersionId(String currentVersionId) {
		this.currentVersionId = currentVersionId;
	}

	void replaceTaxonomy(Set<Category> categories, Set<Tag> tags) {
		this.categories.clear();
		this.categories.addAll(categories);
		this.tags.clear();
		this.tags.addAll(tags);
	}

	void updateMetadata(String title, String status) {
		this.title = title;
		this.status = status;
	}

	void markDeleted() {
		this.status = "DELETED";
		this.deletedAt = LocalDateTime.now();
	}

	void viewed() {
		this.viewCount++;
	}

	void downloaded() {
		this.downloadCount++;
	}
}
