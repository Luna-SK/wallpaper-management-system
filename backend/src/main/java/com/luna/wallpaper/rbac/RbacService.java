package com.luna.wallpaper.rbac;

import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.luna.wallpaper.audit.AuditLogService;
import com.luna.wallpaper.rbac.RbacDtos.PermissionResponse;
import com.luna.wallpaper.rbac.RbacDtos.RolePermissionsRequest;
import com.luna.wallpaper.rbac.RbacDtos.RoleRequest;
import com.luna.wallpaper.rbac.RbacDtos.RoleResponse;
import com.luna.wallpaper.rbac.RbacDtos.UserRequest;
import com.luna.wallpaper.rbac.RbacDtos.UserResponse;
import com.luna.wallpaper.rbac.RbacDtos.UserRolesRequest;

@Service
class RbacService {

	private final AppUserMapper users;
	private final RoleMapper roles;
	private final PermissionMapper permissions;
	private final UserRoleMapper userRoles;
	private final RolePermissionMapper rolePermissions;
	private final AuthRefreshTokenMapper refreshTokens;
	private final PasswordEncoder passwordEncoder;
	private final AuditLogService auditLogService;

	RbacService(AppUserMapper users, RoleMapper roles, PermissionMapper permissions, UserRoleMapper userRoles,
			RolePermissionMapper rolePermissions, AuthRefreshTokenMapper refreshTokens, PasswordEncoder passwordEncoder,
			AuditLogService auditLogService) {
		this.users = users;
		this.roles = roles;
		this.permissions = permissions;
		this.userRoles = userRoles;
		this.rolePermissions = rolePermissions;
		this.refreshTokens = refreshTokens;
		this.passwordEncoder = passwordEncoder;
		this.auditLogService = auditLogService;
	}

	@Transactional(readOnly = true)
	List<UserResponse> users() {
		List<AppUser> result = users.selectOrdered();
		attachRoles(result);
		return result.stream().map(UserResponse::from).toList();
	}

	@Transactional
	UserResponse createUser(UserRequest request) {
		if (users.findByUsername(request.username()).isPresent()) {
			throw new IllegalArgumentException("用户名已存在");
		}
		requirePassword(request.initialPassword());
		AppUser user = new AppUser(request.username().trim(), request.displayName().trim(), request.email(), request.phone(),
				passwordEncoder.encode(request.initialPassword()));
		user.update(user.displayName(), user.email(), user.phone(), normalizeStatus(request.status()));
		users.insert(user);
		auditLogService.record("user.create", "USER", user.id(), Map.of("username", user.username()));
		return UserResponse.from(user);
	}

	@Transactional
	UserResponse updateUser(String id, UserRequest request) {
		AppUser user = getUser(id);
		user.update(request.displayName().trim(), request.email(), request.phone(), normalizeStatus(request.status()));
		users.updateById(user);
		auditLogService.record("user.update", "USER", id, Map.of("status", user.status().name()));
		return UserResponse.from(user);
	}

	@Transactional
	UserResponse updateUserRoles(String id, UserRolesRequest request) {
		AppUser user = getUser(id);
		Set<Role> selected = new LinkedHashSet<>(roles.selectBatchIds(request.roleIds() == null ? List.of() : request.roleIds()));
		userRoles.deleteByUserId(id);
		if (!selected.isEmpty()) {
			userRoles.insertBatch(id, selected.stream().map(Role::id).toList());
		}
		user.replaceRoles(selected);
		auditLogService.record("user.roles.update", "USER", id, Map.of("roleCount", selected.size()));
		return UserResponse.from(user);
	}

	@Transactional
	void resetUserPassword(String id, RbacDtos.UserPasswordResetRequest request) {
		requirePassword(request.newPassword());
		AppUser user = getUser(id);
		user.changePasswordHash(passwordEncoder.encode(request.newPassword()));
		users.updateById(user);
		refreshTokens.revokeByUserId(id);
		auditLogService.record("user.password.reset", "USER", id, Map.of("revokedSessions", true));
	}

	@Transactional(readOnly = true)
	List<RoleResponse> roles() {
		List<Role> result = roles.selectOrdered();
		attachPermissions(result);
		Map<String, Integer> userCounts = userRoles.countUsersByRole().stream()
				.collect(Collectors.toMap(RoleUserCount::roleId, RoleUserCount::userCount));
		return result.stream()
				.map(role -> RoleResponse.from(role, userCounts.getOrDefault(role.id(), 0)))
				.toList();
	}

