package com.luna.wallpaper.rbac;

import java.time.Instant;
import java.util.List;

import com.luna.wallpaper.rbac.RbacDtos.PermissionResponse;
import com.luna.wallpaper.rbac.RbacDtos.RoleBrief;

import jakarta.validation.constraints.NotBlank;

public final class AuthDtos {
	private AuthDtos() {
	}

	public record LoginRequest(@NotBlank String username, @NotBlank String password) {
	}

	public record RegisterRequest(@NotBlank String username, @NotBlank String password,
			@NotBlank String displayName, String email, String phone) {
	}

	public record RefreshRequest(@NotBlank String refreshToken) {
	}

	public record ProfileUpdateRequest(@NotBlank String displayName, String email, String phone) {
	}

	public record PasswordChangeRequest(@NotBlank String currentPassword, @NotBlank String newPassword) {
	}

	public record PasswordResetRequest(@NotBlank String email) {
	}

	public record PasswordResetConfirmRequest(@NotBlank String token, @NotBlank String newPassword) {
	}

	public record PasswordResetPolicyResponse(boolean emailResetEnabled) {
	}

	public record AuthResponse(String username, String tokenType, String accessToken, String refreshToken,
			Instant accessTokenExpiresAt, Instant refreshTokenExpiresAt, AuthUserResponse user,
			List<String> permissions, SessionPolicyResponse sessionPolicy) {
	}

	public record SessionPolicyResponse(boolean idleTimeoutEnabled, int idleTimeoutMinutes,
			boolean absoluteLifetimeEnabled, int absoluteLifetimeDays, Instant absoluteExpiresAt,
			Instant serverTime) {
	}

	public record AuthUserResponse(String id, String username, String displayName, String email, String phone,
			String status, List<RoleBrief> roles, List<PermissionResponse> permissions) {
		static AuthUserResponse from(AppUser user, List<Permission> permissions) {
			return new AuthUserResponse(user.id(), user.username(), user.displayName(), user.email(), user.phone(),
					user.status().name(), user.roles().stream().map(RoleBrief::from).toList(),
					permissions.stream().map(PermissionResponse::from).toList());
		}
	}
}
