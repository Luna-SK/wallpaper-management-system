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
@Table(name = "categories")
public class Category {

	@Id
	@Column(nullable = false, length = 36)
	private String id;

	@Column(nullable = false, unique = true, length = 64)
	private String code;

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

	protected Category() {
	}

	Category(String code, String name, int sortOrder) {
		this.id = UUID.randomUUID().toString();
		this.code = code;
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

	public String code() {
		return code;
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

	LocalDateTime createdAt() {
		return createdAt;
	}

	LocalDateTime updatedAt() {
		return updatedAt;
	}

	void update(String code, String name, int sortOrder, boolean enabled) {
		this.code = code;
		this.name = name;
		this.sortOrder = sortOrder;
		this.enabled = enabled;
	}
}
