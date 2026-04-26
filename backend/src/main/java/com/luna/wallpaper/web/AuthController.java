package com.luna.wallpaper.web;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.luna.wallpaper.common.ApiResponse;
import com.luna.wallpaper.rbac.AuthDtos.AuthResponse;
import com.luna.wallpaper.rbac.AuthDtos.AuthUserResponse;
import com.luna.wallpaper.rbac.AuthDtos.LoginRequest;
import com.luna.wallpaper.rbac.AuthDtos.PasswordChangeRequest;
import com.luna.wallpaper.rbac.AuthDtos.ProfileUpdateRequest;
import com.luna.wallpaper.rbac.AuthDtos.RefreshRequest;
import com.luna.wallpaper.rbac.AuthDtos.RegisterRequest;
import com.luna.wallpaper.rbac.AuthService;

import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/api/auth")
class AuthController {

	private final AuthService authService;

	AuthController(AuthService authService) {
		this.authService = authService;
	}

	@PostMapping("/login")
	ApiResponse<AuthResponse> login(@Valid @RequestBody LoginRequest request, HttpServletRequest servletRequest) {
		return ApiResponse.ok(authService.login(request, servletRequest));
	}

	@PostMapping("/register")
	ApiResponse<AuthResponse> register(@Valid @RequestBody RegisterRequest request, HttpServletRequest servletRequest) {
		return ApiResponse.ok(authService.register(request, servletRequest));
	}

	@PostMapping("/refresh")
	ApiResponse<AuthResponse> refresh(@Valid @RequestBody RefreshRequest request, HttpServletRequest servletRequest) {
		return ApiResponse.ok(authService.refresh(request, servletRequest));
	}

	@PostMapping("/logout")
	ApiResponse<Void> logout(Authentication authentication) {
		authService.logout(authentication);
		return ApiResponse.ok();
	}

	@GetMapping("/me")
	ApiResponse<AuthUserResponse> me(Authentication authentication) {
		return ApiResponse.ok(authService.me(authentication));
	}

	@PatchMapping("/profile")
	ApiResponse<AuthUserResponse> updateProfile(Authentication authentication,
			@Valid @RequestBody ProfileUpdateRequest request) {
		return ApiResponse.ok(authService.updateProfile(authentication, request));
	}

	@PatchMapping("/password")
	ApiResponse<Void> changePassword(Authentication authentication, @Valid @RequestBody PasswordChangeRequest request) {
		authService.changePassword(authentication, request);
		return ApiResponse.ok();
	}
}
