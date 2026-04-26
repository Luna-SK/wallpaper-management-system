package com.luna.wallpaper.taxonomy;

import java.time.LocalDateTime;
import java.util.UUID;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

@TableName("tags")
public class Tag {

	@TableId(type = IdType.INPUT)
	private String id;

	private String groupId;

	private String name;

	private int sortOrder;

	private boolean enabled = true;

	@TableField(fill = FieldFill.INSERT)
	private LocalDateTime createdAt;

	@TableField(fill = FieldFill.INSERT_UPDATE)
	private LocalDateTime updatedAt;

	@TableField(exist = false)
	private String groupName;

	protected Tag() {
	}

	Tag(String groupId, String name, int sortOrder) {
		this.id = UUID.randomUUID().toString();
		this.groupId = groupId;
		this.name = name;
		this.sortOrder = sortOrder;
		this.enabled = true;
	}

	public String id() {
		return id;
	}

	public String groupId() {
		return groupId;
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

	public String groupName() {
		return groupName;
	}

	void update(String name, int sortOrder, boolean enabled) {
		this.name = name;
		this.sortOrder = sortOrder;
		this.enabled = enabled;
	}

	void moveToGroup(String groupId) {
		this.groupId = groupId;
	}

	public void attachGroup(TagGroup group) {
		this.groupName = group == null ? null : group.name();
	}
}
