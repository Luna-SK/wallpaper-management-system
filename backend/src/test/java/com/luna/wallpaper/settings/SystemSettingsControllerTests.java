package com.luna.wallpaper.settings;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.servlet.autoconfigure.MultipartProperties;
import org.springframework.util.unit.DataSize;

import com.luna.wallpaper.audit.AuditLogService;

class SystemSettingsControllerTests {

	private final SystemSettingService settings = mock(SystemSettingService.class);
	private final MultipartProperties multipartProperties = new MultipartProperties();
	private final SystemSettingsController controller = new SystemSettingsController(settings,
			mock(AuditLogService.class), new UploadLimitService(settings, multipartProperties));

	@BeforeEach
	void setUp() {
		multipartProperties.setMaxFileSize(DataSize.ofMegabytes(50));
		multipartProperties.setMaxRequestSize(DataSize.ofMegabytes(500));
		when(settings.get(eq("preview.quality"), anyString())).thenAnswer(invocation -> invocation.getArgument(1));
		when(settings.get(eq("soft_delete.retention_days"), anyString())).thenAnswer(invocation -> invocation.getArgument(1));
		when(settings.get(eq("soft_delete.cleanup.enabled"), anyString())).thenAnswer(invocation -> invocation.getArgument(1));
		when(settings.get(eq("soft_delete.cleanup.cron"), anyString())).thenAnswer(invocation -> invocation.getArgument(1));
		when(settings.get(eq("watermark.enabled"), anyString())).thenAnswer(invocation -> invocation.getArgument(1));
		when(settings.get(eq("watermark.preview.enabled"), anyString())).thenAnswer(invocation -> invocation.getArgument(1));
		when(settings.get(eq("watermark.text"), anyString())).thenAnswer(invocation -> invocation.getArgument(1));
		when(settings.get(eq("watermark.mode"), anyString())).thenAnswer(invocation -> invocation.getArgument(1));
		when(settings.get(eq("watermark.position"), anyString())).thenAnswer(invocation -> invocation.getArgument(1));
		when(settings.get(eq("watermark.opacity_percent"), anyString())).thenAnswer(invocation -> invocation.getArgument(1));
		when(settings.get(eq("watermark.tile_density"), anyString())).thenAnswer(invocation -> invocation.getArgument(1));
		when(settings.get(eq("session.idle_timeout.enabled"), anyString())).thenAnswer(invocation -> invocation.getArgument(1));
		when(settings.get(eq("session.idle_timeout_minutes"), anyString())).thenAnswer(invocation -> invocation.getArgument(1));
		when(settings.get(eq("session.absolute_lifetime.enabled"), anyString())).thenAnswer(invocation -> invocation.getArgument(1));
		when(settings.get(eq("session.absolute_lifetime_days"), anyString())).thenAnswer(invocation -> invocation.getArgument(1));
	}

	@Test
	void getClipsBusinessLimitsToHardLimits() {
		when(settings.get(eq("upload.max_file_size_mb"), anyString())).thenReturn("80");
		when(settings.get(eq("upload.max_batch_size_mb"), anyString())).thenReturn("800");

		var response = controller.get();

		assertThat(response.maxFileSizeMb()).isEqualTo(50);
		assertThat(response.maxBatchSizeMb()).isEqualTo(500);
		assertThat(response.maxFileHardLimitMb()).isEqualTo(50);
		assertThat(response.maxBatchHardLimitMb()).isEqualTo(500);
	}

	@Test
	void updateRejectsFileLimitAboveHardLimit() {
		var request = request(60, 100, "ORIGINAL", 180, false, "0 0 3 * * SUN", true, false,
				"仅供授权使用", "CORNER", "BOTTOM_RIGHT", 16, "SPARSE");

		assertThatThrownBy(() -> controller.update(request))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage("单文件上限不能超过系统硬上限 50 MB");
	}

	@Test
	void updateRejectsBatchLimitAboveHardLimit() {
		var request = request(40, 600, "ORIGINAL", 180, false, "0 0 3 * * SUN", true, false,
				"仅供授权使用", "CORNER", "BOTTOM_RIGHT", 16, "SPARSE");

		assertThatThrownBy(() -> controller.update(request))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage("批量上传上限不能超过系统硬上限 500 MB");
	}

	@Test
	void updateRejectsBatchLimitBelowFileLimit() {
		var request = request(40, 20, "ORIGINAL", 180, false, "0 0 3 * * SUN", true, false,
				"仅供授权使用", "CORNER", "BOTTOM_RIGHT", 16, "SPARSE");

		assertThatThrownBy(() -> controller.update(request))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage("批量上传上限必须不小于单文件上限");
	}

