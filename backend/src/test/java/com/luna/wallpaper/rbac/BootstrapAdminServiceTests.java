package com.luna.wallpaper.rbac;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.luna.wallpaper.config.BootstrapProperties;
import com.luna.wallpaper.settings.SystemSettingService;

class BootstrapAdminServiceTests {

	private final AppUserMapper users = mock(AppUserMapper.class);
	private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
	private final SystemSettingService settings = mock(SystemSettingService.class);

	@Test
	void appliesGeneratedAdminPasswordOnce() {
		AppUser admin = new AppUser("admin", "Administrator", "admin@example.test", null, "old");
		when(settings.get("bootstrap.admin_password_applied", "false")).thenReturn("false");
		when(users.selectByUsername("admin")).thenReturn(admin);
		when(passwordEncoder.encode("generated-secret")).thenReturn("{bcrypt}generated-secret");

		service(new BootstrapProperties(" generated-secret ", true)).run(null);

		assertThat(admin.passwordHash()).isEqualTo("{bcrypt}generated-secret");
		verify(users).updateById(admin);
		verify(settings).put("bootstrap.admin_password_applied", "true");
	}

	@Test
	void skipsAdminPasswordWhenAlreadyApplied() {
		when(settings.get("bootstrap.admin_password_applied", "false")).thenReturn("true");

		service(new BootstrapProperties("generated-secret", true)).run(null);

		verify(users, never()).selectByUsername("admin");
		verify(passwordEncoder, never()).encode(org.mockito.ArgumentMatchers.anyString());
	}

	@Test
	void disablesDemoUsersWhenProductionPolicyRequiresIt() {
		AppUser manager = new AppUser("manager", "Manager", "manager@example.test", null, "old");
		AppUser editor = new AppUser("editor", "Editor", "editor@example.test", null, "old");
		AppUser viewer = new AppUser("viewer", "Viewer", "viewer@example.test", null, "old");
		when(settings.get("bootstrap.demo_users_policy_applied", "false")).thenReturn("false");
		when(users.selectByUsername("manager")).thenReturn(manager);
		when(users.selectByUsername("editor")).thenReturn(editor);
		when(users.selectByUsername("viewer")).thenReturn(viewer);

		service(new BootstrapProperties(null, false)).run(null);

		assertThat(manager.status()).isEqualTo(UserStatus.DISABLED);
		assertThat(editor.status()).isEqualTo(UserStatus.DISABLED);
		assertThat(viewer.status()).isEqualTo(UserStatus.DISABLED);
		verify(settings).put("bootstrap.demo_users_policy_applied", "true");
	}

	@Test
	void keepsDemoUsersEnabledWhenPropertyIsMissing() {
		service(new BootstrapProperties(null, null)).run(null);

		verify(users, never()).selectByUsername("manager");
	}

	private BootstrapAdminService service(BootstrapProperties properties) {
		return new BootstrapAdminService(properties, users, passwordEncoder, settings);
	}
}
