package edu.wzut.wallpaper.web;

import java.util.Map;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import edu.wzut.wallpaper.common.ApiResponse;
import edu.wzut.wallpaper.config.SecurityProperties;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

@RestController
@RequestMapping("/api/auth")
class AuthController {

	private final SecurityProperties securityProperties;

	AuthController(SecurityProperties securityProperties) {
		this.securityProperties = securityProperties;
	}

	@PostMapping("/login")
	ApiResponse<Map<String, Object>> login(@Valid @RequestBody LoginRequest request) {
		return ApiResponse.ok(Map.of(
				"username", request.username(),
				"tokenType", "Bearer",
				"accessToken", securityProperties.developmentToken()));
	}

	@PostMapping("/register")
	ApiResponse<Void> register(@Valid @RequestBody RegisterRequest request) {
		return ApiResponse.ok();
	}

	record LoginRequest(@NotBlank String username, @NotBlank String password) {
	}

	record RegisterRequest(@NotBlank String username, @NotBlank String password, @NotBlank String displayName) {
	}
}
