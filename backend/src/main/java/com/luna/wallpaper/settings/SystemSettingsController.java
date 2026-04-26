package com.luna.wallpaper.settings;

import java.util.Map;

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
				settings.get(WatermarkSettings.TEXT, WatermarkSettings.DEFAULT_TEXT));
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
		String watermarkText = normalizeWatermarkText(
				request.watermarkText() == null
						? settings.get(WatermarkSettings.TEXT, WatermarkSettings.DEFAULT_TEXT)
						: request.watermarkText(),
				watermarkEnabled);
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
		settings.put(WatermarkSettings.TEXT, watermarkText);
		auditLogService.record("settings.update", "SYSTEM_SETTINGS", "system",
				Map.of("previewQuality", quality, "watermarkEnabled", watermarkEnabled));
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

	private static String normalizeCron(String value, String defaultValue, String message) {
		String cron = value == null ? defaultValue : value.trim();
		if (!CronExpression.isValidExpression(cron)) {
			throw new IllegalArgumentException(message);
		}
		return cron;
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
			Boolean watermarkEnabled, String watermarkText) {
	}

	record SystemSettingsResponse(int maxFileSizeMb, int maxBatchSizeMb, int maxFileHardLimitMb,
			int maxBatchHardLimitMb, String previewQuality, int softDeleteRetentionDays,
			boolean softDeleteCleanupEnabled, String softDeleteCleanupCron, boolean watermarkEnabled,
			String watermarkText) {
	}
}
