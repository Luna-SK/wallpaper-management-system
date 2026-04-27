package com.luna.wallpaper.rbac;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Base64;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.luna.wallpaper.audit.AuditLogService;
import com.luna.wallpaper.config.MailProperties;
import com.luna.wallpaper.config.SecurityProperties;
import com.luna.wallpaper.rbac.AuthDtos.AuthResponse;
import com.luna.wallpaper.rbac.AuthDtos.AuthUserResponse;
import com.luna.wallpaper.rbac.AuthDtos.LoginRequest;
import com.luna.wallpaper.rbac.AuthDtos.PasswordChangeRequest;
import com.luna.wallpaper.rbac.AuthDtos.PasswordResetConfirmRequest;
import com.luna.wallpaper.rbac.AuthDtos.PasswordResetRequest;
import com.luna.wallpaper.rbac.AuthDtos.ProfileUpdateRequest;
import com.luna.wallpaper.rbac.AuthDtos.RefreshRequest;
import com.luna.wallpaper.rbac.AuthDtos.RegisterRequest;
import com.luna.wallpaper.rbac.AuthDtos.SessionPolicyResponse;
import com.luna.wallpaper.rbac.JwtTokenService.AccessTokenClaims;

@Service
public class AuthService {

	private static final String TOKEN_TYPE = "Bearer";
	private static final int REFRESH_TOKEN_BYTES = 48;
	private static final int PASSWORD_RESET_TOKEN_BYTES = 48;
	private static final String VIEWER_ROLE_CODE = "VIEWER";

	private final AppUserMapper users;
	private final RoleMapper roles;
	private final PermissionMapper permissions;
	private final UserRoleMapper userRoles;
	private final RolePermissionMapper rolePermissions;
	private final AuthRefreshTokenMapper refreshTokens;
	private final PasswordResetTokenMapper passwordResetTokens;
	private final PasswordResetMailer passwordResetMailer;
	private final PasswordEncoder passwordEncoder;
	private final JwtTokenService jwtTokenService;
	private final SecurityProperties securityProperties;
	private final MailProperties mailProperties;
	private final AuditLogService auditLogService;
	private final SessionLifecyclePolicyService sessionLifecycle;
	private final SecureRandom secureRandom = new SecureRandom();

	AuthService(AppUserMapper users, RoleMapper roles, PermissionMapper permissions, UserRoleMapper userRoles,
			RolePermissionMapper rolePermissions, AuthRefreshTokenMapper refreshTokens,
			PasswordResetTokenMapper passwordResetTokens, PasswordResetMailer passwordResetMailer,
			PasswordEncoder passwordEncoder, JwtTokenService jwtTokenService, SecurityProperties securityProperties,
			MailProperties mailProperties, AuditLogService auditLogService, SessionLifecyclePolicyService sessionLifecycle) {
		this.users = users;
		this.roles = roles;
		this.permissions = permissions;
		this.userRoles = userRoles;
		this.rolePermissions = rolePermissions;
		this.refreshTokens = refreshTokens;
		this.passwordResetTokens = passwordResetTokens;
		this.passwordResetMailer = passwordResetMailer;
		this.passwordEncoder = passwordEncoder;
		this.jwtTokenService = jwtTokenService;
		this.securityProperties = securityProperties;
		this.mailProperties = mailProperties;
		this.auditLogService = auditLogService;
		this.sessionLifecycle = sessionLifecycle;
	}

	@Transactional
	public AuthResponse login(LoginRequest request, HttpServletRequest servletRequest) {
		AppUser user = users.findByUsername(normalizeUsername(request.username()))
				.orElseThrow(() -> new BadCredentialsException("用户名或密码错误"));
		if (!user.status().isActive()) {
			throw new DisabledException("用户已停用");
		}
		if (!passwordEncoder.matches(request.password(), user.passwordHash())) {
			throw new BadCredentialsException("用户名或密码错误");
		}
		UserAccess access = loadUserAccess(user);
		TokenBundle tokens = createSessionAndTokens(user, servletRequest);
		auditLogService.record("auth.login", "USER", user.id(), Map.of());
		return response(access, tokens);
	}

