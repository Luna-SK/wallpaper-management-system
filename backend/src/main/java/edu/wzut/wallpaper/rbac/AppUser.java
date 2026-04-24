package edu.wzut.wallpaper.rbac;

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
@Table(name = "app_users")
class AppUser {

	@Id
	@Column(nullable = false, length = 36)
	private String id;

	@Column(nullable = false, unique = true, length = 64)
	private String username;

	@Column(name = "display_name", nullable = false, length = 120)
	private String displayName;

	@Column(length = 180)
	private String email;

	@Column(length = 40)
	private String phone;

	@Column(name = "password_hash", nullable = false)
	private String passwordHash;

	@Column(nullable = false, length = 24)
	private String status = "ACTIVE";

	@ManyToMany
	@JoinTable(name = "user_roles", joinColumns = @JoinColumn(name = "user_id"), inverseJoinColumns = @JoinColumn(name = "role_id"))
	private Set<Role> roles = new LinkedHashSet<>();

	@Column(name = "created_at", nullable = false)
	private LocalDateTime createdAt;

	@Column(name = "updated_at", nullable = false)
	private LocalDateTime updatedAt;

	protected AppUser() {
	}

	AppUser(String username, String displayName, String email, String phone) {
		this.id = UUID.randomUUID().toString();
		this.username = username;
		this.displayName = displayName;
		this.email = email;
		this.phone = phone;
		this.passwordHash = "{noop}admin123";
		this.status = "ACTIVE";
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
	String username() { return username; }
	String displayName() { return displayName; }
	String email() { return email; }
	String phone() { return phone; }
	String status() { return status; }
	Set<Role> roles() { return roles; }

	void update(String displayName, String email, String phone, String status) {
		this.displayName = displayName;
		this.email = email;
		this.phone = phone;
		this.status = status;
	}

	void replaceRoles(Set<Role> roles) {
		this.roles.clear();
		this.roles.addAll(roles);
	}
}
