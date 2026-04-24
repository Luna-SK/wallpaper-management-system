package edu.wzut.wallpaper.rbac;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import edu.wzut.wallpaper.audit.AuditLogService;
import edu.wzut.wallpaper.rbac.RbacDtos.PermissionResponse;
import edu.wzut.wallpaper.rbac.RbacDtos.RolePermissionsRequest;
import edu.wzut.wallpaper.rbac.RbacDtos.RoleRequest;
import edu.wzut.wallpaper.rbac.RbacDtos.RoleResponse;
import edu.wzut.wallpaper.rbac.RbacDtos.UserRequest;
import edu.wzut.wallpaper.rbac.RbacDtos.UserResponse;
import edu.wzut.wallpaper.rbac.RbacDtos.UserRolesRequest;

@Service
class RbacService {

	private final AppUserRepository users;
	private final RoleRepository roles;
	private final PermissionRepository permissions;
	private final AuditLogService auditLogService;

	RbacService(AppUserRepository users, RoleRepository roles, PermissionRepository permissions,
			AuditLogService auditLogService) {
		this.users = users;
		this.roles = roles;
		this.permissions = permissions;
		this.auditLogService = auditLogService;
	}

	@Transactional(readOnly = true)
	List<UserResponse> users() {
		return users.findAllByOrderByUsernameAsc().stream().map(UserResponse::from).toList();
	}

	@Transactional
	UserResponse createUser(UserRequest request) {
		if (users.findByUsername(request.username()).isPresent()) {
			throw new IllegalArgumentException("账号已存在");
		}
		AppUser user = users.save(new AppUser(request.username().trim(), request.displayName().trim(), request.email(),
				request.phone()));
		user.update(user.displayName(), user.email(), user.phone(), normalizeStatus(request.status()));
		auditLogService.record("user.create", "USER", user.id(), "{\"username\":\"" + user.username() + "\"}");
		return UserResponse.from(user);
	}

	@Transactional
	UserResponse updateUser(String id, UserRequest request) {
		AppUser user = users.findById(id).orElseThrow(() -> new IllegalArgumentException("用户不存在"));
		user.update(request.displayName().trim(), request.email(), request.phone(), normalizeStatus(request.status()));
		auditLogService.record("user.update", "USER", id, "{\"status\":\"" + user.status() + "\"}");
		return UserResponse.from(user);
	}

	@Transactional
	UserResponse updateUserRoles(String id, UserRolesRequest request) {
		AppUser user = users.findById(id).orElseThrow(() -> new IllegalArgumentException("用户不存在"));
		Set<Role> selected = new LinkedHashSet<>(roles.findByIdIn(request.roleIds() == null ? List.of() : request.roleIds()));
		user.replaceRoles(selected);
		auditLogService.record("user.roles.update", "USER", id, "{\"roleCount\":" + selected.size() + "}");
		return UserResponse.from(user);
	}

	@Transactional(readOnly = true)
	List<RoleResponse> roles() {
		var userCounts = users.findAll().stream()
				.flatMap(user -> user.roles().stream().map(role -> role.id()))
				.collect(Collectors.groupingBy(Function.identity(), Collectors.summingInt(id -> 1)));
		return roles.findAllByOrderByCodeAsc().stream()
				.map(role -> RoleResponse.from(role, userCounts.getOrDefault(role.id(), 0)))
				.toList();
	}

	@Transactional
	RoleResponse createRole(RoleRequest request) {
		String code = normalizeCode(request.code());
		if (roles.findByCode(code).isPresent()) {
			throw new IllegalArgumentException("角色编码已存在");
		}
		Role role = roles.save(new Role(code, request.name().trim()));
		role.update(code, role.name(), request.enabled() == null || request.enabled());
		auditLogService.record("role.create", "ROLE", role.id(), "{\"code\":\"" + role.code() + "\"}");
		return RoleResponse.from(role, 0);
	}

	@Transactional
	RoleResponse updateRole(String id, RoleRequest request) {
		Role role = roles.findById(id).orElseThrow(() -> new IllegalArgumentException("角色不存在"));
		String code = normalizeCode(request.code());
		if (roles.existsByCodeAndIdNot(code, id)) {
			throw new IllegalArgumentException("角色编码已存在");
		}
		role.update(code, request.name().trim(), request.enabled() == null || request.enabled());
		auditLogService.record("role.update", "ROLE", id, "{\"enabled\":" + role.enabled() + "}");
		return RoleResponse.from(role, 0);
	}

	@Transactional
	RoleResponse updateRolePermissions(String id, RolePermissionsRequest request) {
		Role role = roles.findById(id).orElseThrow(() -> new IllegalArgumentException("角色不存在"));
		Set<Permission> selected = new LinkedHashSet<>(
				permissions.findByIdIn(request.permissionIds() == null ? List.of() : request.permissionIds()));
		role.replacePermissions(selected);
		auditLogService.record("role.permissions.update", "ROLE", id, "{\"permissionCount\":" + selected.size() + "}");
		return RoleResponse.from(role, 0);
	}

	@Transactional(readOnly = true)
	List<PermissionResponse> permissions() {
		return permissions.findAllByOrderByResourceAscActionAsc().stream().map(PermissionResponse::from).toList();
	}

	private static String normalizeCode(String code) {
		return code.trim().toUpperCase().replace(' ', '_');
	}

	private static String normalizeStatus(String status) {
		return status == null || status.isBlank() ? "ACTIVE" : status.trim().toUpperCase();
	}
}
