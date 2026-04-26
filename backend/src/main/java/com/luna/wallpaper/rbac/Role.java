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

@TableName("roles")
class Role {

	@TableId(type = IdType.INPUT)
	private String id;

	private String code;

	private String name;

	private String description;

	private boolean enabled = true;

	@TableField(exist = false)
	private Set<Permission> permissions = new LinkedHashSet<>();

	@TableField(fill = FieldFill.INSERT)
	private LocalDateTime createdAt;

	@TableField(fill = FieldFill.INSERT_UPDATE)
	private LocalDateTime updatedAt;

	protected Role() {
	}

	Role(String code, String name) {
		this.id = UUID.randomUUID().toString();
		this.code = code;
		this.name = name;
		this.enabled = true;
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
