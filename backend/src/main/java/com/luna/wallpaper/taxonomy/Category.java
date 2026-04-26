package com.luna.wallpaper.taxonomy;

import java.time.LocalDateTime;
import java.util.UUID;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

@TableName("categories")
public class Category {

	@TableId(type = IdType.INPUT)
	private String id;

	private String code;

	private String name;

	private int sortOrder;

	private boolean enabled = true;

	@TableField(fill = FieldFill.INSERT)
	private LocalDateTime createdAt;

	@TableField(fill = FieldFill.INSERT_UPDATE)
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
