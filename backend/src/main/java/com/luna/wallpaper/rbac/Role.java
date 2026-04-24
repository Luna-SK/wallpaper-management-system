package com.luna.wallpaper.rbac;

import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

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
@Table(name = "roles")
class Role {

	@Id
	@Column(nullable = false, length = 36)
	private String id;

	@Column(nullable = false, unique = true, length = 64)
	private String code;

	@Column(nullable = false, length = 120)
	private String name;

	@Column(length = 255)
	private String description;

	@Column(nullable = false, columnDefinition = "tinyint(1)")
	private boolean enabled = true;

	@ManyToMany
	@JoinTable(name = "role_permissions", joinColumns = @JoinColumn(name = "role_id"), inverseJoinColumns = @JoinColumn(name = "permission_id"))
	private Set<Permission> permissions = new LinkedHashSet<>();

	@Column(name = "created_at", nullable = false)
	private LocalDateTime createdAt;

	@Column(name = "updated_at", nullable = false)
	private LocalDateTime updatedAt;

	protected Role() {
	}

	Role(String code, String name) {
		this.id = UUID.randomUUID().toString();
		this.code = code;
		this.name = name;
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

	String id() { return id; }
	String code() { return code; }
	String name() { return name; }
	boolean enabled() { return enabled; }
	Set<Permission> permissions() { return permissions; }

	void update(String code, String name, boolean enabled) {
		this.code = code;
		this.name = name;
		this.enabled = enabled;
	}

	void replacePermissions(Set<Permission> permissions) {
		this.permissions.clear();
		this.permissions.addAll(permissions);
	}
}