	@Test
	void getReturnsSoftDeleteCleanupFlag() {
		when(settings.get(eq("soft_delete.cleanup.enabled"), anyString())).thenReturn("true");

		var response = controller.get();

		assertThat(response.softDeleteCleanupEnabled()).isTrue();
	}

	@Test
	void getReturnsDefaultSoftDeleteCleanupCron() {
		var response = controller.get();

		assertThat(response.softDeleteCleanupCron()).isEqualTo("0 0 3 * * SUN");
	}

	@Test
	void getFallsBackWhenSoftDeleteCleanupCronIsInvalid() {
		when(settings.get(eq("soft_delete.cleanup.cron"), anyString())).thenReturn("bad cron");

		var response = controller.get();

		assertThat(response.softDeleteCleanupCron()).isEqualTo("0 0 3 * * SUN");
	}

	@Test
	void updateRejectsInvalidSoftDeleteCleanupCron() {
		var request = request(10, 100, "ORIGINAL", 180, false, "bad cron", true, false,
				"仅供授权使用", "CORNER", "BOTTOM_RIGHT", 16, "SPARSE");

		assertThatThrownBy(() -> controller.update(request))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage("自动清理执行计划必须是有效的 Spring cron 表达式");
	}

	@Test
	void updateSavesValidSoftDeleteCleanupCron() {
		var request = request(10, 100, "ORIGINAL", 180, true, "0 0 * * * *", true, false,
				"仅供授权使用", "CORNER", "BOTTOM_RIGHT", 16, "SPARSE");

		controller.update(request);

		verify(settings).put("soft_delete.cleanup.cron", "0 0 * * * *");
	}

	@Test
	void getReturnsWatermarkSettings() {
		when(settings.get(eq("watermark.enabled"), anyString())).thenReturn("true");
		when(settings.get(eq("watermark.preview.enabled"), anyString())).thenReturn("true");
		when(settings.get(eq("watermark.text"), anyString())).thenReturn("内部版权");
		when(settings.get(eq("watermark.mode"), anyString())).thenReturn("TILED");
		when(settings.get(eq("watermark.position"), anyString())).thenReturn("CENTER");
		when(settings.get(eq("watermark.opacity_percent"), anyString())).thenReturn("24");
		when(settings.get(eq("watermark.tile_density"), anyString())).thenReturn("NORMAL");

		var response = controller.get();

		assertThat(response.watermarkEnabled()).isTrue();
		assertThat(response.watermarkPreviewEnabled()).isTrue();
		assertThat(response.watermarkText()).isEqualTo("内部版权");
		assertThat(response.watermarkMode()).isEqualTo("TILED");
		assertThat(response.watermarkPosition()).isEqualTo("CENTER");
		assertThat(response.watermarkOpacityPercent()).isEqualTo(24);
		assertThat(response.watermarkTileDensity()).isEqualTo("NORMAL");
	}

	@Test
	void updateRejectsBlankWatermarkTextWhenEnabled() {
		var request = request(10, 100, "ORIGINAL", 180, false, "0 0 3 * * SUN", true, false,
				" ", "CORNER", "BOTTOM_RIGHT", 16, "SPARSE");

		assertThatThrownBy(() -> controller.update(request))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage("启用水印时必须填写水印文字");
	}

	@Test
	void updateRejectsBlankWatermarkTextWhenPreviewWatermarkEnabled() {
		var request = request(10, 100, "ORIGINAL", 180, false, "0 0 3 * * SUN", false, true,
				" ", "CORNER", "BOTTOM_RIGHT", 16, "SPARSE");

		assertThatThrownBy(() -> controller.update(request))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage("启用水印时必须填写水印文字");
	}

	@Test
	void updateRejectsTooLongWatermarkText() {
		var request = request(10, 100, "ORIGINAL", 180, false, "0 0 3 * * SUN", true, false,
				"水".repeat(65), "CORNER", "BOTTOM_RIGHT", 16, "SPARSE");

		assertThatThrownBy(() -> controller.update(request))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage("水印文字不能超过 64 个字符");
	}

	@Test
	void updateSavesWatermarkSettings() {
		var request = request(10, 100, "ORIGINAL", 180, false, "0 0 3 * * SUN", true, true,
				"版权文字", "TILED", "CENTER", 24, "NORMAL");

		controller.update(request);

		verify(settings).put("watermark.enabled", "true");
		verify(settings).put("watermark.preview.enabled", "true");
		verify(settings).put("watermark.text", "版权文字");
		verify(settings).put("watermark.mode", "TILED");
		verify(settings).put("watermark.position", "CENTER");
		verify(settings).put("watermark.opacity_percent", "24");
		verify(settings).put("watermark.tile_density", "NORMAL");
	}

