package com.luna.wallpaper.rbac;

import java.time.LocalDateTime;
import java.util.UUID;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

@TableName("auth_refresh_tokens")
public class AuthRefreshToken {

	@TableId(type = IdType.INPUT)
	private String id;

	private String userId;

	private String tokenHash;

	private LocalDateTime expiresAt;

	private LocalDateTime revokedAt;

	private String createdIp;

	private String userAgent;

	@TableField(fill = FieldFill.INSERT)
	private LocalDateTime createdAt;

	@TableField(fill = FieldFill.INSERT_UPDATE)
	private LocalDateTime updatedAt;

	protected AuthRefreshToken() {
	}

	AuthRefreshToken(String userId, String tokenHash, LocalDateTime expiresAt, String createdIp, String userAgent) {
		this.id = UUID.randomUUID().toString();
		this.userId = userId;
		this.tokenHash = tokenHash;
		this.expiresAt = expiresAt;
		this.createdIp = createdIp;
		this.userAgent = userAgent;
	}

	String id() { return id; }
	String userId() { return userId; }
	String tokenHash() { return tokenHash; }
	LocalDateTime expiresAt() { return expiresAt; }
	LocalDateTime revokedAt() { return revokedAt; }

	boolean isActive(LocalDateTime now) {
		return revokedAt == null && expiresAt.isAfter(now);
	}

	void rotate(String tokenHash, LocalDateTime expiresAt) {
		this.tokenHash = tokenHash;
		this.expiresAt = expiresAt;
		this.revokedAt = null;
	}

	void revoke() {
		if (revokedAt == null) {
			revokedAt = LocalDateTime.now();
		}
	}
}
