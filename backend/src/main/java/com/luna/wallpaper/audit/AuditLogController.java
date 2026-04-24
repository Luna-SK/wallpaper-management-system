package com.luna.wallpaper.audit;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.luna.wallpaper.audit.AuditLogService.AuditLogPageResponse;

@RestController
@RequestMapping("/api/audit-logs")
class AuditLogController {

	private final AuditLogService service;

	AuditLogController(AuditLogService service) {
		this.service = service;
	}

	@GetMapping
	@PreAuthorize("hasAuthority('audit:view')")
	AuditLogPageResponse list(@RequestParam(required = false) String keyword,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
			@RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int size) {
		return service.list(keyword, startDate, endDate, page, size);
	}

	@GetMapping("/export")
	@PreAuthorize("hasAuthority('audit:view')")
	ResponseEntity<byte[]> export(@RequestParam(required = false) String keyword,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
		return ResponseEntity.ok()
				.contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
				.header(HttpHeaders.CONTENT_DISPOSITION,
						ContentDisposition.attachment().filename("audit-logs.csv", StandardCharsets.UTF_8).build().toString())
				.body(service.exportCsv(keyword, startDate, endDate));
	}
}
