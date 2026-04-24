package com.luna.wallpaper.audit;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;

interface AuditLogRepository extends JpaRepository<AuditLog, String> {

	List<AuditLog> findByCreatedAtBeforeOrderByCreatedAtAsc(LocalDateTime cutoffTime, Pageable pageable);

	long countByCreatedAtBefore(LocalDateTime cutoffTime);

	@Query(value = """
			select log from AuditLog log
			where (:keyword is null or lower(log.action) like lower(concat('%', :keyword, '%'))
				or lower(log.targetType) like lower(concat('%', :keyword, '%'))
				or lower(log.targetId) like lower(concat('%', :keyword, '%')))
				and (:startAt is null or log.createdAt >= :startAt)
				and (:endAt is null or log.createdAt <= :endAt)
			order by log.createdAt desc
			""",
			countQuery = """
			select count(log) from AuditLog log
			where (:keyword is null or lower(log.action) like lower(concat('%', :keyword, '%'))
				or lower(log.targetType) like lower(concat('%', :keyword, '%'))
				or lower(log.targetId) like lower(concat('%', :keyword, '%')))
				and (:startAt is null or log.createdAt >= :startAt)
				and (:endAt is null or log.createdAt <= :endAt)
			""")
	Page<AuditLog> search(@Param("keyword") String keyword, @Param("startAt") LocalDateTime startAt,
			@Param("endAt") LocalDateTime endAt, Pageable pageable);

	@Query("""
			select log from AuditLog log
			where (:keyword is null or lower(log.action) like lower(concat('%', :keyword, '%'))
				or lower(log.targetType) like lower(concat('%', :keyword, '%'))
				or lower(log.targetId) like lower(concat('%', :keyword, '%')))
				and (:startAt is null or log.createdAt >= :startAt)
				and (:endAt is null or log.createdAt <= :endAt)
			order by log.createdAt desc
			""")
	List<AuditLog> findForExport(@Param("keyword") String keyword, @Param("startAt") LocalDateTime startAt,
			@Param("endAt") LocalDateTime endAt);
}
