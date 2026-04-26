package com.luna.wallpaper.rbac;

import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

@TableName("app_users")
class AppUser {

	@TableId(type = IdType.INPUT)
	private String id;

	private String username;

	private String displayName;

	private String email;

	private String phone;

	private String passwordHash;

	private String status = "ACTIVE";

	@TableField(exist = false)
	private Set<Role> roles = new LinkedHashSet<>();

	@TableField(fill = FieldFill.INSERT)
	private LocalDateTime createdAt;

	@TableField(fill = FieldFill.INSERT_UPDATE)
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
