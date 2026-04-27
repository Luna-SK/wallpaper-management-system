package com.luna.wallpaper.rbac;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

import org.springframework.stereotype.Service;

import com.luna.wallpaper.rbac.AuthDtos.SessionPolicyResponse;
import com.luna.wallpaper.settings.SessionLifecycleSettings;
import com.luna.wallpaper.settings.SystemSettingService;

@Service
class SessionLifecyclePolicyService {

	private static final Duration TOUCH_INTERVAL = Duration.ofMinutes(5);

	private final SystemSettingService settings;

	SessionLifecyclePolicyService(SystemSettingService settings) {
		this.settings = settings;
	}

	SessionPolicy currentPolicy() {
		return new SessionPolicy(
				booleanSetting(SessionLifecycleSettings.IDLE_TIMEOUT_ENABLED,
						SessionLifecycleSettings.DEFAULT_IDLE_TIMEOUT_ENABLED),
				rangeIntSetting(SessionLifecycleSettings.IDLE_TIMEOUT_MINUTES,
						SessionLifecycleSettings.DEFAULT_IDLE_TIMEOUT_MINUTES,
						SessionLifecycleSettings.MIN_IDLE_TIMEOUT_MINUTES,
						SessionLifecycleSettings.MAX_IDLE_TIMEOUT_MINUTES),
				booleanSetting(SessionLifecycleSettings.ABSOLUTE_LIFETIME_ENABLED,
						SessionLifecycleSettings.DEFAULT_ABSOLUTE_LIFETIME_ENABLED),
				rangeIntSetting(SessionLifecycleSettings.ABSOLUTE_LIFETIME_DAYS,
						SessionLifecycleSettings.DEFAULT_ABSOLUTE_LIFETIME_DAYS,
						SessionLifecycleSettings.MIN_ABSOLUTE_LIFETIME_DAYS,
						SessionLifecycleSettings.MAX_ABSOLUTE_LIFETIME_DAYS));
	}

	boolean isValid(AuthRefreshToken session, LocalDateTime now) {
		if (session == null || !session.isActive(now)) {
			return false;
		}
		SessionPolicy policy = currentPolicy();
		if (policy.idleTimeoutEnabled() && now.isAfter(activityAt(session).plusMinutes(policy.idleTimeoutMinutes()))) {
			return false;
		}
		return !policy.absoluteLifetimeEnabled()
				|| !now.isAfter(createdAt(session).plusDays(policy.absoluteLifetimeDays()));
	}

	boolean shouldTouch(AuthRefreshToken session, LocalDateTime now) {
		return activityAt(session).isBefore(now.minus(TOUCH_INTERVAL));
	}

	Instant cappedAccessExpiresAt(AuthRefreshToken session, Instant candidate) {
		return capByAbsoluteLifetime(session, candidate);
	}

	Instant cappedRefreshExpiresAt(AuthRefreshToken session, Instant candidate) {
		return capByAbsoluteLifetime(session, candidate);
	}

	SessionPolicyResponse response(AuthRefreshToken session, Instant now) {
		SessionPolicy policy = currentPolicy();
		Instant absoluteExpiresAt = policy.absoluteLifetimeEnabled()
				? toInstant(createdAt(session).plusDays(policy.absoluteLifetimeDays()))
				: null;
		return new SessionPolicyResponse(policy.idleTimeoutEnabled(), policy.idleTimeoutMinutes(),
				policy.absoluteLifetimeEnabled(), policy.absoluteLifetimeDays(), absoluteExpiresAt, now);
	}

	private Instant capByAbsoluteLifetime(AuthRefreshToken session, Instant candidate) {
		SessionPolicy policy = currentPolicy();
		if (!policy.absoluteLifetimeEnabled()) {
			return candidate;
		}
		Instant absoluteExpiresAt = toInstant(createdAt(session).plusDays(policy.absoluteLifetimeDays()));
		return candidate.isAfter(absoluteExpiresAt) ? absoluteExpiresAt : candidate;
	}

	private LocalDateTime activityAt(AuthRefreshToken session) {
		if (session.lastActivityAt() != null) {
			return session.lastActivityAt();
		}
		if (session.updatedAt() != null) {
			return session.updatedAt();
		}
		return createdAt(session);
	}

	private LocalDateTime createdAt(AuthRefreshToken session) {
		if (session.createdAt() != null) {
			return session.createdAt();
		}
		return session.lastActivityAt() == null ? LocalDateTime.now() : session.lastActivityAt();
	}

	private boolean booleanSetting(String key, boolean defaultValue) {
		return Boolean.parseBoolean(settings.get(key, String.valueOf(defaultValue)));
	}

	private int rangeIntSetting(String key, int defaultValue, int min, int max) {
		try {
			int value = Integer.parseInt(settings.get(key, String.valueOf(defaultValue)));
			return value < min || value > max ? defaultValue : value;
		}
		catch (NumberFormatException ex) {
			return defaultValue;
		}
	}

	private static Instant toInstant(LocalDateTime time) {
		return time.atZone(ZoneId.systemDefault()).toInstant();
	}

	record SessionPolicy(boolean idleTimeoutEnabled, int idleTimeoutMinutes,
			boolean absoluteLifetimeEnabled, int absoluteLifetimeDays) {
	}
}
