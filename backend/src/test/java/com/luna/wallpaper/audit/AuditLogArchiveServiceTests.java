package com.luna.wallpaper.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doAnswer;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import com.luna.wallpaper.config.StorageProperties;
import com.luna.wallpaper.settings.SystemSettingService;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;
import tools.jackson.databind.json.JsonMapper;

class AuditLogArchiveServiceTests {

	private final AuditLogRepository auditLogRepository = org.mockito.Mockito.mock(AuditLogRepository.class);
	private final AuditLogArchiveRunRepository archiveRunRepository =
			org.mockito.Mockito.mock(AuditLogArchiveRunRepository.class);
	private final SystemSettingService systemSettingService = org.mockito.Mockito.mock(SystemSettingService.class);
	private final S3Client s3Client = org.mockito.Mockito.mock(S3Client.class);
	private final Clock clock = Clock.fixed(Instant.parse("2026-04-24T02:30:00Z"), ZoneId.of("Asia/Shanghai"));

	@Test
	void archivesToRustfsBeforeDeletingExpiredLogs() {
		AuditLog log = auditLog("log-1", LocalDateTime.of(2025, 10, 1, 8, 0));
		AuditLogArchiveService service = service();

		when(auditLogRepository.findByCreatedAtBeforeOrderByCreatedAtAsc(any(), any(Pageable.class)))
				.thenReturn(List.of(log));
		when(archiveRunRepository.save(any(AuditLogArchiveRun.class))).thenAnswer(invocation -> invocation.getArgument(0));
		when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
				.thenReturn(PutObjectResponse.builder().build());

		AuditArchiveRunResponse response = service.archiveNow(AuditLogArchiveService.TRIGGER_MANUAL);

		assertThat(response.status()).isEqualTo("SUCCESS");
		assertThat(response.archivedCount()).isEqualTo(1);
		assertThat(response.deletedCount()).isEqualTo(1);
		assertThat(response.archiveObjectKey()).startsWith("audit-logs/2026/04/");
		verify(s3Client).putObject(any(PutObjectRequest.class), any(RequestBody.class));
		verify(auditLogRepository).deleteAllByIdInBatch(List.of("log-1"));
		verify(archiveRunRepository).deleteByStartedAtBefore(LocalDateTime.of(2025, 10, 26, 10, 30));
	}

	@Test
	void keepsDatabaseLogsWhenArchiveWriteFails() {
		AuditLog log = auditLog("log-2", LocalDateTime.of(2025, 10, 1, 8, 0));
		AuditLogArchiveService service = service();

		when(auditLogRepository.findByCreatedAtBeforeOrderByCreatedAtAsc(any(), any(Pageable.class)))
				.thenReturn(List.of(log));
		when(archiveRunRepository.save(any(AuditLogArchiveRun.class))).thenAnswer(invocation -> invocation.getArgument(0));
		when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
				.thenThrow(SdkClientException.create("rustfs unavailable"));

		AuditArchiveRunResponse response = service.archiveNow(AuditLogArchiveService.TRIGGER_MANUAL);

		assertThat(response.status()).isEqualTo("FAILED");
		assertThat(response.errorMessage()).contains("rustfs unavailable");
		verify(auditLogRepository, never()).deleteAllByIdInBatch(any());
	}

	@Test
	void updatesRetentionSettingsInSystemSettings() {
		AuditLogArchiveService service = service();

		AuditRetentionSettings updated = service.updateSettings(
				new AuditRetentionUpdateRequest(365, false, "0 0 3 * * *", "RUSTFS", 2000));

		assertThat(updated.retentionDays()).isEqualTo(365);
		assertThat(updated.archiveEnabled()).isFalse();
		assertThat(updated.archiveCron()).isEqualTo("0 0 3 * * *");
		assertThat(updated.batchSize()).isEqualTo(2000);
		verify(systemSettingService).put("audit.retention.days", "365");
		verify(systemSettingService).put("audit.archive.enabled", "false");
		verify(systemSettingService).put("audit.archive.cron", "0 0 3 * * *");
		verify(systemSettingService).put("audit.archive.storage", "RUSTFS");
		verify(systemSettingService).put("audit.archive.batch_size", "2000");
	}

	@Test
	void paginatesArchiveRuns() {
		AuditLogArchiveRun run = new AuditLogArchiveRun("run-1", AuditLogArchiveService.TRIGGER_MANUAL,
				LocalDateTime.of(2025, 10, 26, 10, 30));
		AuditLogArchiveService service = service();

		when(archiveRunRepository.findAllByOrderByStartedAtDesc(any(Pageable.class)))
				.thenReturn(new PageImpl<>(List.of(run), PageRequest.of(1, 20), 21));

		AuditLogArchiveService.AuditArchiveRunPageResponse response = service.listArchiveRuns(2, 20);

		assertThat(response.page()).isEqualTo(2);
		assertThat(response.size()).isEqualTo(20);
		assertThat(response.total()).isEqualTo(21);
		assertThat(response.items()).hasSize(1);
	}

	@Test
	void countsExpiredArchiveRunsUsingAuditRetentionDays() {
		AuditLogArchiveService service = service();
		LocalDateTime cutoffTime = LocalDateTime.of(2025, 10, 26, 10, 30);

		when(archiveRunRepository.countByStartedAtBefore(cutoffTime)).thenReturn(3L);

		assertThat(service.countExpiredArchiveRuns()).isEqualTo(3);
	}

	@Test
	void rejectsInvalidCronExpression() {
		AuditLogArchiveService service = service();

		assertThatThrownBy(() -> service.updateSettings(
				new AuditRetentionUpdateRequest(180, true, "bad cron", "RUSTFS", 1000)))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("archiveCron");
	}

	private AuditLogArchiveService service() {
		Map<String, String> settings = new HashMap<>();
		when(systemSettingService.get(anyString(), anyString()))
				.thenAnswer(invocation -> settings.getOrDefault(invocation.getArgument(0), invocation.getArgument(1)));
		doAnswer(invocation -> {
			settings.put(invocation.getArgument(0), invocation.getArgument(1));
			return null;
		}).when(systemSettingService).put(anyString(), anyString());
		return new AuditLogArchiveService(
				auditLogRepository,
				archiveRunRepository,
				systemSettingService,
				new StorageProperties(
						"http://localhost:19010",
						"cn-east-1",
						"access",
						"secret",
						"wallpaper-original",
						"wallpaper-preview",
						"wallpaper-thumbnail",
						"wallpaper-watermark",
						"wallpaper-audit"),
				s3Client,
				JsonMapper.builder().build(),
				clock);
	}

	private AuditLog auditLog(String id, LocalDateTime createdAt) {
		AuditLog log = new AuditLog();
		ReflectionTestUtils.setField(log, "id", id);
		ReflectionTestUtils.setField(log, "actorId", "user-1");
		ReflectionTestUtils.setField(log, "action", "IMAGE_UPLOAD");
		ReflectionTestUtils.setField(log, "targetType", "IMAGE");
		ReflectionTestUtils.setField(log, "targetId", "image-1");
		ReflectionTestUtils.setField(log, "ipAddress", "127.0.0.1");
		ReflectionTestUtils.setField(log, "userAgent", "JUnit");
		ReflectionTestUtils.setField(log, "detailJson", "{\"file\":\"IMG_0001.jpg\"}");
		ReflectionTestUtils.setField(log, "createdAt", createdAt);
		return log;
	}
}
