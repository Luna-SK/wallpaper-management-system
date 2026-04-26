package com.luna.wallpaper.audit;

import java.time.LocalDateTime;
import java.util.UUID;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

@TableName("audit_logs")
class AuditLog {

	@TableId(type = IdType.INPUT)
	private String id;

	private String actorId;

	private String action;

	private String targetType;

	private String targetId;

	private String ipAddress;

	private String userAgent;

	private String detailJson;

	@TableField(fill = FieldFill.INSERT)
	private LocalDateTime createdAt;

	protected AuditLog() {
	}

	AuditLog(String action, String targetType, String targetId, String detailJson) {
		this.id = UUID.randomUUID().toString();
		this.action = action;
		this.targetType = targetType;
		this.targetId = targetId;
		this.detailJson = detailJson;
		this.createdAt = LocalDateTime.now();
	}

	String getId() {
		return id;
	}

	String getActorId() {
		return actorId;
	}

	String getAction() {
		return action;
	}

	String getTargetType() {
		return targetType;
	}

	String getTargetId() {
		return targetId;
	}

	String getIpAddress() {
		return ipAddress;
	}

	String getUserAgent() {
		return userAgent;
	}

	String getDetailJson() {
		return detailJson;
	}

	LocalDateTime getCreatedAt() {
		return createdAt;
	}
}
