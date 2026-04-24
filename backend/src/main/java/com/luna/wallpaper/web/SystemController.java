package com.luna.wallpaper.web;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.luna.wallpaper.common.ApiResponse;

@RestController
@RequestMapping("/api/system")
class SystemController {

	@GetMapping("/health")
	ApiResponse<Map<String, Object>> health() {
		return ApiResponse.ok(Map.of(
				"service", "wallpaper-backend",
				"status", "UP",
				"time", OffsetDateTime.now()));
	}

	@GetMapping("/modules")
	ApiResponse<List<String>> modules() {
		return ApiResponse.ok(List.of(
				"auth", "users", "rbac", "images", "taxonomy",
				"audit", "settings", "backup", "statistics"));
	}
}
