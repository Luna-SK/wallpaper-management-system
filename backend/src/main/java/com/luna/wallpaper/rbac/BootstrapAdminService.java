package com.luna.wallpaper.rbac;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.luna.wallpaper.config.BootstrapProperties;
import com.luna.wallpaper.settings.SystemSettingService;

@Component
public class BootstrapAdminService implements ApplicationRunner {

	private static final Logger log = LoggerFactory.getLogger(BootstrapAdminService.class);
	private static final String ADMIN_PASSWORD_APPLIED = "bootstrap.admin_password_applied";
	private static final String DEMO_USERS_POLICY_APPLIED = "bootstrap.demo_users_policy_applied";
	private static final List<String> DEMO_USERS = List.of("manager", "editor", "viewer");

	private final BootstrapProperties properties;
	private final AppUserMapper users;
	private final PasswordEncoder passwordEncoder;
	private final SystemSettingService settings;

	BootstrapAdminService(BootstrapProperties properties, AppUserMapper users, PasswordEncoder passwordEncoder,
			SystemSettingService settings) {
		this.properties = properties;
		this.users = users;
		this.passwordEncoder = passwordEncoder;
		this.settings = settings;
	}

	@Override
	@Transactional
	public void run(ApplicationArguments args) {
		applyInitialAdminPassword();
		applyDemoUserPolicy();
	}

	private void applyInitialAdminPassword() {
		if (!properties.hasAdminPassword() || settingEnabled(ADMIN_PASSWORD_APPLIED)) {
			return;
		}
		AppUser admin = users.selectByUsername("admin");
		if (admin == null) {
			log.warn("APP_BOOTSTRAP_ADMIN_PASSWORD was provided, but the built-in admin user was not found");
			settings.put(ADMIN_PASSWORD_APPLIED, "true");
			return;
		}
		admin.changePasswordHash(passwordEncoder.encode(properties.requiredAdminPassword()));
		users.updateById(admin);
		settings.put(ADMIN_PASSWORD_APPLIED, "true");
		log.info("Applied one-time bootstrap password for admin");
	}

	private void applyDemoUserPolicy() {
		if (properties.safeDemoUsersEnabled() || settingEnabled(DEMO_USERS_POLICY_APPLIED)) {
			return;
		}
		DEMO_USERS.forEach(username -> {
			AppUser user = users.selectByUsername(username);
			if (user == null || user.status() == UserStatus.DISABLED) {
				return;
			}
			user.disable();
			users.updateById(user);
		});
		settings.put(DEMO_USERS_POLICY_APPLIED, "true");
		log.info("Disabled built-in demo users for production bootstrap");
	}

	private boolean settingEnabled(String key) {
		return Boolean.parseBoolean(settings.get(key, "false"));
	}
}