	@Test
	void updateRejectsInvalidWatermarkStyleSettings() {
		var invalidMode = request(10, 100, "ORIGINAL", 180, false, "0 0 3 * * SUN", true, false,
				"版权文字", "BAD", "BOTTOM_RIGHT", 16, "SPARSE");
		assertThatThrownBy(() -> controller.update(invalidMode))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage("水印样式只能是 CORNER 或 TILED");

		var invalidPosition = request(10, 100, "ORIGINAL", 180, false, "0 0 3 * * SUN", true, false,
				"版权文字", "CORNER", "BAD", 16, "SPARSE");
		assertThatThrownBy(() -> controller.update(invalidPosition))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage("水印位置不正确");

		var invalidOpacity = request(10, 100, "ORIGINAL", 180, false, "0 0 3 * * SUN", true, false,
				"版权文字", "CORNER", "BOTTOM_RIGHT", 41, "SPARSE");
		assertThatThrownBy(() -> controller.update(invalidOpacity))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage("水印透明度必须在 5-40 之间");

		var invalidDensity = request(10, 100, "ORIGINAL", 180, false, "0 0 3 * * SUN", true, false,
				"版权文字", "TILED", "BOTTOM_RIGHT", 16, "BAD");
		assertThatThrownBy(() -> controller.update(invalidDensity))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage("平铺水印密度只能是 SPARSE、NORMAL 或 DENSE");
	}

	@Test
	void getReturnsSessionLifecycleDefaults() {
		var response = controller.get();

		assertThat(response.sessionIdleTimeoutEnabled()).isTrue();
		assertThat(response.sessionIdleTimeoutMinutes()).isEqualTo(120);
		assertThat(response.sessionAbsoluteLifetimeEnabled()).isTrue();
		assertThat(response.sessionAbsoluteLifetimeDays()).isEqualTo(7);
	}

	@Test
	void updateSavesSessionLifecycleSettings() {
		var request = new SystemSettingsController.SystemSettingsRequest(10, 100, "ORIGINAL", 180,
				false, "0 0 3 * * SUN", true, false, "版权文字", "CORNER",
				"BOTTOM_RIGHT", 16, "SPARSE", true, 60, false, 14);

		controller.update(request);

		verify(settings).put("session.idle_timeout.enabled", "true");
		verify(settings).put("session.idle_timeout_minutes", "60");
		verify(settings).put("session.absolute_lifetime.enabled", "false");
		verify(settings).put("session.absolute_lifetime_days", "14");
	}

	@Test
	void updateRejectsInvalidSessionLifecycleRanges() {
		var invalidIdle = new SystemSettingsController.SystemSettingsRequest(10, 100, "ORIGINAL", 180,
				false, "0 0 3 * * SUN", true, false, "版权文字", "CORNER",
				"BOTTOM_RIGHT", 16, "SPARSE", true, 10, true, 7);
		assertThatThrownBy(() -> controller.update(invalidIdle))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage("登录空闲超时必须在 15-1440 分钟之间");

		var invalidAbsolute = new SystemSettingsController.SystemSettingsRequest(10, 100, "ORIGINAL", 180,
				false, "0 0 3 * * SUN", true, false, "版权文字", "CORNER",
				"BOTTOM_RIGHT", 16, "SPARSE", true, 120, true, 31);
		assertThatThrownBy(() -> controller.update(invalidAbsolute))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage("绝对会话时长必须在 1-30 天之间");
	}

	private SystemSettingsController.SystemSettingsRequest request(Integer maxFileSizeMb, Integer maxBatchSizeMb,
			String previewQuality, Integer softDeleteRetentionDays, Boolean softDeleteCleanupEnabled,
			String softDeleteCleanupCron, Boolean watermarkEnabled, Boolean watermarkPreviewEnabled, String watermarkText,
			String watermarkMode, String watermarkPosition, Integer watermarkOpacityPercent, String watermarkTileDensity) {
		return new SystemSettingsController.SystemSettingsRequest(maxFileSizeMb, maxBatchSizeMb, previewQuality,
				softDeleteRetentionDays, softDeleteCleanupEnabled, softDeleteCleanupCron, watermarkEnabled,
				watermarkPreviewEnabled, watermarkText, watermarkMode, watermarkPosition, watermarkOpacityPercent,
				watermarkTileDensity, true, 120, true, 7);
	}
}
