package com.luna.wallpaper.settings;

public final class SoftDeleteCleanupSettings {

	public static final String RETENTION_DAYS = "soft_delete.retention_days";
	public static final String CLEANUP_ENABLED = "soft_delete.cleanup.enabled";
	public static final String CLEANUP_CRON = "soft_delete.cleanup.cron";
	public static final String DEFAULT_CLEANUP_CRON = "0 0 3 * * SUN";

	private SoftDeleteCleanupSettings() {
	}
}