	@Transactional
	public AuthResponse register(RegisterRequest request, HttpServletRequest servletRequest) {
		String username = normalizeUsername(request.username());
		if (users.findByUsername(username).isPresent()) {
			throw new IllegalArgumentException("用户名已存在");
		}
		requirePassword(request.password());
		AppUser user = new AppUser(username, request.displayName().trim(), request.email(), request.phone(),
				passwordEncoder.encode(request.password()));
		users.insert(user);
		Role viewer = roles.findByCode(VIEWER_ROLE_CODE)
				.orElseThrow(() -> new IllegalStateException("默认浏览角色不存在"));
		userRoles.insertBatch(user.id(), List.of(viewer.id()));
		user.replaceRoles(new LinkedHashSet<>(List.of(viewer)));
		UserAccess access = loadUserAccess(user);
		TokenBundle tokens = createSessionAndTokens(user, servletRequest);
		auditLogService.record("auth.register", "USER", user.id(), Map.of("username", user.username()));
		return response(access, tokens);
	}

	@Transactional
	public AuthResponse refresh(RefreshRequest request, HttpServletRequest servletRequest) {
		AuthRefreshToken session = Optional.ofNullable(refreshTokens.selectByTokenHash(sha256(request.refreshToken())))
				.orElseThrow(() -> new BadCredentialsException("登录已失效，请重新登录"));
		LocalDateTime now = LocalDateTime.now();
		if (!sessionLifecycle.isValid(session, now)) {
			throw new BadCredentialsException("登录已失效，请重新登录");
		}
		AppUser user = requireActiveUser(session.userId());
		UserAccess access = loadUserAccess(user);
		TokenBundle tokens = rotateSessionAndTokens(session, user, servletRequest, now);
		auditLogService.record("auth.refresh", "USER", user.id(), Map.of("sessionId", session.id()));
		return response(access, tokens);
	}

	@Transactional
	public void logout(Authentication authentication) {
		AuthenticatedUser current = currentUser(authentication);
		refreshTokens.revokeById(current.sessionId());
		auditLogService.record("auth.logout", "USER", current.id(), Map.of("sessionId", current.sessionId()));
	}

	@Transactional(readOnly = true)
	public AuthUserResponse me(Authentication authentication) {
		AuthenticatedUser current = currentUser(authentication);
		return userResponse(loadUserAccess(requireActiveUser(current.id())));
	}

	@Transactional(readOnly = true)
	public SessionPolicyResponse sessionPolicy(Authentication authentication) {
		AuthenticatedUser current = currentUser(authentication);
		AuthRefreshToken session = Optional.ofNullable(refreshTokens.selectById(current.sessionId()))
				.orElseThrow(() -> new BadCredentialsException("登录已失效，请重新登录"));
		LocalDateTime now = LocalDateTime.now();
		if (!sessionLifecycle.isValid(session, now)) {
			throw new BadCredentialsException("登录已失效，请重新登录");
		}
		return sessionLifecycle.response(session, Instant.now());
	}

	@Transactional
	public AuthUserResponse updateProfile(Authentication authentication, ProfileUpdateRequest request) {
		AuthenticatedUser current = currentUser(authentication);
		AppUser user = requireActiveUser(current.id());
		user.update(request.displayName().trim(), request.email(), request.phone(), user.status());
		users.updateById(user);
		auditLogService.record("auth.profile.update", "USER", user.id(), Map.of());
		return userResponse(loadUserAccess(user));
	}

