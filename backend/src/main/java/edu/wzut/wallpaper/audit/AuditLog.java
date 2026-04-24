package edu.wzut.wallpaper.audit;

import java.time.LocalDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "audit_logs")
class AuditLog {

	@Id
	@Column(nullable = false, length = 36)
	private String id;

	@Column(name = "actor_id", length = 36)
	private String actorId;

	@Column(nullable = false, length = 120)
	private String action;

	@Column(name = "target_type", length = 80)
	private String targetType;

	@Column(name = "target_id", length = 80)
	private String targetId;

	@Column(name = "ip_address", length = 80)
	private String ipAddress;

	@Column(name = "user_agent", length = 512)
	private String userAgent;

	@Column(name = "detail_json", columnDefinition = "json")
	private String detailJson;

	@Column(name = "created_at", nullable = false)
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
