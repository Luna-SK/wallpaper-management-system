package edu.wzut.wallpaper.audit;

import java.util.List;
import java.util.Map;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import edu.wzut.wallpaper.common.ApiResponse;
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
				"expiredCount", archiveService.countExpiredLogs()));
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
	ApiResponse<List<AuditArchiveRunResponse>> archiveRuns(
			@RequestParam(name = "limit", defaultValue = "20") int limit) {
		return ApiResponse.ok(archiveService.listArchiveRuns(limit));
	}
}