	@Transactional
	public void changePassword(Authentication authentication, PasswordChangeRequest request) {
		AuthenticatedUser current = currentUser(authentication);
		AppUser user = requireActiveUser(current.id());
		if (!passwordEncoder.matches(request.currentPassword(), user.passwordHash())) {
			throw new BadCredentialsException("当前密码不正确");
		}
		requirePassword(request.newPassword());
		user.changePasswordHash(passwordEncoder.encode(request.newPassword()));
		users.updateById(user);
		refreshTokens.revokeOtherSessions(user.id(), current.sessionId());
		auditLogService.record("auth.password.change", "USER", user.id(), Map.of("revokedOtherSessions", true));
	}

	@Transactional
	public void requestPasswordReset(PasswordResetRequest request, HttpServletRequest servletRequest) {
		String email = normalizeEmail(request.email());
		Instant expiresAt = Instant.now().plus(mailProperties.safePasswordResetTokenTtl());
		for (AppUser user : users.selectActiveByEmail(email)) {
			passwordResetTokens.consumeOpenTokensByUserId(user.id());
			String token = newPasswordResetToken();
			PasswordResetToken resetToken = new PasswordResetToken(user.id(), sha256(token),
					toLocalDateTime(expiresAt), clientIp(servletRequest), userAgent(servletRequest));
			passwordResetTokens.insert(resetToken);
			passwordResetMailer.send(user, token, expiresAt);
			auditLogService.record("auth.password.reset.request", "USER", user.id(),
					Map.of("emailHash", sha256(email), "expiresAt", expiresAt));
		}
	}

	@Transactional
	public void confirmPasswordReset(PasswordResetConfirmRequest request) {
		requirePassword(request.newPassword());
		PasswordResetToken resetToken = Optional.ofNullable(passwordResetTokens.selectByTokenHash(sha256(request.token())))
				.orElseThrow(() -> new BadCredentialsException("重置链接无效或已过期"));
		LocalDateTime now = LocalDateTime.now();
		if (!resetToken.isUsable(now)) {
			throw new BadCredentialsException("重置链接无效或已过期");
		}
		AppUser user = requireActiveUser(resetToken.userId());
		user.changePasswordHash(passwordEncoder.encode(request.newPassword()));
		users.updateById(user);
		resetToken.markUsed(now);
		passwordResetTokens.updateById(resetToken);
		passwordResetTokens.consumeOpenTokensByUserId(user.id());
		refreshTokens.revokeByUserId(user.id());
		auditLogService.record("auth.password.reset.confirm", "USER", user.id(), Map.of("revokedSessions", true));
	}

	@Transactional
	public void resetPassword(String userId, String newPassword) {
		requirePassword(newPassword);
		AppUser user = Optional.ofNullable(users.selectById(userId))
				.orElseThrow(() -> new IllegalArgumentException("用户不存在"));
		user.changePasswordHash(passwordEncoder.encode(newPassword));
		users.updateById(user);
		refreshTokens.revokeByUserId(user.id());
		auditLogService.record("user.password.reset", "USER", user.id(), Map.of("revokedSessions", true));
	}

	@Transactional
	public Optional<Authentication> authenticateAccessToken(String accessToken) {
		try {
			AccessTokenClaims claims = jwtTokenService.parse(accessToken);
			AuthRefreshToken session = refreshTokens.selectById(claims.sessionId());
			LocalDateTime now = LocalDateTime.now();
			if (!sessionLifecycle.isValid(session, now)) {
				return Optional.empty();
			}
			touchSessionIfNeeded(session, now);
			UserAccess access = loadUserAccess(requireActiveUser(claims.userId()));
			AuthenticatedUser principal = new AuthenticatedUser(access.user().id(), access.user().username(),
					access.user().displayName(), session.id());
			List<SimpleGrantedAuthority> authorities = access.permissions().stream()
					.map(Permission::code)
					.distinct()
					.map(SimpleGrantedAuthority::new)
					.toList();
			return Optional.of(new UsernamePasswordAuthenticationToken(principal, accessToken, authorities));
		}
		catch (RuntimeException ex) {
			return Optional.empty();
		}
	}