	@Transactional
	RoleResponse createRole(RoleRequest request) {
		String code = normalizeCode(request.code());
		if (roles.findByCode(code).isPresent()) {
			throw new IllegalArgumentException("角色编码已存在");
		}
		Role role = new Role(code, request.name().trim());
		role.update(code, role.name(), request.enabled() == null || request.enabled());
		roles.insert(role);
		auditLogService.record("role.create", "ROLE", role.id(), Map.of("code", role.code()));
		return RoleResponse.from(role, 0);
	}

	@Transactional
	RoleResponse updateRole(String id, RoleRequest request) {
		Role role = getRole(id);
		String code = normalizeCode(request.code());
		if (roles.hasCodeExcludingId(code, id)) {
			throw new IllegalArgumentException("角色编码已存在");
		}
		role.update(code, request.name().trim(), request.enabled() == null || request.enabled());
		roles.updateById(role);
		auditLogService.record("role.update", "ROLE", id, Map.of("enabled", role.enabled()));
		return RoleResponse.from(role, 0);
	}

	@Transactional
	RoleResponse updateRolePermissions(String id, RolePermissionsRequest request) {
		Role role = getRole(id);
		Set<Permission> selected = new LinkedHashSet<>(
				permissions.selectBatchIds(request.permissionIds() == null ? List.of() : request.permissionIds()));
		rolePermissions.deleteByRoleId(id);
		if (!selected.isEmpty()) {
			rolePermissions.insertBatch(id, selected.stream().map(Permission::id).toList());
		}
		role.replacePermissions(selected);
		auditLogService.record("role.permissions.update", "ROLE", id, Map.of("permissionCount", selected.size()));
		return RoleResponse.from(role, 0);
	}

	@Transactional(readOnly = true)
	List<PermissionResponse> permissions() {
		return permissions.selectOrdered().stream().map(PermissionResponse::from).toList();
	}

	private AppUser getUser(String id) {
		return Optional.ofNullable(users.selectById(id)).orElseThrow(() -> new IllegalArgumentException("用户不存在"));
	}

	private Role getRole(String id) {
		return Optional.ofNullable(roles.selectById(id)).orElseThrow(() -> new IllegalArgumentException("角色不存在"));
	}

	private void attachRoles(List<AppUser> appUsers) {
		if (appUsers.isEmpty()) {
			return;
		}
		List<String> userIds = appUsers.stream().map(AppUser::id).toList();
		List<UserRoleLink> links = userRoles.selectByUserIds(userIds);
		if (links.isEmpty()) {
			return;
		}
		Map<String, Role> rolesById = roles.selectBatchIds(links.stream().map(UserRoleLink::roleId).distinct().toList())
				.stream()
				.collect(Collectors.toMap(Role::id, Function.identity()));
		Map<String, List<UserRoleLink>> linksByUser = links.stream().collect(Collectors.groupingBy(UserRoleLink::userId));
		for (AppUser user : appUsers) {
			Set<Role> assigned = linksByUser.getOrDefault(user.id(), List.of()).stream()
					.map(link -> rolesById.get(link.roleId()))
					.filter(java.util.Objects::nonNull)
					.sorted(Comparator.comparing(Role::code))
					.collect(Collectors.toCollection(LinkedHashSet::new));
			user.replaceRoles(assigned);
		}
	}

	private void attachPermissions(List<Role> rolesToAttach) {
		if (rolesToAttach.isEmpty()) {
			return;
		}
		List<String> roleIds = rolesToAttach.stream().map(Role::id).toList();
		List<RolePermissionLink> links = rolePermissions.selectByRoleIds(roleIds);
		if (links.isEmpty()) {
			return;
		}
		Map<String, Permission> permissionsById = permissions.selectBatchIds(
						links.stream().map(RolePermissionLink::permissionId).distinct().toList())
				.stream()
				.collect(Collectors.toMap(Permission::id, Function.identity()));
		Map<String, List<RolePermissionLink>> linksByRole = links.stream()
				.collect(Collectors.groupingBy(RolePermissionLink::roleId));
		for (Role role : rolesToAttach) {
			Set<Permission> assigned = linksByRole.getOrDefault(role.id(), List.of()).stream()
					.map(link -> permissionsById.get(link.permissionId()))
					.filter(java.util.Objects::nonNull)
					.sorted(Comparator.comparing(Permission::resource).thenComparing(Permission::action))
					.collect(Collectors.toCollection(LinkedHashSet::new));
			role.replacePermissions(assigned);
		}
	}

	private static String normalizeCode(String code) {
		return code.trim().toUpperCase().replace(' ', '_');
	}

	private static UserStatus normalizeStatus(String status) {
		return UserStatus.parseOrDefault(status, UserStatus.ACTIVE);
	}

	private static void requirePassword(String password) {
		if (password == null || password.length() < 6) {
			throw new IllegalArgumentException("密码至少需要 6 位");
		}
	}
}
