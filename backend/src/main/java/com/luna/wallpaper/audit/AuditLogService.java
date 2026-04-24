package com.luna.wallpaper.audit;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuditLogService {

	private final AuditLogRepository repository;

	AuditLogService(AuditLogRepository repository) {
		this.repository = repository;
	}

	@Transactional
	public void record(String action, String targetType, String targetId, String detailJson) {
		repository.save(new AuditLog(action, targetType, targetId, detailJson));
	}

	@Transactional(readOnly = true)
	public AuditLogPageResponse list(String keyword, LocalDate startDate, LocalDate endDate, int page, int size) {
		validateDateRange(startDate, endDate);
		String query = normalizeKeyword(keyword);
		LocalDateTime startAt = startAt(startDate);
		LocalDateTime endAt = endAt(endDate);
		int safePage = Math.max(1, page);
		int safeSize = Math.min(Math.max(1, size), 100);
		Page<AuditLog> result = repository.search(query, startAt, endAt, PageRequest.of(safePage - 1, safeSize));
		return new AuditLogPageResponse(result.getContent().stream().map(AuditLogResponse::from).toList(),
				safePage, safeSize, result.getTotalElements());
	}

	@Transactional(readOnly = true)
	public byte[] exportCsv(String keyword, LocalDate startDate, LocalDate endDate) {
		validateDateRange(startDate, endDate);
		LocalDateTime startAt = startAt(startDate);
		LocalDateTime endAt = endAt(endDate);
		List<AuditLog> logs = repository.findForExport(normalizeKeyword(keyword), startAt, endAt);
		return toCsv(logs).getBytes(StandardCharsets.UTF_8);
	}

	private void validateDateRange(LocalDate startDate, LocalDate endDate) {
		if (startDate != null && endDate != null && startDate.isAfter(endDate)) {
			throw new IllegalArgumentException("startDate must be before or equal to endDate");
		}
	}

	private LocalDateTime startAt(LocalDate startDate) {
		return startDate == null ? null : startDate.atStartOfDay();
	}

	private LocalDateTime endAt(LocalDate endDate) {
		return endDate == null ? null : endDate.plusDays(1).atStartOfDay().minusNanos(1);
	}

	private String normalizeKeyword(String keyword) {
		return keyword == null || keyword.isBlank() ? null : keyword.trim();
	}

	private String toCsv(List<AuditLog> logs) {
		StringBuilder csv = new StringBuilder("操作,对象,时间,详情\n");
		for (AuditLog log : logs) {
			csv.append(csvValue(log.getAction())).append(',')
					.append(csvValue(target(log))).append(',')
					.append(csvValue(log.getCreatedAt())).append(',')
					.append(csvValue(log.getDetailJson()))
					.append('\n');
		}
		return csv.toString();
	}

	private String target(AuditLog log) {
		if (log.getTargetType() == null || log.getTargetType().isBlank()) {
			return log.getTargetId() == null || log.getTargetId().isBlank() ? "-" : log.getTargetId();
		}
		if (log.getTargetId() == null || log.getTargetId().isBlank()) {
			return log.getTargetType();
		}
		return log.getTargetType() + " / " + log.getTargetId();
	}

	private String csvValue(Object value) {
		String text = value == null ? "" : String.valueOf(value);
		return "\"" + text.replace("\"", "\"\"") + "\"";
	}

	public record AuditLogPageResponse(List<AuditLogResponse> items, int page, int size, long total) {
	}

	public record AuditLogResponse(String id, String action, String targetType, String targetId, String detailJson,
			LocalDateTime createdAt) {
		static AuditLogResponse from(AuditLog log) {
			return new AuditLogResponse(log.getId(), log.getAction(), log.getTargetType(), log.getTargetId(),
					log.getDetailJson(), log.getCreatedAt());
		}
	}
}