	private TokenBundle createSessionAndTokens(AppUser user, HttpServletRequest request) {
		String refreshToken = newRefreshToken();
		Instant now = Instant.now();
		Instant refreshExpiresAt = now.plus(securityProperties.safeRefreshTokenTtl());
		AuthRefreshToken session = new AuthRefreshToken(user.id(), sha256(refreshToken),
				toLocalDateTime(refreshExpiresAt), clientIp(request), userAgent(request));
		refreshTokens.insert(session);
		refreshExpiresAt = sessionLifecycle.cappedRefreshExpiresAt(session, refreshExpiresAt);
		if (session.expiresAt().isAfter(toLocalDateTime(refreshExpiresAt))) {
			session.rotate(session.tokenHash(), toLocalDateTime(refreshExpiresAt), session.lastActivityAt());
			refreshTokens.updateById(session);
		}
		return accessAndRefreshTokens(user, session, refreshToken, refreshExpiresAt);
	}

	private TokenBundle rotateSessionAndTokens(AuthRefreshToken session, AppUser user, HttpServletRequest request,
			LocalDateTime activityAt) {
		String refreshToken = newRefreshToken();
		Instant refreshExpiresAt = Instant.now().plus(securityProperties.safeRefreshTokenTtl());
		refreshExpiresAt = sessionLifecycle.cappedRefreshExpiresAt(session, refreshExpiresAt);
		session.rotate(sha256(refreshToken), toLocalDateTime(refreshExpiresAt), activityAt);
		refreshTokens.updateById(session);
		return accessAndRefreshTokens(user, session, refreshToken, refreshExpiresAt);
	}

	private TokenBundle accessAndRefreshTokens(AppUser user, AuthRefreshToken session, String refreshToken,
			Instant refreshExpiresAt) {
		Instant accessExpiresAt = sessionLifecycle.cappedAccessExpiresAt(session,
				Instant.now().plus(securityProperties.safeAccessTokenTtl()));
		String accessToken = jwtTokenService.createAccessToken(user, session.id(), accessExpiresAt);
		return new TokenBundle(accessToken, refreshToken, accessExpiresAt, refreshExpiresAt,
				sessionLifecycle.response(session, Instant.now()));
	}

	private void touchSessionIfNeeded(AuthRefreshToken session, LocalDateTime now) {
		if (!sessionLifecycle.shouldTouch(session, now)) {
			return;
		}
		session.markActivity(now);
		refreshTokens.updateById(session);
	}

	private AuthResponse response(UserAccess access, TokenBundle tokens) {
		List<String> permissionCodes = access.permissions().stream().map(Permission::code).distinct().toList();
		return new AuthResponse(access.user().username(), TOKEN_TYPE, tokens.accessToken(), tokens.refreshToken(),
				tokens.accessExpiresAt(), tokens.refreshExpiresAt(), userResponse(access), permissionCodes,
				tokens.sessionPolicy());
	}

	private AuthUserResponse userResponse(UserAccess access) {
		return AuthUserResponse.from(access.user(), access.permissions());
	}

	private UserAccess loadUserAccess(AppUser user) {
		List<UserRoleLink> links = userRoles.selectByUserIds(List.of(user.id()));
		List<Role> assignedRoles = links.isEmpty() ? List.of() : roles.selectBatchIds(
						links.stream().map(UserRoleLink::roleId).distinct().toList())
				.stream()
				.filter(Role::enabled)
				.sorted(Comparator.comparing(Role::code))
				.toList();
		List<Permission> assignedPermissions = loadPermissions(assignedRoles);
		Map<String, List<Permission>> permissionsByRole = permissionsByRole(assignedRoles, assignedPermissions);
		for (Role role : assignedRoles) {
			role.replacePermissions(new LinkedHashSet<>(permissionsByRole.getOrDefault(role.id(), List.of())));
		}
		user.replaceRoles(new LinkedHashSet<>(assignedRoles));
		return new UserAccess(user, assignedRoles, assignedPermissions);
	}

