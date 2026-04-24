package edu.wzut.wallpaper.audit;

public record AuditRetentionSettings(
		int retentionDays,
		boolean archiveEnabled,
		String archiveCron,
		String archiveStorage,
		int batchSize
) {
}
