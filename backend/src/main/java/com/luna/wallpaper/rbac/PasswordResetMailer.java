package com.luna.wallpaper.rbac;

import java.time.Instant;

abstract class PasswordResetMailer {

	abstract void send(AppUser user, String token, Instant expiresAt);
}