	private List<Permission> loadPermissions(List<Role> assignedRoles) {
		if (assignedRoles.isEmpty()) {
			return List.of();
		}
		List<RolePermissionLink> links = rolePermissions.selectByRoleIds(assignedRoles.stream().map(Role::id).toList());
		if (links.isEmpty()) {
			return List.of();
		}
		return permissions.selectBatchIds(links.stream().map(RolePermissionLink::permissionId).distinct().toList())
				.stream()
				.sorted(Comparator.comparing(Permission::resource).thenComparing(Permission::action))
				.toList();
	}

	private Map<String, List<Permission>> permissionsByRole(List<Role> assignedRoles, List<Permission> assignedPermissions) {
		if (assignedRoles.isEmpty() || assignedPermissions.isEmpty()) {
			return Map.of();
		}
		Map<String, Permission> permissionsById = assignedPermissions.stream()
				.collect(Collectors.toMap(Permission::id, Function.identity()));
		return rolePermissions.selectByRoleIds(assignedRoles.stream().map(Role::id).toList()).stream()
				.map(link -> new RolePermissionAssignment(link.roleId(), permissionsById.get(link.permissionId())))
				.filter(assignment -> assignment.permission() != null)
				.collect(Collectors.groupingBy(RolePermissionAssignment::roleId,
						Collectors.mapping(RolePermissionAssignment::permission, Collectors.toList())));
	}

	private AppUser requireActiveUser(String id) {
		AppUser user = Optional.ofNullable(users.selectById(id))
				.orElseThrow(() -> new BadCredentialsException("登录已失效，请重新登录"));
		if (!user.status().isActive()) {
			throw new DisabledException("用户已停用");
		}
		return user;
	}

	private AuthenticatedUser currentUser(Authentication authentication) {
		if (authentication != null && authentication.getPrincipal() instanceof AuthenticatedUser user) {
			return user;
		}
		throw new BadCredentialsException("请先登录");
	}

	private String newRefreshToken() {
		return newUrlToken(REFRESH_TOKEN_BYTES);
	}

	private String newPasswordResetToken() {
		return newUrlToken(PASSWORD_RESET_TOKEN_BYTES);
	}

	private String newUrlToken(int byteCount) {
		byte[] bytes = new byte[byteCount];
		secureRandom.nextBytes(bytes);
		return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
	}

	static String sha256(String value) {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
		}
		catch (Exception ex) {
			throw new IllegalStateException("SHA-256 is unavailable", ex);
		}
	}

	private static LocalDateTime toLocalDateTime(Instant instant) {
		return LocalDateTime.ofInstant(instant, ZoneId.systemDefault());
	}

	private static String normalizeUsername(String username) {
		return username == null ? "" : username.trim();
	}

	private static String normalizeEmail(String email) {
		return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
	}

	private static void requirePassword(String password) {
		if (password == null || password.length() < 6) {
			throw new IllegalArgumentException("密码至少需要 6 位");
		}
	}

	private static String clientIp(HttpServletRequest request) {
		String forwarded = request.getHeader("X-Forwarded-For");
		if (forwarded != null && !forwarded.isBlank()) {
			return forwarded.split(",")[0].trim();
		}
		return request.getRemoteAddr();
	}

	private static String userAgent(HttpServletRequest request) {
		String value = request.getHeader("User-Agent");
		return value == null || value.length() <= 512 ? value : value.substring(0, 512);
	}

	private record UserAccess(AppUser user, List<Role> roles, List<Permission> permissions) {
	}

	private record TokenBundle(String accessToken, String refreshToken, Instant accessExpiresAt,
			Instant refreshExpiresAt, SessionPolicyResponse sessionPolicy) {
	}

	private record RolePermissionAssignment(String roleId, Permission permission) {
	}
}
