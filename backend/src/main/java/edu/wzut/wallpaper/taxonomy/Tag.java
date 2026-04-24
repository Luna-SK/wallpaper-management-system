package edu.wzut.wallpaper.taxonomy;

import java.time.LocalDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

@Entity
@Table(name = "tags")
public class Tag {

	@Id
	@Column(nullable = false, length = 36)
	private String id;

	@Column(name = "category_id", nullable = false, length = 36)
	private String categoryId;

	@Column(nullable = false, length = 120)
	private String name;

	@Column(name = "sort_order", nullable = false)
	private int sortOrder;

	@Column(nullable = false, columnDefinition = "tinyint(1)")
	private boolean enabled = true;

	@Column(name = "created_at", nullable = false)
	private LocalDateTime createdAt;

	@Column(name = "updated_at", nullable = false)
	private LocalDateTime updatedAt;

	protected Tag() {
	}

	Tag(String categoryId, String name, int sortOrder) {
		this.id = UUID.randomUUID().toString();
		this.categoryId = categoryId;
		this.name = name;
		this.sortOrder = sortOrder;
		this.enabled = true;
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

	public String id() {
		return id;
	}

	public String categoryId() {
		return categoryId;
	}

	public String name() {
		return name;
	}

	public int sortOrder() {
		return sortOrder;
	}

	public boolean enabled() {
		return enabled;
	}

	void update(String name, int sortOrder, boolean enabled) {
		this.name = name;
		this.sortOrder = sortOrder;
		this.enabled = enabled;
	}
}
