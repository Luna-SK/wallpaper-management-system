package com.luna.wallpaper.audit;

import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

interface AuditLogArchiveRunRepository extends JpaRepository<AuditLogArchiveRun, String> {

	List<AuditLogArchiveRun> findAllByOrderByStartedAtDesc(Pageable pageable);
}
