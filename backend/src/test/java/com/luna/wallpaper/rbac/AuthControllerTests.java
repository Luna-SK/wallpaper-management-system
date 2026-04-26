package com.luna.wallpaper.rbac;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import com.luna.wallpaper.TestcontainersConfiguration;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class AuthControllerTests {

	@Autowired
	private MockMvc mvc;

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private AppUserMapper users;

	@Autowired
	private RoleMapper roles;

	@Autowired
	private PermissionMapper permissions;

	@Autowired
	private UserRoleMapper userRoles;

	@Autowired
	private RolePermissionMapper rolePermissions;

	@Autowired
	private PasswordEncoder passwordEncoder;

	@Test
	void loginUsesStoredPasswordAndDynamicAdminPermissions() throws Exception {
		JsonNode data = login("admin", "admin123");

		assertThat(data.path("accessToken").asString()).isNotBlank();
		assertThat(data.path("refreshToken").asString()).isNotBlank();
		assertThat(permissionCodes(data)).contains("user:manage", "role:manage", "setting:manage");

		mvc.perform(get("/api/users").header("Authorization", bearer(accessToken(data))))
				.andExpect(status().isOk());
	}

	@Test
	void viewerCannotManageUsersAndDevelopmentTokenIsDisabledByDefault() throws Exception {
		JsonNode viewer = login("viewer", "admin123");

		mvc.perform(get("/api/users").header("Authorization", bearer(accessToken(viewer))))
				.andExpect(status().isForbidden());
		mvc.perform(get("/api/users").header("Authorization", bearer("test-development-token")))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void refreshRotatesTokenAndLogoutRevokesSession() throws Exception {
		JsonNode login = login("admin", "admin123");
		String oldRefreshToken = refreshToken(login);
		JsonNode refreshed = refresh(oldRefreshToken);

		assertThat(accessToken(refreshed)).isNotEqualTo(accessToken(login));
		assertThat(refreshToken(refreshed)).isNotEqualTo(oldRefreshToken);
		mvc.perform(post("/api/auth/refresh")
						.contentType(MediaType.APPLICATION_JSON)
						.content(json(Map.of("refreshToken", oldRefreshToken))))
				.andExpect(status().isUnauthorized());

		mvc.perform(post("/api/auth/logout").header("Authorization", bearer(accessToken(refreshed))))
				.andExpect(status().isOk());
		mvc.perform(get("/api/auth/me").header("Authorization", bearer(accessToken(refreshed))))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void registerCreatesViewerUser() throws Exception {
		JsonNode registered = register(uniqueUsername("viewer-reg"));

		assertThat(registered.path("user").path("roles").get(0).path("code").asString()).isEqualTo("VIEWER");
		assertThat(permissionCodes(registered)).contains("image:view");
		assertThat(permissionCodes(registered)).doesNotContain("user:manage");
	}

	@Test
	void passwordChangeRevokesOtherSessions() throws Exception {
		String username = uniqueUsername("pwd");
		JsonNode registered = register(username);
		JsonNode secondLogin = login(username, "admin123");

		mvc.perform(patch("/api/auth/password")
						.header("Authorization", bearer(accessToken(registered)))
						.contentType(MediaType.APPLICATION_JSON)
						.content(json(Map.of("currentPassword", "admin123", "newPassword", "newpass123"))))
				.andExpect(status().isOk());

		mvc.perform(get("/api/auth/me").header("Authorization", bearer(accessToken(secondLogin))))
				.andExpect(status().isUnauthorized());
		assertThat(login(username, "newpass123").path("username").asString()).isEqualTo(username);
	}

	@Test
	void rolePermissionChangesApplyToExistingAccessToken() throws Exception {
		Role role = new Role("AUTH_TEST_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8), "认证测试角色");
		roles.insert(role);
		Permission imageView = permission("image:view");
		Permission userManage = permission("user:manage");
		rolePermissions.insertBatch(role.id(), List.of(imageView.id()));

		String username = uniqueUsername("dynamic");
		AppUser user = new AppUser(username, "动态权限用户", null, null, passwordEncoder.encode("admin123"));
		users.insert(user);
		userRoles.insertBatch(user.id(), List.of(role.id()));
		JsonNode login = login(username, "admin123");

		mvc.perform(get("/api/users").header("Authorization", bearer(accessToken(login))))
				.andExpect(status().isForbidden());

		rolePermissions.insertBatch(role.id(), List.of(userManage.id()));

		mvc.perform(get("/api/users").header("Authorization", bearer(accessToken(login))))
				.andExpect(status().isOk());

		JsonNode admin = login("admin", "admin123");
		mvc.perform(post("/api/roles/{id}/disable", role.id()).header("Authorization", bearer(accessToken(admin))))
				.andExpect(status().isOk());
		mvc.perform(get("/api/users").header("Authorization", bearer(accessToken(login))))
				.andExpect(status().isForbidden());
		mvc.perform(post("/api/roles/{id}/enable", role.id()).header("Authorization", bearer(accessToken(admin))))
				.andExpect(status().isOk());
		mvc.perform(get("/api/users").header("Authorization", bearer(accessToken(login))))
				.andExpect(status().isOk());
	}

	@Test
	void userDisableEnableAndPurgeFollowLifecycleRules() throws Exception {
		JsonNode admin = login("admin", "admin123");
		JsonNode registered = register(uniqueUsername("purge-user"));
		String userId = registered.path("user").path("id").asString();

		mvc.perform(post("/api/users/{id}/disable", userId).header("Authorization", bearer(accessToken(admin))))
				.andExpect(status().isOk());
		assertThat(users.selectById(userId).status()).isEqualTo(UserStatus.DISABLED);
		mvc.perform(get("/api/auth/me").header("Authorization", bearer(accessToken(registered))))
				.andExpect(status().isUnauthorized());

		mvc.perform(post("/api/users/{id}/enable", userId).header("Authorization", bearer(accessToken(admin))))
				.andExpect(status().isOk());
		assertThat(users.selectById(userId).status()).isEqualTo(UserStatus.ACTIVE);
		mvc.perform(delete("/api/users/{id}/purge", userId).header("Authorization", bearer(accessToken(admin))))
				.andExpect(status().isBadRequest());

		mvc.perform(post("/api/users/{id}/disable", userId).header("Authorization", bearer(accessToken(admin))))
				.andExpect(status().isOk());
		mvc.perform(delete("/api/users/{id}/purge", userId).header("Authorization", bearer(accessToken(admin))))
				.andExpect(status().isOk());

		assertThat(users.selectById(userId)).isNull();
		assertThat(userRoles.selectByUserIds(List.of(userId))).isEmpty();
	}

	@Test
	void currentAndBuiltInUsersCannotBePurged() throws Exception {
		JsonNode admin = login("admin", "admin123");
		String adminId = admin.path("user").path("id").asString();

		mvc.perform(delete("/api/users/{id}/purge", adminId).header("Authorization", bearer(accessToken(admin))))
				.andExpect(status().isBadRequest());
		assertThat(users.selectById(adminId)).isNotNull();
	}

	@Test
	void rolePurgeRequiresDisabledUnusedCustomRole() throws Exception {
		JsonNode admin = login("admin", "admin123");
		Role activeRole = new Role("PURGE_ACTIVE_" + shortId(), "待删除启用角色");
		roles.insert(activeRole);
		mvc.perform(delete("/api/roles/{id}/purge", activeRole.id()).header("Authorization", bearer(accessToken(admin))))
				.andExpect(status().isBadRequest());

		Role referencedRole = new Role("PURGE_REF_" + shortId(), "待删除引用角色");
		referencedRole.disable();
		roles.insert(referencedRole);
		AppUser linkedUser = new AppUser(uniqueUsername("role-ref"), "角色引用用户", null, null,
				passwordEncoder.encode("admin123"));
		users.insert(linkedUser);
		userRoles.insertBatch(linkedUser.id(), List.of(referencedRole.id()));

		String conflict = mvc.perform(delete("/api/roles/{id}/purge", referencedRole.id())
						.header("Authorization", bearer(accessToken(admin))))
				.andExpect(status().isConflict())
				.andReturn()
				.getResponse()
				.getContentAsString();
		JsonNode conflictBody = objectMapper.readTree(conflict);
		assertThat(conflictBody.path("code").asString()).isEqualTo("REFERENCE_EXISTS");
		assertThat(conflictBody.path("data").path("userCount").asInt()).isEqualTo(1);
		assertThat(roles.selectById(referencedRole.id())).isNotNull();

		Role purgeableRole = new Role("PURGE_OK_" + shortId(), "可删除角色");
		purgeableRole.disable();
		roles.insert(purgeableRole);
		rolePermissions.insertBatch(purgeableRole.id(), List.of(permission("image:view").id()));
		mvc.perform(delete("/api/roles/{id}/purge", purgeableRole.id())
						.header("Authorization", bearer(accessToken(admin))))
				.andExpect(status().isOk());
		assertThat(roles.selectById(purgeableRole.id())).isNull();
	}

	@Test
	void builtInRolesCannotBePurged() throws Exception {
		JsonNode admin = login("admin", "admin123");
		Role viewer = roles.findByCode("VIEWER").orElseThrow();

		mvc.perform(delete("/api/roles/{id}/purge", viewer.id()).header("Authorization", bearer(accessToken(admin))))
				.andExpect(status().isBadRequest());
		assertThat(roles.selectById(viewer.id())).isNotNull();
	}

	private JsonNode login(String username, String password) throws Exception {
		return data(mvc.perform(post("/api/auth/login")
						.contentType(MediaType.APPLICATION_JSON)
						.content(json(Map.of("username", username, "password", password))))
				.andExpect(status().isOk())
				.andReturn()
				.getResponse()
				.getContentAsString());
	}

	private JsonNode register(String username) throws Exception {
		return data(mvc.perform(post("/api/auth/register")
						.contentType(MediaType.APPLICATION_JSON)
						.content(json(Map.of("username", username, "password", "admin123",
								"displayName", "测试用户"))))
				.andExpect(status().isOk())
				.andReturn()
				.getResponse()
				.getContentAsString());
	}

	private JsonNode refresh(String refreshToken) throws Exception {
		return data(mvc.perform(post("/api/auth/refresh")
						.contentType(MediaType.APPLICATION_JSON)
						.content(json(Map.of("refreshToken", refreshToken))))
				.andExpect(status().isOk())
				.andReturn()
				.getResponse()
				.getContentAsString());
	}

	private Permission permission(String code) {
		return permissions.selectOrdered().stream()
				.filter(permission -> permission.code().equals(code))
				.findFirst()
				.orElseThrow();
	}

	private JsonNode data(String json) throws Exception {
		return objectMapper.readTree(json).path("data");
	}

	private String json(Object value) throws Exception {
		return objectMapper.writeValueAsString(value);
	}

	private String accessToken(JsonNode data) {
		return data.path("accessToken").asString();
	}

	private String refreshToken(JsonNode data) {
		return data.path("refreshToken").asString();
	}

	private String bearer(String token) {
		return "Bearer " + token;
	}

	private List<String> permissionCodes(JsonNode data) {
		return data.path("permissions").values().stream().map(JsonNode::asString).toList();
	}

	private String uniqueUsername(String prefix) {
		return prefix + "-" + UUID.randomUUID().toString().substring(0, 8);
	}

	private String shortId() {
		return UUID.randomUUID().toString().replace("-", "").substring(0, 8);
	}
}
