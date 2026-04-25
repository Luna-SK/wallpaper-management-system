package com.luna.wallpaper.settings;

import org.springframework.boot.servlet.autoconfigure.MultipartProperties;
import org.springframework.stereotype.Service;
import org.springframework.util.unit.DataSize;

@Service
public class UploadLimitService {

	private static final String MAX_FILE_SIZE_KEY = "upload.max_file_size_mb";
	private static final String MAX_BATCH_SIZE_KEY = "upload.max_batch_size_mb";
	private static final long BYTES_PER_MB = 1024L * 1024L;

	private final SystemSettingService settings;
	private final MultipartProperties multipartProperties;

	UploadLimitService(SystemSettingService settings, MultipartProperties multipartProperties) {
		this.settings = settings;
		this.multipartProperties = multipartProperties;
	}

	public UploadLimitSettings current() {
		UploadLimitSettings hardLimits = hardLimits();
		int maxFileSizeMb = clamp(readInt(MAX_FILE_SIZE_KEY, hardLimits.maxFileHardLimitMb()),
				1, hardLimits.maxFileHardLimitMb());
		int maxBatchSizeMb = clamp(readInt(MAX_BATCH_SIZE_KEY, hardLimits.maxBatchHardLimitMb()),
				maxFileSizeMb, hardLimits.maxBatchHardLimitMb());
		return new UploadLimitSettings(maxFileSizeMb, maxBatchSizeMb,
				hardLimits.maxFileHardLimitMb(), hardLimits.maxBatchHardLimitMb());
	}

	public UploadLimitSettings validateForSave(Integer maxFileSizeMb, Integer maxBatchSizeMb) {
		UploadLimitSettings hardLimits = hardLimits();
		if (maxFileSizeMb == null || maxFileSizeMb < 1) {
			throw new IllegalArgumentException("单文件上限必须至少为 1 MB");
		}
		if (maxFileSizeMb > hardLimits.maxFileHardLimitMb()) {
			throw new IllegalArgumentException("单文件上限不能超过系统硬上限 " + hardLimits.maxFileHardLimitMb() + " MB");
		}
		if (maxBatchSizeMb == null || maxBatchSizeMb < maxFileSizeMb) {
			throw new IllegalArgumentException("批量上传上限必须不小于单文件上限");
		}
		if (maxBatchSizeMb > hardLimits.maxBatchHardLimitMb()) {
			throw new IllegalArgumentException("批量上传上限不能超过系统硬上限 " + hardLimits.maxBatchHardLimitMb() + " MB");
		}
		return new UploadLimitSettings(maxFileSizeMb, maxBatchSizeMb,
				hardLimits.maxFileHardLimitMb(), hardLimits.maxBatchHardLimitMb());
	}

	private UploadLimitSettings hardLimits() {
		int maxBatchHardLimitMb = dataSizeToMb(multipartProperties.getMaxRequestSize());
		int maxFileHardLimitMb = Math.min(dataSizeToMb(multipartProperties.getMaxFileSize()), maxBatchHardLimitMb);
		return new UploadLimitSettings(maxFileHardLimitMb, maxBatchHardLimitMb,
				maxFileHardLimitMb, maxBatchHardLimitMb);
	}

	private int readInt(String key, int defaultValue) {
		try {
			String value = settings.get(key, String.valueOf(defaultValue));
			return value == null ? defaultValue : Integer.parseInt(value);
		}
		catch (NumberFormatException ex) {
			return defaultValue;
		}
	}

	private static int dataSizeToMb(DataSize dataSize) {
		long bytes = dataSize == null ? 0 : dataSize.toBytes();
		if (bytes <= 0) {
			return Integer.MAX_VALUE;
		}
		long rounded = (bytes + BYTES_PER_MB - 1) / BYTES_PER_MB;
		return rounded > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) rounded;
	}

	private static int clamp(int value, int min, int max) {
		return Math.max(min, Math.min(value, max));
	}

	public record UploadLimitSettings(int maxFileSizeMb, int maxBatchSizeMb,
			int maxFileHardLimitMb, int maxBatchHardLimitMb) {

		public long maxFileSizeBytes() {
			return maxFileSizeMb * BYTES_PER_MB;
		}

		public long maxBatchSizeBytes() {
			return maxBatchSizeMb * BYTES_PER_MB;
		}
	}
}
