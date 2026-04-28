package com.luna.wallpaper.settings;

import java.util.Map;
import java.util.Set;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.luna.wallpaper.audit.AuditLogService;

@RestController
@RequestMapping("/api/system-settings")
class SystemSettingsController {

	private static final String PREVIEW_QUALITY = "preview.quality";
	private static final Set<String> WATERMARK_MODES = Set.of("CORNER", "TILED");
	private static final Set<String> WATERMARK_POSITIONS = Set.of(
			"TOP_LEFT", "TOP_CENTER", "TOP_RIGHT",
			"CENTER_LEFT", "CENTER", "CENTER_RIGHT",
			"BOTTOM_LEFT", "BOTTOM_CENTER", "BOTTOM_RIGHT");
	private static final Set<String> WATERMARK_TILE_DENSITIES = Set.of("SPARSE", "NORMAL", "DENSE");

	private final SystemSettingService settings;
	private final AuditLogService auditLogService;
	private final UploadLimitService uploadLimitService;

	SystemSettingsController(SystemSettingService settings, AuditLogService auditLogService,
			UploadLimitService uploadLimitService) {
		this.settings = settings;
		this.auditLogService = auditLogService;
		this.uploadLimitService = uploadLimitService;
	}

	@GetMapping
	@PreAuthorize("hasAuthority('setting:manage')")
	SystemSettingsResponse get() {
		UploadLimitService.UploadLimitSettings uploadLimits = uploadLimitService.current();
		return new SystemSettingsResponse(
				uploadLimits.maxFileSizeMb(),
				uploadLimits.maxBatchSizeMb(),
				uploadLimits.maxFileHardLimitMb(),
				uploadLimits.maxBatchHardLimitMb(),
				settings.get(PREVIEW_QUALITY, "ORIGINAL"),
				intSetting(SoftDeleteCleanupSettings.RETENTION_DAYS, 180),
				booleanSetting(SoftDeleteCleanupSettings.CLEANUP_ENABLED, false),
				cronSetting(SoftDeleteCleanupSettings.CLEANUP_CRON, SoftDeleteCleanupSettings.DEFAULT_CLEANUP_CRON),
				booleanSetting(WatermarkSettings.ENABLED, true),
				booleanSetting(WatermarkSettings.PREVIEW_ENABLED, false),
				settings.get(WatermarkSettings.TEXT, WatermarkSettings.DEFAULT_TEXT),
				choiceSetting(WatermarkSettings.MODE, WatermarkSettings.DEFAULT_MODE, WATERMARK_MODES),
				choiceSetting(WatermarkSettings.POSITION, WatermarkSettings.DEFAULT_POSITION, WATERMARK_POSITIONS),
				rangeIntSetting(WatermarkSettings.OPACITY_PERCENT, WatermarkSettings.DEFAULT_OPACITY_PERCENT,
						WatermarkSettings.MIN_OPACITY_PERCENT, WatermarkSettings.MAX_OPACITY_PERCENT),
				choiceSetting(WatermarkSettings.TILE_DENSITY, WatermarkSettings.DEFAULT_TILE_DENSITY, WATERMARK_TILE_DENSITIES),
				booleanSetting(PasswordResetSettings.EMAIL_RESET_ENABLED,
						PasswordResetSettings.DEFAULT_EMAIL_RESET_ENABLED),
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

	@PatchMapping
	@PreAuthorize("hasAuthority('setting:manage')")
	SystemSettingsResponse update(@RequestBody SystemSettingsRequest request) {
		UploadLimitService.UploadLimitSettings uploadLimits =
				uploadLimitService.validateForSave(request.maxFileSizeMb(), request.maxBatchSizeMb());
		int retention = requireRange(request.softDeleteRetentionDays(), 1, 3650, "软删除保留期必须在 1-3650 天之间");
		String quality = request.previewQuality() == null ? "ORIGINAL" : request.previewQuality().trim().toUpperCase();
		if (!quality.equals("ORIGINAL") && !quality.equals("HIGH") && !quality.equals("STANDARD")) {
			throw new IllegalArgumentException("预览质量只能是 ORIGINAL、HIGH 或 STANDARD");
		}
		boolean watermarkEnabled = request.watermarkEnabled() == null
				? booleanSetting(WatermarkSettings.ENABLED, true)
				: request.watermarkEnabled();
		boolean watermarkPreviewEnabled = request.watermarkPreviewEnabled() == null
				? booleanSetting(WatermarkSettings.PREVIEW_ENABLED, false)
				: request.watermarkPreviewEnabled();
		String watermarkText = normalizeWatermarkText(
				request.watermarkText() == null
						? settings.get(WatermarkSettings.TEXT, WatermarkSettings.DEFAULT_TEXT)
						: request.watermarkText(),
				watermarkEnabled || watermarkPreviewEnabled);
		String watermarkMode = normalizeChoice(request.watermarkMode(), WatermarkSettings.DEFAULT_MODE, WATERMARK_MODES,
				"水印样式只能是 CORNER 或 TILED");
		String watermarkPosition = normalizeChoice(request.watermarkPosition(), WatermarkSettings.DEFAULT_POSITION,
				WATERMARK_POSITIONS, "水印位置不正确");
		int watermarkOpacityPercent = request.watermarkOpacityPercent() == null
				? intSetting(WatermarkSettings.OPACITY_PERCENT, WatermarkSettings.DEFAULT_OPACITY_PERCENT)
				: requireRange(request.watermarkOpacityPercent(), WatermarkSettings.MIN_OPACITY_PERCENT,
						WatermarkSettings.MAX_OPACITY_PERCENT, "水印透明度必须在 5-40 之间");
		String watermarkTileDensity = normalizeChoice(request.watermarkTileDensity(), WatermarkSettings.DEFAULT_TILE_DENSITY,
				WATERMARK_TILE_DENSITIES, "平铺水印密度只能是 SPARSE、NORMAL 或 DENSE");
		boolean passwordResetEmailEnabled = request.passwordResetEmailEnabled() == null
				? booleanSetting(PasswordResetSettings.EMAIL_RESET_ENABLED,
						PasswordResetSettings.DEFAULT_EMAIL_RESET_ENABLED)
				: request.passwordResetEmailEnabled();
		boolean sessionIdleTimeoutEnabled = request.sessionIdleTimeoutEnabled() == null
				? booleanSetting(SessionLifecycleSettings.IDLE_TIMEOUT_ENABLED,
						SessionLifecycleSettings.DEFAULT_IDLE_TIMEOUT_ENABLED)
				: request.sessionIdleTimeoutEnabled();
		int sessionIdleTimeoutMinutes = request.sessionIdleTimeoutMinutes() == null
				? rangeIntSetting(SessionLifecycleSettings.IDLE_TIMEOUT_MINUTES,
						SessionLifecycleSettings.DEFAULT_IDLE_TIMEOUT_MINUTES,
						SessionLifecycleSettings.MIN_IDLE_TIMEOUT_MINUTES,
						SessionLifecycleSettings.MAX_IDLE_TIMEOUT_MINUTES)
				: requireRange(request.sessionIdleTimeoutMinutes(),
						SessionLifecycleSettings.MIN_IDLE_TIMEOUT_MINUTES,
						SessionLifecycleSettings.MAX_IDLE_TIMEOUT_MINUTES,
						"登录空闲超时必须在 15-1440 分钟之间");
		boolean sessionAbsoluteLifetimeEnabled = request.sessionAbsoluteLifetimeEnabled() == null
				? booleanSetting(SessionLifecycleSettings.ABSOLUTE_LIFETIME_ENABLED,
						SessionLifecycleSettings.DEFAULT_ABSOLUTE_LIFETIME_ENABLED)
				: request.sessionAbsoluteLifetimeEnabled();
		int sessionAbsoluteLifetimeDays = request.sessionAbsoluteLifetimeDays() == null
				? rangeIntSetting(SessionLifecycleSettings.ABSOLUTE_LIFETIME_DAYS,
						SessionLifecycleSettings.DEFAULT_ABSOLUTE_LIFETIME_DAYS,
						SessionLifecycleSettings.MIN_ABSOLUTE_LIFETIME_DAYS,
						SessionLifecycleSettings.MAX_ABSOLUTE_LIFETIME_DAYS)
				: requireRange(request.sessionAbsoluteLifetimeDays(),
						SessionLifecycleSettings.MIN_ABSOLUTE_LIFETIME_DAYS,
						SessionLifecycleSettings.MAX_ABSOLUTE_LIFETIME_DAYS,
						"绝对会话时长必须在 1-30 天之间");
		String cleanupCron = normalizeCron(request.softDeleteCleanupCron(),
				SoftDeleteCleanupSettings.DEFAULT_CLEANUP_CRON,
				"自动清理执行计划必须是有效的 Spring cron 表达式");
		settings.put("upload.max_file_size_mb", String.valueOf(uploadLimits.maxFileSizeMb()));
		settings.put("upload.max_batch_size_mb", String.valueOf(uploadLimits.maxBatchSizeMb()));
		settings.put(PREVIEW_QUALITY, quality);
		settings.put(SoftDeleteCleanupSettings.RETENTION_DAYS, String.valueOf(retention));
		settings.put(SoftDeleteCleanupSettings.CLEANUP_ENABLED, String.valueOf(Boolean.TRUE.equals(request.softDeleteCleanupEnabled())));
		settings.put(SoftDeleteCleanupSettings.CLEANUP_CRON, cleanupCron);
		settings.put(WatermarkSettings.ENABLED, String.valueOf(watermarkEnabled));
		settings.put(WatermarkSettings.PREVIEW_ENABLED, String.valueOf(watermarkPreviewEnabled));
		settings.put(WatermarkSettings.TEXT, watermarkText);
		settings.put(WatermarkSettings.MODE, watermarkMode);
		settings.put(WatermarkSettings.POSITION, watermarkPosition);
		settings.put(WatermarkSettings.OPACITY_PERCENT, String.valueOf(watermarkOpacityPercent));
		settings.put(WatermarkSettings.TILE_DENSITY, watermarkTileDensity);
		settings.put(PasswordResetSettings.EMAIL_RESET_ENABLED, String.valueOf(passwordResetEmailEnabled));
		settings.put(SessionLifecycleSettings.IDLE_TIMEOUT_ENABLED, String.valueOf(sessionIdleTimeoutEnabled));
		settings.put(SessionLifecycleSettings.IDLE_TIMEOUT_MINUTES, String.valueOf(sessionIdleTimeoutMinutes));
		settings.put(SessionLifecycleSettings.ABSOLUTE_LIFETIME_ENABLED, String.valueOf(sessionAbsoluteLifetimeEnabled));
		settings.put(SessionLifecycleSettings.ABSOLUTE_LIFETIME_DAYS, String.valueOf(sessionAbsoluteLifetimeDays));
		auditLogService.record("settings.update", "SYSTEM_SETTINGS", "system",
				Map.of("previewQuality", quality, "watermarkEnabled", watermarkEnabled,
						"watermarkPreviewEnabled", watermarkPreviewEnabled, "watermarkMode", watermarkMode,
						"passwordResetEmailEnabled", passwordResetEmailEnabled,
						"sessionIdleTimeoutEnabled", sessionIdleTimeoutEnabled,
						"sessionAbsoluteLifetimeEnabled", sessionAbsoluteLifetimeEnabled));
		return get();
	}

	private int intSetting(String key, int defaultValue) {
		try {
			return Integer.parseInt(settings.get(key, String.valueOf(defaultValue)));
		}
		catch (NumberFormatException ex) {
			return defaultValue;
		}
	}

	private int rangeIntSetting(String key, int defaultValue, int min, int max) {
		int value = intSetting(key, defaultValue);
		return value < min || value > max ? defaultValue : value;
	}

	private static int requireRange(Integer value, int min, int max, String message) {
		if (value == null || value < min || value > max) {
			throw new IllegalArgumentException(message);
		}
		return value;
	}

	private boolean booleanSetting(String key, boolean defaultValue) {
		return Boolean.parseBoolean(settings.get(key, String.valueOf(defaultValue)));
	}

	private String cronSetting(String key, String defaultValue) {
		String cron = settings.get(key, defaultValue);
		return CronExpression.isValidExpression(cron) ? cron : defaultValue;
	}

	private String choiceSetting(String key, String defaultValue, Set<String> allowedValues) {
		String value = settings.get(key, defaultValue);
		return allowedValues.contains(value) ? value : defaultValue;
	}

	private static String normalizeCron(String value, String defaultValue, String message) {
		String cron = value == null ? defaultValue : value.trim();
		if (!CronExpression.isValidExpression(cron)) {
			throw new IllegalArgumentException(message);
		}
		return cron;
	}

	private static String normalizeChoice(String value, String defaultValue, Set<String> allowedValues, String message) {
		String normalized = value == null || value.isBlank() ? defaultValue : value.trim().toUpperCase();
		if (!allowedValues.contains(normalized)) {
			throw new IllegalArgumentException(message);
		}
		return normalized;
	}

	private static String normalizeWatermarkText(String value, boolean enabled) {
		String text = value == null ? "" : value.trim();
		if (text.length() > WatermarkSettings.MAX_TEXT_LENGTH) {
			throw new IllegalArgumentException("水印文字不能超过 64 个字符");
		}
		if (enabled && text.isBlank()) {
			throw new IllegalArgumentException("启用水印时必须填写水印文字");
		}
		return text.isBlank() ? WatermarkSettings.DEFAULT_TEXT : text;
	}

	record SystemSettingsRequest(Integer maxFileSizeMb, Integer maxBatchSizeMb, String previewQuality,
			Integer softDeleteRetentionDays, Boolean softDeleteCleanupEnabled, String softDeleteCleanupCron,
			Boolean watermarkEnabled, Boolean watermarkPreviewEnabled, String watermarkText, String watermarkMode,
			String watermarkPosition, Integer watermarkOpacityPercent, String watermarkTileDensity,
			Boolean passwordResetEmailEnabled, Boolean sessionIdleTimeoutEnabled, Integer sessionIdleTimeoutMinutes,
			Boolean sessionAbsoluteLifetimeEnabled, Integer sessionAbsoluteLifetimeDays) {
	}

	record SystemSettingsResponse(int maxFileSizeMb, int maxBatchSizeMb, int maxFileHardLimitMb,
			int maxBatchHardLimitMb, String previewQuality, int softDeleteRetentionDays,
			boolean softDeleteCleanupEnabled, String softDeleteCleanupCron, boolean watermarkEnabled,
			boolean watermarkPreviewEnabled, String watermarkText, String watermarkMode, String watermarkPosition,
			int watermarkOpacityPercent, String watermarkTileDensity, boolean passwordResetEmailEnabled,
			boolean sessionIdleTimeoutEnabled,
			int sessionIdleTimeoutMinutes, boolean sessionAbsoluteLifetimeEnabled, int sessionAbsoluteLifetimeDays) {
	}
}
