package edu.wzut.wallpaper.audit;

import java.time.LocalDateTime;
import java.util.List;

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
	public List<AuditLogResponse> list(String keyword, int limit) {
		String query = keyword == null || keyword.isBlank() ? null : keyword.trim();
		return repository.search(query, PageRequest.of(0, Math.min(Math.max(limit, 1), 200))).stream()
				.map(AuditLogResponse::from)
				.toList();
	}

	public record AuditLogResponse(String id, String action, String targetType, String targetId, String detailJson,
			LocalDateTime createdAt) {
		static AuditLogResponse from(AuditLog log) {
			return new AuditLogResponse(log.getId(), log.getAction(), log.getTargetType(), log.getTargetId(),
					log.getDetailJson(), log.getCreatedAt());
		}
	}
}
