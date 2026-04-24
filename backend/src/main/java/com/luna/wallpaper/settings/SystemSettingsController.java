package com.luna.wallpaper.settings;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.luna.wallpaper.audit.AuditLogService;

@RestController
@RequestMapping("/api/system-settings")
class SystemSettingsController {

	private final SystemSettingService settings;
	private final AuditLogService auditLogService;

	SystemSettingsController(SystemSettingService settings, AuditLogService auditLogService) {
		this.settings = settings;
		this.auditLogService = auditLogService;
	}

	@GetMapping
	@PreAuthorize("hasAuthority('setting:manage')")
	SystemSettingsResponse get() {
		return new SystemSettingsResponse(
				intSetting("upload.max_file_size_mb", 50),
				intSetting("upload.max_batch_size_mb", 500),
				Boolean.parseBoolean(settings.get("watermark.enabled", "true")),
				settings.get("preview.quality", "ORIGINAL"),
				intSetting("soft_delete.retention_days", 180));
	}

	@PatchMapping
	@PreAuthorize("hasAuthority('setting:manage')")
	SystemSettingsResponse update(@RequestBody SystemSettingsRequest request) {
		int maxFile = requireRange(request.maxFileSizeMb(), 1, 2048, "单文件大小必须在 1-2048 MB 之间");
		int maxBatch = requireRange(request.maxBatchSizeMb(), maxFile, 10240, "批量大小必须不小于单文件大小且不超过 10240 MB");
		int retention = requireRange(request.softDeleteRetentionDays(), 1, 3650, "软删除保留期必须在 1-3650 天之间");
		String quality = request.previewQuality() == null ? "ORIGINAL" : request.previewQuality().trim().toUpperCase();
		if (!quality.equals("ORIGINAL") && !quality.equals("HIGH") && !quality.equals("STANDARD")) {
			throw new IllegalArgumentException("预览质量只能是 ORIGINAL、HIGH 或 STANDARD");
		}
		settings.put("upload.max_file_size_mb", String.valueOf(maxFile));
		settings.put("upload.max_batch_size_mb", String.valueOf(maxBatch));
		settings.put("watermark.enabled", String.valueOf(Boolean.TRUE.equals(request.watermarkEnabled())));
		settings.put("preview.quality", quality);
		settings.put("soft_delete.retention_days", String.valueOf(retention));
		auditLogService.record("settings.update", "SYSTEM_SETTINGS", "system", "{\"previewQuality\":\"" + quality + "\"}");
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

	record SystemSettingsRequest(Integer maxFileSizeMb, Integer maxBatchSizeMb, Boolean watermarkEnabled,
			String previewQuality, Integer softDeleteRetentionDays) {
	}

	record SystemSettingsResponse(int maxFileSizeMb, int maxBatchSizeMb, boolean watermarkEnabled, String previewQuality,
			int softDeleteRetentionDays) {
	}
}
