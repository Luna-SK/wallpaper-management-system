package com.luna.wallpaper.audit;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;

interface AuditLogRepository extends JpaRepository<AuditLog, String> {

	List<AuditLog> findByCreatedAtBeforeOrderByCreatedAtAsc(LocalDateTime cutoffTime, Pageable pageable);

	long countByCreatedAtBefore(LocalDateTime cutoffTime);

	@Query("""
			select log from AuditLog log
			where (:keyword is null or lower(log.action) like lower(concat('%', :keyword, '%'))
				or lower(log.targetType) like lower(concat('%', :keyword, '%'))
				or lower(log.targetId) like lower(concat('%', :keyword, '%')))
			order by log.createdAt desc
			""")
	List<AuditLog> search(@Param("keyword") String keyword, Pageable pageable);
}
