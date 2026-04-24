package com.luna.wallpaper.audit;

import java.time.LocalDateTime;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

interface AuditLogArchiveRunRepository extends JpaRepository<AuditLogArchiveRun, String> {

	Page<AuditLogArchiveRun> findAllByOrderByStartedAtDesc(Pageable pageable);

	long countByStartedAtBefore(LocalDateTime cutoffTime);

	long deleteByStartedAtBefore(LocalDateTime cutoffTime);
}
