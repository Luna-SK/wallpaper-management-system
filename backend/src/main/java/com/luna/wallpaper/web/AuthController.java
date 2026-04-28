package com.luna.wallpaper.web;

import org.springframework.security.core.Authentication;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.luna.wallpaper.common.ApiResponse;
import com.luna.wallpaper.rbac.AuthDtos.AuthResponse;
import com.luna.wallpaper.rbac.AuthDtos.AuthUserResponse;
import com.luna.wallpaper.rbac.AuthDtos.LoginRequest;
import com.luna.wallpaper.rbac.AuthDtos.PasswordChangeRequest;
import com.luna.wallpaper.rbac.AuthDtos.PasswordResetConfirmRequest;
import com.luna.wallpaper.rbac.AuthDtos.PasswordResetPolicyResponse;
import com.luna.wallpaper.rbac.AuthDtos.PasswordResetRequest;
import com.luna.wallpaper.rbac.AuthDtos.ProfileUpdateRequest;
import com.luna.wallpaper.rbac.AuthDtos.RefreshRequest;
import com.luna.wallpaper.rbac.AuthDtos.RegisterRequest;
import com.luna.wallpaper.rbac.AuthDtos.SessionPolicyResponse;
import com.luna.wallpaper.rbac.AuthService;

import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

	private final AuthService authService;

	AuthController(AuthService authService) {
		this.authService = authService;
	}

	@PostMapping("/login")
	public ApiResponse<AuthResponse> login(@Valid @RequestBody LoginRequest request, HttpServletRequest servletRequest) {
		return ApiResponse.ok(authService.login(request, servletRequest));
	}

	@PostMapping("/register")
	public ApiResponse<AuthResponse> register(@Valid @RequestBody RegisterRequest request, HttpServletRequest servletRequest) {
		return ApiResponse.ok(authService.register(request, servletRequest));
	}

	@PostMapping("/refresh")
	public ApiResponse<AuthResponse> refresh(@Valid @RequestBody RefreshRequest request, HttpServletRequest servletRequest) {
		return ApiResponse.ok(authService.refresh(request, servletRequest));
	}

	@PostMapping("/logout")
	public ApiResponse<Void> logout(Authentication authentication) {
		authService.logout(authentication);
		return ApiResponse.ok();
	}

	@PostMapping({ "/password-reset/request", "/reset-password/request" })
	public ApiResponse<Void> requestPasswordReset(@Valid @RequestBody PasswordResetRequest request,
			HttpServletRequest servletRequest) {
		authService.requestPasswordReset(request, servletRequest);
		return ApiResponse.ok();
	}

	@GetMapping("/password-reset-policy")
	public ApiResponse<PasswordResetPolicyResponse> passwordResetPolicy() {
		return ApiResponse.ok(authService.passwordResetPolicy());
	}

	@PostMapping({ "/password-reset/confirm", "/reset-password/confirm" })
	public ApiResponse<Void> confirmPasswordReset(@Valid @RequestBody PasswordResetConfirmRequest request) {
		authService.confirmPasswordReset(request);
		return ApiResponse.ok();
	}

	@GetMapping("/me")
	public ApiResponse<AuthUserResponse> me(Authentication authentication) {
		return ApiResponse.ok(authService.me(authentication));
	}

	@GetMapping("/session-policy")
	public ApiResponse<SessionPolicyResponse> sessionPolicy(Authentication authentication) {
		return ApiResponse.ok(authService.sessionPolicy(authentication));
	}

	@PatchMapping("/profile")
	public ApiResponse<AuthUserResponse> updateProfile(Authentication authentication,
			@Valid @RequestBody ProfileUpdateRequest request) {
		return ApiResponse.ok(authService.updateProfile(authentication, request));
	}

	@PostMapping(path = "/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	public ApiResponse<AuthUserResponse> updateAvatar(Authentication authentication,
			@RequestParam("file") MultipartFile file) {
		return ApiResponse.ok(authService.updateAvatar(authentication, file));
	}

	@DeleteMapping("/avatar")
	public ApiResponse<AuthUserResponse> deleteAvatar(Authentication authentication) {
		return ApiResponse.ok(authService.deleteAvatar(authentication));
	}

	@PatchMapping("/password")
	public ApiResponse<Void> changePassword(Authentication authentication, @Valid @RequestBody PasswordChangeRequest request) {
		authService.changePassword(authentication, request);
		return ApiResponse.ok();
	}
}
