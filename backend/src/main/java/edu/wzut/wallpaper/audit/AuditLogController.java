package edu.wzut.wallpaper.audit;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import edu.wzut.wallpaper.audit.AuditLogService.AuditLogResponse;

@RestController
@RequestMapping("/api/audit-logs")
class AuditLogController {

	private final AuditLogService service;

	AuditLogController(AuditLogService service) {
		this.service = service;
	}

	@GetMapping
	@PreAuthorize("hasAuthority('audit:view')")
	List<AuditLogResponse> list(@RequestParam(required = false) String keyword,
			@RequestParam(defaultValue = "50") int limit) {
		return service.list(keyword, limit);
	}
}
