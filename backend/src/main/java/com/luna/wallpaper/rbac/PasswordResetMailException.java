package com.luna.wallpaper.rbac;

public class PasswordResetMailException extends RuntimeException {

	public PasswordResetMailException(String message) {
		super(message);
	}

	public PasswordResetMailException(String message, Throwable cause) {
		super(message, cause);
	}
}
