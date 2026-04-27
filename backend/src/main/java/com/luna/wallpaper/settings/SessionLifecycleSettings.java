package com.luna.wallpaper.settings;

public final class SessionLifecycleSettings {

	public static final String IDLE_TIMEOUT_ENABLED = "session.idle_timeout.enabled";
	public static final String IDLE_TIMEOUT_MINUTES = "session.idle_timeout_minutes";
	public static final String ABSOLUTE_LIFETIME_ENABLED = "session.absolute_lifetime.enabled";
	public static final String ABSOLUTE_LIFETIME_DAYS = "session.absolute_lifetime_days";

	public static final boolean DEFAULT_IDLE_TIMEOUT_ENABLED = true;
	public static final int DEFAULT_IDLE_TIMEOUT_MINUTES = 120;
	public static final int MIN_IDLE_TIMEOUT_MINUTES = 15;
	public static final int MAX_IDLE_TIMEOUT_MINUTES = 1440;

	public static final boolean DEFAULT_ABSOLUTE_LIFETIME_ENABLED = true;
	public static final int DEFAULT_ABSOLUTE_LIFETIME_DAYS = 7;
	public static final int MIN_ABSOLUTE_LIFETIME_DAYS = 1;
	public static final int MAX_ABSOLUTE_LIFETIME_DAYS = 30;

	private SessionLifecycleSettings() {
	}
}
