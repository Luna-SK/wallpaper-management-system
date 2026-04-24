package edu.wzut.wallpaper.audit;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record AuditRetentionUpdateRequest(
		@Min(7) @Max(3650) int retentionDays,
		boolean archiveEnabled,
		@NotBlank String archiveCron,
		@NotBlank String archiveStorage,
		@Min(100) @Max(10000) int batchSize
) {
}
