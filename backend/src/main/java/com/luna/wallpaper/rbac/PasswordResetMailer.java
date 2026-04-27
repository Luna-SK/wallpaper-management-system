package com.luna.wallpaper.rbac;

import java.time.Instant;

interface PasswordResetMailer {

	void send(AppUser user, String token, Instant expiresAt);
}
