package com.luna.wallpaper.rbac;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

@TableName("permissions")
public class Permission {

	@TableId(type = IdType.INPUT)
	private String id;

	private String code;

	private String name;

	private String resource;

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
