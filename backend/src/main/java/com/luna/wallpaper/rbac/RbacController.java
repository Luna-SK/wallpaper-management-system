package com.luna.wallpaper.rbac;

import java.util.List;

import jakarta.validation.Valid;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.luna.wallpaper.rbac.RbacDtos.PermissionResponse;
import com.luna.wallpaper.rbac.RbacDtos.RolePermissionsRequest;
import com.luna.wallpaper.rbac.RbacDtos.RoleRequest;
import com.luna.wallpaper.rbac.RbacDtos.RoleResponse;
import com.luna.wallpaper.rbac.RbacDtos.UserPasswordResetRequest;
import com.luna.wallpaper.rbac.RbacDtos.UserRequest;
import com.luna.wallpaper.rbac.RbacDtos.UserResponse;
import com.luna.wallpaper.rbac.RbacDtos.UserRolesRequest;

@RestController
@RequestMapping("/api")
class RbacController {

	private final RbacService service;

	RbacController(RbacService service) {
		this.service = service;
	}

	@GetMapping("/users")
	@PreAuthorize("hasAuthority('user:manage')")
	List<UserResponse> users() {
		return service.users();
	}

	@PostMapping("/users")
	@PreAuthorize("hasAuthority('user:manage')")
	UserResponse createUser(@Valid @RequestBody UserRequest request) {
		return service.createUser(request);
	}

	@PatchMapping("/users/{id}")
	@PreAuthorize("hasAuthority('user:manage')")
	UserResponse updateUser(@PathVariable String id, @Valid @RequestBody UserRequest request) {
		return service.updateUser(id, request);
	}

	@PutMapping("/users/{id}/roles")
	@PreAuthorize("hasAuthority('user:manage')")
	UserResponse updateUserRoles(@PathVariable String id, @RequestBody UserRolesRequest request) {
		return service.updateUserRoles(id, request);
	}

	@PutMapping("/users/{id}/password")
	@PreAuthorize("hasAuthority('user:manage')")
	void resetUserPassword(@PathVariable String id, @Valid @RequestBody UserPasswordResetRequest request) {
		service.resetUserPassword(id, request);
	}

	@GetMapping("/roles")
	@PreAuthorize("hasAnyAuthority('user:manage','role:manage')")
	List<RoleResponse> roles() {
		return service.roles();
	}

	@PostMapping("/roles")
	@PreAuthorize("hasAuthority('role:manage')")
	RoleResponse createRole(@Valid @RequestBody RoleRequest request) {
		return service.createRole(request);
	}

	@PatchMapping("/roles/{id}")
	@PreAuthorize("hasAuthority('role:manage')")
	RoleResponse updateRole(@PathVariable String id, @Valid @RequestBody RoleRequest request) {
		return service.updateRole(id, request);
	}

	@PutMapping("/roles/{id}/permissions")
	@PreAuthorize("hasAuthority('role:manage')")
	RoleResponse updateRolePermissions(@PathVariable String id, @RequestBody RolePermissionsRequest request) {
		return service.updateRolePermissions(id, request);
	}

	@GetMapping("/permissions")
	@PreAuthorize("hasAnyAuthority('user:manage','role:manage')")
	List<PermissionResponse> permissions() {
		return service.permissions();
	}
}
