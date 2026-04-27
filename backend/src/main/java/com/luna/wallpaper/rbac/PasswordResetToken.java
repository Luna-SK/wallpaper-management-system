package com.luna.wallpaper.rbac;

import java.time.LocalDateTime;
import java.util.UUID;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

@TableName("password_reset_tokens")
public class PasswordResetToken {

	@TableId(type = IdType.INPUT)
	private String id;

	private String userId;

	private String tokenHash;

	private LocalDateTime expiresAt;

	private LocalDateTime usedAt;

	private String requestIp;

	private String userAgent;

	@TableField(fill = FieldFill.INSERT)
	private LocalDateTime createdAt;

	@TableField(fill = FieldFill.INSERT_UPDATE)
	private LocalDateTime updatedAt;

	protected PasswordResetToken() {
	}

	PasswordResetToken(String userId, String tokenHash, LocalDateTime expiresAt, String requestIp, String userAgent) {
		this.id = UUID.randomUUID().toString();
		this.userId = userId;
		this.tokenHash = tokenHash;
		this.expiresAt = expiresAt;
		this.requestIp = requestIp;
		this.userAgent = userAgent;
	}

	String id() { return id; }
	String userId() { return userId; }
	String tokenHash() { return tokenHash; }
	LocalDateTime expiresAt() { return expiresAt; }
	LocalDateTime usedAt() { return usedAt; }
	LocalDateTime createdAt() { return createdAt; }
	LocalDateTime updatedAt() { return updatedAt; }

	boolean isUsable(LocalDateTime now) {
		return usedAt == null && expiresAt.isAfter(now);
	}

	void markUsed(LocalDateTime usedAt) {
		this.usedAt = usedAt;
	}
}
