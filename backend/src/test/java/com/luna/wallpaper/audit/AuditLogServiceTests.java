package com.luna.wallpaper.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class AuditLogServiceTests {

	private final AuditLogRepository repository = org.mockito.Mockito.mock(AuditLogRepository.class);
	private final AuditLogService service = new AuditLogService(repository);

	@Test
	void exportsAllMatchingLogsAsCsv() {
		AuditLog log = auditLog("log-1", "image.update", "IMAGE", "image-1",
				"{\"title\":\"A \\\"quoted\\\" image\"}", LocalDateTime.of(2026, 4, 25, 9, 30));
		when(repository.findForExport("image", null, null)).thenReturn(List.of(log));

		String csv = new String(service.exportCsv(" image ", null, null), StandardCharsets.UTF_8);

		assertThat(csv).startsWith("操作,对象,时间,详情\n");
		assertThat(csv).contains("\"image.update\",\"IMAGE / image-1\",\"2026-04-25T09:30\"");
		assertThat(csv).contains("\"\"title\"\"");
		assertThat(csv).contains("quoted");
	}

	@Test
	void exportsInclusiveLocalDateRange() {
		LocalDate startDate = LocalDate.of(2026, 4, 1);
		LocalDate endDate = LocalDate.of(2026, 4, 25);

		service.exportCsv("download", startDate, endDate);

		verify(repository).findForExport("download",
				LocalDateTime.of(2026, 4, 1, 0, 0),
				LocalDateTime.of(2026, 4, 25, 23, 59, 59, 999_999_999));
	}

	@Test
	void listsInclusiveLocalDateRange() {
		LocalDate startDate = LocalDate.of(2026, 4, 1);
		LocalDate endDate = LocalDate.of(2026, 4, 25);
		PageRequest pageRequest = PageRequest.of(1, 50);
		when(repository.search("preview",
				LocalDateTime.of(2026, 4, 1, 0, 0),
				LocalDateTime.of(2026, 4, 25, 23, 59, 59, 999_999_999),
				pageRequest))
				.thenReturn(new PageImpl<>(List.of(), pageRequest, 0));

		service.list(" preview ", startDate, endDate, 2, 50);

		verify(repository).search("preview",
				LocalDateTime.of(2026, 4, 1, 0, 0),
				LocalDateTime.of(2026, 4, 25, 23, 59, 59, 999_999_999),
				pageRequest);
	}

	@Test
	void rejectsInvertedDateRange() {
		assertThatThrownBy(() -> service.exportCsv(null, LocalDate.of(2026, 4, 25),
				LocalDate.of(2026, 4, 1)))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("startDate");
	}

	@Test
	void rejectsInvertedListDateRange() {
		assertThatThrownBy(() -> service.list(null, LocalDate.of(2026, 4, 25),
				LocalDate.of(2026, 4, 1), 1, 20))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("startDate");
	}

	private AuditLog auditLog(String id, String action, String targetType, String targetId, String detailJson,
			LocalDateTime createdAt) {
		AuditLog log = new AuditLog();
		ReflectionTestUtils.setField(log, "id", id);
		ReflectionTestUtils.setField(log, "action", action);
		ReflectionTestUtils.setField(log, "targetType", targetType);
		ReflectionTestUtils.setField(log, "targetId", targetId);
		ReflectionTestUtils.setField(log, "detailJson", detailJson);
		ReflectionTestUtils.setField(log, "createdAt", createdAt);
		return log;
	}
}
