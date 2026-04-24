package edu.wzut.wallpaper.rbac;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "permissions")
class Permission {

	@Id
	@Column(nullable = false, length = 36)
	private String id;

	@Column(nullable = false, unique = true, length = 120)
	private String code;

	@Column(nullable = false, length = 120)
	private String name;

	@Column(nullable = false, length = 120)
	private String resource;

	@Column(nullable = false, length = 40)
	private String action;

	protected Permission() {
	}

	String id() {
		return id;
	}

	String code() {
		return code;
	}

	String name() {
		return name;
	}

	String resource() {
		return resource;
	}

	String action() {
		return action;
	}
}
