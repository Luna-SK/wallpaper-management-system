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
		var request = new SystemSettingsController.SystemSettingsRequest(60, 100, "ORIGINAL", 180, false,
				"0 0 3 * * SUN");

		assertThatThrownBy(() -> controller.update(request))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage("单文件上限不能超过系统硬上限 50 MB");
	}

	@Test
	void updateRejectsBatchLimitAboveHardLimit() {
		var request = new SystemSettingsController.SystemSettingsRequest(40, 600, "ORIGINAL", 180, false,
				"0 0 3 * * SUN");

		assertThatThrownBy(() -> controller.update(request))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage("批量上传上限不能超过系统硬上限 500 MB");
	}

	@Test
	void updateRejectsBatchLimitBelowFileLimit() {
		var request = new SystemSettingsController.SystemSettingsRequest(40, 20, "ORIGINAL", 180, false,
				"0 0 3 * * SUN");

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
		var request = new SystemSettingsController.SystemSettingsRequest(10, 100, "ORIGINAL", 180, false,
				"bad cron");

		assertThatThrownBy(() -> controller.update(request))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage("自动清理执行计划必须是有效的 Spring cron 表达式");
	}

	@Test
	void updateSavesValidSoftDeleteCleanupCron() {
		var request = new SystemSettingsController.SystemSettingsRequest(10, 100, "ORIGINAL", 180, true,
				"0 0 * * * *");

		controller.update(request);

		verify(settings).put("soft_delete.cleanup.cron", "0 0 * * * *");
	}
}
