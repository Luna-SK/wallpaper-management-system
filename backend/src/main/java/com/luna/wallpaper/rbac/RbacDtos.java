package com.luna.wallpaper.rbac;

import java.util.List;

import jakarta.validation.constraints.NotBlank;

public final class RbacDtos {
	private RbacDtos() {
	}

	public record UserRequest(@NotBlank String username, @NotBlank String displayName, String email, String phone,
			String status) {
	}

	public record UserRolesRequest(List<String> roleIds) {
	}

	public record UserResponse(String id, String username, String displayName, String email, String phone, String status,
			List<RoleBrief> roles) {
		static UserResponse from(AppUser user) {
			return new UserResponse(user.id(), user.username(), user.displayName(), user.email(), user.phone(), user.status(),
					user.roles().stream().map(RoleBrief::from).toList());
		}
	}

	public record RoleRequest(@NotBlank String code, @NotBlank String name, Boolean enabled) {
	}

	public record RolePermissionsRequest(List<String> permissionIds) {
	}

	public record RoleResponse(String id, String code, String name, boolean enabled, int userCount,
			List<PermissionResponse> permissions) {
		static RoleResponse from(Role role, int userCount) {
			return new RoleResponse(role.id(), role.code(), role.name(), role.enabled(), userCount,
					role.permissions().stream().map(PermissionResponse::from).toList());
		}
	}

	public record RoleBrief(String id, String code, String name) {
		static RoleBrief from(Role role) {
			return new RoleBrief(role.id(), role.code(), role.name());
		}
	}

	public record PermissionResponse(String id, String code, String name, String resource, String action) {
		static PermissionResponse from(Permission permission) {
			return new PermissionResponse(permission.id(), permission.code(), permission.name(), permission.resource(),
					permission.action());
		}
	}
}
