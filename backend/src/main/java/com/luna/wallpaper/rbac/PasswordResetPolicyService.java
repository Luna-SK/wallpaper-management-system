package com.luna.wallpaper.rbac;

import org.springframework.stereotype.Service;

import com.luna.wallpaper.rbac.AuthDtos.PasswordResetPolicyResponse;
import com.luna.wallpaper.settings.PasswordResetSettings;
import com.luna.wallpaper.settings.SystemSettingService;

@Service
class PasswordResetPolicyService {

	static final String DISABLED_MESSAGE = "邮件找回密码已关闭，请登录后使用原密码修改密码或联系管理员";

	private final SystemSettingService settings;

	PasswordResetPolicyService(SystemSettingService settings) {
		this.settings = settings;
	}

	boolean emailResetEnabled() {
		return Boolean.parseBoolean(settings.get(PasswordResetSettings.EMAIL_RESET_ENABLED,
				String.valueOf(PasswordResetSettings.DEFAULT_EMAIL_RESET_ENABLED)));
	}

	PasswordResetPolicyResponse response() {
		return new PasswordResetPolicyResponse(emailResetEnabled());
	}

	void requireEmailResetEnabled() {
		if (!emailResetEnabled()) {
			throw new IllegalArgumentException(DISABLED_MESSAGE);
		}
	}
}
