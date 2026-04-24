package com.luna.wallpaper.audit;

import java.util.Map;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.luna.wallpaper.audit.AuditLogArchiveService.AuditArchiveRunPageResponse;
import com.luna.wallpaper.common.ApiResponse;
import jakarta.validation.Valid;

@RestController
class AuditLogArchiveController {

	private final AuditLogArchiveService archiveService;

	AuditLogArchiveController(AuditLogArchiveService archiveService) {
		this.archiveService = archiveService;
	}

	@GetMapping("/api/audit-log-retention")
	@PreAuthorize("hasAuthority('audit:view')")
	ApiResponse<Map<String, Object>> retention() {
		return ApiResponse.ok(Map.of(
				"settings", archiveService.getSettings(),
				"expiredCount", archiveService.countExpiredLogs(),
				"expiredArchiveRunCount", archiveService.countExpiredArchiveRuns()));
	}

	@PatchMapping("/api/audit-log-retention")
	@PreAuthorize("hasAuthority('audit:manage')")
	ApiResponse<AuditRetentionSettings> updateRetention(@Valid @RequestBody AuditRetentionUpdateRequest request) {
		return ApiResponse.ok(archiveService.updateSettings(request));
	}

	@PostMapping("/api/audit-log-archives")
	@PreAuthorize("hasAuthority('audit:manage')")
	ApiResponse<AuditArchiveRunResponse> archiveNow() {
		return ApiResponse.ok(archiveService.archiveNow(AuditLogArchiveService.TRIGGER_MANUAL));
	}

	@GetMapping("/api/audit-log-archives")
	@PreAuthorize("hasAuthority('audit:view')")
	ApiResponse<AuditArchiveRunPageResponse> archiveRuns(
			@RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int size) {
		return ApiResponse.ok(archiveService.listArchiveRuns(page, size));
	}
}
